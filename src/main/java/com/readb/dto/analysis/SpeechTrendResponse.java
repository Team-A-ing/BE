package com.readb.dto.analysis;

import java.time.LocalDateTime;

public record SpeechTrendResponse(
        Long meetingId,
        LocalDateTime scheduledAt,
        int vulnerabilityCount,
        int dissentCount,
        int initiativeCount
) {}
