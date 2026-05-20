package com.readb.dto.meeting;

import java.time.LocalDateTime;

public record MeetingDetailResponse(
        Long meetingId,
        int round,
        LocalDateTime scheduledAt,
        Integer durationSec,
        String status,
        String leaderName,
        String memberName
) {}
