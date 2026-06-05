package com.readb.service.talk;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.talk.TalkSession;
import com.readb.dto.talk.TalkRatioEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TalkRatioService {

    private final AudioAnalyzer audioAnalyzer;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final Map<Long, TalkSession> sessions = new ConcurrentHashMap<>();

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
    }

    public void endSession(Long meetingId) {
        sessions.remove(meetingId);
        sseEmitterRegistry.complete(meetingId);
    }

    public TalkSession getSession(Long meetingId) {
        return sessions.get(meetingId);
    }
}
