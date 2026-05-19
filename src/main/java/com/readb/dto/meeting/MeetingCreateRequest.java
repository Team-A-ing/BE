package com.readb.dto.meeting;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record MeetingCreateRequest(
        @NotNull Long teamId,
        @NotNull Long memberId,
        @Size(max = 100) String title,
        LocalDateTime scheduledAt
) {}
