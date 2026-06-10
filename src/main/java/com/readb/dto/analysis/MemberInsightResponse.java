package com.readb.dto.analysis;

import java.util.List;

public record MemberInsightResponse(
        Long memberId,
        String memberName,
        String jobTitle,
        List<PromiseItem> promises,
        List<TrendPoint> statusTrend,
        List<ActionPlanItem> actionPlans
) {
    // status: DONE / PENDING / OVERDUE
    public record PromiseItem(Long promiseId, String content, String status, String date, int round) {}

    // level: 좋음 / 보통 / 낮음 (내부 지표 용어 비노출)
    public record TrendPoint(int round, String date, double healthScore, String level) {}

    public record ActionPlanItem(Long planId, String content, boolean isCompleted, String date, int round) {}
}
