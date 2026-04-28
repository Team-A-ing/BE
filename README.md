# ReadB — Backend Server

> **행간의 의미를 파악하다 (Read between the lines)**  
> 1on1 미팅의 Honesty Gap을 AI로 수치화하는 B2B HR SaaS 백엔드 서버

---

## 프로젝트 개요

ReadB는 1on1 미팅에서 발생하는 **표면적 답변(서베이)** 과 **내면의 부정 징후(대화 뉘앙스)** 사이의 간극을 AI로 분석해 조직 리스크를 조기에 감지하는 서비스입니다.

| 구분 | 내용 |
|------|------|
| 팀 | Team A-ing (동국대 종합설계1) |
| 역할 | BE1 (AI 파이프라인 / PM), BE2 (인증·CRUD·인프라) |
| 레포 | [BE](https://github.com/Team-A-ing/BE) / [FE](https://github.com/Team-A-ing/FE) |
| 배포 | Railway (BE) / Vercel (FE) / Supabase (DB + Storage) |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| ORM | Spring Data JPA + PostgreSQL (Supabase) |
| 인증 | Spring Security + JWT (jjwt 0.12+) |
| AI / STT | OpenAI Whisper API, Claude Sonnet, GPT-4o-mini |
| Storage | Supabase Storage |
| 빌드 | Gradle 8.7 |
| 배포 | Railway |

---

## 로컬 개발 환경 세팅

### 1. 클론 & 환경변수 설정

```bash
git clone https://github.com/Team-A-ing/BE.git
cd BE

# 환경변수 파일 생성 (절대 커밋 금지)
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` 에 아래 값 채우기 (팀 단톡방에서 공유):

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update

DB_URL: jdbc:postgresql://<supabase-host>:5432/postgres
DB_USERNAME: postgres
DB_PASSWORD: your-db-password

JWT_SECRET: your-local-jwt-secret-min-32-chars

OPENAI_API_KEY: sk-...
CLAUDE_API_KEY: sk-ant-...

SUPABASE_URL: https://your-project.supabase.co
SUPABASE_KEY: your-supabase-anon-key
```

### 2. 빌드 & 실행

```bash
# Windows
.\gradlew.bat bootRun

# Mac / Linux
./gradlew bootRun
```

| 명령어 | 설명 |
|--------|------|
| `./gradlew bootRun` | 로컬 서버 실행 (port 8080) |
| `./gradlew test` | 테스트 실행 |
| `./gradlew build -x test` | 테스트 스킵하고 빌드 |

### 3. API 문서 확인

서버 실행 후 → [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 프로젝트 구조

```
src/main/java/com/readb/
├── config/          # SecurityConfig, CorsConfig, SwaggerConfig, AsyncConfig
├── security/        # JwtAuthFilter
├── common/          # ApiResponse, ErrorCode, BusinessException, JwtUtil
├── domain/          # 엔티티 (User, Team, Meeting, Recording, Survey, Analysis, Promise)
├── repository/      # JPA 레포지토리
├── service/         # 비즈니스 로직
│   ├── auth/        # 인증 (BE2)
│   ├── user/        # 유저 (BE2)
│   ├── team/        # 팀 (BE2)
│   ├── survey/      # 서베이 (BE2)
│   ├── storage/     # Supabase Storage (BE2)
│   ├── meeting/     # 미팅 생성/업로드 (BE1)
│   └── analysis/    # AI 분석 파이프라인 (BE1)
├── adapter/         # 외부 API 추상화 (BE1 전담)
│   ├── stt/         # Whisper STT
│   └── llm/         # Claude / GPT-4o-mini
├── controller/      # REST 컨트롤러 (8개)
└── dto/             # 요청/응답 DTO (Java Record)
```

---

## 역할 분담

### BE1 — AI 파이프라인 (이승규)

| 소유 영역 | 파일 |
|-----------|------|
| 도메인 | Meeting, Recording, Analysis, Promise |
| 서비스 | MeetingService, AnalysisOrchestrator, AnalysisService, PromiseService |
| 어댑터 | SttAdapter, WhisperAdapter, LlmAdapter, ClaudeAdapter, GptMiniAdapter |
| 컨트롤러 | MeetingController, AnalysisController, LeaderController, MemberController |

### BE2 — 인증·CRUD·인프라 (준영)

| 소유 영역 | 파일 |
|-----------|------|
| 도메인 | User, Team, Survey |
| 서비스 | AuthService, UserService, TeamService, SurveyService, FileStorageService |
| 컨트롤러 | AuthController, UserController, TeamController, SurveyController |
| 공통 | SecurityConfig, CorsConfig, SwaggerConfig, JwtUtil |

> **규칙:** 상대방 소유 파일은 절대 수정 금지. 수정이 필요하면 디스코드/슬랙 먼저 공유.

---

## API 엔드포인트

```
# BE2 소유
POST   /api/auth/signup
POST   /api/auth/login
GET    /api/users/me
PUT    /api/users/me
GET    /api/teams/{teamId}/members
POST   /api/surveys
GET    /api/surveys?meetingId=

# BE1 소유
POST   /api/meetings
POST   /api/meetings/{id}/recording     ← 비동기 분석 시작, 202 반환
GET    /api/meetings/{id}/status        ← 폴링 (3초 간격)
GET    /api/meetings/{id}/analysis
GET    /api/leader/radar?teamId=
GET    /api/leader/blockers?teamId=
GET    /api/leader/promises
GET    /api/member/career-memory
GET    /api/member/feedback?meetingId=
```

---

## 브랜치 전략

```
main
 └─ feat/be1-{기능명}     ← BE1 작업 브랜치
 └─ feat/be2-{기능명}     ← BE2 작업 브랜치
 └─ fix/be1-{버그명}
 └─ fix/be2-{버그명}
```

| 규칙 | 내용 |
|------|------|
| main 브랜치 | 항상 빌드 가능한 상태 유지. 직접 push 금지. |
| 작업 브랜치 | 기능 단위로 생성. 완료 후 PR → main 머지. |
| PR 머지 순서 | `domain/` 또는 `common/` 변경이 포함된 PR을 먼저 머지. 이후 상대방이 pull 후 rebase. |
| 접두사 | `feat/be1-`, `feat/be2-`, `fix/be1-`, `fix/be2-` |

**예시**

```bash
git checkout -b feat/be2-auth-jwt
# 작업 완료 후
git push origin feat/be2-auth-jwt
# GitHub에서 PR 생성 → 리뷰 → main 머지
```

---

## 커밋 메시지 규칙

```
<type>(<scope>): <subject>

[선택] body — 변경 이유, 상세 설명
```

| type | 사용 시점 |
|------|-----------|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `chore` | 빌드·설정·의존성 변경 |
| `docs` | 문서 수정 |
| `test` | 테스트 코드 추가/수정 |

**예시**

```
feat(auth): JWT 로그인/회원가입 구현

- BCrypt 비밀번호 해싱
- jjwt 0.12+ 기반 토큰 발급/검증
- 이메일 중복 체크 예외 처리
```

---

## 충돌 방지 규칙

1. **파일 단위 소유권** — 위 역할표에서 내 소유 파일만 수정
2. **domain/ 수정 프로토콜** — 엔티티 필드 추가/삭제 시 디스코드 공유 후 작업
3. **common/ 수정 프로토콜** — `ErrorCode` enum 값 추가는 자유, 기존 값 변경/삭제는 합의 후
4. **application.yml 분리** — 공통 설정은 `application.yml`, 개인 로컬 설정은 `application-local.yml` (gitignore)
5. **API 키는 환경변수** — 코드에 직접 하드코딩 절대 금지

---

## 주요 아키텍처 결정사항

| 결정 | 이유 |
|------|------|
| **통짜 녹음 파이프라인** | 15초 청킹 대신 미팅 종료 후 전체 WebM 파일을 한 번에 수신. 문맥 정확도 우선. (25MB 초과 시 서버에서 분할) |
| **비동기 분석** | 업로드 즉시 202 반환 → 클라이언트가 `/status` 폴링 (3초 간격). 1~2분 딜레이를 UX로 처리. |
| **Adapter Pattern** | STT/LLM 외부 연동을 인터페이스/구현체로 분리. 추후 모델 교체 시 비즈니스 로직 수정 없이 구현체만 교체. |
| **LLM JSON 파싱** | 응답 파싱 실패 시 1회 재시도. 재실패 시 `meeting.status = FAILED` 마킹. |
| **원본 삭제** | 분석 완료 즉시 Supabase Storage에서 녹음 원본 영구 삭제 (개인정보 보호). |
