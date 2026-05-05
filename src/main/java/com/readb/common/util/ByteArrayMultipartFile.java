package com.readb.common.util;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

// MultipartFile을 byte[]로부터 in-memory로 재구성. 비동기 스레드에서
// 원본 요청 InputStream이 닫힌 뒤에도 안전하게 STT/Storage 어댑터에 전달.
public class ByteArrayMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name != null ? name : "file";
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content != null ? content : new byte[0];
    }

    @Override @NonNull public String getName() { return name; }
    @Override public String getOriginalFilename() { return originalFilename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return content.length == 0; }
    @Override public long getSize() { return content.length; }
    @Override @NonNull public byte[] getBytes() { return content; }
    @Override @NonNull public InputStream getInputStream() { return new ByteArrayInputStream(content); }

    @Override
    public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
        Files.write(dest.toPath(), content);
    }

    @Override
    public void transferTo(@NonNull Path dest) throws IOException, IllegalStateException {
        Files.write(dest, content);
    }
}
