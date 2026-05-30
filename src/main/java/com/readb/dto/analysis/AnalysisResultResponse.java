package com.readb.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AnalysisResultResponse(
        Long meetingId,
        int round,
        String memberName,
        String memberJobTitle,
        String meetingDate,
        Integer durationSec,
        GapsResponse gaps,
        Double safetyScore,
        SpeechActsResponse speechActs,
        TalkRatioResponse talkRatio,
        List<FeedbackItem> feedbacks,
        List<ActionPlanItem> nextActionPlans,
        PromisesResponse promises
) {
    public record GapsResponse(
            AlignmentGapDetail alignmentGap,
            HonestyGapDetail honestyGap,
            ExecutionGapDetail executionGap
    ) {}

    public record AlignmentGapDetail(Double score, String detail) {}

    public record HonestyGapDetail(
            Double surveyScore,
            Double safetyScore,
            Double gap,
            String direction,
            String riskLevel
    ) {}

    public record ExecutionGapDetail(
            Double score,
            int totalPromises,
            int fulfilled,
            int missed
    ) {}

    public record SpeechActsResponse(
            SpeechActDetail vulnerability,
            SpeechActDetail constructiveDissent,
            SpeechActDetail initiative
    ) {}

    public record SpeechActDetail(
            int count,
            Double baselineAvg,
            Integer changeRate,
            List<SpeechActInstance> instances
    ) {}

    public record SpeechActInstance(String text, String timestamp) {}

    public record TalkRatioResponse(
            int leaderRatio,
            int memberRatio,
            int recommendedLeaderRatio
    ) {}

    public record FeedbackItem(
            int feedbackId,
            String severity,
            String title,
            String evidenceQuote,
            String dataSummary,
            String actionGuide
    ) {}

    public record ActionPlanItem(Long planId, String content, boolean isCompleted) {}

    public record PromisesResponse(
            List<PreviousPromise> previous,
            @JsonProperty("new") List<NewPromise> newPromises
    ) {}

    public record PreviousPromise(Long promiseId, String content, String status) {}

    public record NewPromise(
            Long promiseId,
            String content,
            String category,
            String dueDate,
            String status
    ) {}
}
