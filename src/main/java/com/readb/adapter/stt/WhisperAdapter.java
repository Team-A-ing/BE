package com.readb.adapter.stt;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;

@Slf4j
@Component
@Profile("!mock")
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
        validate(audioFile);

        try {
            String originalFilename = StringUtils.hasText(audioFile.getOriginalFilename())
                    ? audioFile.getOriginalFilename()
                    : "recording.webm";
            String contentType = resolveContentType(audioFile.getContentType(), originalFilename);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            }).contentType(MediaType.parseMediaType(contentType));
            builder.part("model", model);
            builder.part("language", "ko");
            builder.part("response_format", "verbose_json");
            // timestamp_granularities=segment 로 설정하면 구문 단위 타임스탬프 획득 가능 (옵션)
            builder.part("timestamp_granularities[]", "segment");

            String transcript = webClient.post()
                    .uri(whisperUrl)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> {
                                log.error("Whisper API error. status={}, body={}", response.statusCode(), body);
                                return new BusinessException(ErrorCode.ANALYSIS_FAILED);
                            }))
                    .bodyToMono(String.class)
                    .block();

            if (!StringUtils.hasText(transcript)) {
                log.error("Whisper API returned an empty transcript. file={}", originalFilename);
                throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
            }

            log.info("Whisper transcription completed. file={}, transcriptLength={}",
                    originalFilename, transcript.length());
            return transcript.trim();
        } catch (IOException e) {
            log.error("Failed to read audio file for Whisper. file={}", audioFile.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (WebClientResponseException e) {
            log.error("Whisper API request failed. status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected Whisper transcription failure. file={}", audioFile.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
        }
    }

    private String resolveContentType(String declared, String filename) {
        if (StringUtils.hasText(declared)
                && !declared.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                && (declared.startsWith("audio/") || declared.startsWith("video/"))) {
            return declared;
        }
        String lower = filename != null ? filename.toLowerCase() : "";
        if (lower.endsWith(".mp3"))  return "audio/mpeg";
        if (lower.endsWith(".mp4"))  return "video/mp4";
        if (lower.endsWith(".m4a"))  return "audio/mp4";
        if (lower.endsWith(".wav"))  return "audio/wav";
        if (lower.endsWith(".webm")) return "audio/webm";
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".aac"))  return "audio/aac";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        long maxBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        boolean knownAudioVideoType = StringUtils.hasText(contentType)
                && (contentType.startsWith("audio/") || contentType.startsWith("video/"));
        boolean validExtension = filename.endsWith(".mp3") || filename.endsWith(".mp4")
                || filename.endsWith(".m4a") || filename.endsWith(".wav")
                || filename.endsWith(".webm") || filename.endsWith(".ogg")
                || filename.endsWith(".flac") || filename.endsWith(".aac");
        boolean unknownType = !StringUtils.hasText(contentType)
                || contentType.equals("application/octet-stream");
        if (!knownAudioVideoType && !(unknownType && validExtension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
    }
}
