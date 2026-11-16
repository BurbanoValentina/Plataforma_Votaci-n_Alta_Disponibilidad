package com.voting.platform.processor;

import com.voting.platform.domain.aggregate.VoteCounterAggregate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VoteProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoteProcessorApplication.class, args);
    }

    @Bean
    public VoteCounterAggregate voteCounterAggregate() {
        return new VoteCounterAggregate();
    }
}
