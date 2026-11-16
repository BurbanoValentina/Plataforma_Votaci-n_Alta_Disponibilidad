package com.voting.platform.regional;

import com.voting.platform.domain.aggregate.RegionalVoteCounterAggregate;
import com.voting.platform.domain.strategy.ElectoralWeightingStrategy;
import com.voting.platform.domain.strategy.RegionalStrategyContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RegionalRollupApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegionalRollupApplication.class, args);
    }

    @Bean
    public RegionalVoteCounterAggregate regionalVoteCounterAggregate() {
        return new RegionalVoteCounterAggregate();
    }

    @Bean
    public RegionalStrategyContext regionalStrategyContext() {
        // Default to Electoral weighting strategy (can be switched at runtime)
        return new RegionalStrategyContext(new ElectoralWeightingStrategy());
    }
}
