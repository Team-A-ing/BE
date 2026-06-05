package com.readb.dto.talk;

public record TalkRatioEvent(double leaderRatio, double memberRatio) {
    public static TalkRatioEvent of(double leaderRatio) {
        return new TalkRatioEvent(leaderRatio, Math.round((100.0 - leaderRatio) * 10.0) / 10.0);
    }
}
