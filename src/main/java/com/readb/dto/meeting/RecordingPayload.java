package com.readb.dto.meeting;

public record RecordingPayload(
        byte[] bytes,
        String originalFilename,
        String contentType,
        Integer durationSec
) {}
