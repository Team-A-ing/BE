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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
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

    private Meeting findMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }
}
