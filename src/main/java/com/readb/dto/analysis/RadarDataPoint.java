package com.readb.dto.analysis;

public record RadarDataPoint(
        Long memberId,
        String memberName,
        Double surfaceScore,
        Double inferredScore,
        Double gapScore
) {}
