package com.readb.domain.meeting;

public enum MeetingStatus {
    PENDING,      // 미팅 생성됨, 녹음 전
    RECORDING,    // 녹음 중
    ANALYZING,    // AI 분석 중
    COMPLETED,    // 분석 완료
    FAILED        // 분석 실패
}
