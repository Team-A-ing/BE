package com.readb.dto.team;

import java.util.List;

public record TeamDashboardResponse(
        Long teamId,
        Double teamHealthScore,
        String trend,
        List<String> alerts
) {}
