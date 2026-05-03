# ReadB ERD

> **DB**: PostgreSQL (Supabase)  
> **규칙**: snake_case, PK는 BIGSERIAL, FK는 명시적 제약

---

## 다이어그램

```mermaid
erDiagram
    users {
        bigint id PK
        varchar email UK "NOT NULL"
        varchar password_hash "NOT NULL"
        varchar name "NOT NULL"
        varchar role "ENUM: LEADER | MEMBER"
        varchar job_title "직책 예: 시니어 FE 엔지니어"
        bigint team_id FK
        timestamp created_at
    }

    teams {
        bigint id PK
        varchar name "NOT NULL"
        bigint leader_id FK "→ users.id"
        timestamp created_at
    }

    meetings {
        bigint id PK
        bigint team_id FK "→ teams.id"
        bigint leader_id FK "→ users.id"
        bigint member_id FK "→ users.id"
        int round "회차 번호"
        timestamp scheduled_at "예정 일시"
        int duration_sec "실제 미팅 길이(초)"
        varchar status "ENUM: PENDING | ANALYZING | COMPLETED | FAILED"
        timestamp created_at
    }

    surveys {
        bigint id PK
        bigint meeting_id FK UK "→ meetings.id (1:1)"
        bigint member_id FK "→ users.id"
        jsonb issues "선택 이슈 배열 예: [\"업무 블로커\",\"커리어 성장\"]"
        int energy_level "1~5"
        jsonb desired_roles "배열 예: [\"방향성 코칭\"]"
        float survey_score "0~100 (AI 분석용 X축 원천)"
        timestamp submitted_at
    }

    recordings {
        bigint id PK
        bigint meeting_id FK UK "→ meetings.id (1:1)"
        varchar file_url "업로드 경로 (분석 후 null 가능)"
        bigint file_size_bytes
        int duration_sec
        text transcript "Whisper STT 결과"
        timestamp created_at
    }

    analyses {
        bigint id PK
        bigint meeting_id FK UK "→ meetings.id (1:1)"
        varchar step "ENUM: PENDING | STT | NLP | SCORING | FEEDBACK | COMPLETED | FAILED"
        int progress "0~100"
        float survey_score "서베이 응답 점수 (X축)"
        float ai_score "AI 추론 점수 (Y축)"
        float honesty_gap "survey_score - ai_score"
        float safety_score "행동 기반 Safety Score"
        float alignment_gap "커리어 대화 Gap"
        float talk_ratio_leader "리더 발화 비율 0~1"
        float talk_ratio_member "멤버 발화 비율 0~1"
        jsonb behavior_counts "행동 카운팅 상세"
        jsonb ai_patterns "AI 패턴 감지 목록"
        jsonb blocker_keywords "블로커 키워드+빈도"
        varchar quadrant "ENUM: STABLE | SILENT_RISK | EXPLICIT_RISK | CONSERVATIVE"
        text error_message
        timestamp completed_at
        timestamp created_at
    }

    ai_feedbacks {
        bigint id PK
        bigint meeting_id FK "→ meetings.id"
        varchar target_role "ENUM: LEADER | MEMBER"
        varchar severity "ENUM: ERROR | WARNING | SUCCESS | INFO"
        varchar title "피드백 제목"
        text evidence_quote "대화 인용 근거"
        text data_summary "수치 데이터"
        text action_guide "행동 가이드"
        int display_order
        timestamp created_at
    }

    next_action_plans {
        bigint id PK
        bigint meeting_id FK "→ meetings.id"
        text content "행동 가이드 내용"
        boolean is_completed "기본 false"
        int display_order
        timestamp created_at
    }

    promises {
        bigint id PK
        bigint meeting_id FK "→ meetings.id"
        bigint owner_id FK "→ users.id (약속한 사람=리더)"
        text content "NOT NULL"
        varchar category "ENUM: RESOURCE | TEAM_BUILDING | RECOGNITION | PROCESS"
        date due_date
        varchar status "ENUM: PENDING | DONE | MISSED"
        timestamp created_at
    }

    career_events {
        bigint id PK
        bigint member_id FK "→ users.id"
        bigint meeting_id FK "→ meetings.id"
        varchar type "ENUM: ACHIEVEMENT | LEARNING | BLOCKER | PROPOSAL_ADOPTED"
        varchar title "NOT NULL"
        text description
        varchar impact_metric "예: 다운타임 0분, 공수 -60h"
        date event_date
        timestamp created_at
    }

    teams ||--o{ users : "has members"
    teams ||--o{ meetings : "has"
    users ||--o{ meetings : "leads (leader_id)"
    users ||--o{ meetings : "attends (member_id)"
    meetings ||--o| surveys : "has"
    meetings ||--o| recordings : "has"
    meetings ||--o| analyses : "has"
    meetings ||--o{ ai_feedbacks : "has"
    meetings ||--o{ next_action_plans : "has"
    meetings ||--o{ promises : "has"
    meetings ||--o{ career_events : "from"
    users ||--o{ career_events : "belongs to member"
    users ||--o{ promises : "owns (leader)"
```

---

## 테이블 상세 설명

### users
| 컬럼 | 타입 | 설명 |
|------|------|------|
| role | ENUM | `LEADER` = 팀장, `MEMBER` = 팀원 |
| job_title | VARCHAR | "시니어 FE 엔지니어" 등 표시용 직책 |
| team_id | FK | 소속 팀. NULL이면 미소속 |

---

### meetings
| 컬럼 | 타입 | 설명 |
|------|------|------|
| round | INT | 해당 리더-멤버 페어의 누적 회차 (자동 산출) |
| status | ENUM | `PENDING`→녹음 전, `ANALYZING`→분석중, `COMPLETED`→완료, `FAILED`→실패 |

---

### surveys
| 컬럼 | 타입 | 예시 값 |
|------|------|---------|
| issues | JSONB | `["업무 블로커", "커리어 성장"]` |
| energy_level | INT | 1(많이 지침) ~ 5(최고!) |
| desired_roles | JSONB | `["방향성 코칭", "의사결정 도움"]` |
| survey_score | FLOAT | 0~100. 에너지+이슈 긍부정 가중 산출 |

---

### analyses
| 컬럼 | 타입 | 설명 |
|------|------|------|
| step | ENUM | STT → NLP → SCORING → FEEDBACK 순서 |
| behavior_counts | JSONB | `{"negotiation":0,"initiative":0,"emotion":1,"positive":2,"question":1}` |
| ai_patterns | JSONB | `[{"type":"AVOIDANCE","quote":"그냥 괜찮아요","count":2}]` |
| blocker_keywords | JSONB | `[{"keyword":"QA 리소스 부족","count":5},...]` |
| quadrant | ENUM | `SILENT_RISK`(서베이↑AI↓), `STABLE`(둘다↑), `EXPLICIT_RISK`(둘다↓), `CONSERVATIVE`(서베이↓AI↑) |

---

### promises
| 컬럼 | 타입 | 설명 |
|------|------|------|
| category | ENUM | `RESOURCE`(리소스), `TEAM_BUILDING`(팀빌딩), `RECOGNITION`(인정), `PROCESS`(프로세스) |
| owner_id | FK | 원칙상 리더가 멤버에게 하는 약속 |

---

### career_events
| 컬럼 | 타입 | 설명 |
|------|------|------|
| type | ENUM | `ACHIEVEMENT`(성취), `LEARNING`(배움), `BLOCKER`(블로커 제기), `PROPOSAL_ADOPTED`(제안 채택) |
| impact_metric | VARCHAR | "다운타임 0분", "공수 -60h" 등 임팩트 수치 |
