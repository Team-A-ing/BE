package com.readb.service.meeting;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.meeting.MeetingStatus;
import com.readb.dto.meeting.MeetingCreateRequest;
import com.readb.dto.meeting.MeetingCreateResponse;
import com.readb.dto.meeting.RecordingPayload;
import com.readb.repository.MeetingRepository;
import com.readb.repository.RecordingRepository;
import com.readb.repository.UserRepository;
import com.readb.service.analysis.AnalysisOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AnalysisOrchestrator analysisOrchestrator;

    private MeetingService meetingService;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingService(meetingRepository, recordingRepository, userRepository, analysisOrchestrator);
    }

    @Test
    void createMeetingSavesCreatedMeeting() {
        Meeting saved = Meeting.builder()
                .id(10L)
                .teamId(1L)
                .title("Weekly 1:1")
                .leaderId(100L)
                .memberId(200L)
                .status(MeetingStatus.CREATED)
                .build();
        when(meetingRepository.save(any(Meeting.class))).thenReturn(saved);

        MeetingCreateResponse response = meetingService.createMeeting(
                100L,
                new MeetingCreateRequest(1L, 200L, "Weekly 1:1", null)
        );

        assertThat(response.meetingId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("CREATED");
    }

    @Test
    void uploadRecordingUploadsFileAndStartsAsyncTranscription() {
        Meeting meeting = Meeting.builder()
                .id(10L)
                .teamId(1L)
                .title("Weekly 1:1")
                .leaderId(100L)
                .memberId(200L)
                .status(MeetingStatus.CREATED)
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "meeting-10.webm",
                "audio/webm",
                "audio-bytes".getBytes(StandardCharsets.UTF_8)
        );
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));


        meetingService.uploadRecording(10L, 100L, file, 123);

        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.TRANSCRIBING);
        verify(meetingRepository).save(meeting);

        ArgumentCaptor<RecordingPayload> payloadCaptor = ArgumentCaptor.forClass(RecordingPayload.class);
        verify(analysisOrchestrator).startAnalysis(
                eq(10L),
                payloadCaptor.capture()
        );
        RecordingPayload payload = payloadCaptor.getValue();
        assertThat(payload.originalFilename()).isEqualTo("meeting-10.webm");
        assertThat(payload.contentType()).isEqualTo("audio/webm");
        assertThat(payload.durationSec()).isEqualTo(123);
        assertThat(payload.bytes()).isEqualTo("audio-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadRecordingRejectsNonOwner() {
        Meeting meeting = Meeting.builder()
                .id(10L)
                .teamId(1L)
                .title("Weekly 1:1")
                .leaderId(100L)
                .memberId(200L)
                .status(MeetingStatus.CREATED)
                .build();
        MockMultipartFile file = new MockMultipartFile("file", "a.webm", "audio/webm", new byte[]{1});
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingService.uploadRecording(10L, 999L, file, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(analysisOrchestrator);
    }

    @Test
    void uploadRecordingRejectsMeetingAlreadyInProgress() {
        Meeting meeting = Meeting.builder()
                .id(10L)
                .teamId(1L)
                .title("Weekly 1:1")
                .leaderId(100L)
                .memberId(200L)
                .status(MeetingStatus.TRANSCRIBING)
                .build();
        MockMultipartFile file = new MockMultipartFile("file", "a.webm", "audio/webm", new byte[]{1});
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingService.uploadRecording(10L, 100L, file, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ANALYSIS_IN_PROGRESS);

        verifyNoInteractions(analysisOrchestrator);
    }
}
