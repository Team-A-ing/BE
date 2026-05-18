package com.readb.service.meeting;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.meeting.MeetingStatus;
import com.readb.dto.meeting.MeetingCreateRequest;
import com.readb.dto.meeting.MeetingCreateResponse;
import com.readb.dto.meeting.MeetingStatusResponse;
import com.readb.dto.meeting.RecordingPayload;
import com.readb.repository.MeetingRepository;
import com.readb.service.analysis.AnalysisOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;

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
    private final AnalysisOrchestrator analysisOrchestrator;

    @Transactional
    public MeetingCreateResponse createMeeting(Long leaderId, MeetingCreateRequest request) {
        Meeting meeting = Meeting.builder()
                .teamId(request.teamId())
                .title(resolveTitle(request.title()))
                .leaderId(leaderId)
                .memberId(request.memberId())
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

    @Transactional(readOnly = true)
    public MeetingStatusResponse getStatus(Long meetingId) {
        Meeting meeting = findMeeting(meetingId);
        int progress = switch (meeting.getStatus()) {
            case CREATED -> 0;
            case TRANSCRIBING -> 40;
            case ANALYZING -> 70;
            case COMPLETED -> 100;
            case FAILED -> -1;
        };
        return new MeetingStatusResponse(meetingId, meeting.getStatus().name(), progress);
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
