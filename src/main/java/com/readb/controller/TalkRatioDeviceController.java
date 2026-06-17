package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.dto.talk.ActiveTalkRatioResponse;
import com.readb.service.talk.TalkRatioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IoT 디바이스(ESP32) 전용 — 미팅 ID/인증 없이 '현재 진행 중인' 세션의 발화 비율 폴링.
 * 디바이스는 JWT를 보낼 수 없으므로 이 경로는 SecurityConfig에서 permitAll.
 */
@RestController
@RequestMapping("/api/v1/talk-ratio")
@RequiredArgsConstructor
public class TalkRatioDeviceController {

    private final TalkRatioService talkRatioService;

    @GetMapping("/active")
    public ApiResponse<ActiveTalkRatioResponse> active() {
        return ApiResponse.ok(talkRatioService.getActiveRatio());
    }
}
