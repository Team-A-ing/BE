package com.readb.dto.team;

import java.util.List;

public record TeamPromiseSummaryResponse(
        MemberPromiseSummary leaderPromises,
        List<MemberPromiseSummary> memberPromises) {

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
            boolean isCompleted,
            String partnerName // 리더 약속에서만 사용 — 어느 멤버와의 미팅에서 한 약속인지
    ) {}

    public record Stats(int total, int completed, int pending, int overdue) {}
}
