package com.readb.service.analysis;

import com.readb.adapter.llm.LlmAdapter;
import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.analysis.Analysis;
import com.readb.dto.analysis.AnalysisResultResponse;
import com.readb.dto.analysis.BlockerKeyword;
import com.readb.dto.analysis.CareerMemoryResponse;
import com.readb.dto.analysis.RadarDataPoint;
import com.readb.repository.AnalysisRepository;
import com.readb.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final MeetingRepository meetingRepository;
    private final LlmAdapter llmAdapter;

    @Transactional
    public void analyze(Long meetingId, String transcript) {
        // TODO(5/7): 실제 LLM Cascading 파이프라인으로 교체.
        // 5/8 데모용 stub — Orchestrator → STT → 분석 결과 저장 흐름이 끝까지 동작함을 보여주기 위한 정적 더미.
        // 재실행 idempotent 처리를 위해 기존 분석이 있으면 삭제 후 저장.
        analysisRepository.findByMeetingId(meetingId).ifPresent(analysisRepository::delete);

        Analysis analysis = Analysis.builder()
                .meetingId(meetingId)
                .alignmentGap(68.0)
                .honestyGap(15.0)
                .executionGap(25.0)
                .safetyScore(72.0)
                .speechActs(Map.of(
                        "vulnerability", List.of(),
                        "dissent",       List.of(),
                        "initiative",    List.of()
                ))
                .blockerKeywords(List.of("일정 압박", "QA 리소스", "커뮤니케이션"))
                .leaderFeedback(Map.of(
                        "summary",  "팀원이 자발적 제안을 거의 하지 않았습니다. 안전감 점검이 필요해 보입니다.",
                        "severity", "WARNING"
                ))
                .memberFeedback(Map.of(
                        "summary",  "다음 1on1에서는 우려사항을 미리 정리해 공유해 보세요.",
                        "severity", "INFO"
                ))
                .careerTags(List.of("주도성", "협업"))
                .baselineData(Map.of())
                .build();
        analysisRepository.save(analysis);
    }

    @Transactional(readOnly = true)
    public AnalysisResultResponse getResult(Long meetingId) {
        Analysis analysis = analysisRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND));
        return new AnalysisResultResponse(
                meetingId,
                analysis.getAlignmentGap(),
                analysis.getHonestyGap(),
                analysis.getExecutionGap(),
                analysis.getSafetyScore(),
                analysis.getSpeechActs(),
                analysis.getBlockerKeywords(),
                analysis.getLeaderFeedback(),
                analysis.getMemberFeedback(),
                analysis.getCareerTags(),
                analysis.getBaselineData()
        );
    }

    @Transactional(readOnly = true)
    public List<RadarDataPoint> getRadarData(Long teamId) {
        // TODO(5/7+): 팀의 최근 미팅 analyses 를 멤버별로 집계해 RadarDataPoint 변환.
        // 5/8 데모 stub — 사분면(안정/보수적/조용한 위험/명시적 위험)을 모두 보여주도록 4명 정적 더미.
        return List.of(
                new RadarDataPoint(1L, "김안정", 82.0, 78.0,  4.0),   // 우상: 안정
                new RadarDataPoint(2L, "이조용", 85.0, 45.0, 40.0),   // 우하: 조용한 위험 (서베이↑ Safety↓)
                new RadarDataPoint(3L, "박보수", 40.0, 70.0, -30.0),  // 좌상: 보수적
                new RadarDataPoint(4L, "최명시", 35.0, 30.0,  5.0)    // 좌하: 명시적 위험
        );
    }

    @Transactional(readOnly = true)
    public List<BlockerKeyword> getBlockerData(Long teamId) {
        // TODO(5/7+): analyses.blocker_keywords 를 팀 단위로 집계해 빈도순 정렬.
        // 5/8 데모 stub — 워드클라우드가 보이도록 빈도 다양한 정적 키워드.
        return List.of(
                new BlockerKeyword("일정 압박", 7),
                new BlockerKeyword("QA 리소스", 5),
                new BlockerKeyword("커뮤니케이션", 4),
                new BlockerKeyword("역할 정의", 3),
                new BlockerKeyword("툴 부족", 2)
        );
    }

    @Transactional(readOnly = true)
    public List<CareerMemoryResponse> getCareerMemory(Long memberId) {
        // TODO(5/7+): 해당 멤버가 참여한 미팅의 careerTags 타임라인 조회.
        // 5/8 데모 stub — 최근 시점부터 거꾸로 3건.
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
