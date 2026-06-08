package com.readb.dto.team;

import java.util.List;

public record TeamPromiseSummaryResponse(List<MemberPromiseSummary> memberPromises) {

    public record MemberPromiseSummary(
            String memberId,
            String memberName,
            List<PromiseSummaryItem> promises,
            Stats stats
    ) {}

    public record PromiseSummaryItem(
            String promiseId,
            String content,
            String context,
            String status,
            String createdAt,
            int round,
            boolean isCompleted
    ) {}

    public record Stats(int total, int completed, int pending, int overdue) {}
}
