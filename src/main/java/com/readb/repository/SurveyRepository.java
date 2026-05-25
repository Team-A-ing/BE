package com.readb.repository;

import com.readb.domain.survey.Survey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyRepository extends JpaRepository<Survey, Long> {
    Optional<Survey> findByMeetingIdAndMemberId(Long meetingId, Long memberId);
    List<Survey> findByMeetingId(Long meetingId);
    boolean existsByMeetingIdAndMemberId(Long meetingId, Long memberId);
    List<Survey> findByMeetingIdIn(List<Long> meetingIds);
    Page<Survey> findByMemberId(Long memberId, Pageable pageable);
}
