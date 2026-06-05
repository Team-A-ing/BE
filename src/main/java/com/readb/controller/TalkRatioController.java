package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.service.talk.SseEmitterRegistry;
import com.readb.service.talk.TalkRatioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}")
@RequiredArgsConstructor
public class TalkRatioController {

    private final TalkRatioService talkRatioService;
    private final SseEmitterRegistry sseEmitterRegistry;

    @PostMapping("/calibrate/leader")
    public ApiResponse<Void> calibrateLeader(
            @PathVariable Long meetingId,
            @RequestParam("audio") MultipartFile audio) throws IOException {
        talkRatioService.calibrateLeader(meetingId, audio);
        return ApiResponse.ok();
    }

    @PostMapping("/calibrate/member")
    public ApiResponse<Void> calibrateMember(
            @PathVariable Long meetingId,
            @RequestParam("audio") MultipartFile audio) throws IOException {
        talkRatioService.calibrateMember(meetingId, audio);
        return ApiResponse.ok();
    }

    @PostMapping("/audio-chunk")
    public ApiResponse<Void> processChunk(
            @PathVariable Long meetingId,
            @RequestParam("audio") MultipartFile audio) throws IOException {
        talkRatioService.processChunk(meetingId, audio);
        return ApiResponse.ok();
    }

    @GetMapping(value = "/talk-ratio/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long meetingId) {
        return sseEmitterRegistry.register(meetingId);
    }
}
