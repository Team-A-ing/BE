package com.readb.repository;

import com.readb.domain.meeting.Meeting;
import com.readb.domain.meeting.MeetingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    List<Meeting> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    Page<Meeting> findByMemberId(Long memberId, Pageable pageable);

    long countByMemberId(Long memberId);

    List<Meeting> findByLeaderIdOrderByCreatedAtDesc(Long leaderId);

    long countByLeaderIdAndMemberIdAndIdLessThanEqual(Long leaderId, Long memberId, Long id);

    List<Meeting> findByLeaderIdAndMemberIdAndIdLessThan(Long leaderId, Long memberId, Long id);

    List<Meeting> findByLeaderIdAndMemberIdOrderByCreatedAtDesc(Long leaderId, Long memberId);

    java.util.Optional<Meeting> findTopByLeaderIdAndMemberIdAndIdLessThanOrderByCreatedAtDesc(
            Long leaderId, Long memberId, Long id);
    List<Meeting> findByMemberIdInOrderByCreatedAtDesc(List<Long> memberIds);
}
