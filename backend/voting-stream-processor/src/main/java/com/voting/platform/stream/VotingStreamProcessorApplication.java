package com.voting.platform.stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VotingStreamProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(VotingStreamProcessorApplication.class, args);
    }
}
