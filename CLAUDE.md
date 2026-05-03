# ReadB Server — Backend CLAUDE.md

## 프로젝트 정의
ReadB(리드비)는 1on1 미팅의 Honesty Gap을 AI로 수치화하는 B2B HR SaaS의 백엔드 서버.


## 아키텍처 원칙
- **Adapter Pattern**: STT, LLM 등 외부 연동은 반드시 인터페이스 → 구현체 분리. @Profile로 구현체 교체.
- **통짜 녹음**: 미팅 종료 후 전체 WebM 파일을 한 번에 수신.
- **비동기 분석**: @Async + 상태 폴링. 녹음 업로드 → 즉시 202 응답 → 클라이언트가 /status 폴링.
- **JSON 응답 파싱**: LLM 응답은 항상 구조화된 JSON. 파싱 실패 시 재시도 1회.

