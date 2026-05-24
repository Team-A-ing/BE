package com.readb.dto.meeting;

import java.time.LocalDate;

public record LeaderPromiseResponse(
        Long promiseId,
        String content,
        String category,
        LocalDate dueDate,
        String status
) {}
