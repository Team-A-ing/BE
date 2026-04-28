package com.readb.service.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    // TODO: BE2 구현 — Supabase Storage 업로드/다운로드/삭제

    public String upload(Long meetingId, MultipartFile file) {
        throw new UnsupportedOperationException("BE2 구현 예정");
    }

    public void delete(String fileUrl) {
        throw new UnsupportedOperationException("BE2 구현 예정");
    }
}
