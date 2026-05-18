package com.readb.service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String upload(MultipartFile file, String path);

    default void delete(String fileUrl) {
        throw new UnsupportedOperationException("File delete is not implemented yet.");
    }
}
