package com.readb.repository;

import com.readb.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByLeaderId(Long leaderId);
    Optional<Team> findByInviteCode(String inviteCode);
}
