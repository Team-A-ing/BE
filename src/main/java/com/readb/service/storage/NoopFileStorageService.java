package com.readb.service.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * FileStorageService 임시 구현체.
 * Supabase Storage 실제 연동 전까지 사용. BE2 Step 14에서 교체 예정.
 */
@Service
public class NoopFileStorageService implements FileStorageService {

    @Override
    public String upload(Long meetingId, MultipartFile file) {
        throw new UnsupportedOperationException("Supabase Storage 미구현 — BE2 Step 14 예정");
    }

    @Override
    public void delete(String fileUrl) {
        throw new UnsupportedOperationException("Supabase Storage 미구현 — BE2 Step 14 예정");
    }
}
