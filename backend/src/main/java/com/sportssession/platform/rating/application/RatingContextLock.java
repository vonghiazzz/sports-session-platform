package com.sportssession.platform.rating.application;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

public interface RatingContextLock {

    boolean tryAcquire(SportCode sportCode, MatchFormat matchFormat);
}
