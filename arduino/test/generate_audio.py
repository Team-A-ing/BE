"""
캘리브레이션 및 청크 테스트용 WAV 파일 생성 (외부 라이브러리 불필요)

혼자 테스트 시:
  - leader_sample.wav : 마이크 가까이서 말한 것처럼 → 진폭 0.8
  - member_sample.wav : 멀리서 작게 말한 것처럼 → 진폭 0.1
  - leader_chunk.wav  : 청크 테스트용 (큰 소리)
  - member_chunk.wav  : 청크 테스트용 (작은 소리)

사용법:
  python generate_audio.py
"""

import wave
import struct
import math

SAMPLE_RATE = 16000


def write_wav(filename: str, amplitude: float, duration_sec: float = 3.0):
    n_samples = int(SAMPLE_RATE * duration_sec)
    with wave.open(filename, "w") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SAMPLE_RATE)
        for i in range(n_samples):
            value = amplitude * math.sin(2 * math.pi * 440 * i / SAMPLE_RATE)
            f.writeframes(struct.pack("<h", int(value * 32767)))
    print(f"생성: {filename}  (진폭={amplitude}, {duration_sec}초)")


if __name__ == "__main__":
    write_wav("leader_sample.wav", amplitude=0.8, duration_sec=3.0)
    write_wav("member_sample.wav", amplitude=0.1, duration_sec=3.0)
    write_wav("leader_chunk.wav",  amplitude=0.8, duration_sec=2.0)
    write_wav("member_chunk.wav",  amplitude=0.1, duration_sec=2.0)
    print("\n완료.")
