package com.readb.repository;

import com.readb.domain.career.CareerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerEventRepository extends JpaRepository<CareerEvent, Long> {

    List<CareerEvent> findByUserIdOrderByOccurredAtDesc(Long userId);

    List<CareerEvent> findByMeetingId(Long meetingId);
}
