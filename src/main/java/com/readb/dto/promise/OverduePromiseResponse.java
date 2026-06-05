package com.readb.dto.promise;
public record OverduePromiseResponse (
            int promiseId,
            String content,
            String category,
            String dueDate,
            String status,
            int fromMeetingRound,
            String memberName
){}






