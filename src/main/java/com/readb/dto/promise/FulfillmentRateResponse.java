package com.readb.dto.promise;

public record FulfillmentRateResponse(
        int total,
        int doneCount,
        int missedCount,
        int pendingCount,
        double doneRate,
        double missedRate,
        double pendingRate
) {}
