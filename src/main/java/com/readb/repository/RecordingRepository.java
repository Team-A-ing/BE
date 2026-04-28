package com.readb.repository;

import com.readb.domain.recording.Recording;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    Optional<Recording> findByMeetingId(Long meetingId);
}
