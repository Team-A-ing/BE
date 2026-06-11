package com.readb.dto.leader;

import java.util.List;

public record LeaderGrowthResponse(
        String keyInsight,
        List<MonthlyTrendPoint> talkRatioTrend,
        List<MonthlyTrendPoint> teamSafetyTrend,
        List<MonthlyTrendPoint> monthlyMeetings,
        PromiseStats promiseStats,
        CoachingExecution coachingExecution,
        List<MemberCadence> memberCadence,
        List<String> highlights
) {
    public record MonthlyTrendPoint(String month, double value, int meetingCount) {}

    public record PromiseStats(int total, int done, int missed, int pending, double doneRate) {}

    public record CoachingExecution(int total, int completed, double executionRate) {}

    public record MemberCadence(Long memberId, String memberName, String lastMeetingDate, Long daysSinceLastMeeting) {}
}
