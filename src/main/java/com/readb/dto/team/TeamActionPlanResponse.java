package com.readb.dto.team;

import java.util.List;

public record TeamActionPlanResponse(
        List<MemberActionPlans> memberActionPlans
) {
    public record MemberActionPlans(
            Long memberId,
            String memberName,
            int round,
            List<PlanItem> plans
    ) {}

    public record PlanItem(Long planId, String content) {}
}
