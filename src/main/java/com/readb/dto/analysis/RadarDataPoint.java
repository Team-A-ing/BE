package com.readb.dto.analysis;

// X축: 서베이 점수, Y축: Safety Score (Speech Act 기반)
// honestyGap = surveyScore - safetyScore (Fact-Based Gap)
public record RadarDataPoint(
        Long memberId,
        String memberName,
        Double surveyScore,
        Double safetyScore,
        Double honestyGap
) {}
