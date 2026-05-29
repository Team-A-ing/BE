package com.readb.repository;

import com.readb.domain.career.CareerEvent;
import com.readb.domain.career.CareerEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface CareerEventRepository extends JpaRepository<CareerEvent, Long> {

    List<CareerEvent> findByUserIdOrderByOccurredAtDesc(Long userId);

    List<CareerEvent> findByUserIdAndEventTypeOrderByOccurredAtDesc(Long userId, CareerEventType eventType);

    int countByUserId(Long userId);

    @Query("SELECT e.userId, COUNT(e) FROM CareerEvent e WHERE e.userId IN :userIds GROUP BY e.userId")
    List<Object[]> countGroupByUserIdIn(@Param("userIds") List<Long> userIds);

    default Map<Long, Long> countMapByUserIds(List<Long> userIds) {
        return countGroupByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }

    List<CareerEvent> findByMeetingId(Long meetingId);

    List<CareerEvent> findByMeetingIdAndEventTypeIn(Long meetingId, List<CareerEventType> types);
}
