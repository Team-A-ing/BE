package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.meeting.MeetingCreateRequest;
import com.readb.dto.meeting.MeetingCreateResponse;
import com.readb.dto.meeting.MeetingStatusResponse;
import com.readb.service.meeting.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MeetingCreateResponse> createMeeting(
            @AuthenticationPrincipal Long leaderId,
            @Valid @RequestBody MeetingCreateRequest request) {
        return ApiResponse.ok(meetingService.createMeeting(leaderId, request));
    }

    // 202 Accepted — 비동기 분석 시작, 클라이언트는 /status 폴링
    @PostMapping("/{meetingId}/recording")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> uploadRecording(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long leaderId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "durationSec", required = false) Integer durationSec) {
        meetingService.uploadRecording(meetingId, leaderId, file, durationSec);
        return ApiResponse.ok();
    }

    @GetMapping("/{meetingId}/status")
    public ApiResponse<MeetingStatusResponse> getStatus(@PathVariable Long meetingId) {
        return ApiResponse.ok(meetingService.getStatus(meetingId));
    }
}
