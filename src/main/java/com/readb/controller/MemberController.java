package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.analysis.AnalysisResultResponse;
import com.readb.dto.analysis.CareerMemoryResponse;
import com.readb.service.analysis.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final AnalysisService analysisService;

    // Career Memory — 개인 성장 타임라인
    @GetMapping("/career-memory")
    public ApiResponse<List<CareerMemoryResponse>> getCareerMemory(
            @AuthenticationPrincipal Long memberId) {
        // TODO: BE1 구현 — 해당 멤버의 미팅별 careerTags 타임라인 조회
        throw new UnsupportedOperationException("BE1 구현 예정");
    }

    // 코칭 피드백 카드
    @GetMapping("/feedback")
    public ApiResponse<AnalysisResultResponse> getFeedback(
            @AuthenticationPrincipal Long memberId,
            @RequestParam Long meetingId) {
        return ApiResponse.ok(analysisService.getResult(meetingId));
    }
}
