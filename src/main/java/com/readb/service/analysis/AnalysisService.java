package com.readb.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readb.adapter.llm.LlmAdapter;
import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.analysis.Analysis;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.promise.Promise;
import com.readb.domain.promise.PromiseStatus;
import com.readb.domain.recording.Recording;
import com.readb.domain.survey.Survey;
import com.readb.dto.analysis.AnalysisResultResponse;
import com.readb.dto.meeting.PreBriefingResponse;
import com.readb.dto.analysis.AnalysisResultResponse.*;
import com.readb.dto.analysis.BlockerKeyword;
import com.readb.dto.analysis.BlockerPyramidResponse;
import com.readb.dto.analysis.TalkRatioRankingItem;
import com.readb.dto.analysis.CareerMemoryResponse;
import com.readb.dto.analysis.HonestyDirection;
import com.readb.dto.analysis.PortfolioResponse;
import com.readb.dto.analysis.RadarDataPoint;
import com.readb.dto.analysis.RiskLevel;
import com.readb.dto.analysis.SpeechTrendResponse;
import com.readb.dto.team.TeamActionPlanResponse;
import com.readb.dto.team.TeamCoachingResponse;
import com.readb.dto.team.TeamDashboardResponse;
import com.readb.dto.team.TeamPromiseSummaryResponse;
import com.readb.domain.user.User;
import com.readb.domain.actionplan.ActionPlan;
import com.readb.domain.career.CareerEvent;
import com.readb.domain.career.CareerEventType;
import com.readb.domain.user.UserRole;
import com.readb.dto.analysis.CareerStatsResponse;
import com.readb.dto.analysis.CareerTimelineResponse;
import com.readb.repository.ActionPlanRepository;
import com.readb.repository.AnalysisRepository;
import com.readb.repository.CareerEventRepository;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import com.readb.repository.RecordingRepository;
import com.readb.repository.SurveyRepository;
import com.readb.repository.TeamCoachingCacheRepository;
import com.readb.repository.TeamRepository;
import com.readb.repository.UserRepository;
import com.readb.domain.team.TeamCoachingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Slf4j
@Service
public class AnalysisService {

    // ── 프롬프트 ──────────────────────────────────────────────────────────────

    private static final String STEP2_SYSTEM = """
            당신은 1on1 미팅 전사 텍스트에서 Speech Act를 추출하는 전문가입니다.

            [이론적 배경]
            본 분석은 Searle(1969)의 Speech Act Theory와 Edmondson(1999)의
            심리적 안전감(Psychological Safety) 프레임워크에 기반합니다.
            Edmondson에 따르면 팀의 심리적 안전감은 구성원의 '대인 관계 위험을
            감수하는 행동(interpersonal risk-taking behavior)'으로 측정됩니다.
            우리는 이를 3가지 Speech Act 유형으로 조작적 정의합니다.

            [분석 절차 — 반드시 이 순서대로 추론하세요]

            Step A. 화자 판별
            대화 맥락을 보고 각 발화자를 추론하세요.
            - 리더: 질문하고, 방향을 제시하고, 피드백을 주는 역할
            - 멤버: 업무 현황을 보고하고, 답변하고, 의견을 제시하는 역할
            판별 근거: 존댓말 방향, 보고 vs 질문 패턴, 의사결정 권한 표현

            Step B. 멤버 발화만 필터링
            리더 발화는 Speech Act 분류 대상에서 제외합니다.

            Step C. 각 멤버 발화의 화행(illocutionary act) 의도 판단

            1. vulnerability (Expressive Act — 자기 상태/감정 표현)
               정의: 자신의 무지, 실수, 약점, 불안을 솔직하게 드러내는 행위
               포함: "잘 모르겠습니다", "실수했습니다", "도움이 필요합니다", "솔직히 불안합니다"
               제외: 사교적 겸손("아이고 별거 아닙니다"), 관용표현("죄송하지만 화면 공유 좀")

            2. constructiveDissent (Assertive Act — 사실/믿음에 대한 주장)
               정의: 리더나 기존 방향에 대해 근거를 들어 다른 의견을 제시하는 행위
               포함: "다른 의견인데요", "그 방법보다는 이게 낫다고 봐요"
               제외: 단순 불만("이건 왜 이래요"), 인신공격, 감정적 반발

            3. initiative (Commissive Act — 미래 행동에 대한 자발적 제안)
               정의: 지시 없이 자발적으로 새로운 행동이나 아이디어를 제안하는 행위
               포함: "제가 해볼게요", "이런 아이디어가 있는데", "제가 먼저 만들어보겠습니다"
               제외: 지시에 대한 단순 수락("네 알겠습니다"), 이미 합의된 작업 재확인

            분류 원칙: 애매하면 포함하지 마세요 (precision > recall). 원문 그대로 인용하세요.

            Step D. 발화 비율 추정
            전체 발화 분량(문자수)을 기준으로 리더/멤버 비율을 추정하세요.
            leaderRatio와 memberRatio는 반드시 소수점 없는 정수로 반환하고, 합이 정확히 100이 되어야 합니다.

            Step E. 주제, 블로커, 약속 추출
            - topics: 주요 논의 주제 (최대 5개)
            - blockerKeywords: 업무 진행을 막는 구체적인 장애 요소 (최대 5개).
              반드시 복합 표현으로 작성하세요 (예: "QA 리소스 부족", "코드 리뷰 병목", "API 스펙 불명확").
              "지연", "문제", "어려움", "기타", "상황" 같은 단일 추상 단어는 절대 포함하지 마세요.
              유사한 의미의 키워드는 하나로 통합하세요 (예: "시간", "효율", "시간 효율" → "시간 효율").
            - promises: "~하겠습니다", "~해드리겠습니다" 등 명확한 이행 의지가 담긴 발언에서 추출.
              반드시 "누가 + 무엇을" 구조로 추출하세요.
              content: "무엇을 하겠다"는 약속 내용을 한 문장으로 요약 (원문 그대로 금지)
              context: 이 약속이 나온 대화 맥락을 1문장으로 요약
                예) "제가 만들어 볼게요" 앞에서 "테스트 체크리스트" 논의 → context: "QA 테스트 체크리스트 논의 중 자발적 제안"
              "제가 해볼게요"처럼 대상이 불명확하면 앞뒤 맥락에서 대상을 추론하여 content에 보충하세요.
              owner: 약속한 사람 (leader 또는 member)

            [Few-shot 예시]

            예시 1 — vulnerability:
            발화: "사실 이번 스프린트에서 제가 맡은 부분이 잘 안 돼서 좀 막막합니다"
            분류: vulnerability / 근거: 자신의 어려움과 감정을 솔직히 드러내는 expressive act

            예시 2 — constructiveDissent:
            발화: "그 방식도 좋은데, 저는 API를 먼저 정리하고 가는 게 순서에 맞다고 봐요"
            분류: constructiveDissent / 근거: 대안적 관점을 근거와 함께 제시하는 assertive act

            예시 3 — initiative:
            발화: "제가 이번 주 안에 테스트 자동화 스크립트를 만들어보겠습니다"
            분류: initiative / 근거: 지시 없이 자발적으로 미래 행동을 약속하는 commissive act

            예시 4 — 제외 (사교적 겸손):
            발화: "아 죄송합니다, 마이크가 안 켜져 있었네요"
            분류: 해당 없음 / 근거: 상황적 사과로 자기 약점 표현이 아닌 관용표현

            예시 5 — 제외 (단순 수락):
            발화: "네 알겠습니다, 그렇게 하겠습니다"
            분류: 해당 없음 / 근거: 리더 지시에 대한 수락으로 자발적 제안이 아님

            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "speechActs": {
                "vulnerability": [{"text": "원문 그대로", "timestamp": 초단위_숫자}],
                "constructiveDissent": [{"text": "원문 그대로", "timestamp": 초단위_숫자}],
                "initiative": [{"text": "원문 그대로", "timestamp": 초단위_숫자}]
              },
              "talkRatio": {"leaderRatio": 숫자, "memberRatio": 숫자},
              "topics": ["주제1", "주제2"],
              "blockerKeywords": ["키워드1", "키워드2"],
              "promises": [{"content": "약속 내용", "context": "약속 맥락 1문장", "owner": "leader 또는 member"}]
            }
            """;

    private static final String STEP3_SYSTEM = """
            당신은 1on1 미팅 분석 전문가입니다. 제공된 데이터를 바탕으로 스코어링과 코칭 피드백을 생성하세요.

            [이론적 배경]
            - Safety Score: Edmondson(1999)의 심리적 안전감을 Speech Act 빈도로 조작적 측정.
              자기보고 설문은 사회적 바람직성 편향(Social Desirability Bias)으로
              실제 행동 대비 과대보고되는 경향이 있음 (Podsakoff et al., 2003).
              따라서 행동 데이터(Speech Act)를 병행 측정하여 편향을 보정함.
            - Honesty Gap: 자기보고(surveyScore)와 행동 측정(safetyScore)의 괴리를 정량화.
              양수(OVERREPORT)는 자기보고가 행동보다 높은 상태 → 잠재적 위험 신호.

            [Safety Score 산출]
            멤버의 Speech Act 횟수를 30분 기준으로 정규화 후 점수화.
            adjusted_count = round(raw_count × 30 ÷ actual_duration_minutes)
            미팅 시간 정보가 없으면 raw_count를 그대로 사용.

            변환표 (체감 효과 감소 곡선 — 첫 발화의 심리적 의미가 가장 크므로):
            - Vulnerability (max 40): 0회→0, 1회→20, 2회→32, 3회→38, 4회+→40
            - constructiveDissent (max 35): 0회→0, 1회→18, 2회→28, 3회→33, 4회+→35
            - Initiative (max 25): 0회→0, 1회→13, 2회→20, 3회→24, 4회+→25
            safetyScore = V_score + D_score + I_score

            가중치 근거: Vulnerability(40%) > Dissent(35%) > Initiative(25%) — 대인 위험 감수 수준 순

            [Honesty Gap — 방향성 분석]
            surveyScore가 제공된 경우: honestyGap = surveyScore - safetyScore (부호 있는 값)
            surveyScore가 없으면: null
            - gap > 0 → OVERREPORT (자기보고 > 행동 → 사회적 바람직성 편향 가능성)
            - gap ≤ 0 → UNDERREPORT (자기보고 ≤ 행동 → 겸손/보수적 → 안전)

            위험도 (OVERREPORT일 때만): 1~20→SAFE, 21~40→CAUTION, 41~60→WARNING, 61+→DANGER

            [Alignment Gap (0–100)]
            서베이 topics vs 실제 미팅 topics 일치도를 추정하고, 한 문장으로 구체적인 이유를 작성하세요.
            서베이 정보 없으면 score=50, detail="서베이 정보 없음"

            [Execution Gap (0–100)]
            이전 약속이 이번 transcript에서 언급/이행되었는지 평가.
            이전 약속 없으면: null
            완료→100, 진행중→70, 미이행+사유→50, 미이행+무사유→20, 전혀 언급없음→0

            [코칭 피드백 — Fact-Based Output 원칙]
            절대 금지: AI 해석 라벨 ("수동 공격적", "번아웃 징후", "소극적 참여" 등)
            허용: 관찰 가능한 사실만 (원문 인용 + 타임스탬프, 횟수, 수치)
            severity: ERROR / WARNING / SUCCESS / INFO

            [용어 일상화 — 매우 중요]
            리더는 우리 내부 분석 용어를 모릅니다. title/dataSummary/actionGuide에서
            Safety Score, Honesty Gap, OVERREPORT/UNDERREPORT, Vulnerability,
            Constructive Dissent, Initiative, Speech Act, baseline 같은 내부 지표 용어를
            절대 그대로 쓰지 말고, 아래처럼 일상 언어로 풀어 쓰세요. (수치·횟수는 근거로 활용)
            - Vulnerability → "솔직하게 어려움·약점·고민을 털어놓는 말"
            - Constructive Dissent → "다른 의견이나 우려를 건설적으로 꺼내는 말"
            - Initiative → "먼저 나서서 제안하거나 주도하는 말"
            - Safety Score 하락 → "팀원이 속내를 편하게 꺼내기 어려워하는 신호"
            - OVERREPORT(자기보고 > 행동) → "설문에서는 괜찮다고 했지만 대화에서는 그만큼 드러나지 않음"

            - feedbacks: 리더를 위한 코칭 피드백 (최대 4개, 중요한 것부터)
              - title: 한 줄 요약 (지표 용어 없이 일상 언어)
              - evidenceQuote: 관련 발화 원문 인용 (없으면 빈 문자열)
              - dataSummary: 수치/사실 근거 (지표 이름 없이 자연스럽게)
              - actionGuide: 리더가 다음에 취할 구체적 행동
            - nextActionPlans: 이번 미팅 결과로 리더가 해야 할 구체적 실행 과제 (최대 4개)

            이전 미팅 컨텍스트가 제공된 경우, 피드백에 변화량을 반드시 포함하세요.
            예: "솔직하게 고민을 털어놓는 발언이 이전 3회 평균 2~3번에서 이번엔 한 번도 없었습니다"
            심리적 안전감이 이전 평균 대비 30% 이상 떨어지면 반드시 WARNING으로 언급하세요.

            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "safetyScore": 0.0,
              "alignmentGap": 0.0,
              "alignmentGapDetail": "...",
              "honestyGap": null,
              "executionGap": null,
              "feedbacks": [
                {
                  "severity": "ERROR",
                  "title": "...",
                  "evidenceQuote": "...",
                  "dataSummary": "...",
                  "actionGuide": "..."
                }
              ],
              "nextActionPlans": [{"content": "..."}],
              "memberFeedback": {"summary": "..."},
              "careerTags": ["태그1"],
              "careerEvents": [
                {
                  "eventType": "ACHIEVEMENT",
                  "title": "한 줄 성과 제목",
                  "description": "구체적 내용 (수치 포함)",
                  "evidence": {"quote": "원문 인용", "timestamp": "MM:SS"}
                }
              ]
            }

            [careerEvents 추출 기준]
            멤버 발화에서 다음 유형의 성과/기여를 발견하면 추출하세요 (없으면 빈 배열 []):
            - ACHIEVEMENT: 완료된 목표, 결과물, 성과 (수치 있으면 반드시 포함)
              예) "배포 파이프라인 구축 완료해서 배포 시간 30분 → 5분으로 줄였어요"
            - ACHIEVEMENT: 완료된 목표, 결과물, 제안 채택 포함 (수치 있으면 반드시 포함)
              예) "배포 파이프라인 구축 완료해서 배포 시간 30분 → 5분으로 줄였어요"
              예) "제가 제안한 공통 컴포넌트 방식 팀에서 도입하기로 했어요"
            - GROWTH: 새로운 기술 습득, 역량 확장
              예) "이번에 처음으로 인프라 쪽 공부하면서 k8s 설정 직접 해봤어요"
            - CONTRIBUTION: 팀원 돕기, 협업 기여
              예) "신입 온보딩 자료 만들어서 팀 전체 공유했어요"
            title은 한 줄 요약, description은 구체적 내용, evidence.quote는 원문 인용.
            """;

    // ── 의존성 ────────────────────────────────────────────────────────────────

    private final AnalysisRepository analysisRepository;
    private final MeetingRepository meetingRepository;
    private final SurveyRepository surveyRepository;
    private final RecordingRepository recordingRepository;
    private final PromiseRepository promiseRepository;
    private final UserRepository userRepository;
    private final CareerEventRepository careerEventRepository;
    private final TeamRepository teamRepository;
    private final ActionPlanRepository actionPlanRepository;
    private final TeamCoachingCacheRepository teamCoachingCacheRepository;
    private final LlmAdapter gptAdapter;
    private final LlmAdapter claudeAdapter;
    private final ObjectMapper objectMapper;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            MeetingRepository meetingRepository,
            SurveyRepository surveyRepository,
            RecordingRepository recordingRepository,
            PromiseRepository promiseRepository,
            UserRepository userRepository,
            CareerEventRepository careerEventRepository,
            TeamRepository teamRepository,
            ActionPlanRepository actionPlanRepository,
            TeamCoachingCacheRepository teamCoachingCacheRepository,
            @Qualifier("gptMiniAdapter") LlmAdapter gptAdapter,
            @Qualifier("claudeAdapter") LlmAdapter claudeAdapter,
            ObjectMapper objectMapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.analysisRepository = analysisRepository;
        this.meetingRepository = meetingRepository;
        this.surveyRepository = surveyRepository;
        this.recordingRepository = recordingRepository;
        this.promiseRepository = promiseRepository;
        this.userRepository = userRepository;
        this.careerEventRepository = careerEventRepository;
        this.teamRepository = teamRepository;
        this.actionPlanRepository = actionPlanRepository;
        this.teamCoachingCacheRepository = teamCoachingCacheRepository;
        this.gptAdapter = gptAdapter;
        this.claudeAdapter = claudeAdapter;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    // ── 파이프라인 ────────────────────────────────────────────────────────────

    public void analyze(Long meetingId, String transcript) {
        Meeting meeting = loadMeeting(meetingId);
        Double surveyScore = loadSurveyScore(meetingId, meeting.getMemberId());
        Integer durationSec = loadDurationSec(meetingId);
        List<Promise> prevPromises = loadPrevPromises(meeting, meetingId);
        MeetingContext context = loadMeetingContext(meeting, meetingId);

        String step2Raw = gptAdapter.chat(STEP2_SYSTEM, transcript);
        Map<String, Object> step2 = parseJson(step2Raw);
        log.info("Step2(GPT-mini) 완료. meetingId={}", meetingId);

        String step3UserPrompt = buildStep3UserPrompt(step2, surveyScore, durationSec, prevPromises, context);
        String step3Raw = gptAdapter.chat(STEP3_SYSTEM, step3UserPrompt);
        Map<String, Object> step3 = parseJson(step3Raw);
        log.info("Step3(GPT-mini) 완료. meetingId={}", meetingId);

        transactionTemplate.executeWithoutResult(tx -> persistResults(meetingId, meeting, step2, step3));
    }

    @Transactional(readOnly = true)
    protected Meeting loadMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    protected Double loadSurveyScore(Long meetingId, Long memberId) {
        return surveyRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .map(Survey::getScores)
                .map(this::computeSurveyScore)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    protected Integer loadDurationSec(Long meetingId) {
        return recordingRepository.findByMeetingId(meetingId)
                .map(Recording::getDurationSec)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    protected List<Promise> loadPrevPromises(Meeting meeting, Long meetingId) {
        List<Meeting> prevMeetings = meetingRepository
                .findByLeaderIdAndMemberIdAndIdLessThan(meeting.getLeaderId(), meeting.getMemberId(), meetingId);
        return prevMeetings.isEmpty() ? List.of()
                : promiseRepository.findByMeetingIdIn(prevMeetings.stream().map(Meeting::getId).toList());
    }

    protected void persistResults(Long meetingId, Meeting meeting, Map<String, Object> step2, Map<String, Object> step3) {
        analysisRepository.findByMeetingId(meetingId).ifPresent(analysisRepository::delete);
        analysisRepository.save(buildAnalysis(meetingId, step2, step3));

        promiseRepository.deleteByMeetingId(meetingId);
        savePromises(meetingId, meeting, step2);

        actionPlanRepository.deleteByMeetingId(meetingId);
        saveActionPlans(meetingId, meeting.getLeaderId(), step3);

        careerEventRepository.deleteByMeetingId(meetingId);
        saveCareerEvents(meetingId, meeting, step3);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String raw) {
        try {
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("(?s)```(?:json)?\\s*", "").trim();
            }
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("LLM 응답 JSON 파싱 실패. raw={}", raw, e);
            throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
        }
    }

    private String buildStep3UserPrompt(Map<String, Object> step2, Double surveyScore,
                                        Integer durationSec, List<Promise> prevPromises,
                                        MeetingContext context) {
        StringBuilder sb = new StringBuilder("[분석 데이터]\n");
        if (durationSec != null) {
            sb.append("미팅 시간: ").append(durationSec / 60).append("분\n\n");
        }
        sb.append("[Step2 구조화 결과]\n");
        try {
            sb.append(objectMapper.writeValueAsString(step2));
        } catch (Exception e) {
            sb.append(step2);
        }
        sb.append("\n\n");
        if (surveyScore != null) {
            sb.append("[서베이 정보]\nsurveyScore: ").append(surveyScore).append("\n\n");
        }
        if (!prevPromises.isEmpty()) {
            sb.append("[이전 약속 목록]\n");
            prevPromises.forEach(p -> sb.append("- ").append(p.getContent()).append("\n"));
            sb.append("\n");
        }
        if (!context.isEmpty()) {
            sb.append("[이전 미팅 컨텍스트 — Rolling Baseline]\n");
            sb.append("최근 ").append(context.meetingCount()).append("회 미팅 평균:\n");
            sb.append("- Safety Score 평균: ").append(String.format(java.util.Locale.US, "%.1f", context.avgSafetyScore())).append("\n");
            sb.append("- Vulnerability 평균: ").append(String.format(java.util.Locale.US, "%.1f", context.avgVulnerability())).append("회\n");
            sb.append("- Constructive Dissent 평균: ").append(String.format(java.util.Locale.US, "%.1f", context.avgDissent())).append("회\n");
            sb.append("- Initiative 평균: ").append(String.format(java.util.Locale.US, "%.1f", context.avgInitiative())).append("회\n");
            if (!context.prevBlockers().isEmpty()) {
                sb.append("- 이전 블로커: ").append(String.join(", ", context.prevBlockers())).append("\n");
            }
            sb.append("\n이전 데이터와 비교하여 유의미한 변화가 있으면 피드백에 반영하세요.\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    protected MeetingContext loadMeetingContext(Meeting meeting, Long meetingId) {
        List<Long> recentIds = meetingRepository.findTop3ByLeaderIdAndMemberIdAndIdLessThanOrderByIdDesc(
                meeting.getLeaderId(), meeting.getMemberId(), meetingId)
                .stream().map(Meeting::getId).toList();
        if (recentIds.isEmpty()) return MeetingContext.empty();

        List<Analysis> prevAnalyses = analysisRepository.findByMeetingIdIn(recentIds);
        if (prevAnalyses.isEmpty()) return MeetingContext.empty();

        double avgSafety = prevAnalyses.stream()
                .map(Analysis::getSafetyScore).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0);

        double sumV = 0, sumD = 0, sumI = 0;
        for (Analysis a : prevAnalyses) {
            Map<String, Object> acts = a.getSpeechActs();
            if (acts == null) continue;
            sumV += listSize(acts.get("vulnerability"));
            sumD += listSize(acts.get("constructiveDissent"));
            sumI += listSize(acts.get("initiative"));
        }
        int n = prevAnalyses.size();

        List<String> prevBlockers = prevAnalyses.stream()
                .map(Analysis::getBlockerKeywords).filter(Objects::nonNull)
                .flatMap(List::stream).filter(Objects::nonNull).distinct().toList();

        return new MeetingContext(n, avgSafety, sumV / n, sumD / n, sumI / n, prevBlockers);
    }

    private int listSize(Object obj) {
        return (obj instanceof List<?> list) ? list.size() : 0;
    }

    record MeetingContext(
            int meetingCount, double avgSafetyScore,
            double avgVulnerability, double avgDissent, double avgInitiative,
            List<String> prevBlockers) {
        static MeetingContext empty() {
            return new MeetingContext(0, 0, 0, 0, 0, List.of());
        }
        boolean isEmpty() { return meetingCount == 0; }
    }

    @SuppressWarnings("unchecked")
    private Analysis buildAnalysis(Long meetingId, Map<String, Object> step2, Map<String, Object> step3) {
        return Analysis.builder()
                .meetingId(meetingId)
                .safetyScore(clamp(toDouble(step3.get("safetyScore")), 0.0, 100.0))
                .alignmentGap(toDouble(step3.get("alignmentGap")))
                .alignmentGapDetail((String) step3.get("alignmentGapDetail"))
                .honestyGap(toDouble(step3.get("honestyGap")))
                .executionGap(toDouble(step3.get("executionGap")))
                .speechActs((Map<String, Object>) step2.get("speechActs"))
                .blockerKeywords((List<String>) step2.get("blockerKeywords"))
                .feedbacks((List<Map<String, Object>>) step3.get("feedbacks"))
                .memberFeedback((Map<String, Object>) step3.get("memberFeedback"))
                .careerTags((List<String>) step3.get("careerTags"))
                .talkRatio((Map<String, Object>) step2.get("talkRatio"))
                .baselineData(Map.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private void savePromises(Long meetingId, Meeting meeting, Map<String, Object> step2) {
        Object raw = step2.get("promises");
        if (!(raw instanceof List<?> list)) return;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> p)) continue;
            String owner = String.valueOf(p.get("owner"));
            Long ownerId = "leader".equals(owner) ? meeting.getLeaderId() : meeting.getMemberId();
            String content = String.valueOf(p.get("content"));
            Object ctxObj = p.get("context");
            String context = ctxObj != null ? ctxObj.toString() : null;
            promiseRepository.save(Promise.builder()
                    .meetingId(meetingId)
                    .ownerId(ownerId)
                    .content(content)
                    .context(context)
                    .build());
        }
    }

    @SuppressWarnings("unchecked")
    private void saveActionPlans(Long meetingId, Long leaderId, Map<String, Object> step3) {
        Object raw = step3.get("nextActionPlans");
        if (!(raw instanceof List<?> list)) return;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> p)) continue;
            Object rawContent = p.get("content");
            if (rawContent == null) continue;
            String content = rawContent.toString();
            actionPlanRepository.save(ActionPlan.builder()
                    .meetingId(meetingId)
                    .leaderId(leaderId)
                    .content(content)
                    .build());
        }
    }

    @SuppressWarnings("unchecked")
    private void saveCareerEvents(Long meetingId, Meeting meeting, Map<String, Object> step3) {
        Object raw = step3.get("careerEvents");
        if (!(raw instanceof List<?> list)) return;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> e)) continue;
            Object titleObj = e.get("title");
            if (titleObj == null) continue;
            String title = titleObj.toString();
            if (title.isBlank()) continue;

            Object typeObj = e.get("eventType");
            CareerEventType eventType;
            try {
                eventType = typeObj != null
                        ? CareerEventType.valueOf(typeObj.toString())
                        : CareerEventType.ACHIEVEMENT;
            } catch (IllegalArgumentException ex) {
                eventType = CareerEventType.ACHIEVEMENT;
            }

            Object descObj = e.get("description");
            careerEventRepository.save(CareerEvent.builder()
                    .userId(meeting.getMemberId())
                    .meetingId(meetingId)
                    .eventType(eventType)
                    .title(title)
                    .description(descObj != null ? descObj.toString() : null)
                    .evidence(e.get("evidence") instanceof Map<?, ?> ev ? (Map<String, Object>) ev : null)
                    .occurredAt(meeting.getScheduledAt() != null
                            ? meeting.getScheduledAt() : meeting.getCreatedAt())
                    .build());
        }
    }

    // VDI 기반 surveyScore: safetyScore와 동일 차원(0~100)으로 산출.
    // 리커트(1~5)를 0~1로 정규화((level-1)/4)하여 바닥을 0, 중립(3)을 50 기준으로 두고,
    // 자기보고 과대평가(overreport)를 억제하기 위해 고평가 할인 곡선(γ=1.3)을 적용한다.
    // 가중치 V 40% / D 35% / I 25%. (모두 3점 → ~41, 모두 4점 → ~69, 모두 5점 → 100)
    private static final double SURVEY_GAMMA = 1.3;

    private Double computeSurveyScore(Map<String, Object> scores) {
        if (scores == null) return null;
        Object vObj = scores.get("vulnerabilityLevel");
        Object dObj = scores.get("dissentLevel");
        Object iObj = scores.get("initiativeLevel");
        if (vObj == null || dObj == null || iObj == null) return null;
        double v = surveyDimension(((Number) vObj).doubleValue()) * 40.0;
        double d = surveyDimension(((Number) dObj).doubleValue()) * 35.0;
        double i = surveyDimension(((Number) iObj).doubleValue()) * 25.0;
        return Math.max(0, Math.min(100, v + d + i));
    }

    // 리커트 1~5 → 0~1 정규화 후 γ 곡선 적용 (고평가일수록 더 큰 할인)
    private double surveyDimension(double level) {
        double normalized = Math.max(0.0, Math.min(1.0, (level - 1.0) / 4.0));
        return Math.pow(normalized, SURVEY_GAMMA);
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        return null;
    }

    private static Double clamp(Double val, double min, double max) {
        if (val == null) return null;
        return Math.max(min, Math.min(max, val));
    }

    private static String computeFlightRiskLabel(Double safetyScore, HonestyDirection direction, RiskLevel riskLevel) {
        if (safetyScore == null) return null;
        if (direction == HonestyDirection.OVERREPORT) {
            if (riskLevel == RiskLevel.DANGER)  return "이탈 위험 높음";
            if (riskLevel == RiskLevel.WARNING) return "이탈 위험 주의";
        }
        if (safetyScore < 30) return "관찰 필요";
        if (safetyScore < 60) return "안정";
        return "적극적 참여";
    }

    private static HonestyDirection computeDirection(Double honestyGap) {
        if (honestyGap == null || honestyGap == 0.0) return HonestyDirection.NEUTRAL;
        return honestyGap > 0 ? HonestyDirection.OVERREPORT : HonestyDirection.UNDERREPORT;
    }

    private static RiskLevel computeRiskLevel(Double honestyGap) {
        if (honestyGap == null) return RiskLevel.SAFE;
        double abs = Math.abs(honestyGap);
        if (abs < 10) return RiskLevel.SAFE;
        if (abs < 20) return RiskLevel.CAUTION;
        if (abs < 30) return RiskLevel.WARNING;
        return RiskLevel.DANGER;
    }

    // ── 조회 ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AnalysisResultResponse getResult(Long meetingId) {
        Analysis a = analysisRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND));
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        User member = userRepository.findById(meeting.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 미팅 기본 정보
        Integer durationSec = recordingRepository.findByMeetingId(meetingId)
                .map(Recording::getDurationSec).orElse(null);
        int round = (int) meetingRepository.countByLeaderIdAndMemberIdAndIdLessThanEqual(
                meeting.getLeaderId(), meeting.getMemberId(), meetingId);
        String meetingDate = meeting.getScheduledAt() != null
                ? meeting.getScheduledAt().toLocalDate().toString() : null;

        // Survey score (honestyGap 상세용)
        Double surveyScore = surveyRepository.findByMeetingIdAndMemberId(meetingId, meeting.getMemberId())
                .map(s -> computeSurveyScore(s.getScores())).orElse(null);

        // gaps
        GapsResponse gaps = buildGaps(a, surveyScore, meeting);

        // speechActs
        SpeechActsResponse speechActs = buildSpeechActs(a);

        // talkRatio
        TalkRatioResponse talkRatio = buildTalkRatio(a.getTalkRatio());

        // feedbacks
        List<FeedbackItem> feedbacks = buildFeedbacks(a.getFeedbacks());

        // nextActionPlans
        List<ActionPlanItem> nextActionPlans = actionPlanRepository.findByMeetingIdOrderByIdAsc(meetingId)
                .stream().map(p -> new ActionPlanItem(p.getId(), p.getContent(), p.isCompleted()))
                .toList();

        // promises
        PromisesResponse promises = buildPromises(meeting, meetingId);

        Double gap = gaps.honestyGap() != null ? gaps.honestyGap().gap() : null;
        HonestyDirection dir = computeDirection(gap);
        RiskLevel risk = computeRiskLevel(gap);
        String flightRiskLabel = computeFlightRiskLabel(a.getSafetyScore(), dir, risk);

        // 미팅 코칭 가이드 (GPT)
        MeetingCoaching meetingCoaching = buildMeetingCoaching(a, gaps, talkRatio, promises, meeting, meetingId);

        return new AnalysisResultResponse(meetingId, round, member.getName(), member.getJobTitle(),
                meetingDate, durationSec, gaps, a.getSafetyScore(), flightRiskLabel, speechActs,
                talkRatio, feedbacks, nextActionPlans, promises, meetingCoaching);
    }

    private GapsResponse buildGaps(Analysis a, Double surveyScore, Meeting meeting) {
        // alignmentGap
        AlignmentGapDetail alignmentGap = new AlignmentGapDetail(a.getAlignmentGap(), a.getAlignmentGapDetail());

        // honestyGap
        Double gap = (surveyScore != null && a.getSafetyScore() != null)
                ? Math.round((surveyScore - a.getSafetyScore()) * 10.0) / 10.0 : a.getHonestyGap();
        HonestyDirection dir = computeDirection(gap);
        RiskLevel risk = computeRiskLevel(gap);
        HonestyGapDetail honestyGap = new HonestyGapDetail(surveyScore, a.getSafetyScore(), gap,
                dir != null ? dir.name() : null, risk != null ? risk.name() : null);

        // executionGap — 이전 미팅 promise 집계
        ExecutionGapDetail executionGap = buildExecutionGapDetail(a.getExecutionGap(), meeting);

        return new GapsResponse(alignmentGap, honestyGap, executionGap);
    }

    private ExecutionGapDetail buildExecutionGapDetail(Double score, Meeting meeting) {
        java.util.Optional<Meeting> prev = meetingRepository
                .findTopByLeaderIdAndMemberIdAndIdLessThanOrderByCreatedAtDesc(
                        meeting.getLeaderId(), meeting.getMemberId(), meeting.getId());
        if (prev.isEmpty()) return new ExecutionGapDetail(score, 0, 0, 0);
        List<Promise> prevPromises = promiseRepository.findByMeetingId(prev.get().getId());
        int total = prevPromises.size();
        int fulfilled = (int) prevPromises.stream().filter(p -> p.getStatus() == PromiseStatus.DONE).count();
        int missed = (int) prevPromises.stream().filter(p -> p.getStatus() == PromiseStatus.MISSED).count();
        return new ExecutionGapDetail(score, total, fulfilled, missed);
    }

    @SuppressWarnings("unchecked")
    private SpeechActsResponse buildSpeechActs(Analysis a) {
        Map<String, Object> acts = a.getSpeechActs();
        Map<String, Object> baseline = a.getBaselineData();
        return new SpeechActsResponse(
                buildSpeechActDetail(acts, "vulnerability", baseline, "prev_avg_vulnerability"),
                buildSpeechActDetail(acts, "constructiveDissent", baseline, "prev_avg_dissent"),
                buildSpeechActDetail(acts, "initiative", baseline, "prev_avg_initiative"));
    }

    @SuppressWarnings("unchecked")
    private SpeechActDetail buildSpeechActDetail(Map<String, Object> acts, String key,
                                                  Map<String, Object> baseline, String baselineKey) {
        if (acts == null) return new SpeechActDetail(0, null, null, List.of());
        Object raw = acts.get(key);
        List<Map<String, Object>> rawList = (raw instanceof List<?> l)
                ? l.stream().filter(i -> i instanceof Map).map(i -> (Map<String, Object>) i).toList()
                : List.of();
        int count = rawList.size();
        Double baselineAvg = (baseline != null) ? toDouble(baseline.get(baselineKey)) : null;
        Integer changeRate = (baselineAvg != null && baselineAvg > 0)
                ? (int) Math.round((count - baselineAvg) / baselineAvg * 100) : null;
        List<SpeechActInstance> instances = rawList.stream()
                .map(i -> new SpeechActInstance(
                        (String) i.get("text"),
                        formatTimestamp(i.get("timestamp"))))
                .toList();
        return new SpeechActDetail(count, baselineAvg, changeRate, instances);
    }

    private TalkRatioResponse buildTalkRatio(Map<String, Object> raw) {
        if (raw == null) return new TalkRatioResponse(0, 0, 40);
        int leader = raw.get("leaderRatio") instanceof Number n ? n.intValue() : 0;
        int memberR = raw.get("memberRatio") instanceof Number n ? n.intValue() : 0;
        return new TalkRatioResponse(leader, memberR, 40);
    }

    @SuppressWarnings("unchecked")
    private List<FeedbackItem> buildFeedbacks(List<Map<String, Object>> raw) {
        if (raw == null) return List.of();
        int[] idx = {1};
        return raw.stream().map(fb -> new FeedbackItem(
                idx[0]++,
                (String) fb.get("severity"),
                (String) fb.get("title"),
                (String) fb.get("evidenceQuote"),
                (String) fb.get("dataSummary"),
                (String) fb.get("actionGuide"))).toList();
    }

    private PromisesResponse buildPromises(Meeting meeting, Long meetingId) {
        // previous: 직전 미팅의 약속 — DB에서 1건만 조회
        java.util.Optional<Meeting> prevMeeting = meetingRepository
                .findTopByLeaderIdAndMemberIdAndIdLessThanOrderByCreatedAtDesc(
                        meeting.getLeaderId(), meeting.getMemberId(), meetingId);
        List<PreviousPromise> previous = prevMeeting.isEmpty() ? List.of()
                : promiseRepository.findByMeetingId(prevMeeting.get().getId()).stream()
                        .map(p -> new PreviousPromise(p.getId(), p.getContent(), p.getContext(), p.getStatus().name()))
                        .toList();

        // new: 현재 미팅의 약속
        List<NewPromise> newPromises = promiseRepository.findByMeetingId(meetingId).stream()
                .map(p -> new NewPromise(p.getId(), p.getContent(), p.getContext(), p.getCategory(),
                        p.getDeadline() != null ? p.getDeadline().toString() : null,
                        p.getStatus().name()))
                .toList();

        return new PromisesResponse(previous, newPromises);
    }

    // ── Pre-Meeting Briefing ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public PreBriefingResponse getPreBriefing(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        User member = userRepository.findById(meeting.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        int round = (int) meetingRepository.countByLeaderIdAndMemberIdAndIdLessThanEqual(
                meeting.getLeaderId(), meeting.getMemberId(), meetingId);

        // 이번 미팅 사전 서베이
        PreBriefingResponse.SurveyBrief survey = surveyRepository
                .findByMeetingIdAndMemberId(meetingId, meeting.getMemberId())
                .map(s -> {
                    Map<String, Object> sc = s.getScores();
                    Integer energy = sc.get("energyLevel") instanceof Number n ? n.intValue() : null;
                    List<String> issues = sc.get("issues") instanceof List<?> l
                            ? l.stream().map(Object::toString).toList() : List.of();
                    List<String> roles = sc.get("desiredRoles") instanceof List<?> l
                            ? l.stream().map(Object::toString).toList() : List.of();
                    return new PreBriefingResponse.SurveyBrief(true, energy, issues, roles, computeSurveyScore(sc));
                })
                .orElse(new PreBriefingResponse.SurveyBrief(false, null, List.of(), List.of(), null));

        // 이전 미팅 목록 — 현재 meetingId보다 ID가 작은 것만 (미래 미팅 제외)
        List<Meeting> prevMeetings = meetingRepository
                .findByLeaderIdAndMemberIdOrderByCreatedAtDesc(meeting.getLeaderId(), meeting.getMemberId())
                .stream().filter(m -> m.getId() < meetingId).toList();

        // 이전 미팅 분석 데이터
        PreBriefingResponse.LastMeetingSummary lastMeeting = null;
        if (!prevMeetings.isEmpty()) {
            List<Long> prevIds = prevMeetings.stream().map(Meeting::getId).limit(5).toList();
            lastMeeting = analysisRepository.findTopByMeetingIdInOrderByMeetingIdDesc(prevIds)
                    .map(a -> {
                        Double safetyScore = a.getSafetyScore();

                        // Safety Score 변화량 (baselineData 기반)
                        Double change = null;
                        if (a.getBaselineData() != null && safetyScore != null) {
                            Object prev = a.getBaselineData().get("prev_avg_safety");
                            if (prev instanceof Number n) {
                                change = Math.round((safetyScore - n.doubleValue()) * 10.0) / 10.0;
                            }
                        }

                        // Quadrant — analysis와 동일한 미팅의 서베이 사용
                        Double prevSurveyScore = surveyRepository
                                .findByMeetingIdAndMemberId(a.getMeetingId(), meeting.getMemberId())
                                .map(s -> computeSurveyScore(s.getScores())).orElse(null);
                        String quadrant = computeQuadrant(safetyScore, prevSurveyScore);

                        // Honesty Gap — gap이 null이면 HonestyGapBrief도 null
                        Double gap = a.getHonestyGap();
                        HonestyDirection dir = gap != null ? computeDirection(gap) : null;
                        RiskLevel risk = gap != null ? computeRiskLevel(gap) : null;
                        PreBriefingResponse.HonestyGapBrief honestyGap = (dir != null && risk != null)
                                ? new PreBriefingResponse.HonestyGapBrief(dir.name(), risk.name()) : null;

                        // Speech Act 이상 신호 (Fact-Based)
                        List<String> alerts = buildSpeechActAlerts(a.getSpeechActs(), a.getBaselineData());

                        // 이전 blocker 키워드
                        List<String> blockers = a.getBlockerKeywords() != null ? a.getBlockerKeywords() : List.of();

                        return new PreBriefingResponse.LastMeetingSummary(
                                safetyScore, change, quadrant, honestyGap, alerts, blockers);
                    }).orElse(null);
        }

        // PENDING 약속 전체 (이전 미팅에서 리더가 한 약속)
        List<PreBriefingResponse.PendingPromise> pendingPromises = List.of();
        if (!prevMeetings.isEmpty()) {
            List<Long> prevIds = prevMeetings.stream().map(Meeting::getId).toList();
            pendingPromises = promiseRepository.findByMeetingIdIn(prevIds).stream()
                    .filter(p -> p.getOwnerId().equals(meeting.getLeaderId())
                            && p.getStatus() == PromiseStatus.PENDING)
                    .map(p -> {
                        boolean overdue = p.getDeadline() != null && p.getDeadline().isBefore(LocalDate.now());
                        return new PreBriefingResponse.PendingPromise(
                                p.getId(), p.getContent(),
                                p.getDeadline() != null ? p.getDeadline().toString() : null,
                                overdue);
                    }).toList();
        }

        // 추천 주제 (rule-based)
        List<String> recommendedTopics = buildRecommendedTopics(pendingPromises, lastMeeting, survey);

        // 코칭 가이드 (GPT 생성)
        PreBriefingResponse.CoachingGuide coachingGuide = buildCoachingGuide(
                member.getName(), lastMeeting, pendingPromises, survey, prevMeetings, meeting.getMemberId());

        String scheduledAt = meeting.getScheduledAt() != null ? meeting.getScheduledAt().toString() : null;
        return new PreBriefingResponse(meetingId, round, member.getName(), member.getJobTitle(),
                scheduledAt, survey, lastMeeting, pendingPromises, recommendedTopics, coachingGuide);
    }

    private String computeQuadrant(Double safetyScore, Double surveyScore) {
        if (safetyScore == null || surveyScore == null) return null;
        boolean safetyHigh = safetyScore >= 50;
        boolean surveyHigh = surveyScore >= 50;
        if (surveyHigh && safetyHigh) return "STABLE";
        if (surveyHigh) return "SILENT_RISK";
        if (safetyHigh) return "CONSERVATIVE";
        return "EXPLICIT_RISK";
    }

    @SuppressWarnings("unchecked")
    private List<String> buildSpeechActAlerts(Map<String, Object> speechActs, Map<String, Object> baselineData) {
        if (speechActs == null) return List.of();
        List<String> alerts = new ArrayList<>();
        String[] keys = {"vulnerability", "constructiveDissent", "initiative"};
        String[] baselineKeys = {"prev_avg_vulnerability", "prev_avg_dissent", "prev_avg_initiative"};
        String[] labels = {"Vulnerability", "Constructive Dissent", "Initiative"};
        for (int i = 0; i < keys.length; i++) {
            Object raw = speechActs.get(keys[i]);
            int count = raw instanceof List<?> l ? l.size() : 0;
            if (baselineData != null && baselineData.get(baselineKeys[i]) instanceof Number n) {
                double avg = n.doubleValue();
                if (count == 0 && avg >= 1.0) {
                    alerts.add(labels[i] + " 0회 (이전 평균 " + String.format("%.1f", avg) + "회)");
                }
            } else if (count == 0) {
                alerts.add(labels[i] + " 0회");
            }
        }
        return alerts;
    }

    private List<String> buildRecommendedTopics(
            List<PreBriefingResponse.PendingPromise> pendingPromises,
            PreBriefingResponse.LastMeetingSummary lastMeeting,
            PreBriefingResponse.SurveyBrief survey) {
        List<String> topics = new ArrayList<>();
        // 1. 미이행 약속 팔로업 항상 최우선
        pendingPromises.stream().limit(2)
                .forEach(p -> topics.add("약속 팔로업: " + p.content()));
        // 2. SILENT_RISK 상태면 발화 기회 확인
        if (lastMeeting != null && "SILENT_RISK".equals(lastMeeting.quadrant())) {
            topics.add("멤버 발화가 줄었습니다 — 편하게 이야기할 수 있는지 확인해보세요");
        }
        // 3. 서베이 이슈 기반
        if (survey.submitted() && survey.issues() != null) {
            survey.issues().stream().limit(2)
                    .forEach(issue -> topics.add("서베이 이슈 확인: " + issue));
        }
        return topics;
    }

    @SuppressWarnings("unchecked")
    private PreBriefingResponse.CoachingGuide buildCoachingGuide(
            String memberName,
            PreBriefingResponse.LastMeetingSummary lastMeeting,
            List<PreBriefingResponse.PendingPromise> pendingPromises,
            PreBriefingResponse.SurveyBrief survey,
            List<Meeting> prevMeetings,
            Long memberId) {
        try {
            // 최근 3회 Speech Act 추이 데이터 수집
            List<Long> recentIds = prevMeetings.stream()
                    .sorted((a, b) -> b.getId().compareTo(a.getId()))
                    .limit(3).map(Meeting::getId).toList();
            // ⑤ recentIds(최신순)와 일치하도록 meetingId 내림차순 정렬
            Map<Long, Integer> idOrder = new java.util.HashMap<>();
            for (int i = 0; i < recentIds.size(); i++) idOrder.put(recentIds.get(i), i);
            List<Analysis> recentAnalyses = recentIds.isEmpty()
                    ? List.of()
                    : analysisRepository.findByMeetingIdIn(recentIds).stream()
                            .sorted(java.util.Comparator.comparingInt(a -> idOrder.getOrDefault(a.getMeetingId(), 99)))
                            .toList();

            StringBuilder prompt = new StringBuilder();
            prompt.append("[미팅 전 코칭 가이드 생성]\n");
            prompt.append("멤버: ").append(memberName).append("\n\n");

            if (!recentAnalyses.isEmpty()) {
                prompt.append("[최근 미팅 Speech Act 추이]\n");
                for (int i = 0; i < recentAnalyses.size(); i++) {
                    Map<String, Object> acts = recentAnalyses.get(i).getSpeechActs();
                    if (acts == null) continue;
                    int v = listSize(acts.get("vulnerability"));
                    int d = listSize(acts.get("constructiveDissent"));
                    int ini = listSize(acts.get("initiative"));
                    prompt.append(String.format("- %d회 전: 솔직한표현 %d회, 건설적의견 %d회, 자발적제안 %d회\n", i + 1, v, d, ini));
                }
                prompt.append("\n");
            }

            if (lastMeeting != null) {
                prompt.append("[직전 미팅 상태]\n");
                if (lastMeeting.safetyScore() != null)
                    prompt.append("- Safety Score: ").append(lastMeeting.safetyScore()).append("\n");
                if (lastMeeting.honestyGap() != null)
                    prompt.append("- Honesty Gap: ").append(lastMeeting.honestyGap().direction())
                            .append(" / ").append(lastMeeting.honestyGap().riskLevel()).append("\n");
                if (lastMeeting.quadrant() != null)
                    prompt.append("- 사분면: ").append(lastMeeting.quadrant()).append("\n");
                prompt.append("\n");
            }

            if (!pendingPromises.isEmpty()) {
                prompt.append("[미이행 약속]\n");
                pendingPromises.forEach(p -> prompt.append("- ").append(p.content())
                        .append(p.overdue() ? " (기한초과)" : "").append("\n"));
                prompt.append("\n");
            }

            if (survey.submitted()) {
                prompt.append("[사전 서베이]\n");
                prompt.append("- 이슈: ").append(survey.issues()).append("\n");
                prompt.append("\n");
            }

            prompt.append("""
                    위 데이터를 바탕으로 리더를 위한 미팅 전 코칭 가이드를 생성하세요.
                    Fact-Based 원칙: AI 해석 라벨("번아웃 의심", "소극적" 등) 절대 금지.
                    수치와 관찰 사실만 사용하세요.

                    반드시 아래 JSON 형식으로만 응답하세요:
                    {
                      "focusArea": "코칭 핵심 키워드 (예: 경청 강화, 약속 점검, 솔직함 유도)",
                      "guideSummary": "핵심 관찰 사실 + 이번 미팅 제안 (1~2문장)",
                      "evidence": ["수치 근거 1", "수치 근거 2"],
                      "suggestedQuestions": ["제안 질문 1", "제안 질문 2"]
                    }
                    """);

            String raw = gptAdapter.chat("당신은 1on1 미팅 코칭 전문가입니다.", prompt.toString());
            Map<String, Object> parsed = parseJson(raw);
            if (parsed == null) return null;

            Object evidenceRaw = parsed.get("evidence");
            Object questionsRaw = parsed.get("suggestedQuestions");
            List<String> evidence = evidenceRaw instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();
            List<String> questions = questionsRaw instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();

            return new PreBriefingResponse.CoachingGuide(
                    parsed.get("focusArea") != null ? parsed.get("focusArea").toString() : null,
                    parsed.get("guideSummary") != null ? parsed.get("guideSummary").toString() : null,
                    evidence, questions);
        } catch (Exception e) {
            log.warn("코칭 가이드 생성 실패. memberId={}", memberId, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private MeetingCoaching buildMeetingCoaching(
            Analysis a, GapsResponse gaps, TalkRatioResponse talkRatio,
            PromisesResponse promises, Meeting meeting, Long meetingId) {
        try {
            StringBuilder prompt = new StringBuilder("[미팅 분석 코칭 가이드 생성]\n\n");

            // Gap 데이터
            if (gaps.alignmentGap() != null)
                prompt.append("Alignment Gap 점수: ").append(gaps.alignmentGap().score())
                        .append(" — ").append(gaps.alignmentGap().detail()).append("\n");
            if (gaps.honestyGap() != null)
                prompt.append("Honesty Gap: ").append(gaps.honestyGap().direction())
                        .append(", 크기: ").append(gaps.honestyGap().gap())
                        .append(" (").append(gaps.honestyGap().riskLevel()).append(")\n");
            if (gaps.executionGap() != null)
                prompt.append("Execution Gap 점수: ").append(gaps.executionGap().score())
                        .append(", 총 약속 ").append(gaps.executionGap().totalPromises())
                        .append("건 중 이행 ").append(gaps.executionGap().fulfilled()).append("건\n");

            // 발화 비율
            if (talkRatio != null)
                prompt.append("발화 비율 — 리더: ").append(talkRatio.leaderRatio())
                        .append("%, 멤버: ").append(talkRatio.memberRatio()).append("%\n");

            // Speech Act
            Map<String, Object> speechActs = a.getSpeechActs();
            if (speechActs != null) {
                prompt.append("Speech Act — 솔직한표현: ").append(listSize(speechActs.get("vulnerability")))
                        .append("회, 건설적의견: ").append(listSize(speechActs.get("constructiveDissent")))
                        .append("회, 자발적제안: ").append(listSize(speechActs.get("initiative"))).append("회\n");
            }

            // 약속 이행
            if (!promises.previous().isEmpty()) {
                long fulfilled = promises.previous().stream().filter(p -> "DONE".equals(p.status())).count();
                prompt.append("이전 약속 이행: ").append(fulfilled).append("/").append(promises.previous().size()).append("건\n");
            }

            prompt.append("""

                    위 데이터를 바탕으로 리더를 위한 미팅 후 코칭 가이드를 생성하세요.
                    Fact-Based 원칙: AI 해석 라벨("번아웃 의심", "소극적" 등) 절대 금지. 수치와 관찰 사실만 사용.

                    반드시 아래 JSON 형식으로만 응답하세요:
                    {
                      "gapSummary": {
                        "alignment": "의제 일치도 관련 한 문장 (수치 포함)",
                        "honesty": "솔직함 간극 관련 한 문장 (수치 포함)",
                        "execution": "약속 이행 관련 한 문장 (수치 포함)"
                      },
                      "behaviorAnalysis": {
                        "talkRatio": "발화 비율 관련 한 문장 + 제안",
                        "speechActTrend": "Speech Act 관찰 사실 한 문장"
                      },
                      "nextSteps": ["다음 미팅 제안 1", "다음 미팅 제안 2"]
                    }
                    """);

            String raw = gptAdapter.chat("당신은 1on1 미팅 분석 전문가입니다.", prompt.toString());
            Map<String, Object> parsed = parseJson(raw);
            if (parsed == null) return null;

            Map<String, Object> gapRaw = parsed.get("gapSummary") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
            Map<String, Object> behaviorRaw = parsed.get("behaviorAnalysis") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
            Object stepsRaw = parsed.get("nextSteps");
            List<String> nextSteps = stepsRaw instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();

            GapSummary gapSummary = gapRaw == null ? null : new GapSummary(
                    gapRaw.get("alignment") != null ? gapRaw.get("alignment").toString() : null,
                    gapRaw.get("honesty") != null ? gapRaw.get("honesty").toString() : null,
                    gapRaw.get("execution") != null ? gapRaw.get("execution").toString() : null);

            BehaviorAnalysis behaviorAnalysis = behaviorRaw == null ? null : new BehaviorAnalysis(
                    behaviorRaw.get("talkRatio") != null ? behaviorRaw.get("talkRatio").toString() : null,
                    behaviorRaw.get("speechActTrend") != null ? behaviorRaw.get("speechActTrend").toString() : null);

            return new MeetingCoaching(gapSummary, behaviorAnalysis, nextSteps);
        } catch (Exception e) {
            log.warn("미팅 코칭 가이드 생성 실패. meetingId={}", meetingId, e);
            return null;
        }
    }

    private String formatTimestamp(Object seconds) {
        if (seconds == null) return null;
        int s = ((Number) seconds).intValue();
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    @Transactional(readOnly = true)
    public TeamDashboardResponse getTeamDashboard(Long teamId) {
        List<Meeting> meetings = meetingRepository.findByTeamIdOrderByCreatedAtDesc(teamId)
                .stream().limit(20).collect(Collectors.toList());
        if (meetings.isEmpty()) {
            return new TeamDashboardResponse(teamId, 0.0, "NO_DATA", List.of());
        }

        List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();

        Map<Long, Analysis> analysisByMeeting = analysisRepository.findByMeetingIdIn(meetingIds)
                .stream().collect(Collectors.toMap(Analysis::getMeetingId, a -> a));
        Map<Long, Survey> surveyByMeeting = surveyRepository.findByMeetingIdIn(meetingIds)
                .stream().collect(Collectors.toMap(Survey::getMeetingId, s -> s));

        // 미팅별 teamHealthScore = safetyScore×0.6 + surveyScore×0.4 (서베이 없으면 safetyScore 단독)
        record ScoredMeeting(YearMonth month, double health, Long memberId, Long meetingId) {}

        List<ScoredMeeting> scored = meetings.stream()
                .map(m -> {
                    Analysis a = analysisByMeeting.get(m.getId());
                    if (a == null || a.getSafetyScore() == null) return null;
                    double safety = a.getSafetyScore();
                    Survey s = surveyByMeeting.get(m.getId());
                    double health = (s != null)
                            ? safety * 0.6 + computeSurveyScore(s.getScores()) * 0.4
                            : safety;
                    return new ScoredMeeting(YearMonth.from(m.getCreatedAt()), health,
                            m.getMemberId(), m.getId());
                })
                .filter(Objects::nonNull)
                .toList();

        if (scored.isEmpty()) {
            return new TeamDashboardResponse(teamId, 0.0, "NO_DATA", List.of());
        }

        double avg = scored.stream().mapToDouble(ScoredMeeting::health).average().orElse(0.0);
        double rounded = Math.round(avg * 10.0) / 10.0;

        // ④ Trend: 전월 대비
        YearMonth thisMonth = YearMonth.now();
        YearMonth prevMonth = thisMonth.minusMonths(1);
        OptionalDouble thisAvg = scored.stream()
                .filter(e -> e.month().equals(thisMonth))
                .mapToDouble(ScoredMeeting::health).average();
        OptionalDouble prevAvg = scored.stream()
                .filter(e -> e.month().equals(prevMonth))
                .mapToDouble(ScoredMeeting::health).average();

        String trend = "STABLE";
        if (thisAvg.isPresent() && prevAvg.isPresent()) {
            double diff = thisAvg.getAsDouble() - prevAvg.getAsDouble();
            if (diff > 5) trend = "IMPROVING";
            else if (diff < -5) trend = "DECLINING";
        }

        // ⑤ Silent Risk: 멤버별 최근 3회 baseline 대비 현재 30%+ 하락
        Map<Long, String> memberNames = userRepository.findByTeamId(teamId)
                .stream().collect(Collectors.toMap(User::getId, User::getName));

        Map<Long, List<Long>> meetingIdsByMember = new LinkedHashMap<>();
        meetings.forEach(m -> meetingIdsByMember
                        .computeIfAbsent(m.getMemberId(), k -> new ArrayList<>())
                        .add(m.getId()));

        List<String> alerts = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : meetingIdsByMember.entrySet()) {
            List<Long> ids = entry.getValue();
            if (ids.size() < 4) continue;
            Analysis cur = analysisByMeeting.get(ids.get(0));
            if (cur == null || cur.getSafetyScore() == null) continue;
            double current = cur.getSafetyScore();
            double baseline = 0;
            int cnt = 0;
            for (int i = 1; i <= 3; i++) {
                Analysis prev = analysisByMeeting.get(ids.get(i));
                if (prev != null && prev.getSafetyScore() != null) {
                    baseline += prev.getSafetyScore();
                    cnt++;
                }
            }
            if (cnt == 0) continue;
            baseline /= cnt;
            if (baseline > 0 && current < baseline * 0.7) {
                String name = memberNames.getOrDefault(entry.getKey(), "멤버#" + entry.getKey());
                alerts.add(name + " - 심리적 안전감 급락 (현재 " + Math.round(current)
                        + " / 기준선 " + Math.round(baseline) + ")");
            }
        }

        return new TeamDashboardResponse(teamId, rounded, trend, alerts);
    }

    @Transactional(readOnly = true)
    public List<RadarDataPoint> getRadarData(Long teamId) {
        List<User> members = userRepository.findByTeamId(teamId);
        if (members.isEmpty()) return List.of();

        List<Long> memberIds = members.stream().map(User::getId).toList();

        // 멤버별 최신 meetingId 추출 — 1 쿼리
        Map<Long, Long> latestMeetingIdByMember = new LinkedHashMap<>();
        meetingRepository.findByMemberIdInOrderByCreatedAtDesc(memberIds)
                .forEach(m -> latestMeetingIdByMember.putIfAbsent(m.getMemberId(), m.getId()));

        List<Long> latestMeetingIds = new ArrayList<>(latestMeetingIdByMember.values());
        if (latestMeetingIds.isEmpty()) return List.of();

        // bulk 조회 — 각 1 쿼리
        Map<Long, Analysis> analysisByMeetingId = analysisRepository.findByMeetingIdIn(latestMeetingIds)
                .stream().collect(Collectors.toMap(Analysis::getMeetingId, a -> a));
        Map<Long, Survey> surveyByMeetingId = surveyRepository.findByMeetingIdIn(latestMeetingIds)
                .stream().collect(Collectors.toMap(Survey::getMeetingId, s -> s));

        Map<Long, User> userById = members.stream().collect(Collectors.toMap(User::getId, u -> u));
        List<RadarDataPoint> result = new ArrayList<>();

        for (Map.Entry<Long, Long> entry : latestMeetingIdByMember.entrySet()) {
            Long memberId = entry.getKey();
            Long meetingId = entry.getValue();
            Analysis analysis = analysisByMeetingId.get(meetingId);
            if (analysis == null) continue;

            Double safetyScore = analysis.getSafetyScore();
            Survey survey = surveyByMeetingId.get(meetingId);
            Double surveyScore = survey != null ? computeSurveyScore(survey.getScores()) : null;
            Double honestyGap = (surveyScore != null && safetyScore != null)
                    ? Math.round((surveyScore - safetyScore) * 10.0) / 10.0
                    : null;

            User member = userById.get(memberId);
            HonestyDirection dir = computeDirection(honestyGap);
            RiskLevel risk = computeRiskLevel(honestyGap);
            result.add(new RadarDataPoint(memberId, member.getName(), surveyScore, safetyScore, honestyGap,
                    dir, risk, computeFlightRiskLabel(safetyScore, dir, risk)));
        }
        return result;
    }

    // @Transactional 미적용: 내부에서 외부 LLM API를 호출하므로 DB 커넥션을 길게 점유하지 않도록
    // 조회/캐시 확인은 트랜잭션 없이 수행하고, 캐시 저장만 TransactionTemplate으로 짧게 처리한다.
    public TeamCoachingResponse getTeamCoaching(Long teamId, Long leaderId) {
        // ① IDOR 방어: 요청자가 해당 팀의 리더인지 검증
        teamRepository.findById(teamId).ifPresent(team -> {
            if (!leaderId.equals(team.getLeaderId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        });

        List<User> members = userRepository.findByTeamId(teamId).stream()
                .filter(u -> u.getRole() != UserRole.LEADER).toList();
        if (members.isEmpty()) return new TeamCoachingResponse("팀 멤버 데이터가 없습니다.", List.of(), List.of());

        List<Long> memberIds = members.stream().map(User::getId).toList();
        // ⑥ Collectors.toMap NPE 방지: HashMap 직접 수집
        Map<Long, User> userById = new java.util.HashMap<>();
        members.forEach(u -> userById.put(u.getId(), u));

        // 멤버별 최신 분석 데이터
        Map<Long, Long> latestMeetingIdByMember = new LinkedHashMap<>();
        meetingRepository.findByMemberIdInOrderByCreatedAtDesc(memberIds)
                .forEach(m -> latestMeetingIdByMember.putIfAbsent(m.getMemberId(), m.getId()));

        List<Long> latestIds = new ArrayList<>(latestMeetingIdByMember.values());
        if (latestIds.isEmpty()) return new TeamCoachingResponse("완료된 미팅 데이터가 없습니다.", List.of(), List.of());

        Map<Long, Analysis> analysisByMeetingId = analysisRepository.findByMeetingIdIn(latestIds)
                .stream().collect(Collectors.toMap(Analysis::getMeetingId, a -> a));

        // GPT 입력 프롬프트 구성
        StringBuilder prompt = new StringBuilder("[팀 전체 코칭 가이드 생성]\n\n");
        prompt.append("[멤버별 최신 미팅 분석]\n");

        List<String> memberSummaries = new ArrayList<>();
        List<Long> usedMeetingIds = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : latestMeetingIdByMember.entrySet()) {

            Long memberId = entry.getKey();
            Long meetingId = entry.getValue();
            Analysis a = analysisByMeetingId.get(meetingId);
            if (a == null) continue;
            usedMeetingIds.add(meetingId);

            User member = userById.get(memberId);
            Map<String, Object> acts = a.getSpeechActs();
            int v = acts != null ? listSize(acts.get("vulnerability")) : 0;
            int d = acts != null ? listSize(acts.get("constructiveDissent")) : 0;
            int i = acts != null ? listSize(acts.get("initiative")) : 0;

            memberSummaries.add(String.format(
                    "- %s(id:%d): SafetyScore=%.0f, V=%d, D=%d, I=%d, HonestyGap=%s",
                    member.getName(), memberId,
                    a.getSafetyScore() != null ? a.getSafetyScore() : 0,
                    v, d, i,
                    a.getHonestyGap() != null ? String.format(java.util.Locale.US, "%.1f", a.getHonestyGap()) : "N/A"));
        }
        // ③ 빈 데이터 조기 반환 — 분석 완료 미팅 없으면 GPT 호출 불필요
        if (memberSummaries.isEmpty()) return new TeamCoachingResponse("분석 완료된 미팅 데이터가 없습니다.", List.of(), List.of());

        // 캐시 확인: 입력(분석에 사용된 미팅 집합)이 동일하면 기존 코칭을 그대로 반환하여
        // 새 미팅이 없을 때 매 요청마다 GPT 호출로 결과가 달라지는 것을 방지한다.
        String signature = usedMeetingIds.stream().sorted()
                .map(String::valueOf).collect(Collectors.joining(","));
        TeamCoachingCache cached = teamCoachingCacheRepository.findById(teamId).orElse(null);
        if (cached != null && signature.equals(cached.getSignature())) {
            try {
                return objectMapper.readValue(cached.getPayload(), TeamCoachingResponse.class);
            } catch (Exception e) {
                log.warn("팀 코칭 캐시 역직렬화 실패, 재생성합니다. teamId={}", teamId, e);
            }
        }

        memberSummaries.forEach(s -> prompt.append(s).append("\n"));

        prompt.append("""

                위 팀 데이터를 바탕으로 리더를 위한 팀 코칭 가이드를 생성하세요.

                [출력 원칙 — 매우 중요]
                - Fact-Based 원칙 유지: 모든 서술은 관찰된 행동·발언·대화 패턴 등 '사실'에 근거하세요.
                  근거 없는 추측이나 "번아웃", "이직 위험" 같은 단정적 진단 라벨은 금지합니다.
                - 단, SafetyScore, HonestyGap, V/D/I, 점수, 숫자 등 내부 지표 용어·수치는 절대 출력에 노출하지 마세요.
                  리더와 팀원은 이 용어를 이해하지 못합니다. 수치는 판단의 '근거'로만 활용하고, 표현에는 쓰지 마세요.
                - 내부 지표가 의미하는 '팀원의 상태'를 관찰된 사실 기반의 일상 언어로 바꿔 설명하세요.
                  · SafetyScore 낮음 → "솔직한 의견이나 우려를 편하게 꺼내지 못하는 분위기"
                  · HonestyGap 큰 양(+) → "스스로는 괜찮다고 말하지만 실제 대화에서는 속마음을 잘 드러내지 않음"
                  · HonestyGap 큰 음(-) → "실제로는 적극적으로 의견을 내지만 스스로를 과소평가함"
                  · V/D/I 낮음 → "질문·반대 의견·먼저 나서는 발언이 거의 없음"
                - suggestedActions는 리더가 이번 주에 바로 실행할 수 있는 구체적 행동으로 작성하세요.
                  무엇을·누구에게·어떻게 할지가 드러나야 합니다.
                  ("관심을 가지세요", "지원하세요" 같은 추상적 표현 금지 / 예: "오멤버와 30분 1:1을 잡아
                  최근 맡은 업무에서 부담되는 부분이 무엇인지 먼저 물어보고, 리더가 덜어줄 수 있는 일을 1가지 정하세요.")

                insights의 type은 반드시 ATTENTION(주의) 또는 POSITIVE(긍정) 중 하나.
                relatedMemberId는 언급된 멤버의 id(숫자)로 반환하세요.

                반드시 아래 JSON 형식으로만 응답하세요:
                {
                  "overallAssessment": "팀 전체 소통 상태를 일상 언어로 설명한 1~2문장 (지표 용어·숫자 금지)",
                  "insights": [
                    {
                      "type": "ATTENTION",
                      "content": "특정 멤버의 상태를 일상 언어로 설명한 1문장 (지표 용어·숫자 금지)",
                      "relatedMemberId": 멤버id숫자
                    }
                  ],
                  "suggestedActions": ["이번 주 실행 가능한 구체적 행동 1", "구체적 행동 2"]
                }
                """);

        try {
            String raw = gptAdapter.chat("당신은 1on1 미팅 팀 분석 전문가입니다.", prompt.toString());
            Map<String, Object> parsed = parseJson(raw);
            if (parsed == null) return null;

            Object insightsRaw = parsed.get("insights");
            List<TeamCoachingResponse.Insight> insights = new ArrayList<>();
            if (insightsRaw instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) continue;
                    // ④ LLM이 ID를 문자열로 반환하는 경우도 방어 처리
                    Object memberIdObj = m.get("relatedMemberId");
                    Long relMemberId = null;
                    if (memberIdObj instanceof Number n) {
                        relMemberId = n.longValue();
                    } else if (memberIdObj instanceof String s) {
                        try { relMemberId = Long.parseLong(s); } catch (NumberFormatException ignored) {}
                    }
                    String relMemberName = relMemberId != null && userById.containsKey(relMemberId)
                            ? userById.get(relMemberId).getName() : null;
                    insights.add(new TeamCoachingResponse.Insight(
                            m.get("type") != null ? m.get("type").toString() : null,
                            m.get("content") != null ? m.get("content").toString() : null,
                            relMemberId, relMemberName));
                }
            }

            Object actionsRaw = parsed.get("suggestedActions");
            List<String> actions = actionsRaw instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();

            String assessment = parsed.get("overallAssessment") != null
                    ? parsed.get("overallAssessment").toString() : null;

            TeamCoachingResponse response = new TeamCoachingResponse(assessment, insights, actions);

            // 동일 입력에 대해 다음 요청부터 재사용하도록 캐시에 저장 (짧은 쓰기 트랜잭션)
            try {
                String payload = objectMapper.writeValueAsString(response);
                transactionTemplate.executeWithoutResult(status -> {
                    TeamCoachingCache entity = teamCoachingCacheRepository.findById(teamId).orElse(null);
                    if (entity == null) {
                        teamCoachingCacheRepository.save(TeamCoachingCache.builder()
                                .teamId(teamId).signature(signature).payload(payload).build());
                    } else {
                        entity.update(signature, payload);
                        teamCoachingCacheRepository.save(entity);
                    }
                });
            } catch (Exception e) {
                log.warn("팀 코칭 캐시 저장 실패. teamId={}", teamId, e);
            }

            return response;
        } catch (Exception e) {
            log.warn("팀 코칭 가이드 생성 실패. teamId={}", teamId, e);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<TalkRatioRankingItem> getTalkRatioRanking(Long teamId) {
        List<User> members = userRepository.findByTeamId(teamId);
        if (members.isEmpty()) return List.of();

        List<Long> memberIds = members.stream().map(User::getId).toList();

        // 멤버별 최신 meetingId 추출
        Map<Long, Long> latestMeetingIdByMember = new LinkedHashMap<>();
        meetingRepository.findByMemberIdInOrderByCreatedAtDesc(memberIds)
                .forEach(m -> latestMeetingIdByMember.putIfAbsent(m.getMemberId(), m.getId()));

        List<Long> latestMeetingIds = new ArrayList<>(latestMeetingIdByMember.values());
        if (latestMeetingIds.isEmpty()) return List.of();

        // bulk 조회
        Map<Long, Analysis> analysisByMeetingId = analysisRepository.findByMeetingIdIn(latestMeetingIds)
                .stream().collect(Collectors.toMap(Analysis::getMeetingId, a -> a));

        Map<Long, User> userById = members.stream().collect(Collectors.toMap(User::getId, u -> u));
        List<TalkRatioRankingItem> result = new ArrayList<>();

        for (Map.Entry<Long, Long> entry : latestMeetingIdByMember.entrySet()) {
            Long memberId = entry.getKey();
            Long meetingId = entry.getValue();
            Analysis analysis = analysisByMeetingId.get(meetingId);
            if (analysis == null) continue;

            Map<String, Object> talkRatioData = analysis.getTalkRatio();
            if (talkRatioData == null) continue;

            Object leaderRatioObj = talkRatioData.get("leaderRatio");
            Object memberRatioObj = talkRatioData.get("memberRatio");
            if (leaderRatioObj == null || memberRatioObj == null) continue;

            int leaderRatio = ((Number) leaderRatioObj).intValue();
            int memberRatio = ((Number) memberRatioObj).intValue();

            String status;
            if (leaderRatio >= 70) {
                status = "위험";
            } else if (leaderRatio >= 50) {
                status = "관찰";
            } else {
                status = "적정";
            }

            User member = userById.get(memberId);
            result.add(new TalkRatioRankingItem(memberId, member.getName(), leaderRatio, memberRatio, status));
        }

        // leaderRatio 내림차순 정렬 (높을수록 위험)
        result.sort((a, b) -> Integer.compare(b.leaderRatio(), a.leaderRatio()));
        return result;
    }

    @Transactional(readOnly = true)
    public BlockerPyramidResponse getBlockerData(Long teamId) {
        List<Meeting> meetings = meetingRepository.findByTeamIdOrderByCreatedAtDesc(teamId);
        if (meetings.isEmpty()) return new BlockerPyramidResponse(List.of(), List.of());

        List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();
        Map<Long, Long> memberByMeeting = meetings.stream()
                .collect(Collectors.toMap(Meeting::getId, Meeting::getMemberId));

        // 멤버 이름 조회
        List<Long> memberIds = meetings.stream().map(Meeting::getMemberId).distinct().toList();
        // ⑥ Collectors.toMap NPE 방지: name이 null인 경우 대비
        Map<Long, String> memberNameById = new java.util.HashMap<>();
        userRepository.findAllById(memberIds).forEach(u -> memberNameById.put(u.getId(), u.getName() != null ? u.getName() : "알 수 없음"));

        // 키워드별 (총 출현 횟수, 멤버별 언급 횟수) 집계
        Map<String, Integer> countMap = new LinkedHashMap<>();
        Map<String, Map<Long, Integer>> memberCountMap = new LinkedHashMap<>();
        analysisRepository.findByMeetingIdIn(meetingIds).forEach(analysis -> {
            List<String> kws = analysis.getBlockerKeywords();
            if (kws == null) return;
            Long memberId = memberByMeeting.get(analysis.getMeetingId());
            kws.stream().filter(Objects::nonNull).forEach(kw -> {
                countMap.merge(kw, 1, Integer::sum);
                memberCountMap.computeIfAbsent(kw, k -> new java.util.HashMap<>())
                        .merge(memberId, 1, Integer::sum);
            });
        });

        List<BlockerKeyword> keywords = countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> {
                    Map<Long, Integer> mcMap = memberCountMap.getOrDefault(e.getKey(), Map.of());
                    List<BlockerKeyword.RelatedMember> relatedMembers = mcMap.entrySet().stream()
                            .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                            .map(me -> new BlockerKeyword.RelatedMember(
                                    me.getKey(),
                                    memberNameById.getOrDefault(me.getKey(), "알 수 없음"),
                                    me.getValue()))
                            .toList();
                    String guide = e.getValue() >= 3
                            ? "이번 주 팀 회의에서 '" + e.getKey() + "' 해결 안건을 상정하세요."
                            : "'" + e.getKey() + "' 관련 멤버와 1on1에서 구체적 원인을 파악하세요.";
                    return new BlockerKeyword(e.getKey(), e.getValue(), mcMap.size(), relatedMembers, guide);
                })
                .toList();

        List<BlockerPyramidResponse.ActionPrescription> prescriptions = keywords.stream()
                .limit(3)
                .map(kw -> {
                    String severity = kw.count() >= 3 ? "ERROR" : kw.count() == 2 ? "WARNING" : "INFO";
                    String summary = kw.mentionedBy() + "명의 멤버가 총 " + kw.count() + "회 언급";
                    return new BlockerPyramidResponse.ActionPrescription(severity,
                            kw.keyword() + " 반복 언급", summary, kw.actionGuide());
                })
                .toList();

        return new BlockerPyramidResponse(keywords, prescriptions);
    }

    @Transactional(readOnly = true)
    public Page<CareerMemoryResponse> getCareerMemory(Long memberId, Pageable pageable) {
        Page<Meeting> meetingPage = meetingRepository.findByMemberId(memberId, pageable);
        if (meetingPage.isEmpty()) return Page.empty(pageable);

        List<Long> meetingIds = meetingPage.getContent().stream().map(Meeting::getId).toList();
        Map<Long, Analysis> byMeetingId = analysisRepository.findByMeetingIdIn(meetingIds)
                .stream().collect(Collectors.toMap(Analysis::getMeetingId, a -> a));

        List<CareerMemoryResponse> content = meetingPage.getContent().stream()
                .filter(m -> byMeetingId.containsKey(m.getId()))
                .map(m -> {
                    Analysis a = byMeetingId.get(m.getId());
                    return new CareerMemoryResponse(
                            m.getId(),
                            m.getScheduledAt(),
                            a.getCareerTags() != null ? a.getCareerTags() : List.of(),
                            a.getMemberFeedback() != null ? a.getMemberFeedback() : Map.of()
                    );
                })
                .toList();

        return new PageImpl<>(content, pageable, meetingPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<SpeechTrendResponse> getSpeechTrend(Long memberId, Pageable pageable) {
        Page<Meeting> meetingPage = meetingRepository.findByMemberId(memberId, pageable);
        if (meetingPage.isEmpty()) return Page.empty(pageable);

        List<Long> meetingIds = meetingPage.getContent().stream().map(Meeting::getId).toList();
        Map<Long, Analysis> byMeetingId = analysisRepository.findByMeetingIdIn(meetingIds)
                .stream().collect(Collectors.toMap(Analysis::getMeetingId, a -> a));

        List<SpeechTrendResponse> content = meetingPage.getContent().stream()
                .filter(m -> byMeetingId.containsKey(m.getId()))
                .map(m -> {
                    Map<String, Object> acts = byMeetingId.get(m.getId()).getSpeechActs();
                    return new SpeechTrendResponse(
                            m.getId(),
                            m.getScheduledAt(),
                            countSpeechAct(acts, "vulnerability"),
                            countSpeechAct(acts, "dissent"),
                            countSpeechAct(acts, "initiative")
                    );
                })
                .toList();

        return new PageImpl<>(content, pageable, meetingPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(Long memberId) {
        Pageable recent = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Meeting> meetings = meetingRepository.findByMemberId(memberId, recent).getContent();
        if (meetings.isEmpty()) return new PortfolioResponse(List.of(), List.of(), List.of(), List.of());

        List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();
        Map<Long, Analysis> byMeetingId = analysisRepository.findByMeetingIdIn(meetingIds)
                .stream().collect(Collectors.toMap(Analysis::getMeetingId, a -> a));

        List<PortfolioResponse.MeetingSnapshot> meetingHistory = meetings.stream()
                .map(m -> new PortfolioResponse.MeetingSnapshot(m.getId(), m.getScheduledAt(), m.getTitle()))
                .toList();

        List<PortfolioResponse.ScorePoint> scoreTrend = meetings.stream()
                .filter(m -> byMeetingId.containsKey(m.getId()))
                .filter(m -> byMeetingId.get(m.getId()).getSafetyScore() != null)
                .map(m -> new PortfolioResponse.ScorePoint(m.getId(), m.getScheduledAt(), byMeetingId.get(m.getId()).getSafetyScore()))
                .toList();

        Map<String, Long> tagCount = byMeetingId.values().stream()
                .filter(a -> a.getCareerTags() != null)
                .flatMap(a -> a.getCareerTags().stream())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        List<String> topCareerTags = tagCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(5)
                .toList();

        List<String> feedbackSummaries = meetings.stream()
                .filter(m -> byMeetingId.containsKey(m.getId()))
                .map(m -> byMeetingId.get(m.getId()).getMemberFeedback())
                .filter(fb -> fb != null && fb.containsKey("summary"))
                .map(fb -> String.valueOf(fb.get("summary")))
                .toList();

        return new PortfolioResponse(meetingHistory, scoreTrend, topCareerTags, feedbackSummaries);
    }

    private int countSpeechAct(Map<String, Object> speechActs, String key) {
        if (speechActs == null) return 0;
        Object val = speechActs.get(key);
        return val instanceof List<?> list ? list.size() : 0;
    }

    // ── 11절: Career Memory (본인 또는 본인 팀 리더 조회 가능) ──────────────

    @Transactional(readOnly = true)
    public CareerStatsResponse getCareerStats(Long requesterId, Long memberId) {
        checkCareerAccess(requesterId, memberId);

        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String teamName = member.getTeamId() != null
                ? teamRepository.findById(member.getTeamId()).map(t -> t.getName()).orElse(null)
                : null;

        int totalMeetings = (int) meetingRepository.countByMemberId(memberId);

        List<CareerEvent> events = careerEventRepository.findByUserIdOrderByOccurredAtDesc(memberId);
        int achievementCount = (int) events.stream()
                .filter(e -> e.getEventType() == CareerEventType.ACHIEVEMENT).count();
        int leaderEndorsementCount = (int) events.stream()
                .filter(e -> e.getEventType() == CareerEventType.ACHIEVEMENT).count();

        int contributionPercentile = computeContributionPercentile(memberId, member.getTeamId());

        String aiSummary = buildAiSummary(memberId);

        return new CareerStatsResponse(
                memberId, member.getName(), member.getJobTitle(), teamName,
                totalMeetings, achievementCount, leaderEndorsementCount,
                contributionPercentile, aiSummary);
    }

    @Transactional(readOnly = true)
    public List<CareerTimelineResponse> getCareerTimeline(Long requesterId, Long memberId, String type) {
        checkCareerAccess(requesterId, memberId);

        List<CareerEvent> events;
        if (type != null) {
            try {
                CareerEventType eventType = CareerEventType.valueOf(type.toUpperCase());
                events = careerEventRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(memberId, eventType);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        } else {
            events = careerEventRepository.findByUserIdOrderByOccurredAtDesc(memberId);
        }

        List<Meeting> meetings = meetingRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        Map<Long, Integer> roundByMeetingId = buildRoundMap(meetings);

        return events.stream()
                .map(e -> toTimelineResponse(e, roundByMeetingId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CareerTimelineResponse> getCareerShowcase(Long requesterId, Long memberId) {
        checkCareerAccess(requesterId, memberId);

        List<Meeting> meetings = meetingRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        Map<Long, Integer> roundByMeetingId = buildRoundMap(meetings);

        return careerEventRepository.findByUserIdOrderByOccurredAtDesc(memberId).stream()
                .filter(e -> e.getEvidence() != null && e.getEvidence().containsKey("impactMetric"))
                .limit(5)
                .map(e -> toTimelineResponse(e, roundByMeetingId))
                .toList();
    }

    private void checkCareerAccess(Long requesterId, Long memberId) {
        if (requesterId.equals(memberId)) return;
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (requester.getRole() != UserRole.LEADER
                || requester.getTeamId() == null
                || !requester.getTeamId().equals(member.getTeamId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private int computeContributionPercentile(Long memberId, Long teamId) {
        if (teamId == null) return 100;
        List<User> teammates = userRepository.findByTeamId(teamId).stream()
                .filter(u -> u.getRole() != UserRole.LEADER)
                .toList();
        if (teammates.size() <= 1) return 1;
        List<Long> teammateIds = teammates.stream().map(User::getId).toList();
        Map<Long, Long> countMap = careerEventRepository.countMapByUserIds(teammateIds);
        long myCount = countMap.getOrDefault(memberId, 0L);
        long betterCount = teammates.stream()
                .filter(u -> !u.getId().equals(memberId))
                .mapToLong(u -> countMap.getOrDefault(u.getId(), 0L))
                .filter(c -> c > myCount)
                .count();
        return (int) Math.round((double) betterCount / (teammates.size() - 1) * 100);
    }

    private String buildAiSummary(Long memberId) {
        Pageable top5 = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Long> ids = meetingRepository.findByMemberId(memberId, top5)
                .map(Meeting::getId).toList();
        if (ids.isEmpty()) return null;
        List<String> tags = analysisRepository.findByMeetingIdIn(ids).stream()
                .filter(a -> a.getCareerTags() != null)
                .flatMap(a -> a.getCareerTags().stream())
                .distinct().limit(5).toList();
        return tags.isEmpty() ? null : String.join(", ", tags);
    }

    private Map<Long, Integer> buildRoundMap(List<Meeting> meetings) {
        List<Meeting> ascending = meetings.stream()
                .sorted(java.util.Comparator.comparing(Meeting::getId))
                .toList();
        Map<Long, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < ascending.size(); i++) {
            map.put(ascending.get(i).getId(), i + 1);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public TeamPromiseSummaryResponse getTeamPromiseSummary(Long teamId) {
        List<User> members = userRepository.findByTeamId(teamId).stream()
                .filter(u -> u.getRole() == UserRole.MEMBER)
                .toList();
        if (members.isEmpty()) {
            return new TeamPromiseSummaryResponse(List.of());
        }

        List<Long> memberIds = members.stream().map(User::getId).toList();

        List<Promise> allPromises = promiseRepository.findByOwnerIdIn(memberIds);
        Map<Long, List<Promise>> byMember = allPromises.stream()
                .collect(Collectors.groupingBy(Promise::getOwnerId));

        // 회차 계산: 멤버별 전체 미팅을 1번에 조회해 메모리에서 id 순서대로 round 부여 (N+1 제거).
        // 1:1 미팅은 멤버당 단일 리더이므로 멤버별 그룹핑이 (리더, 멤버) 쌍과 동일하다.
        Map<Long, Integer> roundByMeetingId = new java.util.HashMap<>();
        meetingRepository.findByMemberIdInOrderByCreatedAtDesc(memberIds).stream()
                .collect(Collectors.groupingBy(Meeting::getMemberId))
                .values()
                .forEach(meetingsOfMember -> {
                    meetingsOfMember.sort(java.util.Comparator.comparing(Meeting::getId));
                    for (int i = 0; i < meetingsOfMember.size(); i++) {
                        roundByMeetingId.put(meetingsOfMember.get(i).getId(), i + 1);
                    }
                });

        List<TeamPromiseSummaryResponse.MemberPromiseSummary> memberSummaries = members.stream()
                .filter(m -> byMember.containsKey(m.getId()))
                .map(member -> {
                    List<Promise> promises = byMember.get(member.getId());
                    long completed = promises.stream().filter(p -> p.getStatus() == PromiseStatus.DONE).count();
                    long pending = promises.stream().filter(p -> p.getStatus() == PromiseStatus.PENDING).count();
                    long overdue = promises.stream().filter(p -> p.getStatus() == PromiseStatus.MISSED).count();

                    LocalDate today = LocalDate.now();
                    // 미이행(PENDING/MISSED)은 항상 표시, 오늘 완료한 약속은 체크된 상태로 당일까지 유지
                    List<TeamPromiseSummaryResponse.PromiseSummaryItem> items = promises.stream()
                            .filter(p -> p.getStatus() == PromiseStatus.PENDING
                                    || p.getStatus() == PromiseStatus.MISSED
                                    || (p.getStatus() == PromiseStatus.DONE
                                        && p.getCompletedAt() != null
                                        && p.getCompletedAt().toLocalDate().isEqual(today)))
                            .map(p -> {
                                boolean isCompleted = p.getStatus() == PromiseStatus.DONE;
                                String status = isCompleted ? "PENDING"
                                        : (p.getStatus() == PromiseStatus.MISSED ? "OVERDUE" : "PENDING");
                                return new TeamPromiseSummaryResponse.PromiseSummaryItem(
                                        String.valueOf(p.getId()),
                                        p.getContent(),
                                        p.getContext(),
                                        status,
                                        p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate().toString() : null,
                                        roundByMeetingId.getOrDefault(p.getMeetingId(), 0),
                                        isCompleted
                                );
                            })
                            // 미완료를 위로, 완료된 항목을 아래로 정렬
                            .sorted(java.util.Comparator.comparing(TeamPromiseSummaryResponse.PromiseSummaryItem::isCompleted))
                            .toList();

                    if (items.isEmpty()) return null;

                    return new TeamPromiseSummaryResponse.MemberPromiseSummary(
                            String.valueOf(member.getId()),
                            member.getName(),
                            items,
                            new TeamPromiseSummaryResponse.Stats(
                                    promises.size(), (int) completed, (int) pending, (int) overdue)
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        return new TeamPromiseSummaryResponse(memberSummaries);
    }

    // 팀 단위: 멤버별 최신 미팅에서 나온 '미완료' 액션 플랜을 묶어 반환.
    // '1on1 미팅' 화면 액션 아이템에서 멤버별 다음 할 일을 간단히 보여주기 위함.
    @Transactional(readOnly = true)
    public TeamActionPlanResponse getTeamActionPlans(Long teamId) {
        List<User> members = userRepository.findByTeamId(teamId).stream()
                .filter(u -> u.getRole() == UserRole.MEMBER)
                .toList();
        if (members.isEmpty()) return new TeamActionPlanResponse(List.of());

        List<Long> memberIds = members.stream().map(User::getId).toList();
        Map<Long, User> userById = members.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Meeting> allMeetings = meetingRepository.findByMemberIdInOrderByCreatedAtDesc(memberIds);
        if (allMeetings.isEmpty()) return new TeamActionPlanResponse(List.of());

        // 회차 계산 (멤버별 id 오름차순) + 멤버별 최신 미팅(createdAt 내림차순 첫 항목)
        Map<Long, Integer> roundByMeetingId = new java.util.HashMap<>();
        allMeetings.stream().collect(Collectors.groupingBy(Meeting::getMemberId))
                .values().forEach(list -> {
                    list.sort(java.util.Comparator.comparing(Meeting::getId));
                    for (int i = 0; i < list.size(); i++) {
                        roundByMeetingId.put(list.get(i).getId(), i + 1);
                    }
                });
        Map<Long, Long> latestMeetingIdByMember = new LinkedHashMap<>();
        allMeetings.forEach(m -> latestMeetingIdByMember.putIfAbsent(m.getMemberId(), m.getId()));

        List<Long> latestMeetingIds = new ArrayList<>(latestMeetingIdByMember.values());
        Map<Long, List<ActionPlan>> plansByMeeting = actionPlanRepository
                .findByMeetingIdInAndIsCompletedFalseOrderByIdAsc(latestMeetingIds).stream()
                .collect(Collectors.groupingBy(ActionPlan::getMeetingId));

        List<TeamActionPlanResponse.MemberActionPlans> result = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : latestMeetingIdByMember.entrySet()) {
            Long memberId = entry.getKey();
            Long meetingId = entry.getValue();
            List<ActionPlan> plans = plansByMeeting.getOrDefault(meetingId, List.of());
            if (plans.isEmpty()) continue;
            result.add(new TeamActionPlanResponse.MemberActionPlans(
                    memberId,
                    userById.get(memberId).getName(),
                    roundByMeetingId.getOrDefault(meetingId, 0),
                    plans.stream()
                            .map(p -> new TeamActionPlanResponse.PlanItem(p.getId(), p.getContent()))
                            .toList()
            ));
        }
        return new TeamActionPlanResponse(result);
    }

    private CareerTimelineResponse toTimelineResponse(CareerEvent e, Map<Long, Integer> roundByMeetingId) {
        String impactMetric = e.getEvidence() != null
                ? (String) e.getEvidence().get("impactMetric") : null;
        int round = e.getMeetingId() != null
                ? roundByMeetingId.getOrDefault(e.getMeetingId(), 0) : 0;
        java.time.LocalDate eventDate = e.getOccurredAt() != null
                ? e.getOccurredAt().toLocalDate() : null;
        return new CareerTimelineResponse(
                e.getId(), e.getEventType().name(), e.getTitle(),
                e.getDescription(), impactMetric, eventDate, round);
    }
}
