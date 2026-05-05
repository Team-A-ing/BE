package com.readb.service.analysis;

import com.readb.adapter.llm.LlmAdapter;
import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.analysis.Analysis;
import com.readb.dto.analysis.AnalysisResultResponse;
import com.readb.dto.analysis.BlockerKeyword;
import com.readb.dto.analysis.RadarDataPoint;
import com.readb.repository.AnalysisRepository;
import com.readb.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // TODO: 팀의 최근 미팅 분석 결과를 RadarDataPoint 리스트로 변환
        throw new UnsupportedOperationException("BE1 구현 예정");
    }

    @Transactional(readOnly = true)
    public List<BlockerKeyword> getBlockerData(Long teamId) {
        // TODO: 팀 전체 blocker_keywords 집계 후 빈도순 정렬
        throw new UnsupportedOperationException("BE1 구현 예정");
    }
}
