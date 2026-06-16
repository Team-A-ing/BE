package com.readb.dto.talk;

/**
 * IoT 디바이스(ESP32) 폴링용 — 미팅 ID 없이 '현재 진행 중인' 세션의 발화 비율.
 * 진행 중인 세션이 없거나(또는 일정 시간 청크가 끊기면) active=false, 비율 0.
 */
public record ActiveTalkRatioResponse(
        boolean active,
        Long meetingId,
        double leaderRatio,
        double memberRatio
) {
    public static ActiveTalkRatioResponse inactive() {
        return new ActiveTalkRatioResponse(false, null, 0.0, 0.0);
    }

    public static ActiveTalkRatioResponse of(Long meetingId, double leaderRatio) {
        double member = Math.round((100.0 - leaderRatio) * 10.0) / 10.0;
        return new ActiveTalkRatioResponse(true, meetingId, leaderRatio, member);
    }
}
