package com.readb.adapter.stt;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhisperAdapter implements SttAdapter {

    private final WebClient webClient;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.whisper.url}")
    private String whisperUrl;

    @Value("${openai.whisper.model}")
    private String model;

    @Value("${openai.whisper.max-file-size-mb}")
    private int maxFileSizeMb;

    @Override
    public String transcribe(MultipartFile audioFile) {
        validateFileSize(audioFile);

        try {
            byte[] bytes = audioFile.getBytes();
            String originalFilename = audioFile.getOriginalFilename() != null
                    ? audioFile.getOriginalFilename() : "recording.webm";

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            }).contentType(MediaType.parseMediaType("audio/webm"));
            builder.part("model", model);
            builder.part("language", "ko");
            builder.part("response_format", "text");

            String transcript = webClient.post()
                    .uri(whisperUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Whisper 전사 완료. 텍스트 길이: {}", transcript != null ? transcript.length() : 0);
            return transcript;

        } catch (IOException e) {
            log.error("Whisper 파일 읽기 실패", e);
            throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
        }
    }

    private void validateFileSize(MultipartFile file) {
        long maxBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }
}
