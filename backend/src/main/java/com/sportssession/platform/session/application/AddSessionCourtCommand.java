package com.sportssession.platform.session.application;

import java.util.UUID;

public record AddSessionCourtCommand(
        UUID sessionId,
        UUID courtId
) {
}
