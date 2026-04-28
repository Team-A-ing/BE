# ReadB Server — Backend CLAUDE.md

## 프로젝트 정의
ReadB(리드비)는 1on1 미팅의 Honesty Gap을 AI로 수치화하는 B2B HR SaaS의 백엔드 서버.

## 기술 스택
- Java 17 + Spring Boot 3.2
- Spring Data JPA + PostgreSQL (Supabase)
- Spring Security + JWT (jjwt 0.12+)
- Supabase Storage (녹음 파일 임시 저장)
- OpenAI Whisper API (STT)
- Claude Sonnet + GPT-4o-mini (LLM Cascading)
- Gradle (빌드)
- Railway (배포)

## 아키텍처 원칙
- **Adapter Pattern**: STT, LLM 등 외부 연동은 반드시 인터페이스 → 구현체 분리. @Profile로 구현체 교체.
- **통짜 녹음**: 미팅 종료 후 전체 WebM 파일을 한 번에 수신. 15초 청킹 아님.
- **비동기 분석**: @Async + 상태 폴링. 녹음 업로드 → 즉시 202 응답 → 클라이언트가 /status 폴링.
- **JSON 응답 파싱**: LLM 응답은 항상 구조화된 JSON. 파싱 실패 시 재시도 1회.

## 디렉토리 구조 + 담당자

```
src/main/java/com/readb/
│
├── config/                    # [공유] 둘 다 수정 가능하나 사전 공유 필수
│   ├── SecurityConfig.java        ← BE2
│   ├── CorsConfig.java            ← BE2
│   ├── AsyncConfig.java           ← BE1
│   └── SwaggerConfig.java         ← BE2
│
├── domain/                    # [공유] 엔티티 추가/수정 시 반드시 상대방에게 알린 후 작업
│   ├── user/
│   │   └── User.java              ← BE2
│   ├── team/
│   │   └── Team.java              ← BE2
│   ├── meeting/
│   │   └── Meeting.java           ← BE1
│   ├── recording/
│   │   └── Recording.java         ← BE1
│   ├── survey/
│   │   └── Survey.java            ← BE2
│   ├── analysis/
│   │   └── Analysis.java          ← BE1
│   └── promise/
│       └── Promise.java           ← BE1
│
├── repository/                # [각자 담당 엔티티의 레포지토리만 작성]
│   ├── UserRepository.java        ← BE2
│   ├── TeamRepository.java        ← BE2
│   ├── MeetingRepository.java     ← BE1
│   ├── RecordingRepository.java   ← BE1
│   ├── SurveyRepository.java      ← BE2
│   ├── AnalysisRepository.java    ← BE1
│   └── PromiseRepository.java     ← BE1
│
├── service/                   # [핵심 분리 영역 — 절대 상대 파일 건드리지 않기]
│   ├── auth/
│   │   └── AuthService.java       ← BE2 전담
│   ├── user/
│   │   └── UserService.java       ← BE2 전담
│   ├── team/
│   │   └── TeamService.java       ← BE2 전담
│   ├── meeting/
│   │   └── MeetingService.java    ← BE1 전담
│   ├── survey/
│   │   └── SurveyService.java     ← BE2 전담
│   ├── analysis/
│   │   ├── AnalysisOrchestrator.java  ← BE1 전담 (핵심 파이프라인)
│   │   ├── AnalysisService.java       ← BE1 전담
│   │   └── PromiseService.java        ← BE1 전담
│   └── storage/
│       └── FileStorageService.java    ← BE2 전담 (Supabase Storage 업/다운)
│
├── adapter/                   # [BE1 전담 — BE2 절대 수정 금지]
│   ├── stt/
│   │   ├── SttAdapter.java            (인터페이스)
│   │   └── WhisperAdapter.java        (구현체)
│   └── llm/
│       ├── LlmAdapter.java            (인터페이스)
│       ├── ClaudeAdapter.java         (구현체)
│       └── GptMiniAdapter.java        (구현체)
│
├── controller/                # [각자 담당 API만 작성 — 파일 단위로 분리]
│   ├── AuthController.java        ← BE2 전담
│   ├── UserController.java        ← BE2 전담
│   ├── TeamController.java        ← BE2 전담
│   ├── SurveyController.java      ← BE2 전담
│   ├── MeetingController.java     ← BE1 전담
│   ├── AnalysisController.java    ← BE1 전담
│   ├── LeaderController.java      ← BE1 전담 (radar, blockers, promises)
│   └── MemberController.java      ← BE1 전담 (career-memory)
│
├── dto/                       # [각자 담당 컨트롤러의 DTO만 작성]
│   ├── auth/                      ← BE2
│   ├── user/                      ← BE2
│   ├── team/                      ← BE2
│   ├── survey/                    ← BE2
│   ├── meeting/                   ← BE1
│   └── analysis/                  ← BE1
│
└── common/                    # [공유] 수정 전 반드시 상대방에게 알릴 것
    ├── exception/
    │   ├── GlobalExceptionHandler.java  ← BE2 초기 작성, 이후 공유
    │   └── ErrorCode.java               ← 공유 (enum 추가만, 기존 값 수정 금지)
    ├── response/
    │   └── ApiResponse.java             ← BE2 초기 작성, 이후 수정 금지
    └── util/
        └── JwtUtil.java                 ← BE2 전담
```

## 팀 역할 요약

### BE1 (PM 겸 AI 파이프라인)
- 담당 도메인: Meeting, Recording, Analysis, Promise
- 핵심 업무: Adapter 설계, LLM 프롬프트, 분석 오케스트레이터, 리더/멤버 결과 API
- 소유 패키지: adapter/*, service/analysis/*, service/meeting/*, controller/Meeting*, controller/Analysis*, controller/Leader*, controller/Member*

### BE2 (인증 + CRUD + 인프라)
- 담당 도메인: User, Team, Survey
- 핵심 업무: JWT 인증, 유저/팀 CRUD, 서베이, 파일 업로드, 공통 에러 처리
- 소유 패키지: config/Security*, service/auth/*, service/user/*, service/team/*, service/survey/*, service/storage/*, controller/Auth*, controller/User*, controller/Team*, controller/Survey*

## 충돌 방지 규칙

1. **파일 단위 소유권**: 위 구조에서 ← 표시된 담당자만 해당 파일 수정. 상대 파일 수정 필요 시 슬랙/디코 먼저 공유.
2. **domain/ 엔티티 수정 프로토콜**: 필드 추가/삭제 시 반드시 상대에게 알린 후 작업. 엔티티는 양쪽 서비스가 참조하므로 가장 충돌이 잘 남.
3. **common/ 수정 프로토콜**: ErrorCode enum에 값 추가는 자유. 기존 값 변경/삭제는 금지. ApiResponse 구조 변경은 합의 후.
4. **application.yml 분리**: 공통 설정은 application.yml, 개인 설정은 application-local.yml (.gitignore). API 키는 환경변수로.
5. **브랜치 네이밍**: `feat/be1-whisper-adapter`, `feat/be2-auth-jwt` — 접두사로 담당자 구분.
6. **PR 머지 순서**: domain/ 또는 common/ 변경이 포함된 PR은 먼저 머지. 이후 상대방이 pull 받고 자기 브랜치 rebase 후 작업 계속.

## API 엔드포인트 소유권

```
# BE2 소유
POST   /api/auth/signup
POST   /api/auth/login
GET    /api/users/me
PUT    /api/users/me
GET    /api/teams/{teamId}/members
POST   /api/surveys                     (사전 서베이 제출)
GET    /api/surveys?meetingId=           (서베이 조회)

# BE1 소유
POST   /api/meetings                    (미팅 생성)
POST   /api/meetings/{id}/recording     (녹음 업로드 → 비동기 분석)
GET    /api/meetings/{id}/status        (분석 진행 상태)
GET    /api/meetings/{id}/analysis      (분석 결과)
GET    /api/leader/radar?teamId=        (Silent Risk 산점도 데이터)
GET    /api/leader/blockers?teamId=     (Blocker Cloud 데이터)
GET    /api/leader/promises             (Promise Ledger)
GET    /api/member/career-memory        (Career Memory 타임라인)
GET    /api/member/feedback?meetingId=  (코칭 피드백 카드)
```

## DB 스키마 소유권

```sql
-- BE2 담당 (초기 생성 + 유지)
users       (id, email, password_hash, name, role, team_id, created_at)
teams       (id, name, leader_id, created_at)
surveys     (id, meeting_id, member_id, scores JSONB, submitted_at)

-- BE1 담당 (초기 생성 + 유지)
meetings    (id, team_id, leader_id, member_id, status, created_at)
recordings  (id, meeting_id, file_url, duration_sec, transcript TEXT, created_at)
analyses    (id, meeting_id, gap_score, surface_score, inferred_score,
            blocker_keywords JSONB, leader_feedback JSONB, member_feedback JSONB,
            career_tags JSONB, created_at)
promises    (id, meeting_id, owner_id, content, deadline, status, created_at)
```

## 개발 명령어
```bash
./gradlew bootRun                    # 로컬 실행
./gradlew test                       # 테스트
./gradlew build -x test              # 빠른 빌드 (테스트 스킵)
```

## 주의사항
- Whisper API 25MB 제한: 초과 시 ffmpeg로 서버에서 분할 → 순차 호출 → 병합
- 오디오 원본은 분석 완료 즉시 Supabase Storage에서 영구 삭제
- LLM 응답 JSON 파싱 실패 시 1회 재시도, 그래도 실패 시 status를 FAILED로 마킹
- application-local.yml은 절대 커밋 금지 (.gitignore 확인)
