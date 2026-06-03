package com.readb.dto.meeting;

import java.util.List;

public record PreBriefingResponse(
        Long meetingId,
        int round,
        String memberName,
        String memberJobTitle,
        String scheduledAt,
        SurveyBrief survey,
        LastMeetingSummary lastMeeting,
        List<PendingPromise> pendingPromises,
        List<String> recommendedTopics
) {
    public record SurveyBrief(
            boolean submitted,
            Integer energyLevel,
            List<String> issues,
            List<String> desiredRoles,
            Double surveyScore
    ) {}

    public record LastMeetingSummary(
            Double safetyScore,
            Double safetyScoreChange,
            String quadrant,
            HonestyGapBrief honestyGap,
            List<String> speechActAlerts,
            List<String> blockerKeywords
    ) {}

    public record HonestyGapBrief(String direction, String riskLevel) {}

    public record PendingPromise(
            Long promiseId,
            String content,
            String dueDate,
            boolean overdue
    ) {}
}
