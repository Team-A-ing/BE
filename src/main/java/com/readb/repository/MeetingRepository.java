package com.readb.repository;

import com.readb.domain.meeting.Meeting;
import com.readb.domain.meeting.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    List<Meeting> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<Meeting> findByLeaderIdOrderByCreatedAtDesc(Long leaderId);

    long countByLeaderIdAndMemberIdAndIdLessThanEqual(Long leaderId, Long memberId, Long id);

    List<Meeting> findByLeaderIdAndMemberIdAndIdLessThan(Long leaderId, Long memberId, Long id);

    List<Meeting> findByMemberIdInOrderByCreatedAtDesc(List<Long> memberIds);
}
