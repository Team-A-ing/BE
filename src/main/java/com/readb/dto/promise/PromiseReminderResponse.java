package com.readb.dto.promise;

import java.util.List;

public record PromiseReminderResponse(
        List<ReminderItem> overdue,
        List<ReminderItem> dueSoon
) {
    public record ReminderItem(
            Long promiseId,
            String content,
            String dueDate,
            long daysLeft,
            String memberName,
            String meetingTitle
    ) {}
}
