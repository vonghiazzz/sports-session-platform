package com.sportssession.platform.match.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchParticipantTest {

    @Test
    void assignmentCapturesTeamSideAndSlot() {
        MatchParticipant participant = MatchParticipant.assign(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TeamSide.A,
                2
        );

        assertThat(participant.teamSide()).isEqualTo(TeamSide.A);
        assertThat(participant.teamSlot()).isEqualTo(2);
    }

    @Test
    void teamSlotMustBeOneOrTwo() {
        assertThatThrownBy(() -> MatchParticipant.assign(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TeamSide.B,
                3
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("teamSlot must be 1 or 2");
    }
}
