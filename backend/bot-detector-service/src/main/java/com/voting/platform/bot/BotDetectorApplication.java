package com.voting.platform.bot;

import com.voting.platform.domain.aggregate.BotDetectorAggregate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

@SpringBootApplication
public class BotDetectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BotDetectorApplication.class, args);
    }

    @Bean
    public BotDetectorAggregate botDetectorAggregate() {
        // Window 10 seconds, warning at 5 votes, block at 15 votes
        return new BotDetectorAggregate(Duration.ofSeconds(10), 5, 15);
    }
}
