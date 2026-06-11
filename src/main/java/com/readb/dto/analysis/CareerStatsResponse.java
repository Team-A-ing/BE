package com.readb.dto.analysis;

public record CareerStatsResponse(
        Long memberId,
        String name,
        String jobTitle,
        String teamName,
        int totalMeetings,
        int achievementCount,
        double promiseFulfillmentRate,
        int completedActionCount,
        String aiSummary
) {}
