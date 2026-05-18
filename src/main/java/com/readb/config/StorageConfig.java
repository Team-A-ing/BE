package com.readb.config;

import com.readb.service.storage.FileStorageService;
import com.readb.service.storage.NoopFileStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnMissingBean(FileStorageService.class)
    public FileStorageService noopFileStorageService() {
        return new NoopFileStorageService();
    }
}
