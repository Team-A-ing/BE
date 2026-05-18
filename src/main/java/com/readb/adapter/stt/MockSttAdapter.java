package com.readb.adapter.stt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@Profile("mock")
public class MockSttAdapter implements SttAdapter {

    @Override
    public String transcribe(MultipartFile audioFile) {
        log.info("[Mock] STT 변환 요청됨. filename={}", audioFile.getOriginalFilename());
        
        // 지연 시간 시뮬레이션
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Whisper API의 verbose_json 형식을 모방한 더미 JSON 반환
        return """
                {
                  "task": "transcribe",
                  "language": "ko",
                  "duration": 45.5,
                  "text": "아 네, 이번 스프린트에서 일정이 조금 빡빡해서 우려가 됩니다. 제가 QA 쪽 리소스를 더 도와드릴까요?",
                  "segments": [
                    {
                      "id": 0,
                      "start": 0.0,
                      "end": 2.5,
                      "text": "아 네, 이번 스프린트에서 일정이 조금 빡빡해서 우려가 됩니다."
                    },
                    {
                      "id": 1,
                      "start": 3.0,
                      "end": 5.5,
                      "text": "제가 QA 쪽 리소스를 더 도와드릴까요?"
                    }
                  ]
                }
                """;
    }
}
