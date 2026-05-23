package com.readb.dto.meeting;

import java.time.LocalDateTime;

public record MeetingListResponse(
        Long meetingId,
        int round,
        String memberName,
        LocalDateTime scheduledAt,
        Integer durationSec,
        String status
) {}
