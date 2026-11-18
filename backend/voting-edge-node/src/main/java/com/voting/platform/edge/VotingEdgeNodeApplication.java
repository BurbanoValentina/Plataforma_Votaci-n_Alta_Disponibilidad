package com.voting.platform.edge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VotingEdgeNodeApplication {
    public static void main(String[] args) {
        SpringApplication.run(VotingEdgeNodeApplication.class, args);
    }
}
