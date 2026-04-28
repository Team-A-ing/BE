package com.readb.adapter.stt;

import org.springframework.web.multipart.MultipartFile;

public interface SttAdapter {

    /**
     * 오디오 파일을 텍스트로 변환한다.
     * 구현체 교체 시 이 인터페이스만 유지하면 상위 로직 변경 없음.
     *
     * @param audioFile WebM 등 오디오 파일 (25MB 이하)
     * @return 전사된 텍스트 (전체 대화 내용)
     */
    String transcribe(MultipartFile audioFile);
}
