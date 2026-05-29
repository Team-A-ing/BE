package com.readb.dto.analysis;

public record CareerStatsResponse(
        Long memberId,
        String name,
        String jobTitle,
        String teamName,
        int totalMeetings,
        int achievementCount,
        int leaderEndorsementCount,
        int contributionPercentile,
        String aiSummary
) {}
