package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.analysis.AnalysisResultResponse;
import com.readb.service.analysis.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/{meetingId}/analysis")
    public ApiResponse<AnalysisResultResponse> getAnalysis(@PathVariable Long meetingId) {
        return ApiResponse.ok(analysisService.getResult(meetingId));
    }
}
