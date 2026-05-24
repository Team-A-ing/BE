package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.analysis.CareerMemoryResponse;
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
}
