package com.readb.dto.auth;

public record LoginResponse(
        String token,
        Long userId,
        String name,
        String role
) {}
