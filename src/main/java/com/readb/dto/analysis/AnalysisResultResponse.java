package com.readb.dto.analysis;

import java.util.List;
import java.util.Map;

public record AnalysisResultResponse(
        Long meetingId,
        Double alignmentGap,
        Double honestyGap,
        Double executionGap,
        Double safetyScore,
        Map<String, Object> speechActs,
        List<String> blockerKeywords,
        Map<String, Object> leaderFeedback,
        Map<String, Object> memberFeedback,
        List<String> careerTags,
        Map<String, Object> baselineData,
        String direction,
        String riskLevel
) {}
