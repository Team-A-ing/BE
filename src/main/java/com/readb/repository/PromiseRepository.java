package com.readb.repository;

import com.readb.domain.promise.Promise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromiseRepository extends JpaRepository<Promise, Long> {

    List<Promise> findByMeetingId(Long meetingId);

    List<Promise> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<Promise> findByMeetingIdIn(List<Long> meetingIds);
}
