package com.readb.service.analysis;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.promise.Promise;
import com.readb.domain.promise.PromiseStatus;
import com.readb.domain.user.User;
import com.readb.dto.promise.FulfillmentRateResponse;
import com.readb.dto.promise.OverduePromiseResponse;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import com.readb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromiseService {

    private final PromiseRepository promiseRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Promise> getPromisesByTeam(Long teamId, Long userId) {
        boolean belongs = userRepository.findById(userId)
                .map(u -> teamId.equals(u.getTeamId()))
                .orElse(false);
        if (!belongs) throw new BusinessException(ErrorCode.FORBIDDEN);

        List<Long> meetingIds = meetingRepository.findByTeamIdOrderByCreatedAtDesc(teamId)
                .stream().map(Meeting::getId).toList();
        return promiseRepository.findByMeetingIdIn(meetingIds);
    }

    @Transactional(readOnly = true)
    public FulfillmentRateResponse getFulfillmentRate(Long ownerId) {
        List<Promise> promises = promiseRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        int total = promises.size();
        if (total == 0) return new FulfillmentRateResponse(0, 0, 0, 0, 0.0, 0.0, 0.0);

        int doneCount = (int) promises.stream().filter(p -> p.getStatus() == PromiseStatus.DONE).count();
        int missedCount = (int) promises.stream().filter(p -> p.getStatus() == PromiseStatus.MISSED).count();
        int pendingCount = total - doneCount - missedCount;

        return new FulfillmentRateResponse(
                total,
                doneCount,
                missedCount,
                pendingCount,
                round(doneCount, total),
                round(missedCount, total),
                round(pendingCount, total)
        );
    }

    private double round(int count, int total) {
        return Math.round((double) count / total * 1000) / 10.0;
    }

    @Transactional
    public void completePromise(Long promiseId, Long userId) {
        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROMISE_NOT_FOUND));
        Meeting meeting = meetingRepository.findById(promise.getMeetingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!meeting.getLeaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        promise.updateStatus(PromiseStatus.DONE);
    }

    @Transactional(readOnly = true)
    public List<OverduePromiseResponse> getOverduePromises(Long userId, Long memberId) {
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getTeamId() == null || !user.getTeamId().equals(member.getTeamId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String memberName = member.getName();
        List<Promise> promises = promiseRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(memberId, PromiseStatus.PENDING);

        return promises.stream().map(p -> {
            Meeting meeting = meetingRepository.findById(p.getMeetingId()).orElse(null);
            int round = meeting == null ? 0 : (int) meetingRepository.countByLeaderIdAndMemberIdAndIdLessThanEqual(
                    meeting.getLeaderId(), meeting.getMemberId(), meeting.getId());
            return new OverduePromiseResponse(
                    p.getId(),
                    p.getContent(),
                    p.getCategory(),
                    p.getDeadline() != null ? p.getDeadline().toString() : null,
                    p.getStatus().name(),
                    round,
                    memberName
            );
        }).toList();
    }
}
