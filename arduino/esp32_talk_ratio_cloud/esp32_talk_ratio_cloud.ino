/**
 * ReadB - ESP32 Talk Ratio Lamp (CLOUD / 이동용)
 *
 * 배포 BE(Railway, HTTPS)를 폴링하므로 인터넷만 되면 어디서든 동작.
 * WiFi는 WiFiManager(캡티브 포털)로 — 카페를 옮겨도 펌웨어 재업로드 없이
 * 휴대폰으로 그 자리에서 새 WiFi를 입력하면 된다.
 *
 * 흐름:
 *   전원 ON → 저장된 WiFi로 자동 연결 시도
 *     - 성공 → 폴링 시작
 *     - 실패(=새 카페 등) → ESP32가 'ReadB-Lamp' AP 생성
 *         → 휴대폰으로 그 AP 접속 → 열리는 페이지에서 카페 WiFi 선택/입력
 *         → 저장 후 자동 연결 (다음부터는 자동)
 *
 * 필요 라이브러리 (Arduino IDE → 라이브러리 매니저):
 *   - ArduinoJson (Benoit Blanchon) v7+
 *   - WiFiManager (tzapu)
 *   - (LCD 쓸 때만) LiquidCrystal I2C (Frank de Brabander)
 *
 * 보드: ESP32 Dev Module
 *
 * 배선: esp32_talk_ratio_v2 와 동일
 *   초록 25 · 노랑 26 · 빨강 27 · 흰색 32 (각 220Ω → GND)
 *   [선택] LCD I2C: SDA 21 / SCL 22 / VCC 5V / GND
 */

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <WiFiManager.h>   // tzapu/WiFiManager

// ── LCD1602(I2C)가 있을 때만 주석 해제 (없으면 LED만 동작) ──
// #define USE_LCD
#ifdef USE_LCD
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#define LCD_ADDR 0x27
LiquidCrystal_I2C lcd(LCD_ADDR, 16, 2);
#endif

// ═══════════════════════════════════════════════════════
//  설정
// ═══════════════════════════════════════════════════════
// 배포 BE의 active 엔드포인트 (BE에 /talk-ratio/active 가 배포돼 있어야 함)
const char* API_URL = "https://be-production-de9a.up.railway.app/api/v1/talk-ratio/active";

// WiFi 설정 포털용 AP 이름/비번 (카페에서 휴대폰으로 접속)
const char* AP_NAME = "ReadB-Lamp";
const char* AP_PASS = "readb1234";    // 8자 이상

// 핀
const int GREEN_PIN = 25;
const int YELLOW_PIN = 26;
const int RED_PIN   = 27;
const int WHITE_PIN = 32;

// 동작 파라미터
const unsigned long POLL_INTERVAL_MS = 2000;
const int           HTTP_TIMEOUT_MS  = 4000;
// 일시적 끊김은 백그라운드 자동 재연결에 맡기고, 아주 오래(10분) 끊겼을 때만 재부팅(→포털 재진입).
// (장소 이동은 보통 전원을 껐다 켜므로 짧은 재부팅 타이머는 오히려 UX를 해침)
const unsigned long WIFI_LOST_RESTART_MS = 600000;

// 발화비율 밴드 (리더 %)
const double BAND_OK    = 50.0;
const double BAND_WATCH = 65.0;
const double BAND_WARN  = 75.0;

// 상태
unsigned long lastPollTime = 0;
unsigned long wifiLostSince = 0;
double currentLeaderRatio = 0.0;
double currentMemberRatio = 0.0;
bool   sessionActive = false;
bool   wifiOk = false;

// HTTPS 연결 재사용 — 매 폴링마다 새 TLS 핸드셰이크(1~3초·메모리 부담)를 피하기 위해 전역으로 두고 Keep-Alive.
WiFiClientSecure secureClient;
HTTPClient       http;

// ─── 유틸 / LED (v2와 동일) ─────────────────────────
int clampPwm(double v) { return v < 0 ? 0 : (v > 255 ? 255 : (int)v); }

double breath(unsigned long now, unsigned long periodMs) {
  double phase = (double)(now % periodMs) / (double)periodMs;
  return (sin(phase * 2.0 * PI) + 1.0) / 2.0;
}

void renderTrafficLeds(double r, unsigned long now) {
  double g = 0, y = 0, rd = 0;
  if (r < BAND_OK) {
    g = 255;
  } else if (r < BAND_WATCH) {
    double t = (r - BAND_OK) / (BAND_WATCH - BAND_OK);
    g = 255 * (1.0 - t); y = 255 * t;
  } else if (r < BAND_WARN) {
    double t = (r - BAND_WATCH) / (BAND_WARN - BAND_WATCH);
    y = 255 * (1.0 - 0.4 * t); rd = 255 * t;
  } else {
    double over = (r - BAND_WARN) / (100.0 - BAND_WARN);
    unsigned long period = (unsigned long)(900 - 500 * over);
    if (period < 250) period = 250;
    rd = 255 * (0.45 + 0.55 * breath(now, period));
  }
  analogWrite(GREEN_PIN, clampPwm(g));
  analogWrite(YELLOW_PIN, clampPwm(y));
  analogWrite(RED_PIN, clampPwm(rd));
}

void ledsOff() {
  analogWrite(GREEN_PIN, 0); analogWrite(YELLOW_PIN, 0); analogWrite(RED_PIN, 0);
}

void renderWhite(unsigned long now) {
  if (sessionActive && wifiOk) analogWrite(WHITE_PIN, 0);
  else if (wifiOk)            analogWrite(WHITE_PIN, clampPwm(20 + 80 * breath(now, 3000)));
  else                        analogWrite(WHITE_PIN, clampPwm(60 + 195 * breath(now, 700))); // 미연결: 빠른 호흡
}

// ─── LCD (선택) ─────────────────────────────────────
#ifdef USE_LCD
void lcdShowMsg(const char* l0, const char* l1) {
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print(l0);
  lcd.setCursor(0, 1); lcd.print(l1);
}
void lcdShowRatio() {
  char line0[17];
  snprintf(line0, sizeof(line0), "L:%3d%%   M:%3d%%",
           (int)(currentLeaderRatio + 0.5), (int)(currentMemberRatio + 0.5));
  lcd.setCursor(0, 0); lcd.print(line0);
  int blocks = (int)(currentLeaderRatio / 100.0 * 16.0 + 0.5);
  if (blocks < 0) blocks = 0; if (blocks > 16) blocks = 16;
  lcd.setCursor(0, 1);
  for (int i = 0; i < 16; i++) lcd.write(i < blocks ? (uint8_t)255 : ' ');
}
#else
inline void lcdShowMsg(const char*, const char*) {}
inline void lcdShowRatio() {}
#endif

// ─── HTTPS 폴링 ─────────────────────────────────────
bool pollTalkRatio() {
  if (WiFi.status() != WL_CONNECTED) return false;

  // 전역 secureClient/http 재사용 (Keep-Alive) — 매번 새 TLS 핸드셰이크 방지
  if (!http.begin(secureClient, API_URL)) { Serial.println("[HTTP] begin fail"); return false; }
  http.addHeader("Accept", "application/json");

  int code = http.GET();
  if (code != 200) { Serial.printf("[HTTP] code: %d\n", code); http.end(); return false; }

  String payload = http.getString();
  http.end();

  JsonDocument doc;
  if (deserializeJson(doc, payload)) { Serial.println("[JSON] parse error"); return false; }
  if (!(doc["success"] | false)) { Serial.println("[API] success=false"); return false; }

  sessionActive = doc["data"]["active"] | false;
  currentLeaderRatio = doc["data"]["leaderRatio"] | 0.0;
  currentMemberRatio = doc["data"]["memberRatio"] | 0.0;
  Serial.printf("[Talk] active=%d L:%.1f%% M:%.1f%%\n",
                sessionActive, currentLeaderRatio, currentMemberRatio);
  return true;
}

// ═══════════════════════════════════════════════════════
//  setup / loop
// ═══════════════════════════════════════════════════════
void setup() {
  Serial.begin(115200);
  Serial.println("\n=== ReadB ESP32 Talk Ratio Lamp (CLOUD) ===");

  pinMode(GREEN_PIN, OUTPUT); pinMode(YELLOW_PIN, OUTPUT);
  pinMode(RED_PIN, OUTPUT);   pinMode(WHITE_PIN, OUTPUT);
  ledsOff();

  // HTTPS 클라이언트 1회 설정 후 재사용
  secureClient.setInsecure();      // 인증서 검증 생략 (데모/내부용)
  http.setReuse(true);             // 연결 재사용(Keep-Alive)
  http.setTimeout(HTTP_TIMEOUT_MS);

#ifdef USE_LCD
  Wire.begin(21, 22);
  lcd.init(); lcd.backlight();
#endif
  lcdShowMsg("ReadB Lamp", "WiFi setup...");

  // WiFi 연결 또는 설정 포털 — 연결될 때까지 흰 LED 켜두기
  analogWrite(WHITE_PIN, 200);
  WiFiManager wm;
  wm.setConfigPortalTimeout(180);  // 3분간 폰 입력 없으면 종료 후 재시도
  bool ok = wm.autoConnect(AP_NAME, AP_PASS);
  analogWrite(WHITE_PIN, 0);

  if (!ok) {
    Serial.println("[WiFi] config timeout, restart");
    lcdShowMsg("WiFi setup", "timeout, retry");
    delay(1500);
    ESP.restart();
  }
  wifiOk = true;
  WiFi.setAutoReconnect(true);
  Serial.print("[WiFi] OK, IP: "); Serial.println(WiFi.localIP());
  lcdShowMsg("ReadB Lamp", "Waiting...");
}

void loop() {
  unsigned long now = millis();

  if (WiFi.status() != WL_CONNECTED) {
    wifiOk = false; sessionActive = false;
    ledsOff(); renderWhite(now);
    if (wifiLostSince == 0) wifiLostSince = now;
    else if (now - wifiLostSince > WIFI_LOST_RESTART_MS) {
      Serial.println("[WiFi] lost too long (10min), restart (re-open portal if needed)");
      ESP.restart();   // 새 장소면 재부팅 후 포털 자동 오픈
    }
    delay(20);   // 짧게 — 미연결 흰 LED 호흡을 부드럽게 유지
    return;
  }
  wifiOk = true; wifiLostSince = 0;

  if (now - lastPollTime >= POLL_INTERVAL_MS) {
    lastPollTime = now;
    bool ok = pollTalkRatio();
    if (ok) {
      if (sessionActive) lcdShowRatio();
      else lcdShowMsg("ReadB Lamp", "Waiting...");
    }
  }

  if (sessionActive) {
    renderTrafficLeds(currentLeaderRatio, now);
    analogWrite(WHITE_PIN, 0);
  } else {
    ledsOff();
    renderWhite(now);
  }

  delay(20);
}
