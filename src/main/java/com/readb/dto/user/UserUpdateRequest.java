package com.readb.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UserUpdateRequest(
        @NotBlank String name
) {}
