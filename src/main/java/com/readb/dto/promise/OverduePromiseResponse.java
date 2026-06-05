package com.readb.dto.promise;
public record OverduePromiseResponse (
            Long promiseId,
            String content,
            String category,
            String deadline,
            String status,
            int fromMeetingRound,
            String memberName
){}






