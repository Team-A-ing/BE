package com.readb.service.user;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.team.Team;
import com.readb.domain.user.User;
import com.readb.dto.user.UserProfileResponse;
import com.readb.dto.user.UserUpdateRequest;
import com.readb.repository.TeamRepository;
import com.readb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = findUser(userId);
        Team team = user.getTeamId() != null ? teamRepository.findById(user.getTeamId()).orElse(null) : null;
        return UserProfileResponse.from(user, team);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UserUpdateRequest request) {
        User user = findUser(userId);
        user.updateProfile(request.name());
        Team team = user.getTeamId() != null ? teamRepository.findById(user.getTeamId()).orElse(null) : null;
        return UserProfileResponse.from(user, team);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
