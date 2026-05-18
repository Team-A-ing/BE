package com.readb.adapter.stt;

import org.springframework.web.multipart.MultipartFile;

public interface SttAdapter {

    String transcribe(MultipartFile audioFile);
}
