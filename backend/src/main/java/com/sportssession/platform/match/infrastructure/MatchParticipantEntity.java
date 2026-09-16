package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.TeamSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "match_participants")
public class MatchParticipantEntity {

    @Id
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "session_participant_id", nullable = false)
    private UUID sessionParticipantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_side", nullable = false, length = 32)
    private TeamSide teamSide;

    @Column(name = "team_slot", nullable = false)
    private int teamSlot;

    protected MatchParticipantEntity() {
    }

    private MatchParticipantEntity(MatchParticipant participant) {
        this.id = participant.id();
        this.matchId = participant.matchId();
        this.sessionParticipantId = participant.sessionParticipantId();
        this.teamSide = participant.teamSide();
        this.teamSlot = participant.teamSlot();
    }

    public static MatchParticipantEntity from(MatchParticipant participant) {
        return new MatchParticipantEntity(participant);
    }

    public MatchParticipant toDomain() {
        return new MatchParticipant(
                id,
                matchId,
                sessionParticipantId,
                teamSide,
                teamSlot
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getSessionParticipantId() {
        return sessionParticipantId;
    }
}
