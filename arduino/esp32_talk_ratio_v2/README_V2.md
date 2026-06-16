# ReadB ESP32 Talk Ratio Lamp v2 — 셋업 가이드

키트 부품(단색 LED 빨/노/초 + 흰색 LED + I2C LCD1602)만으로 만드는 발화비율 램프.
NeoPixel 없이 **ESP32 PWM 크로스페이드**로 초록→노랑→주황→빨강 그라데이션 효과를 낸다.

> ESP32 단독 동작(WiFi). 아두이노 UNO는 WiFi가 없어 이 용도엔 사용하지 않음.
> 폴링 엔드포인트: `GET /api/v1/talk-ratio/active` — **미팅 ID 불필요**. 서버가 현재 진행 중인 세션을 알아서 돌려주므로, 펌웨어를 한 번만 올리면 이후 어떤 미팅이든 ESP32에 전원만 꽂으면 자동 추적된다. (인증 불필요 — permitAll)

## 1. 부품
| 부품 | 수량 |
|------|------|
| ESP32 DevKit | 1 |
| 단색 LED 초록/노랑/빨강 | 각 1 |
| 흰색 LED (상태등) | 1 |
| 220Ω 저항 | 4 |
| I2C LCD1602 (QAPASS, 뒤 4핀 백팩) | 1 |
| 브레드보드 + 점퍼 | — |

## 2. 배선
```
초록 LED : GPIO 25 ─ 220Ω ─ LED(+) ─ GND
노랑 LED : GPIO 26 ─ 220Ω ─ LED(+) ─ GND
빨강 LED : GPIO 27 ─ 220Ω ─ LED(+) ─ GND
흰색 LED : GPIO 32 ─ 220Ω ─ LED(+) ─ GND

LCD1602(I2C):  SDA → GPIO 21
               SCL → GPIO 22
               VCC → 5V (VIN 핀)
               GND → GND
```
- LED 긴 다리(+)가 GPIO 쪽, 짧은 다리(−)가 GND.
- LCD 화면이 안 보이면 백팩의 **파란 가변저항(대비)** 을 돌린다. 그래도 안 되면 펌웨어의 `LCD_ADDR`을 `0x27 → 0x3F`로.

## 3. Arduino IDE
1. ESP32 보드 매니저 설치 (Boards Manager → "esp32" by Espressif)
2. 보드: **ESP32 Dev Module**
3. 라이브러리 매니저에서 설치:
   - **ArduinoJson** (Benoit Blanchon) v7+
   - **LiquidCrystal I2C** (Frank de Brabander)

## 4. 펌웨어 설정 (`esp32_talk_ratio_v2.ino` 상단)
```cpp
const char* WIFI_SSID     = "...";          // 2.4GHz WiFi (ESP32는 5GHz 미지원)
const char* WIFI_PASSWORD = "...";
const char* BACKEND_HOST  = "192.168.0.100"; // 서버 PC 내부 IP (ipconfig)
#define LCD_ADDR 0x27                         // 안 되면 0x3F
```
ESP32와 서버 PC가 **같은 WiFi**에 있어야 한다. 미팅 ID 설정은 없음 — 서버가 진행 중인 세션을 자동 판단.

> **미팅 때마다 할 일**: ESP32를 USB-C 전원(충전기·보조배터리·노트북 아무거나)에 꽂기만 하면 됨. 아두이노 IDE는 최초 1회 업로드에만 필요.

## 5. 동작
| 리더 발화 | 신호등 | 의미 |
|-----------|--------|------|
| < 50% | 🟢 초록 | 적정 |
| 50–65% | 초록→노랑 (PWM 혼합) | 관찰 |
| 65–75% | 노랑+빨강 = 주황 | 주의 |
| > 75% | 🔴 빨강 + 호흡(비율↑일수록 빠르게) | 과다 |

- **흰색 LED**: 세션 대기 중 은은한 호흡(살아있음), 미팅 시작되면 꺼짐.
- **LCD**: 1행 `L:65%  M:35%`, 2행 16칸 막대(리더 비율). 대기 중엔 `Waiting session`.
- LCD는 ASCII만 표시(1602 폰트 한글 미지원) — 영문/숫자로 출력.

## 6. 빠른 점검
```bash
# 서버 응답 확인 (PC에서)
curl http://localhost:8080/api/v1/meetings/1/talk-ratio/current
```
시리얼 모니터(115200): `[Talk] L:65.0% M:35.0% active=1` 형태 로그 확인.

## 7. 확장 아이디어 (부품 추가 시)
- 피에조 부저 → 리더 과다발화 지속 시 짧은 ambient 넛지
- 푸시버튼 → 미팅 중 "이 순간" 북마크 (BE에 기록 엔드포인트 추가 필요)
