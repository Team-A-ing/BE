package com.readb.service.meeting;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.career.CareerEvent;
import com.readb.domain.career.CareerEventType;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.meeting.MeetingStatus;
import com.readb.domain.promise.Promise;
import com.readb.domain.recording.Recording;
import com.readb.domain.user.User;
import com.readb.domain.user.UserRole;
import com.readb.dto.meeting.ConfirmedAchievementResponse;
import com.readb.dto.meeting.LeaderPromiseResponse;
import com.readb.dto.meeting.MeetingCreateRequest;
import com.readb.dto.meeting.MeetingCreateResponse;
import com.readb.dto.meeting.MeetingDetailResponse;
import com.readb.dto.meeting.MeetingListResponse;
import com.readb.dto.meeting.MeetingStatusResponse;
import com.readb.dto.meeting.MemberReportResponse;
import com.readb.dto.meeting.RecordingPayload;
import com.readb.repository.CareerEventRepository;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import com.readb.repository.RecordingRepository;
import com.readb.repository.SurveyRepository;
import com.readb.repository.UserRepository;
import com.readb.service.analysis.AnalysisOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private static final Set<MeetingStatus> UPLOAD_BLOCKED_STATUSES = Set.of(
            MeetingStatus.TRANSCRIBING,
            MeetingStatus.ANALYZING,
            MeetingStatus.COMPLETED
    );

    private final MeetingRepository meetingRepository;
    private final RecordingRepository recordingRepository;
    private final UserRepository userRepository;
    private final SurveyRepository surveyRepository;
    private final AnalysisOrchestrator analysisOrchestrator;
    private final CareerEventRepository careerEventRepository;
    private final PromiseRepository promiseRepository;

    @Transactional
    public MeetingCreateResponse createMeeting(Long leaderId, MeetingCreateRequest request) {
        Meeting meeting = Meeting.builder()
                .teamId(request.teamId())
                .title(resolveTitle(request.title()))
                .leaderId(leaderId)
                .memberId(request.memberId())
                .scheduledAt(request.scheduledAt())
                .build();

        Meeting saved = meetingRepository.save(meeting);
        log.info("Meeting created. meetingId={}, teamId={}, leaderId={}, memberId={}",
                saved.getId(), saved.getTeamId(), saved.getLeaderId(), saved.getMemberId());
        return new MeetingCreateResponse(saved.getId(), saved.getStatus().name());
    }

    public void uploadRecording(Long meetingId, Long leaderId, MultipartFile file, Integer durationSec) {
        Meeting meeting = findMeeting(meetingId);
        validateOwner(meeting, leaderId);
        validateUploadable(meeting);
        validateFile(file);

        RecordingPayload payload = extractPayload(file, durationSec);

        meeting.updateStatus(MeetingStatus.TRANSCRIBING);
        meetingRepository.save(meeting);
        log.info("Recording accepted. meetingId={}", meetingId);

        analysisOrchestrator.startAnalysis(meetingId, payload);
    }

    // 예정 시각에 진행되지 않은 미팅 취소 = 삭제. 회차는 동적 계산이라 이후 미팅 회차가 자동으로 1씩 감소한다.
    @Transactional
    public void cancelMeeting(Long meetingId, Long leaderId) {
        Meeting meeting = findMeeting(meetingId);
        validateOwner(meeting, leaderId);
        if (meeting.getStatus() != MeetingStatus.CREATED) {
            throw new BusinessException(ErrorCode.MEETING_NOT_CANCELABLE);
        }
        // 진행 전 미팅이라 녹음/분석/약속은 없고, 멤버가 미리 낸 서베이만 정리하면 됨
        surveyRepository.deleteAll(surveyRepository.findByMeetingId(meetingId));
        meetingRepository.delete(meeting);
        log.info("Meeting cancelled (deleted). meetingId={}, leaderId={}", meetingId, leaderId);
    }

    @Transactional(readOnly = true)
    public MeetingStatusResponse getStatus(Long meetingId) {
        Meeting meeting = findMeeting(meetingId);
        int progress = switch (meeting.getStatus()) {
            case CREATED -> 0;
            case TRANSCRIBING -> 30;
            case ANALYZING -> 50;
            case COMPLETED -> 100;
            case FAILED -> -1;
        };
        return new MeetingStatusResponse(meetingId, meeting.getStatus().name(), progress);
    }

    @Transactional(readOnly = true)
    public List<MeetingListResponse> getMeetings(Long memberId, Long userId) {
        List<Meeting> meetings;
        if (memberId != null) {
            meetings = meetingRepository.findByLeaderIdAndMemberIdOrderByCreatedAtDesc(userId, memberId);
        } else if (userRepository.findById(userId).map(User::getRole).orElse(null) == UserRole.MEMBER) {
            // 멤버는 본인이 멤버로 참여한 '리더와의 1on1'만 본다.
            // leaderId == memberId인 셀프 미팅(테스트로 같은 계정이 리더가 된 경우)은 제외.
            meetings = meetingRepository.findByMemberIdOrderByCreatedAtDesc(userId).stream()
                    .filter(m -> !userId.equals(m.getLeaderId()))
                    .toList();
        } else {
            meetings = Stream.concat(
                    meetingRepository.findByLeaderIdOrderByCreatedAtDesc(userId).stream(),
                    meetingRepository.findByMemberIdOrderByCreatedAtDesc(userId).stream()
            ).sorted(Comparator.comparing(Meeting::getCreatedAt).reversed()).toList();
        }

        return meetings.stream().map(m -> {
            int round = (int) meetingRepository.countByLeaderIdAndMemberIdAndIdLessThanEqual(
                    m.getLeaderId(), m.getMemberId(), m.getId());
            Long partnerId = userId.equals(m.getLeaderId()) ? m.getMemberId() : m.getLeaderId();
            String partnerName = userRepository.findById(partnerId)
                    .map(User::getName).orElse("");
            Integer durationSec = recordingRepository.findByMeetingId(m.getId())
                    .map(Recording::getDurationSec).orElse(null);
            return new MeetingListResponse(m.getId(), round, partnerName,
                    m.getScheduledAt(), durationSec, m.getStatus().name());
        }).toList();
    }

    @Transactional(readOnly = true)
    public MeetingDetailResponse getMeeting(Long meetingId, Long userId) {
        Meeting meeting = findMeeting(meetingId);

        if (!userId.equals(meeting.getLeaderId()) && !userId.equals(meeting.getMemberId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        int round = (int) meetingRepository.countByLeaderIdAndMemberIdAndIdLessThanEqual(
                meeting.getLeaderId(), meeting.getMemberId(), meetingId);

        User leader = userRepository.findById(meeting.getLeaderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User member = userRepository.findById(meeting.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Integer durationSec = recordingRepository.findByMeetingId(meetingId)
                .map(Recording::getDurationSec)
                .orElse(null);

        boolean surveySubmitted = surveyRepository.existsByMeetingIdAndMemberId(
                meetingId, meeting.getMemberId());

        return new MeetingDetailResponse(
                meetingId,
                meeting.getMemberId(),
                round,
                meeting.getScheduledAt(),
                durationSec,
                meeting.getStatus().name(),
                leader.getName(),
                member.getName(),
                surveySubmitted
        );
    }

    @Transactional(readOnly = true)
    public MemberReportResponse getMemberReport(Long meetingId, Long userId) {
        Meeting meeting = findMeeting(meetingId);

        if (!userId.equals(meeting.getLeaderId()) && !userId.equals(meeting.getMemberId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        int round = (int) meetingRepository.countByLeaderIdAndMemberIdAndIdLessThanEqual(
                meeting.getLeaderId(), meeting.getMemberId(), meetingId);

        User leader = userRepository.findById(meeting.getLeaderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Integer durationSec = recordingRepository.findByMeetingId(meetingId)
                .map(Recording::getDurationSec)
                .orElse(null);

        List<CareerEvent> events = careerEventRepository.findByMeetingId(meetingId);

        List<Promise> promises = promiseRepository.findByMeetingIdAndOwnerId(meetingId, meeting.getLeaderId());

        List<ConfirmedAchievementResponse> confirmedAchievements = events.stream()
                .map(e -> new ConfirmedAchievementResponse(
                        e.getId(),
                        e.getEventType().name(),
                        e.getTitle(),
                        e.getDescription(),
                        e.getEvidence() != null ? toStr(e.getEvidence().get("impactMetric")) : null,
                        e.getEvidence() != null ? toStr(e.getEvidence().get("quote")) : null,
                        leader.getName()
                )).toList();

        List<LeaderPromiseResponse> leaderPromises = promises.stream()
                .map(p -> new LeaderPromiseResponse(
                        p.getId(),
                        p.getContent(),
                        null,
                        p.getDeadline(),
                        p.getStatus().name()
                )).toList();

        return new MemberReportResponse(
                meetingId, round, leader.getName(), meeting.getScheduledAt(),
                durationSec, confirmedAchievements, leaderPromises);
    }

    private static String toStr(Object val) {
        return val == null ? null : val.toString();
    }

    private String resolveTitle(String title) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        return "1:1 Meeting " + LocalDate.now();
    }

    private void validateOwner(Meeting meeting, Long leaderId) {
        if (!meeting.getLeaderId().equals(leaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateUploadable(Meeting meeting) {
        if (UPLOAD_BLOCKED_STATUSES.contains(meeting.getStatus())) {
            throw new BusinessException(ErrorCode.ANALYSIS_IN_PROGRESS);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
    }

    private RecordingPayload extractPayload(MultipartFile file, Integer durationSec) {
        try {
            return new RecordingPayload(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    durationSec
            );
        } catch (IOException e) {
            log.error("Failed to read uploaded recording. filename={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private Meeting findMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }
}
