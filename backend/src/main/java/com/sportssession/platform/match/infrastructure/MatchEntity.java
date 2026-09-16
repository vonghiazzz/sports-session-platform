package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchResult;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.domain.TeamSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
public class MatchEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "session_court_id", nullable = false)
    private UUID sessionCourtId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private MatchSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "winner_team", length = 32)
    private TeamSide winnerTeam;

    @Column(name = "team_a_score")
    private Integer teamAScore;

    @Column(name = "team_b_score")
    private Integer teamBScore;

    @Column(name = "result_version", nullable = false)
    private int resultVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MatchEntity() {
    }

    private MatchEntity(Match match) {
        this.id = match.id();
        this.sessionId = match.sessionId();
        this.sessionCourtId = match.sessionCourtId();
        this.status = match.status();
        this.source = match.source();
        applyResult(match.result());
        this.resultVersion = match.resultVersion();
        this.createdAt = match.createdAt();
        this.startedAt = match.startedAt();
        this.completedAt = match.completedAt();
        this.cancelledAt = match.cancelledAt();
        this.updatedAt = match.updatedAt();
        this.version = match.version();
    }

    public static MatchEntity from(Match match) {
        return new MatchEntity(match);
    }

    public void applyRuntimeState(Match match) {
        this.status = match.status();
        applyResult(match.result());
        this.resultVersion = match.resultVersion();
        this.startedAt = match.startedAt();
        this.completedAt = match.completedAt();
        this.cancelledAt = match.cancelledAt();
        this.updatedAt = match.updatedAt();
    }

    public Match toDomain() {
        MatchResult result = winnerTeam == null
                ? null
                : new MatchResult(winnerTeam, teamAScore, teamBScore);

        return new Match(
                id,
                sessionId,
                sessionCourtId,
                status,
                source,
                result,
                resultVersion,
                createdAt,
                startedAt,
                completedAt,
                cancelledAt,
                updatedAt,
                version
        );
    }

    public UUID getId() {
        return id;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    private void applyResult(MatchResult result) {
        this.winnerTeam = result == null ? null : result.winnerTeam();
        this.teamAScore = result == null ? null : result.teamAScore();
        this.teamBScore = result == null ? null : result.teamBScore();
    }
}
