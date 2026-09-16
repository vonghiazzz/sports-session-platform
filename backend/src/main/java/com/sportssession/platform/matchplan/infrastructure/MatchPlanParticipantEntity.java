package com.sportssession.platform.matchplan.infrastructure;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchplan.domain.MatchPlanParticipant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "match_plan_participants")
public class MatchPlanParticipantEntity {

    @Id
    private UUID id;

    @Column(name = "match_plan_id", nullable = false)
    private UUID matchPlanId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "session_participant_id", nullable = false)
    private UUID sessionParticipantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_side", nullable = false, length = 32)
    private TeamSide teamSide;

    @Column(name = "team_slot", nullable = false)
    private int teamSlot;

    protected MatchPlanParticipantEntity() {
    }

    private MatchPlanParticipantEntity(MatchPlanParticipant participant) {
        this.id = participant.id();
        this.matchPlanId = participant.matchPlanId();
        this.sessionId = participant.sessionId();
        this.sessionParticipantId = participant.sessionParticipantId();
        this.teamSide = participant.teamSide();
        this.teamSlot = participant.teamSlot();
    }

    public static MatchPlanParticipantEntity from(
            MatchPlanParticipant participant
    ) {
        return new MatchPlanParticipantEntity(participant);
    }

    public MatchPlanParticipant toDomain() {
        return new MatchPlanParticipant(
                id, matchPlanId, sessionId, sessionParticipantId,
                teamSide, teamSlot
        );
    }

    public UUID getId() {
        return id;
    }
}
