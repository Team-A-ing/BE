package com.readb.service.talk;

import com.readb.domain.talk.SpeakerProfile;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AudioAnalyzerTest {

    private final AudioAnalyzer analyzer = new AudioAnalyzer();

    private byte[] generateSinePcm(double amplitude, int durationMs) {
        int sampleRate = 16000;
        int samples = sampleRate * durationMs / 1000;
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            short val = (short) (amplitude * Short.MAX_VALUE * Math.sin(2 * Math.PI * 440 * i / sampleRate));
            pcm[i * 2] = (byte) (val & 0xFF);
            pcm[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }
        return pcm;
    }

    private byte[] generateSilencePcm(int durationMs) {
        return new byte[16000 * durationMs / 1000 * 2];
    }

    @Test
    void extractProfile_highAmplitude_returnsHighMeanRms() {
        byte[] pcm = generateSinePcm(0.8, 5000);
        SpeakerProfile profile = analyzer.extractProfile(pcm);
        assertThat(profile.meanRms()).isGreaterThan(0.4);
    }

    @Test
    void extractProfile_silence_returnsNearZeroMeanRms() {
        byte[] pcm = generateSilencePcm(5000);
        SpeakerProfile profile = analyzer.extractProfile(pcm);
        assertThat(profile.meanRms()).isLessThan(0.01);
    }

    @Test
    void classifyChunk_leaderLouder_assignsMoreTimeToLeader() {
        SpeakerProfile leaderProfile = new SpeakerProfile(0.6, 0.05);
        SpeakerProfile memberProfile = new SpeakerProfile(0.2, 0.05);
        byte[] chunk = generateSinePcm(0.6, 10000);
        long[] result = analyzer.classifyChunk(chunk, leaderProfile, memberProfile);
        assertThat(result[0]).isGreaterThan(result[1]);
    }
}
