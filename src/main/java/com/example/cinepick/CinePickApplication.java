package com.example.cinepick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CinePickApplication {

    public static void main(String[] args) {
        SpringApplication.run(CinePickApplication.class, args);
    }

}
