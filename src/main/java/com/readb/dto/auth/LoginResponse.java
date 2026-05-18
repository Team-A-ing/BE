package com.readb.dto.auth;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        UserInfo user
) {
    public record UserInfo(
            Long id,
            String email,
            String name,
            String role,
            String jobTitle,
            Long teamId
    ) {}
}
