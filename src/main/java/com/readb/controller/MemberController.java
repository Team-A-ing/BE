package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.analysis.CareerMemoryResponse;
import com.readb.dto.analysis.PortfolioResponse;
import com.readb.dto.analysis.SpeechTrendResponse;
import com.readb.service.analysis.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MemberController {

    private final AnalysisService analysisService;

    @GetMapping("/career-memory")
    public ApiResponse<List<CareerMemoryResponse>> getCareerMemory(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(analysisService.getCareerMemory(memberId));
    }

    @GetMapping("/members/me/speech-trend")
    public ApiResponse<List<SpeechTrendResponse>> getSpeechTrend(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(analysisService.getSpeechTrend(memberId));
    }

    @GetMapping("/members/me/portfolio")
    public ApiResponse<PortfolioResponse> getPortfolio(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(analysisService.getPortfolio(memberId));
    }
}
