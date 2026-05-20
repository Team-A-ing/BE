package com.readb.dto.team;

import jakarta.validation.constraints.NotBlank;

public record TeamJoinRequest(
        @NotBlank String inviteCode
) {}
