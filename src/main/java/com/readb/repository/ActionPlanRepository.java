package com.readb.repository;

import com.readb.domain.actionplan.ActionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActionPlanRepository extends JpaRepository<ActionPlan, Long> {

    List<ActionPlan> findByMeetingIdOrderByIdAsc(Long meetingId);

    List<ActionPlan> findByMeetingIdInOrderByIdAsc(List<Long> meetingIds);

    List<ActionPlan> findByMeetingIdInAndIsCompletedFalseOrderByIdAsc(List<Long> meetingIds);

    void deleteByMeetingId(Long meetingId);
}
