package com.readb.dto.meeting;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record MeetingCreateRequest(
        @NotNull Long teamId,
        @NotNull Long memberId,
        LocalDateTime scheduledAt
) {}
