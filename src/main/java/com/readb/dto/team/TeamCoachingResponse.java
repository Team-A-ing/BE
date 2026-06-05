package com.readb.dto.team;

import java.util.List;

public record TeamCoachingResponse(
        String overallAssessment,
        List<Insight> insights,
        List<String> suggestedActions
) {
    public record Insight(
            String type,
            String content,
            Long relatedMemberId,
            String relatedMemberName
    ) {}
}
