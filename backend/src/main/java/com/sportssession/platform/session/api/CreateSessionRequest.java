package com.sportssession.platform.session.api;

import com.sportssession.platform.session.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateSessionRequest(
        @NotNull(message = "venueId is required")
        UUID venueId,

        @NotBlank(message = "title must not be blank")
        @Size(max = 160, message = "title must not exceed 160 characters")
        String title,

        @NotNull(message = "sport is required")
        SportCode sport,

        @NotNull(message = "matchFormat is required")
        MatchFormat matchFormat,

        @NotNull(message = "plannedStartAt is required")
        Instant plannedStartAt,

        @NotNull(message = "plannedEndAt is required")
        Instant plannedEndAt
) {
}
