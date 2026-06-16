# ReadB ESP32 Talk Ratio LED — 하드웨어 셋업 가이드

## 1. 아키텍처 개요

기존 Arduino USB 방식(Web Serial → USB → Arduino)의 불안정성을 해결하기 위해, WiFi 기반 ESP32로 전환합니다.

```
[브라우저]                  [백엔드 서버]                [ESP32]
    │                           │                        │
    │  POST /audio-chunk ──────→│                        │
    │                           │ TalkSession 업데이트    │
    │                           │                        │
    │  SSE (talk-ratio/stream)←─│                        │
    │  (프론트엔드 UI 표시)       │                        │
    │                           │                        │
    │                           │←─ GET /talk-ratio/current ──│ (2초 폴링)
    │                           │──→ JSON Response ──────────→│
    │                           │                        │ LED 색상 변경
    │                           │                        │ (+ OLED 표시)
```

**핵심 장점:** 브라우저에서 USB/Serial 연결이 필요 없음. ESP32는 WiFi로 독립 동작.

---

## 2. 필요 부품

| 부품 | 수량 | 비고 |
|------|------|------|
| ESP32 DevKit V1 (또는 호환보드) | 1 | WiFi 내장, USB-C 권장 |
| 빨간 LED (5mm) | 1 | 리더 과다 발화 경고 |
| 초록 LED (5mm) | 1 | 균형 대화 표시 |
| 220Ω 저항 | 2 | LED 보호용 |
| 브레드보드 | 1 | |
| 점퍼 와이어 | 5~6개 | M-M |
| [선택] SSD1306 OLED (128×64, I2C) | 1 | 발화 비율 수치 표시용 |

---

## 3. 배선도

### 3.1 기본 (LED만)

```
ESP32 GPIO 25 ──→ 220Ω ──→ 빨간 LED(+) ──→ GND
ESP32 GPIO 26 ──→ 220Ω ──→ 초록 LED(+) ──→ GND
```

**핀 정리:**

| ESP32 핀 | 연결 대상 |
|----------|----------|
| GPIO 25 | 빨간 LED (+) — 220Ω 저항 경유 |
| GPIO 26 | 초록 LED (+) — 220Ω 저항 경유 |
| GND | 두 LED의 (-) 공통 |

### 3.2 OLED 추가 시

```
ESP32 GPIO 21 (SDA) ──→ OLED SDA
ESP32 GPIO 22 (SCL) ──→ OLED SCL
ESP32 3.3V         ──→ OLED VCC
ESP32 GND          ──→ OLED GND
```

---

## 4. 개발 환경 설정

### 4.1 Arduino IDE 설정

1. **Arduino IDE 2.x** 설치 (https://www.arduino.cc/en/software)

2. **ESP32 보드 매니저 추가:**
   - File → Preferences → Additional boards manager URLs에 추가:
   ```
   https://dl.espressif.com/dl/package_esp32_index.json
   ```
   - Tools → Board → Boards Manager → "esp32" 검색 → **esp32 by Espressif Systems** 설치

3. **보드 선택:**
   - Tools → Board → ESP32 Arduino → **ESP32 Dev Module**
   - Upload Speed: 921600
   - Flash Frequency: 80MHz

### 4.2 필요 라이브러리 설치

Sketch → Include Library → Manage Libraries에서:

| 라이브러리 | 검색어 | 버전 |
|-----------|--------|------|
| ArduinoJson | "ArduinoJson" | 7.x |
| [OLED] Adafruit SSD1306 | "Adafruit SSD1306" | 2.x |
| [OLED] Adafruit GFX | "Adafruit GFX" | 1.x |

---

## 5. 펌웨어 설정 및 업로드

### 5.1 WiFi 및 서버 설정 변경

`esp32_talk_ratio.ino` 파일에서 다음 값을 수정:

```cpp
// WiFi 설정
const char* WIFI_SSID     = "YOUR_WIFI_SSID";      // ← WiFi 이름
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";   // ← WiFi 비밀번호

// 백엔드 서버 설정
const char* BACKEND_HOST  = "192.168.0.100";        // ← 서버 PC의 내부 IP
const int   BACKEND_PORT  = 8080;                   // ← Spring Boot 포트
const long  MEETING_ID    = 1;                      // ← 현재 미팅 ID
```

**서버 IP 확인 방법:**
- Windows: `ipconfig` → IPv4 Address
- Mac/Linux: `ifconfig` 또는 `ip addr`

**주의:** ESP32와 서버가 **같은 WiFi 네트워크**에 있어야 합니다.

### 5.2 OLED 활성화 (선택)

OLED을 사용하려면 파일 상단의 주석을 해제:

```cpp
#define USE_OLED   // ← 이 줄의 주석 해제
```

### 5.3 업로드

1. ESP32를 USB로 PC에 연결
2. Tools → Port → ESP32 포트 선택 (COMx 또는 /dev/ttyUSBx)
3. Upload 클릭 (→)
4. "Connecting..." 메시지가 나오면 ESP32의 **BOOT 버튼**을 누를 것
5. 업로드 완료 후 자동 리부트

### 5.4 시리얼 모니터 확인

Tools → Serial Monitor → 115200 baud:

```
=== ReadB ESP32 Talk Ratio LED ===
[WiFi] 연결 중: MY_WIFI
.....
[WiFi] 연결 성공! IP: 192.168.0.105
[Talk] Leader: 45.2% | Member: 54.8% | GREEN
[Talk] Leader: 72.1% | Member: 27.9% | RED
```

---

## 6. 동작 설명

| LED 상태 | 의미 | 조건 |
|----------|------|------|
| 초록 ON | 균형 대화 | 리더 발화 ≤ 70% |
| 빨간 ON | 리더 과다 발화 | 리더 발화 > 70% |
| 양쪽 OFF | 대기 중 | 미팅 세션 미시작 또는 WiFi 미연결 |
| 초록 깜빡 3회 | WiFi 연결 성공 | 부팅 시 |
| 빨간 깜빡 5회 | WiFi 연결 실패 | 부팅 시 |

**OLED (활성화 시):**
- 리더/멤버 발화 비율 수치 (큰 글씨)
- 하단 비율 바 그래프
- 상태 텍스트 (BALANCED / LEADER OVER)

---

## 7. 트러블슈팅

| 증상 | 해결 |
|------|------|
| WiFi 연결 안 됨 | SSID/비밀번호 확인, 2.4GHz인지 확인 (ESP32는 5GHz 미지원) |
| LED 안 켜짐 | GPIO 핀 번호 확인, 저항 방향 확인, LED 극성 (+/-) 확인 |
| HTTP 에러 -1 | 서버 IP/포트 확인, 같은 네트워크인지 확인, 방화벽 확인 |
| JSON 파싱 에러 | 서버가 정상 응답하는지 브라우저에서 직접 확인 |
| OLED 안 켜짐 | I2C 주소 확인 (0x3C vs 0x3D), SDA/SCL 핀 확인 |

**서버 응답 직접 테스트:**
```bash
curl http://192.168.0.100:8080/api/v1/meetings/1/talk-ratio/current
```

---

## 8. 기존 Arduino 대비 변경사항

| 항목 | Arduino (기존) | ESP32 (신규) |
|------|---------------|-------------|
| 연결 방식 | USB Serial (Web Serial API) | WiFi HTTP |
| 브라우저 의존 | Chrome만 지원 (Web Serial) | 브라우저 독립 |
| 데이터 흐름 | 브라우저 → USB → Arduino | 백엔드 → WiFi → ESP32 |
| 안정성 | 직렬 연결 끊김 빈번 | WiFi 자동 재연결 |
| 추가 기능 | LED만 가능 | LED + OLED + 확장 가능 |
