package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.leader.LeaderGrowthResponse;
import com.readb.service.analysis.LeaderGrowthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaders")
@RequiredArgsConstructor
public class LeaderController {

    private final LeaderGrowthService leaderGrowthService;

    @GetMapping("/me/growth")
    @PreAuthorize("hasRole('LEADER')")
    public ApiResponse<LeaderGrowthResponse> getMyGrowth(@AuthenticationPrincipal Long leaderId) {
        return ApiResponse.ok(leaderGrowthService.getLeaderGrowth(leaderId));
    }
}
