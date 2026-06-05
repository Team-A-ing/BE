package com.readb.service.talk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readb.dto.talk.TalkRatioEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterRegistry {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitter register(Long meetingId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        SseEmitter previous = emitters.put(meetingId, emitter);
        if (previous != null) previous.complete();
        emitter.onCompletion(() -> emitters.remove(meetingId, emitter));
        emitter.onTimeout(() -> emitters.remove(meetingId, emitter));
        return emitter;
    }

    public void push(Long meetingId, TalkRatioEvent event) {
        SseEmitter emitter = emitters.get(meetingId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("talkRatio")
                    .data(objectMapper.writeValueAsString(event)));
        } catch (IOException e) {
            log.warn("SSE push failed for meetingId={}", meetingId);
            emitters.remove(meetingId, emitter);
        }
    }

    public void complete(Long meetingId) {
        SseEmitter emitter = emitters.remove(meetingId);
        if (emitter != null) emitter.complete();
    }
}
