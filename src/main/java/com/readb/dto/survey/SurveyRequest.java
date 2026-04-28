package com.readb.dto.survey;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SurveyRequest(
        @NotNull Long meetingId,
        @NotNull Map<String, Object> scores
) {}
