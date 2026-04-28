package com.readb.dto.meeting;

import jakarta.validation.constraints.NotNull;

public record MeetingCreateRequest(
        @NotNull Long teamId,
        @NotNull Long memberId
) {}
