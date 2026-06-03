package com.readb.dto.analysis;

public record TalkRatioRankingItem(
        Long memberId,
        String name,
        int leaderRatio,
        int memberRatio,
        String status   // 위험 / 관찰 / 적정
) {}
