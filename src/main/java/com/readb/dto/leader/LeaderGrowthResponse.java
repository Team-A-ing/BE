package com.readb.dto.leader;

import java.util.List;

public record LeaderGrowthResponse(
        List<MonthlyTrendPoint> talkRatioTrend,
        List<MonthlyTrendPoint> teamSafetyTrend,
        PromiseStats promiseStats,
        List<String> highlights
) {
    public record MonthlyTrendPoint(String month, double value, int meetingCount) {}

    public record PromiseStats(int total, int done, int missed, int pending, double doneRate) {}
}
