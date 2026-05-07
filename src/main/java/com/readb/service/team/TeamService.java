package com.readb.service.team;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.team.Team;
import com.readb.domain.user.User;
import com.readb.dto.team.TeamCreateRequest;
import com.readb.dto.team.TeamCreateResponse;
import com.readb.dto.team.TeamMemberResponse;
import com.readb.repository.TeamRepository;
import com.readb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional
    public TeamCreateResponse createTeam(Long leaderId, TeamCreateRequest request) {
        User leader = userRepository.findById(leaderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (leader.getTeamId() != null) {
            throw new BusinessException(ErrorCode.ALREADY_IN_TEAM);
        }

        Team team = Team.builder()
                .name(request.name())
                .leaderId(leaderId)
                .build();
        Team saved = teamRepository.save(team);

        leader.assignTeam(saved.getId());

        return TeamCreateResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeamMembers(Long teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }
        return userRepository.findByTeamId(teamId).stream()
                .map(u -> new TeamMemberResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name()))
                .toList();
    }
}
