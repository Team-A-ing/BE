package com.readb.service.talk;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.talk.TalkSession;
import com.readb.dto.talk.ActiveTalkRatioResponse;
import com.readb.dto.talk.TalkRatioEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TalkRatioService {

    // 마지막 청크 이후 이 시간이 지나면 '진행 중 아님'으로 간주 (청크는 5초 주기로 들어옴)
    private static final long ACTIVE_STALE_MS = 15_000;

    private final AudioAnalyzer audioAnalyzer;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final Map<Long, TalkSession> sessions = new ConcurrentHashMap<>();

    // 가장 최근에 청크가 들어온(=현재 진행 중인) 세션 — IoT 디바이스가 미팅 ID 없이 폴링하기 위함
    private volatile Long activeMeetingId;
    private volatile long activeUpdatedAtMs;

    public void calibrateLeader(Long meetingId, MultipartFile audio) throws IOException {
        TalkSession session = sessions.computeIfAbsent(meetingId, id -> new TalkSession());
        byte[] pcm = audioAnalyzer.toPcm(audio);
        session.setLeaderProfile(audioAnalyzer.extractProfile(pcm));
    }

    public void calibrateMember(Long meetingId, MultipartFile audio) throws IOException {
        TalkSession session = sessions.computeIfAbsent(meetingId, id -> new TalkSession());
        byte[] pcm = audioAnalyzer.toPcm(audio);
        session.setMemberProfile(audioAnalyzer.extractProfile(pcm));
    }

    public void processChunk(Long meetingId, MultipartFile audio) throws IOException {
        TalkSession session = sessions.get(meetingId);
        if (session == null || !session.isCalibrated()) {
            throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
        }
        byte[] pcm = audioAnalyzer.toPcm(audio);
        long[] result = audioAnalyzer.classifyChunk(pcm, session.getLeaderProfile(), session.getMemberProfile());
        session.addLeaderMs(result[0]);
        session.addMemberMs(result[1]);
        sseEmitterRegistry.push(meetingId, TalkRatioEvent.of(session.leaderRatio()));

        // 현재 진행 중인 세션 갱신 (IoT 디바이스 폴링용)
        activeMeetingId = meetingId;
        activeUpdatedAtMs = System.currentTimeMillis();
    }

    public void endSession(Long meetingId) {
        sessions.remove(meetingId);
        sseEmitterRegistry.complete(meetingId);
        if (Objects.equals(meetingId, activeMeetingId)) {
            activeMeetingId = null;
        }
    }

    public TalkSession getSession(Long meetingId) {
        return sessions.get(meetingId);
    }

    /** 미팅 ID 없이 현재 진행 중인 세션의 발화 비율 — ESP32 등 IoT 디바이스 폴링용. */
    public ActiveTalkRatioResponse getActiveRatio() {
        Long id = activeMeetingId;
        if (id == null || System.currentTimeMillis() - activeUpdatedAtMs > ACTIVE_STALE_MS) {
            return ActiveTalkRatioResponse.inactive();
        }
        TalkSession session = sessions.get(id);
        if (session == null || !session.isCalibrated()) {
            return ActiveTalkRatioResponse.inactive();
        }
        return ActiveTalkRatioResponse.of(id, session.leaderRatio());
    }
}
