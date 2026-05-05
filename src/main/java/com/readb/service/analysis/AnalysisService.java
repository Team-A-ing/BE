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

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final MeetingRepository meetingRepository;
    private final LlmAdapter llmAdapter;

    @Transactional
    public void analyze(Long meetingId, String transcript) {
        // TODO: LLM 프롬프트 설계 및 JSON 파싱 구현
        // 파싱 실패 시 1회 재시도, 재실패 시 예외 throw → Orchestrator가 FAILED 처리
        throw new UnsupportedOperationException("BE1 구현 예정");
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
