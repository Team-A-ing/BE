package com.readb.dto.team;

public record TeamMemberResponse(
        Long id,
        String name,
        String email,
        String role
) {}
