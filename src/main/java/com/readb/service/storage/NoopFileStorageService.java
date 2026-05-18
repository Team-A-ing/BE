package com.readb.service.storage;

import org.springframework.web.multipart.MultipartFile;

public class NoopFileStorageService implements FileStorageService {

    @Override
    public String upload(MultipartFile file, String path) {
        throw new UnsupportedOperationException("Supabase Storage implementation is owned by BE2.");
    }
}
