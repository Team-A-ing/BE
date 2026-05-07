package com.readb.dto.team;

import jakarta.validation.constraints.NotBlank;

public record TeamCreateRequest(
        @NotBlank String name
) {}
