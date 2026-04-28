package com.readb.dto.analysis;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CareerMemoryResponse(
        Long meetingId,
        LocalDateTime meetingDate,
        List<String> careerTags,
        Map<String, Object> memberFeedback
) {}
