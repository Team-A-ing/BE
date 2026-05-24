package com.readb.dto.analysis;

import java.time.LocalDateTime;
import java.util.List;

public record PortfolioResponse(
        List<MeetingSnapshot> meetingHistory,
        List<ScorePoint> scoreTrend,
        List<String> topCareerTags,
        List<String> feedbackSummaries
) {
    public record MeetingSnapshot(Long meetingId, LocalDateTime scheduledAt, String title) {}
    public record ScorePoint(Long meetingId, LocalDateTime scheduledAt, Double safetyScore) {}
}
