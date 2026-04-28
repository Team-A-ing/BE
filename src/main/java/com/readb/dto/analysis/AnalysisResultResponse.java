package com.readb.dto.analysis;

import java.util.List;
import java.util.Map;

public record AnalysisResultResponse(
        Long meetingId,
        Double gapScore,
        Double surfaceScore,
        Double inferredScore,
        List<String> blockerKeywords,
        Map<String, Object> leaderFeedback,
        Map<String, Object> memberFeedback,
        List<String> careerTags
) {}
