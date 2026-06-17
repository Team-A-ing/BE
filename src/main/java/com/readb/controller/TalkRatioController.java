package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.domain.talk.TalkSession;
import com.readb.dto.talk.TalkRatioEvent;
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

    /**
     * ESP32 IoT 디바이스 폴링용 엔드포인트.
     * 현재 진행 중인 미팅의 실시간 발화 비율을 반환한다.
     */
    @GetMapping("/talk-ratio/current")
    public ApiResponse<TalkRatioEvent> currentRatio(@PathVariable Long meetingId) {
        TalkSession session = talkRatioService.getSession(meetingId);
        if (session == null || !session.isCalibrated()) {
            return ApiResponse.ok(TalkRatioEvent.of(0.0));
        }
        return ApiResponse.ok(TalkRatioEvent.of(session.leaderRatio()));
    }
}
