package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.analysis.CareerMemoryResponse;
import com.readb.dto.analysis.CareerStatsResponse;
import com.readb.dto.analysis.CareerTimelineResponse;
import com.readb.dto.analysis.MemberInsightResponse;
import com.readb.dto.analysis.PortfolioResponse;
import com.readb.dto.analysis.SpeechTrendResponse;
import com.readb.service.analysis.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MemberController {

    private final AnalysisService analysisService;

    @GetMapping("/career-memory")
    public ApiResponse<Page<CareerMemoryResponse>> getCareerMemory(
            @AuthenticationPrincipal Long memberId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(analysisService.getCareerMemory(memberId, pageable));
    }

    @GetMapping("/members/me/speech-trend")
    public ApiResponse<Page<SpeechTrendResponse>> getSpeechTrend(
            @AuthenticationPrincipal Long memberId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(analysisService.getSpeechTrend(memberId, pageable));
    }

    @GetMapping("/members/me/portfolio")
    public ApiResponse<PortfolioResponse> getPortfolio(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(analysisService.getPortfolio(memberId));
    }

    // ── 11절: 본인 또는 본인 팀 리더 조회 가능 ────────────────────────────

    @GetMapping("/members/{memberId}/career-stats")
    public ApiResponse<CareerStatsResponse> getCareerStats(
            @PathVariable Long memberId,
            @AuthenticationPrincipal Long requesterId) {
        return ApiResponse.ok(analysisService.getCareerStats(requesterId, memberId));
    }

    @GetMapping("/members/{memberId}/career-timeline")
    public ApiResponse<List<CareerTimelineResponse>> getCareerTimeline(
            @PathVariable Long memberId,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal Long requesterId) {
        return ApiResponse.ok(analysisService.getCareerTimeline(requesterId, memberId, type));
    }

    @GetMapping("/members/{memberId}/career-showcase")
    public ApiResponse<List<CareerTimelineResponse>> getCareerShowcase(
            @PathVariable Long memberId,
            @AuthenticationPrincipal Long requesterId) {
        return ApiResponse.ok(analysisService.getCareerShowcase(requesterId, memberId));
    }

    @GetMapping("/members/{memberId}/insight")
    public ApiResponse<MemberInsightResponse> getMemberInsight(
            @PathVariable Long memberId,
            @AuthenticationPrincipal Long requesterId) {
        return ApiResponse.ok(analysisService.getMemberInsight(requesterId, memberId));
    }
}
