package com.readb.service.meeting;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.meeting.MeetingStatus;
import com.readb.domain.recording.Recording;
import com.readb.domain.user.User;
import com.readb.dto.meeting.MeetingCreateRequest;
import com.readb.dto.meeting.MeetingCreateResponse;
import com.readb.dto.meeting.MeetingDetailResponse;
import com.readb.dto.meeting.MeetingStatusResponse;
import com.readb.dto.meeting.RecordingPayload;
import com.readb.repository.MeetingRepository;
import com.readb.repository.RecordingRepository;
import com.readb.repository.UserRepository;
import com.readb.service.analysis.AnalysisOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final RecordingRepository recordingRepository;
    private final UserRepository userRepository;
    private final AnalysisOrchestrator analysisOrchestrator;

    @Transactional
    public MeetingCreateResponse createMeeting(Long leaderId, MeetingCreateRequest request) {
        Meeting meeting = Meeting.builder()
                .teamId(request.teamId())
                .leaderId(leaderId)
                .memberId(request.memberId())
                .build();
        Meeting saved = meetingRepository.save(meeting);
        return new MeetingCreateResponse(saved.getId(), saved.getStatus().name());
    }

    // TODO(데모 후): @Transactional 안에서 @Async 호출 시 race condition 가능성.
    //   - 비동기 startAnalysis가 매우 빨리 끝나 status=COMPLETED로 갱신했을 때,
    //     이 트랜잭션이 뒤늦게 커밋되며 dirty checking으로 ANALYZING을 다시 덮어쓸 위험.
    //   - 또한 비동기 스레드가 아직 커밋되지 않은 ANALYZING 상태를 못 볼 수도 있음.
    //   - 실제 STT는 5초 이상 소요되어 데모에선 발생률 낮으나, stub 분석에선 발생 가능.
    //   - 정식 해결: ApplicationEvent + @TransactionalEventListener(AFTER_COMMIT) 패턴으로 교체.
    @Transactional
    public void uploadRecording(Long meetingId, Long leaderId, MultipartFile file) {
        Meeting meeting = findMeeting(meetingId);
        if (!meeting.getLeaderId().equals(leaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // MultipartFile은 요청 스레드 종료 시 InputStream이 닫힐 수 있어
        // 비동기 스레드로 넘기기 전 byte[]로 동기 추출.
        RecordingPayload payload = extractPayload(file);
        meeting.updateStatus(MeetingStatus.ANALYZING);
        analysisOrchestrator.startAnalysis(meetingId, payload);
    }

    private RecordingPayload extractPayload(MultipartFile file) {
        try {
            return new RecordingPayload(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public MeetingStatusResponse getStatus(Long meetingId) {
        Meeting meeting = findMeeting(meetingId);
        int progress = switch (meeting.getStatus()) {
            case PENDING -> 0;
            case RECORDING -> 10;
            case ANALYZING -> 50;
            case COMPLETED -> 100;
            case FAILED -> -1;
        };
        return new MeetingStatusResponse(meetingId, meeting.getStatus().name(), progress);
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

        return new MeetingDetailResponse(
                meetingId,
                round,
                meeting.getCreatedAt(),
                durationSec,
                meeting.getStatus().name(),
                leader.getName(),
                member.getName()
        );
    }

    private Meeting findMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }
}
