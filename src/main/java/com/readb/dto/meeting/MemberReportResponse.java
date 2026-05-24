package com.readb.dto.meeting;

import java.time.LocalDateTime;
import java.util.List;

public record MemberReportResponse(
        Long meetingId,
        int round,
        String leaderName,
        LocalDateTime meetingDate,
        Integer durationSec,
        List<ConfirmedAchievementResponse> confirmedAchievements,
        List<LeaderPromiseResponse> leaderPromises
) {}
