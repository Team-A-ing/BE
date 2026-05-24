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
import com.readb.dto.analysis.BlockerKeyword;
import com.readb.dto.analysis.CareerMemoryResponse;
import com.readb.dto.analysis.RadarDataPoint;
import com.readb.dto.team.TeamDashboardResponse;
import com.readb.domain.user.User;
import com.readb.repository.AnalysisRepository;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import com.readb.repository.RecordingRepository;
import com.readb.repository.SurveyRepository;
import com.readb.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            - dissent: 건설적 반대 ("다른 의견인데요", "그 방법보다는...")
              제외: 단순 불만, 인신공격
            - initiative: 자발적 제안 ("제가 해볼게요", "이런 아이디어가 있는데")
              제외: 지시에 대한 단순 수락

            [기타 추출]
            - topics: 주요 논의 주제 (최대 5개)
            - blockerKeywords: 업무 장애 요소 키워드 (최대 5개)
            - promises: 이행 의지가 담긴 발언 ("하겠습니다", "해드릴게요" 등)

            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "speechActs": {
                "vulnerability": [{"text": "원문 그대로", "timestamp": 초단위_숫자}],
                "dissent": [{"text": "원문 그대로", "timestamp": 초단위_숫자}],
                "initiative": [{"text": "원문 그대로", "timestamp": 초단위_숫자}]
              },
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
            - Dissent (max 35): 0회→0, 1회→18, 2회→28, 3회→33, 4회+→35
            - Initiative (max 25): 0회→0, 1회→13, 2회→20, 3회→24, 4회+→25
            safetyScore = V_score + D_score + I_score

            [Honesty Gap]
            surveyScore가 제공된 경우: honestyGap = surveyScore - safetyScore (부호 있는 값)
            surveyScore가 없으면: null

            [Alignment Gap (0–100)]
            서베이 topics vs 실제 미팅 topics 일치도 추정. 서베이 정보 없으면 50.

            [Execution Gap (0–100)]
            이전 약속이 이번 transcript에서 언급/이행되었는지 평가.
            이전 약속 없으면: null
            완료→100, 진행중→70, 미이행+사유→50, 미이행+무사유→20, 전혀 언급없음→0

            [코칭 피드백 원칙 — 반드시 준수]
            절대 금지: AI 해석 라벨 ("수동 공격적", "번아웃 징후", "소극적 참여" 등)
            허용: 관찰 가능한 사실만 (원문 인용 + 타임스탬프, 횟수, 수치)
            severity: SAFE / CAUTION / WARNING / DANGER

            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "safetyScore": 0.0,
              "alignmentGap": 0.0,
              "honestyGap": null,
              "executionGap": null,
              "leaderFeedback": {"summary": "...", "severity": "SAFE"},
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
            @Qualifier("gptMiniAdapter") LlmAdapter gptAdapter,
            @Qualifier("claudeAdapter") LlmAdapter claudeAdapter,
            ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.meetingRepository = meetingRepository;
        this.surveyRepository = surveyRepository;
        this.recordingRepository = recordingRepository;
        this.promiseRepository = promiseRepository;
        this.userRepository = userRepository;
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
                .honestyGap(toDouble(step3.get("honestyGap")))
                .executionGap(toDouble(step3.get("executionGap")))
                .speechActs((Map<String, Object>) step2.get("speechActs"))
                .blockerKeywords((List<String>) step2.get("blockerKeywords"))
                .leaderFeedback((Map<String, Object>) step3.get("leaderFeedback"))
                .memberFeedback((Map<String, Object>) step3.get("memberFeedback"))
                .careerTags((List<String>) step3.get("careerTags"))
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

    // ── 조회 (stub → 실데이터로 교체 예정) ──────────────────────────────────

    @Transactional(readOnly = true)
    public AnalysisResultResponse getResult(Long meetingId) {
        Analysis a = analysisRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND));
        return new AnalysisResultResponse(meetingId, a.getAlignmentGap(), a.getHonestyGap(),
                a.getExecutionGap(), a.getSafetyScore(), a.getSpeechActs(), a.getBlockerKeywords(),
                a.getLeaderFeedback(), a.getMemberFeedback(), a.getCareerTags(), a.getBaselineData());
    }

    @Transactional(readOnly = true)
    public TeamDashboardResponse getTeamDashboard(Long teamId) {
        List<Long> meetingIds = meetingRepository.findByTeamIdOrderByCreatedAtDesc(teamId)
                .stream().limit(20).map(Meeting::getId).toList();
        if (meetingIds.isEmpty()) {
            return new TeamDashboardResponse(teamId, 0.0, "NO_DATA", List.of());
        }

        List<Double> scores = analysisRepository.findByMeetingIdIn(meetingIds)
                .stream()
                .sorted(Comparator.comparingLong(Analysis::getMeetingId).reversed())
                .map(Analysis::getSafetyScore)
                .filter(s -> s != null)
                .toList();
        if (scores.isEmpty()) {
            return new TeamDashboardResponse(teamId, 0.0, "NO_DATA", List.of());
        }

        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double rounded = Math.round(avg * 10.0) / 10.0;

        String trend;
        if (scores.size() < 2) {
            trend = "STABLE";
        } else {
            // 최근 절반 vs 이전 절반 평균 비교
            int half = scores.size() / 2;
            double recent = scores.subList(0, half).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double previous = scores.subList(half, scores.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (recent > previous + 5) trend = "IMPROVING";
            else if (recent < previous - 5) trend = "DECLINING";
            else trend = "STABLE";
        }

        return new TeamDashboardResponse(teamId, rounded, trend, List.of());
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
            result.add(new RadarDataPoint(memberId, member.getName(), surveyScore, safetyScore, honestyGap));
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
    public List<CareerMemoryResponse> getCareerMemory(Long memberId) {
        // TODO: 멤버 참여 미팅 careerTags 타임라인 조회
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                new CareerMemoryResponse(101L, now.minusDays(2),
                        List.of("주도성", "협업"),
                        Map.of("summary", "회의 안건을 먼저 정리해 공유한 주도적 행동이 인상적이었습니다.")),
                new CareerMemoryResponse(102L, now.minusDays(9),
                        List.of("문제 해결"),
                        Map.of("summary", "QA 리소스 이슈를 직접 식별하고 대안을 제시했습니다.")),
                new CareerMemoryResponse(103L, now.minusDays(16),
                        List.of("학습", "성장"),
                        Map.of("summary", "새로운 도구 학습 후 팀에 공유한 점이 좋았습니다."))
        );
    }
}
