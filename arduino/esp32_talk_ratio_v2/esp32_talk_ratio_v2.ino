/**
 * ReadB - ESP32 Talk Ratio Lamp v2 (3색 신호등 + 흰색 상태등 + I2C LCD1602)
 *
 * 키트 부품만 사용: 단색 LED(빨/노/초) + 흰색 LED + LCD1602(I2C 백팩, QAPASS).
 * NeoPixel 없이 ESP32 PWM(analogWrite)으로 색을 '크로스페이드'해서 4단계 그라데이션 효과를 낸다.
 *
 * 아키텍처:
 *   Browser → Backend(AudioAnalyzer) → TalkSession(in-memory)
 *                                           ↓
 *                              GET /api/v1/meetings/{id}/talk-ratio/current
 *                                           ↓
 *                                   ESP32 (HTTP 2초 폴링)
 *                                           ↓
 *                       신호등 LED(PWM 크로스페이드) + 흰색 상태등 + LCD1602
 *
 * 필요 라이브러리 (Arduino IDE → 라이브러리 매니저):
 *   - ArduinoJson (Benoit Blanchon) v7+
 *   - LiquidCrystal I2C (Frank de Brabander)   // I2C LCD1602
 *
 * 보드: ESP32 Dev Module
 *
 * ── 배선 ────────────────────────────────────────────
 *   초록 LED  : GPIO 25 → 220Ω → LED(+) → GND
 *   노랑 LED  : GPIO 26 → 220Ω → LED(+) → GND
 *   빨강 LED  : GPIO 27 → 220Ω → LED(+) → GND
 *   흰색 LED  : GPIO 32 → 220Ω → LED(+) → GND   (상태등)
 *   LCD(I2C)  : SDA→GPIO21, SCL→GPIO22, VCC→5V(VIN), GND→GND
 *   ※ LCD가 안 보이면 백팩의 파란 가변저항(대비)을 돌리고, 주소를 0x27↔0x3F로 바꿔본다.
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>

// ═══════════════════════════════════════════════════════
//  사용자 설정 (반드시 변경!)
// ═══════════════════════════════════════════════════════
const char* WIFI_SSID     = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

const char* BACKEND_HOST  = "192.168.0.100";  // 서버 PC 내부 IP (같은 WiFi)
const int   BACKEND_PORT  = 8080;
// 미팅 ID 불필요 — 서버가 '현재 진행 중인 세션'을 알아서 돌려줌 (전원만 꽂으면 어떤 미팅이든 자동 추적)

// LCD I2C 주소 — 보통 0x27, 안 되면 0x3F
#define LCD_ADDR 0x27

// ═══════════════════════════════════════════════════════
//  핀
// ═══════════════════════════════════════════════════════
const int GREEN_PIN = 25;
const int YELLOW_PIN = 26;
const int RED_PIN   = 27;
const int WHITE_PIN = 32;

// ═══════════════════════════════════════════════════════
//  동작 파라미터
// ═══════════════════════════════════════════════════════
const unsigned long POLL_INTERVAL_MS = 2000;  // 서버 폴링 주기
const unsigned long WIFI_RETRY_MS    = 5000;
const int           HTTP_TIMEOUT_MS  = 3000;
const int           MAX_ERRORS       = 10;

// 발화비율 밴드 경계 (리더 %)
const double BAND_OK     = 50.0;   // <50  적정 (초록)
const double BAND_WATCH  = 65.0;   // 50~65 관찰 (초록→노랑)
const double BAND_WARN   = 75.0;   // 65~75 주의 (노랑→빨강=주황) / >75 과다(빨강)

LiquidCrystal_I2C lcd(LCD_ADDR, 16, 2);

// ─── 상태 ───────────────────────────────────────────
unsigned long lastPollTime = 0;
double currentLeaderRatio  = 0.0;
double currentMemberRatio  = 0.0;
bool   sessionActive       = false;
int    consecutiveErrors   = 0;
bool   wifiOk              = false;

// ─── 유틸 ───────────────────────────────────────────
int clampPwm(double v) {
  if (v < 0) return 0;
  if (v > 255) return 255;
  return (int)v;
}

// 0~1 호흡(사인) — 주기 periodMs
double breath(unsigned long now, unsigned long periodMs) {
  double phase = (double)(now % periodMs) / (double)periodMs;  // 0~1
  return (sin(phase * 2.0 * PI) + 1.0) / 2.0;                  // 0~1
}

// ─── 신호등 LED: 비율 → 색 크로스페이드 (매 루프 호출) ──
void renderTrafficLeds(double r, unsigned long now) {
  double g = 0, y = 0, rd = 0;  // 0~255

  if (r < BAND_OK) {
    // 적정: 초록
    g = 255;
  } else if (r < BAND_WATCH) {
    // 관찰: 초록 → 노랑 크로스페이드
    double t = (r - BAND_OK) / (BAND_WATCH - BAND_OK);  // 0~1
    g = 255 * (1.0 - t);
    y = 255 * t;
  } else if (r < BAND_WARN) {
    // 주의: 노랑 + 빨강(상승) = 주황
    double t = (r - BAND_WATCH) / (BAND_WARN - BAND_WATCH); // 0~1
    y = 255 * (1.0 - 0.4 * t);   // 노랑 약간 줄이며
    rd = 255 * t;                // 빨강 올림
  } else {
    // 과다: 빨강 + 호흡(비율 높을수록 빠르게)
    double over = (r - BAND_WARN) / (100.0 - BAND_WARN);    // 0~1
    unsigned long period = (unsigned long)(900 - 500 * over); // 900→400ms
    if (period < 250) period = 250;
    double b = 0.45 + 0.55 * breath(now, period);          // 0.45~1.0
    rd = 255 * b;
  }

  analogWrite(GREEN_PIN, clampPwm(g));
  analogWrite(YELLOW_PIN, clampPwm(y));
  analogWrite(RED_PIN, clampPwm(rd));
}

void ledsOff() {
  analogWrite(GREEN_PIN, 0);
  analogWrite(YELLOW_PIN, 0);
  analogWrite(RED_PIN, 0);
}

// 흰색 상태등: 대기 중 은은한 호흡 / 세션 중 OFF
void renderWhite(unsigned long now) {
  if (sessionActive && wifiOk) {
    analogWrite(WHITE_PIN, 0);
  } else if (wifiOk) {
    analogWrite(WHITE_PIN, clampPwm(20 + 80 * breath(now, 3000)));  // 대기 호흡
  } else {
    analogWrite(WHITE_PIN, 0);
  }
}

// ─── LCD ────────────────────────────────────────────
void lcdShowBoot() {
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print("ReadB Talk Ratio");
  lcd.setCursor(0, 1); lcd.print("Booting...");
}

void lcdShowWaiting() {
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print("ReadB Talk Ratio");
  lcd.setCursor(0, 1); lcd.print(wifiOk ? "Waiting session" : "WiFi connect...");
}

void lcdShowRatio() {
  // Row0: L:65%  M:35%
  char line0[17];
  snprintf(line0, sizeof(line0), "L:%3d%%   M:%3d%%",
           (int)(currentLeaderRatio + 0.5), (int)(currentMemberRatio + 0.5));
  lcd.setCursor(0, 0);
  lcd.print(line0);

  // Row1: 16칸 막대 (리더 비율) — 채워진 칸은 풀블록(255)
  int blocks = (int)(currentLeaderRatio / 100.0 * 16.0 + 0.5);
  if (blocks < 0) blocks = 0; if (blocks > 16) blocks = 16;
  lcd.setCursor(0, 1);
  for (int i = 0; i < 16; i++) lcd.write(i < blocks ? (uint8_t)255 : ' ');
}

// ─── WiFi ───────────────────────────────────────────
void connectWiFi() {
  Serial.print("[WiFi] connecting: "); Serial.println(WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500); Serial.print("."); attempts++;
  }
  wifiOk = (WiFi.status() == WL_CONNECTED);
  Serial.println();
  if (wifiOk) {
    Serial.print("[WiFi] OK, IP: "); Serial.println(WiFi.localIP());
  } else {
    Serial.println("[WiFi] FAILED, retry later");
  }
}

// ─── HTTP 폴링 ──────────────────────────────────────
bool pollTalkRatio() {
  if (WiFi.status() != WL_CONNECTED) { wifiOk = false; return false; }

  HTTPClient http;
  // 미팅 ID 없이 '현재 진행 중인' 세션을 폴링 (서버가 활성 세션 판단)
  String url = String("http://") + BACKEND_HOST + ":" + BACKEND_PORT
             + "/api/v1/talk-ratio/active";
  http.begin(url);
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.addHeader("Accept", "application/json");

  int code = http.GET();
  if (code != 200) {
    Serial.printf("[HTTP] code: %d\n", code);
    http.end();
    return false;
  }

  String payload = http.getString();
  http.end();

  JsonDocument doc;
  if (deserializeJson(doc, payload)) { Serial.println("[JSON] parse error"); return false; }
  if (!(doc["success"] | false)) { Serial.println("[API] success=false"); return false; }

  // 응답: {"data":{"active":true,"meetingId":85,"leaderRatio":65.3,"memberRatio":34.7}}
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
  Serial.println("\n=== ReadB ESP32 Talk Ratio Lamp v2 ===");

  pinMode(GREEN_PIN, OUTPUT);
  pinMode(YELLOW_PIN, OUTPUT);
  pinMode(RED_PIN, OUTPUT);
  pinMode(WHITE_PIN, OUTPUT);
  ledsOff();

  Wire.begin(21, 22);   // ESP32 기본 I2C: SDA=21, SCL=22
  lcd.init();
  lcd.backlight();
  lcdShowBoot();

  connectWiFi();
  lcdShowWaiting();
}

void loop() {
  unsigned long now = millis();

  // WiFi 끊기면 재연결
  if (WiFi.status() != WL_CONNECTED) {
    wifiOk = false;
    sessionActive = false;
    ledsOff();
    renderWhite(now);
    lcdShowWaiting();
    connectWiFi();
    delay(WIFI_RETRY_MS);
    return;
  }
  wifiOk = true;

  // 주기적 서버 폴링 + LCD 갱신
  if (now - lastPollTime >= POLL_INTERVAL_MS) {
    lastPollTime = now;
    bool ok = pollTalkRatio();
    if (ok) {
      consecutiveErrors = 0;
      if (sessionActive) lcdShowRatio();
      else lcdShowWaiting();
    } else if (++consecutiveErrors >= MAX_ERRORS) {
      Serial.println("[Error] too many errors, reconnect WiFi");
      WiFi.disconnect(); delay(1000); connectWiFi(); consecutiveErrors = 0;
    }
  }

  // LED는 매 루프 갱신 (크로스페이드·호흡 부드럽게)
  if (sessionActive) {
    renderTrafficLeds(currentLeaderRatio, now);
    analogWrite(WHITE_PIN, 0);
  } else {
    ledsOff();
    renderWhite(now);
  }

  delay(20);  // ~50fps
}
