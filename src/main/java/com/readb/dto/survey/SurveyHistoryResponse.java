package com.readb.dto.survey;

import java.time.LocalDateTime;
import java.util.Map;

public record SurveyHistoryResponse(
        Long meetingId,
        LocalDateTime submittedAt,
        Map<String, Object> scores
) {}
