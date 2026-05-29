package com.readb.controller;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.common.response.ApiResponse;
import com.readb.domain.actionplan.ActionPlan;
import com.readb.dto.analysis.AnalysisResultResponse;
import com.readb.dto.meeting.MeetingCreateRequest;
import com.readb.dto.meeting.MeetingCreateResponse;
import com.readb.dto.meeting.MeetingDetailResponse;
import com.readb.dto.meeting.MeetingListResponse;
import com.readb.dto.meeting.MeetingStatusResponse;
import com.readb.dto.meeting.MemberReportResponse;
import com.readb.repository.ActionPlanRepository;
import com.readb.service.analysis.AnalysisService;
import com.readb.service.meeting.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final AnalysisService analysisService;
    private final ActionPlanRepository actionPlanRepository;

    @GetMapping
    public ApiResponse<List<MeetingListResponse>> getMeetings(
            @RequestParam(required = false) Long memberId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(meetingService.getMeetings(memberId, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MeetingCreateResponse> createMeeting(
            @AuthenticationPrincipal Long leaderId,
            @Valid @RequestBody MeetingCreateRequest request) {
        return ApiResponse.ok(meetingService.createMeeting(leaderId, request));
    }

    // 202 Accepted — 비동기 분석 시작, 클라이언트는 /status 폴링
    @PostMapping(value = "/{meetingId}/recording", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> uploadRecording(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long leaderId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "durationSec", required = false) Integer durationSec) {
        meetingService.uploadRecording(meetingId, leaderId, file, durationSec);
        return ApiResponse.ok();
    }

    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingDetailResponse> getMeeting(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(meetingService.getMeeting(meetingId, userId));
    }

    @GetMapping("/{meetingId}/status")
    public ApiResponse<MeetingStatusResponse> getStatus(@PathVariable Long meetingId) {
        return ApiResponse.ok(meetingService.getStatus(meetingId));
    }

    @GetMapping("/{meetingId}/leader-report")
    public ApiResponse<AnalysisResultResponse> getLeaderReport(@PathVariable Long meetingId) {
        return ApiResponse.ok(analysisService.getResult(meetingId));
    }

    @GetMapping("/{meetingId}/member-report")
    public ApiResponse<MemberReportResponse> getMemberReport(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(meetingService.getMemberReport(meetingId, userId));
    }

    @PatchMapping("/action-plans/{planId}/complete")
    public ApiResponse<Void> completeActionPlan(
            @PathVariable Long planId,
            @AuthenticationPrincipal Long leaderId) {
        ActionPlan plan = actionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!plan.getLeaderId().equals(leaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        plan.complete();
        actionPlanRepository.save(plan);
        return ApiResponse.ok();
    }

    // 개발/테스트 전용 — transcript를 직접 주입해서 분석 파이프라인 트리거
    @PostMapping("/{meetingId}/analyze-test")
    public ApiResponse<Void> analyzeTest(
            @PathVariable Long meetingId,
            @RequestBody java.util.Map<String, String> body) {
        analysisService.analyze(meetingId, body.get("transcript"));
        return ApiResponse.ok();
    }
}
