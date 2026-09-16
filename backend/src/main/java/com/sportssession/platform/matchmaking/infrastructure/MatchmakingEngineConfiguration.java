package com.sportssession.platform.matchmaking.infrastructure;

import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchmakingEngineConfiguration {

    @Bean
    public MatchmakingEngine matchmakingEngine() {
        return new MatchmakingEngine();
    }
}
