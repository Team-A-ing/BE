package com.readb.dto.survey;

import com.readb.domain.survey.Survey;

import java.util.Map;

public record SurveyResponse(
        Long id,
        Long meetingId,
        Long memberId,
        Map<String, Object> scores
) {
    public static SurveyResponse from(Survey survey) {
        return new SurveyResponse(
                survey.getId(),
                survey.getMeetingId(),
                survey.getMemberId(),
                survey.getScores()
        );
    }
}
