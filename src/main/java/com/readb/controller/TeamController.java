package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.team.TeamMemberResponse;
import com.readb.service.team.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping("/{teamId}/members")
    public ApiResponse<List<TeamMemberResponse>> getTeamMembers(@PathVariable Long teamId) {
        return ApiResponse.ok(teamService.getTeamMembers(teamId));
    }
}
