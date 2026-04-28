package com.readb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ReadbApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadbApplication.class, args);
    }
}
