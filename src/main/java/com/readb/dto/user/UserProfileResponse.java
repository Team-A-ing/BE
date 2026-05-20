package com.readb.dto.user;

import com.readb.domain.user.User;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String role,
        Long teamId,
        String inviteCode
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                user.getTeamId(),
                null
        );
    }

    public static UserProfileResponse from(User user, String inviteCode) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                user.getTeamId(),
                inviteCode
        );
    }
}
