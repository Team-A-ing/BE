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
import com.readb.dto.team.TeamDashboardResponse;
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
import com.readb.repository.TeamRepository;
import com.readb.repository.UserRepository;
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
              content는 원문 그대로가 아니라 "무엇을 하겠다"는 약속 내용을 한 문장으로 요약하세요.
              예: "다음 주까지 AWS 접근 권한을 부여하겠습니다" → content: "AWS 접근 권한 부여"
              owner는 약속한 사람 (leader 또는 member)

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
              "promises": [{"content": "약속 내용", "owner": "leader 또는 member"}]
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
            - feedbacks: 리더를 위한 코칭 피드백 (최대 4개, 중요한 것부터)
              - title: 한 줄 요약
              - evidenceQuote: 관련 발화 원문 인용 (없으면 빈 문자열)
              - dataSummary: 수치/사실 근거
              - actionGuide: 리더가 다음에 취할 행동
            - nextActionPlans: 이번 미팅 결과로 리더가 해야 할 구체적 실행 과제 (최대 4개)

            이전 미팅 컨텍스트가 제공된 경우, 피드백에 변화량을 반드시 포함하세요.
            예: "Vulnerability 발화가 이전 3회 평균 2.3건에서 0건으로 감소했습니다"
            Safety Score가 baseline 대비 30%+ 하락하면 반드시 WARNING으로 언급하세요.

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
            - PROPOSAL_ADOPTED: 제안이 팀/리더에게 채택된 경우
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
            promiseRepository.save(Promise.builder()
                    .meetingId(meetingId)
                    .ownerId(ownerId)
                    .content(content)
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

    // VDI 기반 surveyScore: safetyScore와 동일 차원(0~100)으로 산출
    // V(1~5)→max 40, D(1~5)→max 35, I(1~5)→max 25
    private Double computeSurveyScore(Map<String, Object> scores) {
        if (scores == null) return null;
        Object vObj = scores.get("vulnerabilityLevel");
        Object dObj = scores.get("dissentLevel");
        Object iObj = scores.get("initiativeLevel");
        if (vObj == null || dObj == null || iObj == null) return null;
        double v = ((Number) vObj).doubleValue() * 8.0;   // max 40
        double d = ((Number) dObj).doubleValue() * 7.0;   // max 35
        double i = ((Number) iObj).doubleValue() * 5.0;   // max 25
        return Math.max(0, Math.min(100, v + d + i));
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

        HonestyDirection dir = gaps.honestyGap() != null ? computeDirection(gaps.honestyGap().gap()) : null;
        RiskLevel risk = gaps.honestyGap() != null ? computeRiskLevel(gaps.honestyGap().gap()) : null;
        String flightRiskLabel = computeFlightRiskLabel(a.getSafetyScore(), dir, risk);

        return new AnalysisResultResponse(meetingId, round, member.getName(), member.getJobTitle(),
                meetingDate, durationSec, gaps, a.getSafetyScore(), flightRiskLabel, speechActs,
                talkRatio, feedbacks, nextActionPlans, promises);
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
                        .map(p -> new PreviousPromise(p.getId(), p.getContent(), p.getStatus().name()))
                        .toList();

        // new: 현재 미팅의 약속
        List<NewPromise> newPromises = promiseRepository.findByMeetingId(meetingId).stream()
                .map(p -> new NewPromise(p.getId(), p.getContent(), p.getCategory(),
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

        String scheduledAt = meeting.getScheduledAt() != null ? meeting.getScheduledAt().toString() : null;
        return new PreBriefingResponse(meetingId, round, member.getName(), member.getJobTitle(),
                scheduledAt, survey, lastMeeting, pendingPromises, recommendedTopics);
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

        // 키워드별 (총 출현 횟수, 언급 멤버 Set) 집계
        Map<String, Integer> countMap = new LinkedHashMap<>();
        Map<String, java.util.Set<Long>> memberMap = new LinkedHashMap<>();
        analysisRepository.findByMeetingIdIn(meetingIds).forEach(analysis -> {
            List<String> keywords = analysis.getBlockerKeywords();
            if (keywords == null) return;
            Long memberId = memberByMeeting.get(analysis.getMeetingId());
            keywords.forEach(kw -> {
                countMap.merge(kw, 1, Integer::sum);
                memberMap.computeIfAbsent(kw, k -> new java.util.HashSet<>()).add(memberId);
            });
        });

        List<BlockerKeyword> keywords = countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new BlockerKeyword(e.getKey(), e.getValue(),
                        memberMap.getOrDefault(e.getKey(), java.util.Set.of()).size()))
                .toList();

        List<BlockerPyramidResponse.ActionPrescription> prescriptions = keywords.stream()
                .limit(3)
                .map(kw -> {
                    String severity = kw.count() >= 3 ? "ERROR" : kw.count() == 2 ? "WARNING" : "INFO";
                    String summary = kw.mentionedBy() + "명의 멤버가 총 " + kw.count() + "회 언급";
                    String guide = kw.count() >= 3
                            ? "이번 주 팀 회의에서 '" + kw.keyword() + "' 해결 안건을 상정하세요."
                            : "'" + kw.keyword() + "' 관련 멤버와 1on1에서 구체적 원인을 파악하세요.";
                    return new BlockerPyramidResponse.ActionPrescription(severity,
                            kw.keyword() + " 반복 언급", summary, guide);
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
                .filter(e -> e.getEventType() == CareerEventType.ACHIEVEMENT
                        || e.getEventType() == CareerEventType.PROPOSAL_ADOPTED).count();

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
