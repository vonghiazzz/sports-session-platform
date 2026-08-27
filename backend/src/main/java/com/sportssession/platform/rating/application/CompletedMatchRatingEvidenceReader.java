package com.sportssession.platform.rating.application;

import java.util.Optional;
import java.util.UUID;

public interface CompletedMatchRatingEvidenceReader {

    Optional<CompletedMatchRatingContext> findCompletedMatch(UUID matchId);
}
