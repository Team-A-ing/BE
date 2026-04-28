package com.readb.dto.meeting;

public record MeetingStatusResponse(
        Long meetingId,
        String status,
        Integer progressPercent
) {}
