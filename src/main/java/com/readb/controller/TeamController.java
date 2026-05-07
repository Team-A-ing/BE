package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.team.TeamCreateRequest;
import com.readb.dto.team.TeamCreateResponse;
import com.readb.dto.team.TeamMemberResponse;
import com.readb.service.team.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public ApiResponse<TeamCreateResponse> createTeam(
            @AuthenticationPrincipal Long leaderId,
            @Valid @RequestBody TeamCreateRequest request) {
        return ApiResponse.ok(teamService.createTeam(leaderId, request));
    }

    @GetMapping("/{teamId}/members")
    public ApiResponse<List<TeamMemberResponse>> getTeamMembers(@PathVariable Long teamId) {
        return ApiResponse.ok(teamService.getTeamMembers(teamId));
    }
}
