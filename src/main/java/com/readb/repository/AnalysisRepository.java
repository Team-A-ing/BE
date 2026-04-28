package com.readb.repository;

import com.readb.domain.analysis.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    Optional<Analysis> findByMeetingId(Long meetingId);

    List<Analysis> findByMeetingIdIn(List<Long> meetingIds);
}
