package com.readb.service.talk;

import com.readb.domain.talk.SpeakerProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AudioAnalyzer {

    private static final int SAMPLE_RATE = 16000;
    private static final int WINDOW_MS = 50;
    private static final int WINDOW_SAMPLES = SAMPLE_RATE * WINDOW_MS / 1000; // 800
    private static final double SILENCE_THRESHOLD = 0.01;

    public byte[] toPcm(MultipartFile file) throws IOException {
        Path input = Files.createTempFile("talk_", ".webm");
        Path output = Files.createTempFile("talk_", ".raw");
        try {
            file.transferTo(input.toFile());
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-i", input.toString(),
                "-f", "s16le", "-ar", "16000", "-ac", "1",
                output.toString()
            );
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            boolean finished = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IOException("FFmpeg timed out after 60 seconds");
            }
            if (p.exitValue() != 0) {
                throw new IOException("FFmpeg failed with exit code " + p.exitValue());
            }
            return Files.readAllBytes(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg interrupted", e);
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    public SpeakerProfile extractProfile(byte[] pcm) {
        List<Double> rmsList = windowRms(pcm);
        if (rmsList.isEmpty()) return new SpeakerProfile(0.0, 0.0);
        double mean = rmsList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = rmsList.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0.0);
        return new SpeakerProfile(mean, Math.sqrt(variance));
    }

    // returns [leaderMs, memberMs]
    public long[] classifyChunk(byte[] pcm, SpeakerProfile leader, SpeakerProfile member) {
        long leaderMs = 0, memberMs = 0;
        List<Double> rmsList = windowRms(pcm);
        for (double rms : rmsList) {
            if (rms < SILENCE_THRESHOLD) continue;
            if (leader.distance(rms) <= member.distance(rms)) {
                leaderMs += WINDOW_MS;
            } else {
                memberMs += WINDOW_MS;
            }
        }
        return new long[]{leaderMs, memberMs};
    }

    private List<Double> windowRms(byte[] pcm) {
        List<Double> result = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int totalSamples = pcm.length / 2;
        int offset = 0;
        while (offset + WINDOW_SAMPLES <= totalSamples) {
            double sumSq = 0;
            for (int i = 0; i < WINDOW_SAMPLES; i++) {
                double sample = buf.getShort((offset + i) * 2) / 32768.0;
                sumSq += sample * sample;
            }
            result.add(Math.sqrt(sumSq / WINDOW_SAMPLES));
            offset += WINDOW_SAMPLES;
        }
        return result;
    }
}
