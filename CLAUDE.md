# ReadB Server — Backend CLAUDE.md

## 프로젝트 정의
ReadB(리드비)는 1on1 미팅의 Honesty Gap을 AI로 수치화하는 B2B HR SaaS의 백엔드 서버.

## 핵심 분석 프레임워크: 3-Gap Model

### Gap ① Alignment Gap (기대 vs 실제 의제)
- 사전 서베이에서 리더/멤버가 각각 선택한 "논의할 주제"와 실제 미팅에서 다뤄진 주제를 비교
- 스코어링: 0(언급 없음) → 25(멤버 제기했으나 리더 묵살) → 40(10% 미만 언급) → 60(논의했으나 결론 없음) → 85~100(충분히 논의 + 액션아이템)

### Gap ② Honesty Gap (서베이 응답 vs 관찰된 행동)
- 서베이에서 "좋음"이라 답했는데, 실제 미팅에서 Initiative 0회 + Vulnerability 0회 → Gap 감지
- **핵심 원칙**: LLM의 감정 추론이 아닌, Speech Act 카운팅(관찰 가능한 사실)과 서베이 응답을 비교
- Safety Score = Speech Act 기반 행동 지표 (아래 참조)

### Gap ③ Execution Gap (과거 약속 vs 이행)
- 이전 미팅에서 합의된 약속(Promise)이 이번 미팅에서 언급/이행되었는지 추적
- 스코어링(약속별): 0(언급 없음) → 20(미이행, 사유 없음) → 50(미이행, 합리적 사유) → 70(진행 중) → 100(완료)
- 전체 점수 = 개별 약속 점수의 평균

## Safety Score: Speech Act 카운팅

AI 감정 추론 대신 **관찰 가능한 발화 행위(Speech Act)**를 카운팅하여 팀 심리적 안전감을 수치화.

### 3가지 행동 지표
1. **Vulnerability** (취약성 표현): "잘 모르겠습니다", "실수했습니다", "도움이 필요합니다" 등
   - 포함: 불확실성 인정, 실수 고백, 도움 요청
   - 제외: 사교적 겸손("에이 뭐 저야 별로..."), 맥락 없는 관용표현
2. **Constructive Dissent** (건설적 반대): "다른 의견인데요", "그 방법보다는...", "한 가지 우려가..."
   - 포함: 대안 제시 동반 반대, 근거 있는 우려 표명
   - 제외: 단순 불만("그건 안 될 거 같은데"), 인신공격
3. **Initiative** (자발적 제안): "제가 해볼게요", "이런 아이디어가 있는데", "제안드리자면"
   - 포함: 자발적 업무 인수, 새 아이디어 제안, 프로세스 개선 제안
   - 제외: 지시에 대한 단순 수락("네 알겠습니다")

### 경계 규칙
- **애매하면 카운팅하지 않는다** (보수적 접근)
- 각 Speech Act는 원문 인용 + 타임스탬프와 함께 저장

## Rolling Baseline + 이상 탐지

- 개인별 최근 3회 미팅 평균을 베이스라인으로 사용
- 30% 이상 하락 시 Silent Risk 알림 (리더 대시보드)
- **Cold Start 규칙**: 1~2회차 미팅은 절대값 스코어링만 사용 (베이스라인 미형성)
- 알림 문구는 AI 해석이 아닌 사실 기반: "최근 3회 평균 대비 Initiative 42% 감소" 형태

## Fact-Based Output 원칙 (BE→FE 응답 설계)

- **금지**: AI가 해석/판단한 라벨 ("수동 공격적", "소극적 참여", "번아웃 징후")
- **허용**: 원문 인용 + 타임스탬프 + 카운트 수치 + 베이스라인 대비 변화율
- 예시: "Vulnerability 발화 3회 (이전 3회 평균: 5.3회, -43%)" + 해당 발화 원문 목록
- FE가 사용자에게 보여주는 화면에서 해석은 사용자 스스로 하도록 설계

## ML-Ready 데이터 파이프라인

미팅별 구조화된 데이터 오브젝트를 축적하여 향후 예측 모델(퇴사 예측, 번아웃 예측) 학습 데이터로 활용.

```
[T-1] 사전 서베이 → surveys 테이블 (scores JSONB)
[T-0] 미팅 정량 데이터 → analyses 테이블 (speech_acts JSONB, gap scores)
[T+1] 스코어링 결과 → analyses 테이블 (safety_score, alignment_gap, honesty_gap, execution_gap)
[T+2] 행동 데이터 → promises 테이블 (이행률 추적)
```

## 기술 스택
- Java 17 + Spring Boot 3.2
- Spring Data JPA + PostgreSQL (Supabase)
- Spring Security + JWT (jjwt 0.12+)
- Supabase Storage (녹음 파일 임시 저장)
- OpenAI Whisper API (STT)
- Claude Sonnet + GPT-4o-mini (LLM Cascading)
- OpenAI Embedding API + pgvector (RAG)
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
│   ├── promise/
│   │   └── Promise.java           ← BE1
│   └── career/
│       ├── CareerEvent.java       ← BE1
│       └── CareerEventType.java   ← BE1
│
├── repository/                # [각자 담당 엔티티의 레포지토리만 작성]
│   ├── UserRepository.java        ← BE2
│   ├── TeamRepository.java        ← BE2
│   ├── MeetingRepository.java     ← BE1
│   ├── RecordingRepository.java   ← BE1
│   ├── SurveyRepository.java      ← BE2
│   ├── AnalysisRepository.java    ← BE1
│   ├── PromiseRepository.java     ← BE1
│   └── CareerEventRepository.java ← BE1
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
│   ├── llm/
│   │   ├── LlmAdapter.java            (인터페이스)
│   │   ├── ClaudeAdapter.java         (구현체)
│   │   └── GptMiniAdapter.java        (구현체)
│   └── rag/
│       ├── RagAdapter.java            (인터페이스)
│       └── PgVectorAdapter.java       (구현체 — pgvector on Supabase)
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
- 담당 도메인: Meeting, Recording, Analysis, Promise, CareerEvent
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

-- BE2 추가 필드
-- users 테이블에 churned_at TIMESTAMP NULL 컬럼 추가 (향후 퇴사 예측 모델용)

-- BE1 담당 (초기 생성 + 유지)
meetings       (id, team_id, leader_id, member_id, status, created_at)
recordings     (id, meeting_id, file_url, duration_sec, transcript TEXT, created_at)
analyses       (id, meeting_id,
                alignment_gap FLOAT, honesty_gap FLOAT, execution_gap FLOAT,
                safety_score FLOAT,
                speech_acts JSONB,          -- {vulnerability: [{text, timestamp}], dissent: [...], initiative: [...]}
                blocker_keywords JSONB,
                leader_feedback JSONB, member_feedback JSONB,
                career_tags JSONB,
                baseline_data JSONB,        -- {prev_avg_vulnerability, prev_avg_dissent, prev_avg_initiative}
                created_at)
promises       (id, meeting_id, owner_id, content, deadline, status, created_at)
career_events  (id, user_id, meeting_id, event_type, title, description,
                evidence JSONB,             -- {quote, timestamp, source_meeting_id}
                occurred_at, created_at)
```

전체 DDL 은 [BE/db/schema.sql](db/schema.sql) 참조. Supabase SQL Editor 에 붙여 실행.

## 개발 명령어
```bash
./gradlew bootRun                    # 로컬 실행
./gradlew test                       # 테스트
./gradlew build -x test              # 빠른 빌드 (테스트 스킵)
```

## LLM Cascading 전략

```
Step 1: Whisper API → 전체 Transcript 생성
Step 2: GPT-4o-mini (저비용) → 구조화 작업
        - Speech Act 분류 (Vulnerability / Constructive Dissent / Initiative)
        - 주제별 발화 매핑
        - Promise 추출
Step 3: Claude Sonnet (고품질) → 판단 작업
        - 3-Gap 스코어링 (Alignment / Honesty / Execution)
        - 리더 코칭 피드백 생성
        - 멤버 Career Memory 태그 생성
```

## 주의사항
- Whisper API 25MB 제한: 초과 시 ffmpeg로 서버에서 분할 → 순차 호출 → 병합
- 오디오 원본은 분석 완료 즉시 Supabase Storage에서 영구 삭제
- LLM 응답 JSON 파싱 실패 시 1회 재시도, 그래도 실패 시 status를 FAILED로 마킹
- application-local.yml은 절대 커밋 금지 (.gitignore 확인)
- **Fact-Based 출력 원칙 준수**: API 응답에 AI 해석 라벨 포함 금지. 원문+수치+변화율만 반환
