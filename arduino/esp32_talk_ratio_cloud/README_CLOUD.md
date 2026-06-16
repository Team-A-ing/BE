# ReadB ESP32 Talk Ratio Lamp — CLOUD (이동용) 셋업

카페를 옮겨 다녀도 **펌웨어 재업로드 없이** 쓰는 버전.
- **배포 BE(Railway, HTTPS)** 를 폴링 → 인터넷만 되면 어디서든 동작 (노트북·로컬 서버 불필요).
- **WiFiManager(캡티브 포털)** → 새 WiFi는 휴대폰으로 그 자리에서 입력.

> 로컬 개발용은 `esp32_talk_ratio_v2`(같은 WiFi의 로컬 BE). 이동/시연용이 이 `cloud` 버전.

## ⚠️ 전제조건
배포 BE에 `/api/v1/talk-ratio/active` 가 **떠 있어야** 한다. 이 엔드포인트는 `feat/iot-esp32-talk-ratio-lamp` 브랜치에 있으므로:
1. 해당 BE PR을 **main에 머지**
2. Railway가 **재배포** 될 때까지 대기
3. 확인: `curl https://be-production-de9a.up.railway.app/api/v1/talk-ratio/active`
   → `{"data":{"active":false,...}}` 가 나오면 준비 완료 (지금은 403 = 아직 미배포).

## 1. 라이브러리 (Arduino IDE)
- **ArduinoJson** (Benoit Blanchon) v7+
- **WiFiManager** (tzapu)
- (LCD 쓸 때만) **LiquidCrystal I2C**

## 2. 배선
`esp32_talk_ratio_v2`와 동일 — 초록 25 · 노랑 26 · 빨강 27 · 흰색 32 (각 220Ω → GND). LCD는 선택(SDA21/SCL22/5V/GND).

## 3. 업로드 (최초 1회)
- 펌웨어 상단에서 바꿀 것은 거의 없음. (API_URL은 이미 Railway 주소로 박혀 있음)
- AP 이름/비번만 원하면 변경: `AP_NAME = "ReadB-Lamp"`, `AP_PASS = "readb1234"`.
- ESP32 USB 연결 → 업로드.

## 4. 사용 흐름 (카페에서)
1. ESP32에 **USB-C 전원**(충전기/보조배터리/노트북 아무거나) 연결.
2. **처음 가는 WiFi면** — 흰 LED가 켜진 채 ESP32가 `ReadB-Lamp` 라는 WiFi(AP)를 만든다.
   - 휴대폰 WiFi 목록에서 **`ReadB-Lamp`** 접속 (비번 `readb1234`).
   - 자동으로 뜨는 설정 페이지에서 **그 카페 WiFi 선택 + 비번 입력 → 저장**.
   - ESP32가 그 WiFi로 연결되고, 다음부터 그 장소에선 자동 연결.
3. 연결되면 폴링 시작 → 진행 중인 미팅이 있으면 신호등 색이 발화비율 따라 변함.

**다음부터**: 같은 장소면 전원만 꽂으면 자동. 새 장소면 위 2번(폰으로 WiFi 입력)만 한 번 더.

## 5. LED 의미
| 상태 | 표시 |
|------|------|
| WiFi 미연결/설정 중 | 흰 LED 빠른 호흡(또는 켜짐) |
| 연결됨 · 미팅 대기 | 흰 LED 은은한 호흡 |
| 미팅 진행 중 | 신호등(초<50 / 초~노 50-65 / 주황 65-75 / 빨강·호흡 >75) |

## 6. WiFi 다시 설정하려면
- 새 장소에서 **저장된 WiFi가 안 잡히면 자동으로 `ReadB-Lamp` 포털이 다시 열림** → 폰으로 새 WiFi 입력.
- (옵션) 푸시버튼을 GPIO에 달고 `wm.resetSettings()`를 호출하면 수동 초기화도 가능 — 필요하면 추가해줄 수 있음.

## 7. 참고
- HTTPS 인증서는 단순화를 위해 `client.setInsecure()`로 검증 생략. 데모/내부용으로 충분.
- ESP32는 **2.4GHz WiFi만** 지원 (5GHz 불가).
