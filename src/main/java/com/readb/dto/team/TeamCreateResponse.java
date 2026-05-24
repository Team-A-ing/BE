package com.readb.dto.team;

import com.readb.domain.team.Team;

import java.time.LocalDateTime;

public record TeamCreateResponse(
        Long id,
        String name,
        Long leaderId,
        String inviteCode,
        LocalDateTime createdAt
) {
    public static TeamCreateResponse from(Team team) {
        return new TeamCreateResponse(
                team.getId(),
                team.getName(),
                team.getLeaderId(),
                team.getInviteCode(),
                team.getCreatedAt()
        );
    }
}
