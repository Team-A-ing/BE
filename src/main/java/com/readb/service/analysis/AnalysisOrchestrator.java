package com.readb.service.analysis;

import com.readb.adapter.stt.SttAdapter;
import com.readb.adapter.llm.LlmAdapter;
import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.common.util.ByteArrayMultipartFile;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.meeting.MeetingStatus;
import com.readb.domain.recording.Recording;
import com.readb.dto.meeting.RecordingPayload;
import com.readb.repository.MeetingRepository;
import com.readb.repository.RecordingRepository;
import com.readb.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final MeetingRepository meetingRepository;
    private final RecordingRepository recordingRepository;
    private final FileStorageService fileStorageService;
    private final SttAdapter sttAdapter;
    private final LlmAdapter llmAdapter;
    private final AnalysisService analysisService;

    @Async("analysisExecutor")
    public void startAnalysis(Long meetingId, RecordingPayload payload) {
        Meeting meeting = null;
        try {
            // 미팅 조회를 try 블록 안에서 수행 — 존재하지 않을 때 catch로 떨어져 로그/상태 처리.
            meeting = meetingRepository.findById(meetingId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));

            // 비동기 스레드에서도 안전하게 재사용 가능한 in-memory MultipartFile
            MultipartFile file = new ByteArrayMultipartFile(
                    "file",
                    payload.originalFilename(),
                    payload.contentType(),
                    payload.bytes()
            );

            // 1단계: Supabase Storage 업로드 — BE2 미구현 시 fileUrl=null로 진행
            String fileUrl = tryUpload(meetingId, file);

            // 2단계: STT (Whisper) — 통짜 파일 전송
            String transcript = sttAdapter.transcribe(file);

            // 3단계: Recording 저장
            Recording recording = Recording.builder()
                    .meetingId(meetingId)
                    .fileUrl(fileUrl)
                    .transcript(transcript)
                    .build();
            recordingRepository.save(recording);

            // 4단계: LLM 분석 → Analysis 저장
            analysisService.analyze(meetingId, transcript);

            // 5단계: 원본 파일 삭제 (보안) — fileUrl이 있을 때만 시도
            if (fileUrl != null) {
                tryDelete(fileUrl);
                recording.deleteFileUrl();
                recordingRepository.save(recording);
            }

            meeting.updateStatus(MeetingStatus.COMPLETED);
        } catch (Exception e) {
            log.error("Analysis failed for meetingId={}", meetingId, e);
            if (meeting != null) {
                meeting.updateStatus(MeetingStatus.FAILED);
            }
        }
        if (meeting != null) {
            meetingRepository.save(meeting);
        }
    }

    private String tryUpload(Long meetingId, MultipartFile file) {
        try {
            return fileStorageService.upload(file, "meetings/%d/%s".formatted(meetingId, file.getOriginalFilename()));
        } catch (UnsupportedOperationException e) {
            log.warn("FileStorageService 미구현 — Storage 단계 건너뜀 (meetingId={})", meetingId);
            return null;
        }
    }

    private void tryDelete(String fileUrl) {
        try {
            fileStorageService.delete(fileUrl);
        } catch (UnsupportedOperationException e) {
            log.warn("FileStorageService 미구현 — 파일 삭제 건너뜀 (fileUrl={})", fileUrl);
        }
    }
}
