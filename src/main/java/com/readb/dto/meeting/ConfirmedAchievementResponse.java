package com.readb.dto.meeting;

public record ConfirmedAchievementResponse(
        Long careerEventId,
        String type,
        String title,
        String description,
        String impactMetric,
        String leaderQuote,
        String leaderName
) {}
