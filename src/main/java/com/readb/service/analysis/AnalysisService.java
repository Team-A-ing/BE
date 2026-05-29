package com.readb.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readb.adapter.llm.LlmAdapter;
import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.analysis.Analysis;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.promise.Promise;
import com.readb.domain.recording.Recording;
import com.readb.domain.survey.Survey;
import com.readb.dto.analysis.AnalysisResultResponse;
import com.readb.dto.analysis.AnalysisResultResponse.*;
import com.readb.dto.analysis.BlockerKeyword;
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
            당신은 1on1 미팅 전사 텍스트 구조화 전문가입니다.
            Whisper verbose_json 형식의 전사 텍스트를 분석해 다음을 추출하세요.

            [화자 추론]
            대화 맥락을 보고 각 발화자를 추론하세요.
            - 리더: 질문하고 방향을 제시하는 역할
            - 멤버: 업무 현황을 보고하고 답변하는 역할

            [Speech Act 분류 — 멤버 발화만, 애매하면 포함하지 마세요]
            - vulnerability: 취약성 표현 ("잘 모르겠습니다", "실수했습니다", "도움이 필요합니다")
              제외: 사교적 겸손, 관용표현
            - constructiveDissent: 건설적 반대 ("다른 의견인데요", "그 방법보다는...")
              제외: 단순 불만, 인신공격
            - initiative: 자발적 제안 ("제가 해볼게요", "이런 아이디어가 있는데")
              제외: 지시에 대한 단순 수락

            [발화 비율]
            전체 발화 분량(문자수 또는 시간) 기준으로 리더/멤버 발화 비율을 추정하세요.
            recommendedLeaderRatio는 항상 40입니다.

            [기타 추출]
            - topics: 주요 논의 주제 (최대 5개)
            - blockerKeywords: 업무 장애 요소 키워드 (최대 5개)
            - promises: 이행 의지가 담긴 발언 ("하겠습니다", "해드릴게요" 등)

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

            [Safety Score 산출]
            멤버의 Speech Act 횟수를 30분 기준으로 정규화 후 점수화.
            adjusted_count = round(raw_count × 30 ÷ actual_duration_minutes)
            미팅 시간 정보가 없으면 raw_count를 그대로 사용.

            변환표:
            - Vulnerability (max 40): 0회→0, 1회→20, 2회→32, 3회→38, 4회+→40
            - constructiveDissent (max 35): 0회→0, 1회→18, 2회→28, 3회→33, 4회+→35
            - Initiative (max 25): 0회→0, 1회→13, 2회→20, 3회→24, 4회+→25
            safetyScore = V_score + D_score + I_score

            [Honesty Gap]
            surveyScore가 제공된 경우: honestyGap = surveyScore - safetyScore (부호 있는 값)
            surveyScore가 없으면: null

            [Alignment Gap (0–100)]
            서베이 topics vs 실제 미팅 topics 일치도를 추정하고, 한 문장으로 구체적인 이유를 작성하세요.
            서베이 정보 없으면 score=50, detail="서베이 정보 없음"

            [Execution Gap (0–100)]
            이전 약속이 이번 transcript에서 언급/이행되었는지 평가.
            이전 약속 없으면: null
            완료→100, 진행중→70, 미이행+사유→50, 미이행+무사유→20, 전혀 언급없음→0

            [코칭 피드백 — 반드시 준수]
            절대 금지: AI 해석 라벨 ("수동 공격적", "번아웃 징후", "소극적 참여" 등)
            허용: 관찰 가능한 사실만 (원문 인용 + 타임스탬프, 횟수, 수치)
            severity: ERROR / WARNING / SUCCESS / INFO
            - feedbacks: 리더를 위한 코칭 피드백 (최대 4개, 중요한 것부터)
              - title: 한 줄 요약
              - evidenceQuote: 관련 발화 원문 인용 (없으면 빈 문자열)
              - dataSummary: 수치/사실 근거
              - actionGuide: 리더가 다음에 취할 행동
            - nextActionPlans: 이번 미팅 결과로 리더가 해야 할 구체적 실행 과제 (최대 4개)

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
              "careerTags": ["태그1"]
            }
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
            ObjectMapper objectMapper) {
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
    }

    // ── 파이프라인 ────────────────────────────────────────────────────────────

    public void analyze(Long meetingId, String transcript) {
        // DB 조회는 짧은 별도 트랜잭션으로
        Meeting meeting = loadMeeting(meetingId);
        Double surveyScore = loadSurveyScore(meetingId, meeting.getMemberId());
        Integer durationSec = loadDurationSec(meetingId);
        List<Promise> prevPromises = loadPrevPromises(meeting, meetingId);

        // LLM 호출 — 트랜잭션 밖 (seconds 단위 네트워크 I/O)
        String step2Raw = gptAdapter.chat(STEP2_SYSTEM, transcript);
        Map<String, Object> step2 = parseJson(step2Raw);
        log.info("Step2(GPT-mini) 완료. meetingId={}", meetingId);

        String step3UserPrompt = buildStep3UserPrompt(step2, surveyScore, durationSec, prevPromises);
        String step3Raw = gptAdapter.chat(STEP3_SYSTEM, step3UserPrompt);
        Map<String, Object> step3 = parseJson(step3Raw);
        log.info("Step3(GPT-mini) 완료. meetingId={}", meetingId);

        // DB 저장만 트랜잭션
        persistResults(meetingId, meeting, step2, step3);
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

    @Transactional
    protected void persistResults(Long meetingId, Meeting meeting, Map<String, Object> step2, Map<String, Object> step3) {
        analysisRepository.findByMeetingId(meetingId).ifPresent(analysisRepository::delete);
        analysisRepository.save(buildAnalysis(meetingId, step2, step3));

        promiseRepository.deleteByMeetingId(meetingId);
        savePromises(meetingId, meeting, step2);

        actionPlanRepository.deleteByMeetingId(meetingId);
        saveActionPlans(meetingId, meeting.getLeaderId(), step3);
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
                                        Integer durationSec, List<Promise> prevPromises) {
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
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Analysis buildAnalysis(Long meetingId, Map<String, Object> step2, Map<String, Object> step3) {
        return Analysis.builder()
                .meetingId(meetingId)
                .safetyScore(toDouble(step3.get("safetyScore")))
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
            String content = String.valueOf(p.get("content"));
            actionPlanRepository.save(ActionPlan.builder()
                    .meetingId(meetingId)
                    .leaderId(leaderId)
                    .content(content)
                    .build());
        }
    }

    private Double computeSurveyScore(Map<String, Object> scores) {
        if (scores == null) return null;
        Object energyObj = scores.get("energyLevel");
        if (energyObj == null) return null;
        double base = ((Number) energyObj).doubleValue() * 20.0;
        double adj = 0;
        Object issuesObj = scores.get("issues");
        if (issuesObj instanceof List<?> issues) {
            for (Object issue : issues) {
                adj += switch (issue.toString()) {
                    case "업무 블로커" -> -10;
                    case "리소스 요청" -> -5;
                    case "팀 분위기", "프로세스 개선" -> -3;
                    default -> 0;
                };
            }
        }
        return Math.max(0, Math.min(100, base + adj));
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        return null;
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

        return new AnalysisResultResponse(meetingId, round, member.getName(), member.getJobTitle(),
                meetingDate, durationSec, gaps, a.getSafetyScore(), speechActs,
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
        List<Meeting> prev = meetingRepository.findByLeaderIdAndMemberIdOrderByCreatedAtDesc(
                        meeting.getLeaderId(), meeting.getMemberId()).stream()
                .filter(m -> !m.getId().equals(meeting.getId()))
                .limit(1).toList();
        if (prev.isEmpty()) return new ExecutionGapDetail(score, 0, 0, 0);
        List<Promise> prevPromises = promiseRepository.findByMeetingId(prev.get(0).getId());
        int total = prevPromises.size();
        int fulfilled = (int) prevPromises.stream().filter(p -> p.getStatus().name().equals("DONE")).count();
        int missed = (int) prevPromises.stream().filter(p -> p.getStatus().name().equals("MISSED")).count();
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
        // previous: 직전 미팅의 약속
        List<Meeting> prevMeetings = meetingRepository.findByLeaderIdAndMemberIdOrderByCreatedAtDesc(
                        meeting.getLeaderId(), meeting.getMemberId()).stream()
                .filter(m -> !m.getId().equals(meetingId)).limit(1).toList();
        List<PreviousPromise> previous = prevMeetings.isEmpty() ? List.of()
                : promiseRepository.findByMeetingId(prevMeetings.get(0).getId()).stream()
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
            result.add(new RadarDataPoint(memberId, member.getName(), surveyScore, safetyScore, honestyGap,
                    computeDirection(honestyGap), computeRiskLevel(honestyGap)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<BlockerKeyword> getBlockerData(Long teamId) {
        List<Long> meetingIds = meetingRepository.findByTeamIdOrderByCreatedAtDesc(teamId)
                .stream().map(Meeting::getId).toList();
        if (meetingIds.isEmpty()) return List.of();

        Map<String, Integer> counts = new LinkedHashMap<>();
        analysisRepository.findByMeetingIdIn(meetingIds).forEach(analysis -> {
            List<String> keywords = analysis.getBlockerKeywords();
            if (keywords == null) return;
            keywords.forEach(kw -> counts.merge(kw, 1, Integer::sum));
        });

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new BlockerKeyword(e.getKey(), e.getValue()))
                .toList();
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

        int totalMeetings = meetingRepository.findByMemberIdOrderByCreatedAtDesc(memberId).size();

        List<CareerEvent> events = careerEventRepository.findByUserIdOrderByOccurredAtDesc(memberId);
        int achievementCount = (int) events.stream()
                .filter(e -> e.getEventType() == CareerEventType.ACHIEVEMENT).count();
        int leaderEndorsementCount = events.size();

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
