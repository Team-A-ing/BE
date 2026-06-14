package com.readb.service.analysis;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.promise.Promise;
import com.readb.domain.promise.PromiseStatus;
import com.readb.domain.user.User;
import com.readb.dto.promise.FulfillmentRateResponse;
import com.readb.dto.promise.OverduePromiseResponse;
import com.readb.dto.promise.PromiseReminderResponse;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import com.readb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        loadParticipantPromise(promiseId, userId).complete();
    }

    @Transactional
    public void incompletePromise(Long promiseId, Long userId) {
        loadParticipantPromise(promiseId, userId).incomplete();
    }

    // 약속 상태 변경은 해당 1on1의 참여자(리더 또는 멤버)만 가능
    private Promise loadParticipantPromise(Long promiseId, Long userId) {
        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROMISE_NOT_FOUND));
        Meeting meeting = meetingRepository.findById(promise.getMeetingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!userId.equals(meeting.getLeaderId()) && !userId.equals(meeting.getMemberId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return promise;
    }

    // 리더 본인이 등록한 PENDING 약속 중 기한 초과/임박(7일 이내) 항목.
    // 미이행 약속은 다음 미팅 pre-briefing의 recommendedTopics에 자동 반영되므로,
    // 이 응답은 대시보드 리마인더 표시 용도.
    @Transactional(readOnly = true)
    public PromiseReminderResponse getReminders(Long ownerId) {
        List<Promise> pendings = promiseRepository
                .findByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, PromiseStatus.PENDING);
        List<Promise> withDeadline = pendings.stream().filter(p -> p.getDeadline() != null).toList();
        if (withDeadline.isEmpty()) return new PromiseReminderResponse(List.of(), List.of());

        List<Long> meetingIds = withDeadline.stream().map(Promise::getMeetingId).distinct().toList();
        Map<Long, Meeting> meetingById = meetingRepository.findAllById(meetingIds)
                .stream().collect(Collectors.toMap(Meeting::getId, m -> m));
        List<Long> memberIds = meetingById.values().stream().map(Meeting::getMemberId).distinct().toList();
        Map<Long, String> memberNames = userRepository.findAllById(memberIds)
                .stream().collect(Collectors.toMap(User::getId, User::getName));

        LocalDate today = LocalDate.now();
        LocalDate soonLimit = today.plusDays(7);
        List<PromiseReminderResponse.ReminderItem> overdue = new ArrayList<>();
        List<PromiseReminderResponse.ReminderItem> dueSoon = new ArrayList<>();

        for (Promise p : withDeadline) {
            Meeting meeting = meetingById.get(p.getMeetingId());
            String memberName = meeting == null ? ""
                    : memberNames.getOrDefault(meeting.getMemberId(), "");
            String meetingTitle = (meeting == null || meeting.getTitle() == null)
                    ? "1:1 미팅" : meeting.getTitle();
            long daysLeft = ChronoUnit.DAYS.between(today, p.getDeadline());
            PromiseReminderResponse.ReminderItem item = new PromiseReminderResponse.ReminderItem(
                    p.getId(), p.getContent(), p.getDeadline().toString(),
                    daysLeft, memberName, meetingTitle);
            if (p.getDeadline().isBefore(today)) overdue.add(item);
            else if (!p.getDeadline().isAfter(soonLimit)) dueSoon.add(item);
        }
        overdue.sort(Comparator.comparing(PromiseReminderResponse.ReminderItem::dueDate));
        dueSoon.sort(Comparator.comparing(PromiseReminderResponse.ReminderItem::dueDate));
        return new PromiseReminderResponse(overdue, dueSoon);
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
