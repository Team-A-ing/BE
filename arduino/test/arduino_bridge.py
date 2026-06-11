"""
로컬 테스트용 아두이노 브릿지

흐름:
  1. 로그인 → JWT 발급
  2. SSE 스트림 구독 (백그라운드) → leaderRatio 기준으로 R/G 시리얼 전송
  3. 리더 캘리브레이션
  4. 멤버 캘리브레이션
  5. 청크 반복 전송

사전 준비:
  pip install requests pyserial
  python generate_audio.py  (WAV 파일 생성)
  아두이노 포트 확인: 장치관리자 → 포트(COM & LPT) → Arduino Uno (COMx)
"""

import json
import threading
import time

import requests
import serial

# ── 설정 (본인 환경에 맞게 수정) ─────────────────────────
BASE_URL         = "http://localhost:8080"
EMAIL            = "hleader@gmail.com"
PASSWORD         = "test1234!"
MEETING_ID       = 85
SERIAL_PORT      = "COM8"             # 장치관리자에서 확인한 포트
LEADER_THRESHOLD = 50.0               # leaderRatio >= 50 → 초록, < 50 → 빨강
# ──────────────────────────────────────────────────────────


def login() -> str:
    res = requests.post(f"{BASE_URL}/api/v1/auth/login", json={
        "email": EMAIL,
        "password": PASSWORD,
    })
    res.raise_for_status()
    token = res.json()["data"]["accessToken"]
    print(f"[로그인 성공] 토큰: {token[:30]}...")
    return token


def sse_listener(token: str, ser: serial.Serial):
    """SSE 이벤트를 받아 아두이노로 R/G 전송 (백그라운드 스레드)"""
    headers = {"Authorization": f"Bearer {token}"}
    url = f"{BASE_URL}/api/v1/meetings/{MEETING_ID}/talk-ratio/stream"
    print("[SSE] 스트림 연결 중...")
    with requests.get(url, headers=headers, stream=True) as r:
        r.raise_for_status()
        print("[SSE] 연결됨. 이벤트 대기 중...")
        for line in r.iter_lines():
            if not line:
                continue
            line = line.decode("utf-8")
            if line.startswith("data:"):
                data = json.loads(line[5:].strip())
                leader_ratio = data.get("leaderRatio", 0)
                member_ratio = data.get("memberRatio", 0)
                if leader_ratio >= LEADER_THRESHOLD:
                    ser.write(b"G")
                    print(f"[SSE] leaderRatio={leader_ratio:.1f}% → 초록불")
                else:
                    ser.write(b"R")
                    print(f"[SSE] leaderRatio={leader_ratio:.1f}% → 빨간불")


def post_audio(url: str, token: str, filepath: str):
    headers = {"Authorization": f"Bearer {token}"}
    with open(filepath, "rb") as f:
        res = requests.post(url, headers=headers, files={"audio": (filepath, f, "audio/wav")})
    res.raise_for_status()
    print(f"[OK] {filepath} → {url}")


def main():
    # 1. 로그인
    token = login()

    # 2. 아두이노 시리얼 연결
    print(f"\n[아두이노] {SERIAL_PORT} 연결 중...")
    ser = serial.Serial(SERIAL_PORT, 9600, timeout=1)
    time.sleep(2)  # 아두이노 리셋 대기
    print(f"[아두이노] 연결됨")

    # 3. SSE 리스너 백그라운드 실행
    t = threading.Thread(target=sse_listener, args=(token, ser), daemon=True)
    t.start()
    time.sleep(1)

    base = f"{BASE_URL}/api/v1/meetings/{MEETING_ID}"

    # 4. 캘리브레이션
    print("\n[캘리브레이션] 리더...")
    post_audio(f"{base}/calibrate/leader", token, "leader_sample.wav")

    print("[캘리브레이션] 멤버...")
    post_audio(f"{base}/calibrate/member", token, "member_sample.wav")

    # 5. 청크 반복 전송
    print("\n[청크 전송] 리더/멤버 교대로 5회 전송합니다.")
    for i in range(5):
        print(f"\n--- 라운드 {i + 1} ---")
        post_audio(f"{base}/audio-chunk", token, "leader_chunk.wav")
        time.sleep(0.5)
        post_audio(f"{base}/audio-chunk", token, "member_chunk.wav")
        time.sleep(0.5)

    print("\n[완료] 5초 후 종료합니다. Ctrl+C로 즉시 종료 가능.")
    time.sleep(5)
    ser.close()


if __name__ == "__main__":
    main()
