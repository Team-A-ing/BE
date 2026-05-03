# ReadB API 명세서

> **Base URL**: `https://api.readb.io` (개발: `http://localhost:8080`)  
> **버전**: `v1`  
> **인증**: JWT Bearer Token (`Authorization: Bearer <token>`)  
> **응답 형식**: 모든 응답은 `ApiResponse<T>` 래퍼 사용

---

## 공통 규약

### 공통 응답 형식
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": { ... }
}
```

### 에러 응답
```json
{
  "success": false,
  "code": "USER_NOT_FOUND",
  "message": "사용자를 찾을 수 없습니다.",
  "data": null
}
```

### HTTP 상태 코드
| 코드 | 의미 |
|------|------|
| 200 | OK |
| 201 | Created |
| 202 | Accepted (비동기 작업 시작) |
| 400 | Bad Request (입력 오류) |
| 401 | Unauthorized (토큰 없음/만료) |
| 403 | Forbidden (권한 없음) |
| 404 | Not Found |
| 409 | Conflict (중복) |
| 500 | Internal Server Error |

---

## 1. AUTH

### 1.1 회원가입
```
POST /api/v1/auth/signup
```
**Request Body**
```json
{
  "email": "kang@company.com",
  "password": "password123!",
  "name": "강다은",
  "role": "MEMBER",
  "jobTitle": "시니어 FE 엔지니어"
}
```
**Response** `201`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "user": {
      "id": 1,
      "email": "kang@company.com",
      "name": "강다은",
      "role": "MEMBER",
      "jobTitle": "시니어 FE 엔지니어",
      "teamId": null
    }
  }
}
```

---

### 1.2 로그인
```
POST /api/v1/auth/login
```
**Request Body**
```json
{
  "email": "lee@company.com",
  "password": "password123!"
}
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "user": {
      "id": 2,
      "email": "lee@company.com",
      "name": "이준혁",
      "role": "LEADER",
      "jobTitle": "팀장",
      "teamId": 1
    }
  }
}
```

---

### 1.3 토큰 갱신
```
POST /api/v1/auth/refresh
```
**Request Body**
```json
{
  "refreshToken": "eyJhbGci..."
}
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "accessToken": "eyJhbGci..."
  }
}
```

---

## 2. USER

### 2.1 내 정보 조회
```
GET /api/v1/users/me
Authorization: Bearer <token>
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "id": 1,
    "email": "kang@company.com",
    "name": "강다은",
    "role": "MEMBER",
    "jobTitle": "시니어 FE 엔지니어",
    "teamId": 1,
    "teamName": "Product A팀"
  }
}
```

---

## 3. TEAM

### 3.1 팀 생성 (리더 전용)
```
POST /api/v1/teams
Authorization: Bearer <token> (LEADER)
```
**Request Body**
```json
{
  "name": "Product A팀"
}
```
**Response** `201`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "id": 1,
    "name": "Product A팀",
    "leaderId": 2,
    "leaderName": "이준혁",
    "inviteCode": "READB-A1B2C3"
  }
}
```

---

### 3.2 팀 초대 코드로 참여 (멤버)
```
POST /api/v1/teams/join
Authorization: Bearer <token>
```
**Request Body**
```json
{
  "inviteCode": "READB-A1B2C3"
}
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "teamId": 1,
    "teamName": "Product A팀"
  }
}
```

---

### 3.3 팀 멤버 목록 조회
```
GET /api/v1/teams/{teamId}/members
Authorization: Bearer <token>
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": [
    {
      "id": 1,
      "name": "강다은",
      "jobTitle": "시니어 FE 엔지니어",
      "role": "MEMBER",
      "quadrant": "SILENT_RISK",
      "totalScore": 38,
      "blockerTags": ["QA 리소스", "일정 압박"]
    },
    {
      "id": 3,
      "name": "김민준",
      "jobTitle": "FE 엔지니어",
      "role": "MEMBER",
      "quadrant": "STABLE",
      "totalScore": 81,
      "blockerTags": ["컴포넌트 최적화", "RSC 학습"]
    }
  ]
}
```

---

## 4. TEAM DASHBOARD (리더 전용)

### 4.1 팀 헬스 스코어 조회
```
GET /api/v1/teams/{teamId}/health-score
Authorization: Bearer <token> (LEADER)
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "currentScore": 74,
    "previousScore": 62,
    "diff": 12,
    "trend": "IMPROVING",
    "history": [
      { "yearMonth": "2025-11", "score": 55 },
      { "yearMonth": "2025-12", "score": 58 },
      { "yearMonth": "2026-01", "score": 60 },
      { "yearMonth": "2026-02", "score": 63 },
      { "yearMonth": "2026-03", "score": 62 },
      { "yearMonth": "2026-04", "score": 74 }
    ]
  }
}
```

---

### 4.2 블로커 보드 조회
```
GET /api/v1/teams/{teamId}/blocker-board
Authorization: Bearer <token> (LEADER)
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "aiDiagnosis": "QA 리소스 부족과 FE 소통 지연이 이번 스프린트의 핵심 병목입니다. 위험군 2명에게 즉시 비공식 1on1을 배정하세요.",
    "blockerKeywords": [
      { "keyword": "QA 리소스 부족", "count": 5 },
      { "keyword": "프론트엔드 소통 지연", "count": 4 },
      { "keyword": "코드 리뷰 병목", "count": 4 },
      { "keyword": "기획 변경 잦음", "count": 3 },
      { "keyword": "API 스펙 불명확", "count": 3 }
    ],
    "actionPrescriptions": [
      {
        "severity": "ERROR",
        "message": "QA 리소스 부족이 5명에게서 3회 연속 언급되었습니다. 이번 주 주간 회의에서 배포 일정 1주 연기 또는 QA 계약직 충원 안건을 상정하세요."
      },
      {
        "severity": "WARNING",
        "message": "강다은·윤재원 두 FE/BE 멤버의 AI 추론이 서베이보다 50pt 이상 낮습니다. 이번 주 안에 비공식 1on1을 먼저 배정하세요."
      },
      {
        "severity": "INFO",
        "message": "스프린트 킥오프 전 30분 내 의제 사전 공유를 도입하면 Alignment Gap을 평균 12pt 줄일 수 있습니다."
      }
    ]
  }
}
```

---

### 4.3 팀 사분면 레이더 조회
```
GET /api/v1/teams/{teamId}/quadrant
Authorization: Bearer <token> (LEADER)
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "members": [
      {
        "memberId": 1,
        "name": "강다은",
        "surveyScore": 87,
        "aiScore": 31,
        "quadrant": "SILENT_RISK"
      },
      {
        "memberId": 3,
        "name": "김민준",
        "surveyScore": 80,
        "aiScore": 82,
        "quadrant": "STABLE"
      }
    ],
    "quadrantCounts": {
      "STABLE": 5,
      "SILENT_RISK": 2,
      "EXPLICIT_RISK": 1,
      "CONSERVATIVE": 3
    }
  }
}
```

---

## 5. MEETING

### 5.1 1on1 미팅 생성 (리더)
```
POST /api/v1/meetings
Authorization: Bearer <token> (LEADER)
```
**Request Body**
```json
{
  "memberId": 1,
  "scheduledAt": "2026-04-29T14:00:00"
}
```
**Response** `201`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "meetingId": 10,
    "round": 12,
    "memberId": 1,
    "memberName": "강다은",
    "scheduledAt": "2026-04-29T14:00:00",
    "status": "PENDING",
    "surveyUrl": "/survey/abc123"
  }
}
```

---

### 5.2 미팅 목록 조회
```
GET /api/v1/meetings?teamId={teamId}&memberId={memberId}
Authorization: Bearer <token>
```
**Query Params**
| 파라미터 | 필수 | 설명 |
|---------|------|------|
| teamId | N | 팀 필터 |
| memberId | N | 특정 멤버 필터 |

**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": [
    {
      "meetingId": 10,
      "round": 12,
      "memberName": "강다은",
      "scheduledAt": "2026-04-28T14:00:00",
      "durationSec": 2400,
      "status": "COMPLETED"
    }
  ]
}
```

---

## 6. SURVEY (사전 서베이)

### 6.1 사전 서베이 제출 (멤버)
```
POST /api/v1/surveys
Authorization: Bearer <token> (MEMBER)
```
**Request Body**
```json
{
  "meetingId": 10,
  "issues": ["업무 블로커", "커리어 성장"],
  "energyLevel": 3,
  "desiredRoles": ["방향성 코칭"]
}
```
**Response** `201`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "surveyId": 5,
    "surveyScore": 87.0,
    "submittedAt": "2026-04-29T13:30:00"
  }
}
```

---

### 6.2 서베이 조회
```
GET /api/v1/surveys/{meetingId}
Authorization: Bearer <token>
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "surveyId": 5,
    "issues": ["업무 블로커", "커리어 성장"],
    "energyLevel": 3,
    "desiredRoles": ["방향성 코칭"],
    "surveyScore": 87.0,
    "submittedAt": "2026-04-29T13:30:00"
  }
}
```

---

## 7. RECORDING & ANALYSIS

### 7.1 녹음 파일 업로드
```
POST /api/v1/meetings/{meetingId}/recording
Authorization: Bearer <token>
Content-Type: multipart/form-data
```
**Request**
```
file: <WebM 파일, 최대 25MB>
durationSec: 2400
```
**Response** `202` (즉시 반환, 비동기 분석 시작)
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "meetingId": 10,
    "analysisId": 7,
    "message": "분석이 시작되었습니다. /api/v1/analysis/10/status 에서 진행률을 확인하세요."
  }
}
```

---

### 7.2 분석 진행 상태 폴링
```
GET /api/v1/analysis/{meetingId}/status
Authorization: Bearer <token>
```
> 프론트엔드는 3초 간격으로 폴링. `step`이 `COMPLETED` 또는 `FAILED`이면 폴링 종료.

**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "meetingId": 10,
    "step": "STT",
    "stepNumber": 1,
    "totalSteps": 4,
    "progress": 22,
    "stepLabel": "대화를 텍스트로 바꾸는 중...",
    "wittyMessage": {
      "leader": "팀원이 '괜찮다'고 했을 때, 진짜 괜찮은 건지 확인 중...",
      "member": "오늘 내가 얼마나 솔직했는지 들여다보는 중..."
    }
  }
}
```
**step 값 목록**
| step | stepNumber | stepLabel |
|------|------------|-----------|
| PENDING | 0 | 분석 준비 중... |
| STT | 1 | 대화를 텍스트로 바꾸는 중... |
| NLP | 2 | 행간의 의미를 읽는 중... |
| SCORING | 3 | 3대 Gap을 계산하는 중... |
| FEEDBACK | 4 | 피드백을 다듬는 중... |
| COMPLETED | 4 | 분석 완료 |
| FAILED | - | 분석 실패 |

---

## 8. 1ON1 REPORT

### 8.1 리더 리포트 조회
```
GET /api/v1/meetings/{meetingId}/leader-report
Authorization: Bearer <token> (LEADER)
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "meetingId": 10,
    "round": 12,
    "memberName": "강다은",
    "memberJobTitle": "시니어 FE 엔지니어",
    "meetingDate": "2026-04-28",
    "durationSec": 2400,
    "safetyScore": 30,
    "honestyGap": {
      "surveyScore": 87,
      "aiScore": 31,
      "gap": 56,
      "riskLevel": "DANGER"
    },
    "behaviorCounts": {
      "negotiation": { "count": 0, "average": 2.4, "isAlert": true },
      "initiative":  { "count": 0, "average": 1.8, "isAlert": true },
      "emotion":     { "count": 1, "average": 3.1, "isAlert": true },
      "positive":    { "count": 2, "average": 3.8, "isAlert": false },
      "question":    { "count": 1, "average": 2.0, "isAlert": false }
    },
    "aiPatterns": [
      { "type": "PASSIVE_AGGRESSIVE", "quote": "제가 다 해야죠", "count": 1 },
      { "type": "RESPONSE_DELAY",     "avgDelaySec": 2.3, "teamAvgSec": 0.9 },
      { "type": "AVOIDANCE",          "quote": "그냥 괜찮아요", "count": 2 },
      { "type": "SIGH",               "count": 3 }
    ],
    "talkRatio": {
      "leaderRatio": 70,
      "memberRatio": 30,
      "recommendedLeaderRatio": 40
    },
    "feedbacks": [
      {
        "feedbackId": 1,
        "severity": "WARNING",
        "title": "멤버 발언 비율 30% — 권장(50%) 미달",
        "evidenceQuote": "저도 뭐 그냥... 괜찮아요. 별로 할 말이 없어요.",
        "dataSummary": "리더 발화 70%, 멤버 30%. 7회 연속 리더 발언 구간 2회 감지.",
        "actionGuide": "다음 1on1 시작 5분은 질문만 하세요."
      },
      {
        "feedbackId": 2,
        "severity": "ERROR",
        "title": "Initiative 0회 — 베이스라인(2.8회) 대비 100% 하락",
        "evidenceQuote": "제가 한번 맡아볼게요 발화 없음 (지난 3회 미팅 평균 2.8회)",
        "dataSummary": "Vulnerability 2회 / Dissent 0회 / Initiative 0회. Safety 49→31점.",
        "actionGuide": "이슈를 숨기고 있을 가능성이 높습니다."
      },
      {
        "feedbackId": 3,
        "severity": "SUCCESS",
        "title": "QA 리소스 이슈를 명확하게 제기했습니다",
        "evidenceQuote": "이번 스프린트도 QA가 부족해서 배포를 또 미뤘어요.",
        "dataSummary": "Constructive Dissent 1회. 구체적 상황+의견 제시 고품질 발화.",
        "actionGuide": "그 외에 팀 차원에서 바꿨으면 하는 게 있어? 로 더 꺼내도록 유도하세요."
      }
    ],
    "nextActionPlans": [
      { "planId": 1, "content": "다음 1on1 시작 5분은 질문만 합니다.", "isCompleted": false },
      { "planId": 2, "content": "QA 리소스 이슈에 대해 이번 주 목요일까지 구체적 답변을 전달합니다.", "isCompleted": false },
      { "planId": 3, "content": "발화 비율을 40% 이하로 줄이기 위해 '3초 pause' 기법을 연습합니다.", "isCompleted": true },
      { "planId": 4, "content": "강다은 님의 MSA 전환 성과를 5월 올핸즈 미팅에서 공개 발표합니다.", "isCompleted": false }
    ],
    "promises": {
      "previous": [
        { "promiseId": 1, "content": "기술 블로그 주제 함께 정하기", "status": "DONE" },
        { "promiseId": 2, "content": "AWS 프로덕션 접근 권한 부여", "status": "MISSED" },
        { "promiseId": 3, "content": "QA 리소스 이슈 에스컬레이션", "status": "MISSED" }
      ],
      "new": [
        { "promiseId": 4, "content": "AWS 프로덕션 접근 권한 부여", "category": "RESOURCE",      "dueDate": "2026-05-06", "status": "PENDING" },
        { "promiseId": 5, "content": "QA 충원 안건 전사 회의 상정",  "category": "TEAM_BUILDING", "dueDate": "2026-05-02", "status": "PENDING" },
        { "promiseId": 6, "content": "강다은 성과 올핸즈 발표",      "category": "RECOGNITION",   "dueDate": "2026-05-15", "status": "PENDING" }
      ]
    }
  }
}
```

---

### 8.2 멤버 리포트 조회
```
GET /api/v1/meetings/{meetingId}/member-report
Authorization: Bearer <token> (MEMBER)
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "meetingId": 10,
    "round": 12,
    "leaderName": "이준혁 팀장",
    "meetingDate": "2026-04-28",
    "durationSec": 2400,
    "overallResult": "SUCCESS",
    "confirmedAchievements": [
      {
        "careerEventId": 15,
        "type": "ACHIEVEMENT",
        "title": "스프린트 3 FE 리드 완주",
        "description": "API 지연 상황에서 목 서버를 직접 구축해 팀 전체 개발을 무중단으로 유지했습니다.",
        "impactMetric": "출시 지연 0일",
        "leaderQuote": "위기 상황에서의 문제해결 능력과 팀 전체를 이끄는 자세가 탁월했습니다.",
        "leaderName": "이준혁 팀장"
      },
      {
        "careerEventId": 16,
        "type": "PROPOSAL_ADOPTED",
        "title": "공통 컴포넌트 라이브러리 기여",
        "description": "DatePicker 공통화로 3개 팀 총 60시간 절감. 조직 기여 성과로 공식 등록.",
        "impactMetric": "공수 -60h",
        "leaderQuote": "혼자 해결하지 않고 팀 전체를 끌어올리는 기여였습니다.",
        "leaderName": "이준혁 팀장"
      }
    ],
    "leaderPromises": [
      { "promiseId": 4, "content": "AWS 프로덕션 접근 권한 부여",       "category": "RESOURCE",      "dueDate": "2026-05-06", "status": "PENDING" },
      { "promiseId": 5, "content": "QA 계약직 충원 안건 전사 회의 상정", "category": "TEAM_BUILDING", "dueDate": "2026-05-02", "status": "PENDING" },
      { "promiseId": 6, "content": "MSA 전환 성과 5월 올핸즈 발표",      "category": "RECOGNITION",   "dueDate": "2026-05-15", "status": "PENDING" },
      { "promiseId": 7, "content": "FE 코드 리뷰 SLA 기준 문서화",       "category": "PROCESS",       "dueDate": "2026-04-30", "status": "DONE" }
    ]
  }
}
```

---

## 9. PROMISE (약속 장부)

### 9.1 약속 생성 (리더)
```
POST /api/v1/promises
Authorization: Bearer <token> (LEADER)
```
**Request Body**
```json
{
  "meetingId": 10,
  "content": "AWS 프로덕션 접근 권한 부여",
  "category": "RESOURCE",
  "dueDate": "2026-05-06"
}
```
**Response** `201`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "promiseId": 4,
    "status": "PENDING"
  }
}
```

---

### 9.2 약속 상태 업데이트
```
PATCH /api/v1/promises/{promiseId}/status
Authorization: Bearer <token> (LEADER)
```
**Request Body**
```json
{
  "status": "DONE"
}
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "promiseId": 4,
    "status": "DONE"
  }
}
```

---

### 9.3 미이행 약속 목록 조회
```
GET /api/v1/promises/overdue?memberId={memberId}
Authorization: Bearer <token> (LEADER)
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": [
    {
      "promiseId": 2,
      "content": "AWS 프로덕션 접근 권한 부여",
      "category": "RESOURCE",
      "dueDate": "2026-04-20",
      "status": "MISSED",
      "fromMeetingRound": 11
    }
  ]
}
```

---

## 10. NEXT ACTION PLAN

### 10.1 액션 플랜 완료 체크
```
PATCH /api/v1/action-plans/{planId}/complete
Authorization: Bearer <token> (LEADER)
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "planId": 3,
    "isCompleted": true
  }
}
```

---

## 11. CAREER MEMORY (멤버용)

### 11.1 커리어 통계 조회
```
GET /api/v1/members/{memberId}/career-stats
Authorization: Bearer <token>
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "memberId": 1,
    "name": "강다은",
    "jobTitle": "시니어 프론트엔드 엔지니어",
    "teamName": "Product A팀",
    "totalMeetings": 12,
    "achievementCount": 6,
    "leaderEndorsementCount": 12,
    "contributionPercentile": 15,
    "aiSummary": "결제 MSA 전환, 검색 성능 개선, 팀 DX 리드 — 기술적 역량과 팀 임팩트를 동시에 만드는 엔지니어입니다."
  }
}
```

---

### 11.2 커리어 이벤트 타임라인 조회
```
GET /api/v1/members/{memberId}/career-timeline?type={type}
Authorization: Bearer <token>
```
**Query Params**
| 파라미터 | 필수 | 설명 |
|---------|------|------|
| type | N | ACHIEVEMENT \| LEARNING \| BLOCKER \| PROPOSAL_ADOPTED |

**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": [
    {
      "careerEventId": 15,
      "type": "ACHIEVEMENT",
      "title": "결제 시스템 MSA 전환 완료",
      "description": "모놀리스 결제를 3개 마이크로서비스로 분리. 다운타임 0분, 배포 주기 3배 단축.",
      "impactMetric": "다운타임 0분",
      "eventDate": "2026-04-25",
      "meetingRound": 12
    },
    {
      "careerEventId": 14,
      "type": "PROPOSAL_ADOPTED",
      "title": "공통 DatePicker 컴포넌트 제안 → 팀 도입",
      "description": "3개 팀이 중복 개발 중인 DatePicker를 공통화하여 약 60h 절감.",
      "impactMetric": "공수 -60h",
      "eventDate": "2026-04-10",
      "meetingRound": 11
    }
  ]
}
```

---

### 11.3 핵심 성과 쇼케이스 조회 (상위 5건)
```
GET /api/v1/members/{memberId}/career-showcase
Authorization: Bearer <token>
```
**Response** `200`
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": [
    {
      "careerEventId": 15,
      "type": "ACHIEVEMENT",
      "title": "결제 시스템 MSA 전환 완료",
      "description": "모놀리스 결제를 3개 마이크로서비스로 분리. 다운타임 0분, 배포 주기 3배 단축.",
      "impactMetric": "다운타임 0분",
      "eventDate": "2026-04-25",
      "meetingRound": 12
    }
  ]
}
```

---

## 에러 코드 목록

| 코드 | HTTP | 설명 |
|------|------|------|
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 사용 중인 이메일 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |
| `EXPIRED_TOKEN` | 401 | 액세스 토큰 만료 |
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰 |
| `UNAUTHORIZED` | 401 | 인증이 필요합니다 |
| `FORBIDDEN` | 403 | 접근 권한 없음 |
| `USER_NOT_FOUND` | 404 | 사용자 없음 |
| `TEAM_NOT_FOUND` | 404 | 팀 없음 |
| `MEETING_NOT_FOUND` | 404 | 미팅 없음 |
| `RECORDING_NOT_FOUND` | 404 | 녹음 파일 없음 |
| `SURVEY_NOT_FOUND` | 404 | 서베이 없음 |
| `SURVEY_ALREADY_SUBMITTED` | 409 | 이미 제출된 서베이 |
| `ANALYSIS_NOT_FOUND` | 404 | 분석 결과 없음 |
| `ANALYSIS_IN_PROGRESS` | 409 | 이미 분석 진행 중 |
| `ANALYSIS_FAILED` | 500 | 분석 처리 중 오류 |
| `FILE_TOO_LARGE` | 413 | 파일 25MB 초과 |
| `INVALID_FILE_FORMAT` | 400 | 지원하지 않는 파일 형식 |
| `LLM_PARSE_FAILED` | 500 | AI 응답 파싱 실패 |
| `INVALID_INPUT` | 400 | 잘못된 입력값 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |
