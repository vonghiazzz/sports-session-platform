package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.rating.domain.RatingEngine;
import com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RatingEngineConfiguration {

    @Bean
    public RatingEngine ratingEngine() {
        return new WengLinPlackettLuceRatingEngine();
    }
}
