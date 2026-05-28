package com.readb.dto.analysis;

public enum HonestyDirection {
    OVERREPORT,   // surveyScore > safetyScore
    UNDERREPORT,  // surveyScore < safetyScore
    NEUTRAL
}
