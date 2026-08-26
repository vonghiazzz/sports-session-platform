package com.sportssession.platform.match.application;

import com.sportssession.platform.match.domain.TeamSide;

import java.util.UUID;

public record ManualMatchParticipantAssignment(
        UUID sessionParticipantId,
        TeamSide teamSide,
        int teamSlot
) {
}
