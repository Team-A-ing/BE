package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.analysis.AnalysisResultResponse;
import com.readb.dto.meeting.MeetingCreateRequest;
import com.readb.dto.meeting.PreBriefingResponse;
import com.readb.dto.meeting.MeetingCreateResponse;
import com.readb.dto.meeting.MeetingDetailResponse;
import com.readb.dto.meeting.MeetingListResponse;
import com.readb.dto.meeting.MeetingStatusResponse;
import com.readb.dto.meeting.MemberReportResponse;
import com.readb.service.analysis.AnalysisService;
import com.readb.service.meeting.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    @PostMapping(value = "/{meetingId}/recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    // 미팅 취소(진행 전 미팅 삭제). 리더만 가능.
    @DeleteMapping("/{meetingId}")
    public ApiResponse<Void> cancelMeeting(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long leaderId) {
        meetingService.cancelMeeting(meetingId, leaderId);
        return ApiResponse.ok();
    }

    @GetMapping("/{meetingId}/status")
    public ApiResponse<MeetingStatusResponse> getStatus(@PathVariable Long meetingId) {
        return ApiResponse.ok(meetingService.getStatus(meetingId));
    }

    @GetMapping("/{meetingId}/pre-briefing")
    public ApiResponse<PreBriefingResponse> getPreBriefing(@PathVariable Long meetingId) {
        return ApiResponse.ok(analysisService.getPreBriefing(meetingId));
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

    // 개발/테스트 전용 — transcript를 직접 주입해서 분석 파이프라인 트리거
    @PostMapping("/{meetingId}/analyze-test")
    public ApiResponse<Void> analyzeTest(
            @PathVariable Long meetingId,
            @RequestBody java.util.Map<String, String> body) {
        analysisService.analyze(meetingId, body.get("transcript"));
        return ApiResponse.ok();
    }
}
