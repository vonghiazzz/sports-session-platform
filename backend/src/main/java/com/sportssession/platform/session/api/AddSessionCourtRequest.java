package com.sportssession.platform.session.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddSessionCourtRequest(
        @NotNull(message = "courtId is required")
        UUID courtId
) {
}
