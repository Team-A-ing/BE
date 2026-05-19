package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.analysis.BlockerKeyword;
import com.readb.dto.analysis.RadarDataPoint;
import com.readb.dto.team.TeamCreateRequest;
import com.readb.dto.team.TeamCreateResponse;
import com.readb.dto.team.TeamDashboardResponse;
import com.readb.dto.team.TeamMemberResponse;
import com.readb.service.analysis.AnalysisService;
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
    private final AnalysisService analysisService;

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

    @GetMapping("/{teamId}/dashboard")
    public ApiResponse<TeamDashboardResponse> getDashboard(@PathVariable Long teamId) {
        return ApiResponse.ok(analysisService.getTeamDashboard(teamId));
    }

    @GetMapping("/{teamId}/quadrant")
    public ApiResponse<List<RadarDataPoint>> getQuadrant(@PathVariable Long teamId) {
        return ApiResponse.ok(analysisService.getRadarData(teamId));
    }

    @GetMapping("/{teamId}/blocker-pyramid")
    public ApiResponse<List<BlockerKeyword>> getBlockerPyramid(@PathVariable Long teamId) {
        return ApiResponse.ok(analysisService.getBlockerData(teamId));
    }
}
