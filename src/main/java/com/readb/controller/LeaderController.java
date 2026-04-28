package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.domain.promise.Promise;
import com.readb.dto.analysis.BlockerKeyword;
import com.readb.dto.analysis.RadarDataPoint;
import com.readb.service.analysis.AnalysisService;
import com.readb.service.analysis.PromiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leader")
@RequiredArgsConstructor
public class LeaderController {

    private final AnalysisService analysisService;
    private final PromiseService promiseService;

    // Silent Risk Radar — X: surfaceScore, Y: inferredScore
    @GetMapping("/radar")
    public ApiResponse<List<RadarDataPoint>> getRadar(@RequestParam Long teamId) {
        return ApiResponse.ok(analysisService.getRadarData(teamId));
    }

    // Blocker Cloud — 팀 전체 blocker_keywords 빈도 집계
    @GetMapping("/blockers")
    public ApiResponse<List<BlockerKeyword>> getBlockers(@RequestParam Long teamId) {
        return ApiResponse.ok(analysisService.getBlockerData(teamId));
    }

    // Promise Ledger
    @GetMapping("/promises")
    public ApiResponse<List<Promise>> getPromises(@AuthenticationPrincipal Long leaderId) {
        return ApiResponse.ok(promiseService.getPromisesByLeader(leaderId));
    }
}
