package com.readb.dto.user;

import com.readb.domain.user.User;
import com.readb.domain.team.Team;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String role,
        String jobTitle,
        Long teamId,
        String teamName,
        String inviteCode
) {
    public static UserProfileResponse from(User user, Team team) {
        boolean isLeader = "LEADER".equals(user.getRole().name());
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                user.getJobTitle(),
                user.getTeamId(),
                team != null ? team.getName() : null,
                (isLeader && team != null) ? team.getInviteCode() : null
        );
    }
}
