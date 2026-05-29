package com.readb.dto.analysis;

import java.time.LocalDate;

public record CareerTimelineResponse(
        Long careerEventId,
        String type,
        String title,
        String description,
        String impactMetric,
        LocalDate eventDate,
        int meetingRound
) {}
