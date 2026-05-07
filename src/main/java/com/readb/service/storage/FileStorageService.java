package com.readb.service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String upload(Long meetingId, MultipartFile file);

    void delete(String fileUrl);
}
