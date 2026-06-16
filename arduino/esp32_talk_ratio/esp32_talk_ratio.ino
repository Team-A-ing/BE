/**
 * ReadB - ESP32 Talk Ratio LED Controller
 *
 * WiFi 기반으로 백엔드 서버를 폴링하여 실시간 발화 비율을 가져오고,
 * LED 색상을 변경하여 리더/멤버 발화 균형 상태를 시각적으로 표시합니다.
 *
 * 아키텍처:
 *   Browser → Backend(AudioAnalyzer) → TalkSession(in-memory)
 *                                           ↓
 *                              GET /talk-ratio/current
 *                                           ↓
 *                                   ESP32 (HTTP Poll)
 *                                           ↓
 *                                    LED (R/G) + OLED
 *
 * 필요 라이브러리:
 *   - ArduinoJson (by Benoit Blanchon) v7+
 *   - [OLED] Adafruit SSD1306
 *   - [OLED] Adafruit GFX Library
 *
 * 보드 설정: ESP32 Dev Module (Arduino IDE → 보드 매니저 → esp32)
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// ─── OLED 사용 시 주석 해제 ─────────────────────────
// #define USE_OLED
#ifdef USE_OLED
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#define OLED_WIDTH  128
#define OLED_HEIGHT  64
#define OLED_RESET   -1
Adafruit_SSD1306 display(OLED_WIDTH, OLED_HEIGHT, &Wire, OLED_RESET);
#endif

// ═══════════════════════════════════════════════════════
//  사용자 설정 (반드시 변경!)
// ═══════════════════════════════════════════════════════

// WiFi 설정
const char* WIFI_SSID     = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// 백엔드 서버 설정
const char* BACKEND_HOST  = "192.168.0.100";  // 서버 IP (같은 WiFi 네트워크)
const int   BACKEND_PORT  = 8080;
const long  MEETING_ID    = 1;                 // 현재 미팅 ID

// ═══════════════════════════════════════════════════════
//  하드웨어 핀 설정
// ═══════════════════════════════════════════════════════

const int RED_PIN   = 25;   // ESP32 GPIO 25 → 빨간 LED (+)
const int GREEN_PIN = 26;   // ESP32 GPIO 26 → 초록 LED (+)

// ═══════════════════════════════════════════════════════
//  동작 파라미터
// ═══════════════════════════════════════════════════════

const double LEADER_RATIO_THRESHOLD = 70.0;  // 리더 발화 비율 경고 기준(%)
const unsigned long POLL_INTERVAL_MS = 2000; // 폴링 주기 (2초)
const unsigned long WIFI_RETRY_MS   = 5000;  // WiFi 재연결 대기 (5초)
const int HTTP_TIMEOUT_MS           = 3000;  // HTTP 타임아웃 (3초)

// ═══════════════════════════════════════════════════════
//  내부 상태
// ═══════════════════════════════════════════════════════

unsigned long lastPollTime = 0;
double currentLeaderRatio  = 0.0;
double currentMemberRatio  = 0.0;
bool   sessionActive       = false;
int    consecutiveErrors   = 0;
const int MAX_ERRORS       = 10;  // 연속 에러 시 WiFi 재연결

// ─── WiFi 연결 ──────────────────────────────────────

void connectWiFi() {
  Serial.print("[WiFi] 연결 중: ");
  Serial.println(WIFI_SSID);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.print("[WiFi] 연결 성공! IP: ");
    Serial.println(WiFi.localIP());
    blinkLed(GREEN_PIN, 3);  // 연결 성공 표시
  } else {
    Serial.println();
    Serial.println("[WiFi] 연결 실패. 재시도합니다...");
    blinkLed(RED_PIN, 5);    // 연결 실패 표시
  }
}

// ─── LED 제어 ───────────────────────────────────────

void setLedState(bool leaderOverThreshold) {
  if (leaderOverThreshold) {
    // 리더 과다 발화 → 빨간불 (경고)
    digitalWrite(RED_PIN, HIGH);
    digitalWrite(GREEN_PIN, LOW);
  } else {
    // 균형 잡힌 대화 → 초록불 (정상)
    digitalWrite(RED_PIN, LOW);
    digitalWrite(GREEN_PIN, HIGH);
  }
}

void blinkLed(int pin, int times) {
  for (int i = 0; i < times; i++) {
    digitalWrite(pin, HIGH);
    delay(150);
    digitalWrite(pin, LOW);
    delay(150);
  }
}

void setIdleLed() {
  // 세션 미활성 → 두 LED 모두 OFF (또는 녹색 약하게)
  digitalWrite(RED_PIN, LOW);
  digitalWrite(GREEN_PIN, LOW);
}

// ─── OLED 표시 ──────────────────────────────────────

#ifdef USE_OLED
void initOled() {
  if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    Serial.println("[OLED] 초기화 실패!");
    return;
  }
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("ReadB IoT");
  display.println("Initializing...");
  display.display();
}

void updateOled() {
  display.clearDisplay();

  // 상단 제목
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.print("ReadB Talk Ratio");

  if (!sessionActive) {
    display.setCursor(0, 20);
    display.setTextSize(1);
    display.print("Waiting for");
    display.setCursor(0, 32);
    display.print("meeting session...");
    display.display();
    return;
  }

  // 리더 비율 숫자 (큰 글씨)
  display.setTextSize(2);
  display.setCursor(0, 14);
  display.print("L:");
  display.print(currentLeaderRatio, 1);
  display.print("%");

  // 멤버 비율
  display.setCursor(0, 34);
  display.print("M:");
  display.print(currentMemberRatio, 1);
  display.print("%");

  // 하단 상태 바
  display.setTextSize(1);
  display.setCursor(0, 56);
  int barWidth = (int)(currentLeaderRatio * 128.0 / 100.0);
  barWidth = constrain(barWidth, 0, 128);
  display.fillRect(0, 55, barWidth, 9, SSD1306_WHITE);

  // 상태 텍스트 (바 위에)
  if (currentLeaderRatio > LEADER_RATIO_THRESHOLD) {
    display.setTextColor(SSD1306_BLACK);
    display.setCursor(2, 56);
    display.print("LEADER OVER");
  } else {
    display.setCursor(barWidth + 2, 56);
    display.setTextColor(SSD1306_WHITE);
    display.print("BALANCED");
  }

  display.setTextColor(SSD1306_WHITE);
  display.display();
}
#endif

// ─── HTTP 폴링 ──────────────────────────────────────

bool pollTalkRatio() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[HTTP] WiFi 미연결. 스킵.");
    return false;
  }

  HTTPClient http;
  String url = String("http://") + BACKEND_HOST + ":" + BACKEND_PORT
             + "/api/v1/meetings/" + MEETING_ID + "/talk-ratio/current";

  http.begin(url);
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.addHeader("Accept", "application/json");

  int httpCode = http.GET();

  if (httpCode == 200) {
    String payload = http.getString();

    // JSON 파싱
    // 응답 형식: {"success":true,"code":"SUCCESS","data":{"leaderRatio":65.3,"memberRatio":34.7}}
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, payload);

    if (err) {
      Serial.print("[JSON] 파싱 에러: ");
      Serial.println(err.c_str());
      http.end();
      return false;
    }

    bool success = doc["success"] | false;
    if (!success) {
      Serial.println("[API] 서버 응답 실패");
      http.end();
      return false;
    }

    double leader = doc["data"]["leaderRatio"] | 0.0;
    double member = doc["data"]["memberRatio"] | 0.0;

    // 세션 활성 여부 판단: 둘 다 0이면 미시작
    sessionActive = (leader > 0.0 || member > 0.0);
    currentLeaderRatio = leader;
    currentMemberRatio = member;

    Serial.printf("[Talk] Leader: %.1f%% | Member: %.1f%% | %s\n",
                  leader, member,
                  leader > LEADER_RATIO_THRESHOLD ? "RED" : "GREEN");

    http.end();
    return true;
  } else {
    Serial.printf("[HTTP] 에러 코드: %d\n", httpCode);
    http.end();
    return false;
  }
}

// ═══════════════════════════════════════════════════════
//  Arduino 메인
// ═══════════════════════════════════════════════════════

void setup() {
  Serial.begin(115200);
  Serial.println();
  Serial.println("=== ReadB ESP32 Talk Ratio LED ===");

  // LED 핀 초기화
  pinMode(RED_PIN, OUTPUT);
  pinMode(GREEN_PIN, OUTPUT);
  setIdleLed();

  // OLED 초기화
  #ifdef USE_OLED
  initOled();
  #endif

  // WiFi 연결
  connectWiFi();
}

void loop() {
  // WiFi 연결 확인 및 재연결
  if (WiFi.status() != WL_CONNECTED) {
    setIdleLed();
    Serial.println("[WiFi] 연결 끊김. 재연결...");
    connectWiFi();
    delay(WIFI_RETRY_MS);
    return;
  }

  // 폴링 주기 확인
  unsigned long now = millis();
  if (now - lastPollTime < POLL_INTERVAL_MS) {
    return;
  }
  lastPollTime = now;

  // 서버 폴링
  bool ok = pollTalkRatio();

  if (ok) {
    consecutiveErrors = 0;

    if (sessionActive) {
      // LED 업데이트
      setLedState(currentLeaderRatio > LEADER_RATIO_THRESHOLD);
    } else {
      // 세션 미시작 → 대기 상태
      setIdleLed();
    }

    #ifdef USE_OLED
    updateOled();
    #endif
  } else {
    consecutiveErrors++;

    if (consecutiveErrors >= MAX_ERRORS) {
      Serial.println("[Error] 연속 에러 한도 초과. WiFi 재연결...");
      WiFi.disconnect();
      delay(1000);
      connectWiFi();
      consecutiveErrors = 0;
    }
  }
}
