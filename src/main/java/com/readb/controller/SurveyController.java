package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.survey.SurveyHistoryResponse;
import com.readb.dto.survey.SurveyRequest;
import com.readb.dto.survey.SurveyResponse;
import com.readb.service.survey.SurveyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> submitSurvey(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SurveyRequest request) {
        surveyService.submitSurvey(memberId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/history")
    public ApiResponse<List<SurveyHistoryResponse>> getHistory(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(surveyService.getHistory(memberId));
    }

    @GetMapping("/{meetingId}")
    public ApiResponse<SurveyResponse> getSurvey(@PathVariable Long meetingId) {
        return ApiResponse.ok(surveyService.getSurvey(meetingId));
    }
}
