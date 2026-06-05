package com.readb.service.talk;

import com.readb.common.exception.BusinessException;
import com.readb.domain.talk.SpeakerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TalkRatioServiceTest {

    @Mock AudioAnalyzer audioAnalyzer;
    @Mock SseEmitterRegistry sseEmitterRegistry;
    @Mock MultipartFile mockFile;

    TalkRatioService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TalkRatioService(audioAnalyzer, sseEmitterRegistry);
    }

    @Test
    void calibrateLeader_storesLeaderProfile() throws IOException {
        byte[] pcm = new byte[1600];
        SpeakerProfile profile = new SpeakerProfile(0.6, 0.05);
        when(audioAnalyzer.toPcm(mockFile)).thenReturn(pcm);
        when(audioAnalyzer.extractProfile(pcm)).thenReturn(profile);

        service.calibrateLeader(1L, mockFile);

        assertThat(service.getSession(1L).getLeaderProfile()).isEqualTo(profile);
    }

    @Test
    void calibrateMember_storesMemberProfile() throws IOException {
        byte[] pcm = new byte[1600];
        SpeakerProfile profile = new SpeakerProfile(0.3, 0.04);
        when(audioAnalyzer.toPcm(mockFile)).thenReturn(pcm);
        when(audioAnalyzer.extractProfile(pcm)).thenReturn(profile);

        service.calibrateMember(1L, mockFile);

        assertThat(service.getSession(1L).getMemberProfile()).isEqualTo(profile);
    }

    @Test
    void processChunk_notCalibrated_throwsException() {
        assertThatThrownBy(() -> service.processChunk(99L, mockFile))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void processChunk_calibrated_pushesRatio() throws IOException {
        SpeakerProfile leaderProfile = new SpeakerProfile(0.6, 0.05);
        SpeakerProfile memberProfile = new SpeakerProfile(0.2, 0.05);
        byte[] calibPcm = new byte[1600];
        when(audioAnalyzer.toPcm(mockFile)).thenReturn(calibPcm);
        when(audioAnalyzer.extractProfile(calibPcm)).thenReturn(leaderProfile, memberProfile);
        service.calibrateLeader(1L, mockFile);
        service.calibrateMember(1L, mockFile);

        byte[] chunkPcm = new byte[16000 * 10 * 2];
        when(audioAnalyzer.toPcm(mockFile)).thenReturn(chunkPcm);
        when(audioAnalyzer.classifyChunk(chunkPcm, leaderProfile, memberProfile))
                .thenReturn(new long[]{7000L, 3000L});

        service.processChunk(1L, mockFile);

        verify(sseEmitterRegistry).push(eq(1L), argThat(e -> e.leaderRatio() == 70.0));
    }
}
