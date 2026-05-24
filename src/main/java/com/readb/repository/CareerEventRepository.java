package com.readb.repository;

import com.readb.domain.career.CareerEvent;
import com.readb.domain.career.CareerEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerEventRepository extends JpaRepository<CareerEvent, Long> {

    List<CareerEvent> findByUserIdOrderByOccurredAtDesc(Long userId);

    List<CareerEvent> findByMeetingId(Long meetingId);

    List<CareerEvent> findByMeetingIdAndEventTypeIn(Long meetingId, List<CareerEventType> types);
}
