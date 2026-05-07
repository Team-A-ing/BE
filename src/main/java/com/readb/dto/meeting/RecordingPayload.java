package com.readb.dto.meeting;

// MultipartFile에서 동기적으로 추출한 byte[] + 메타데이터.
// 비동기 분석 스레드로 안전하게 전달하기 위함.
public record RecordingPayload(
        byte[] bytes,
        String originalFilename,
        String contentType
) {}
