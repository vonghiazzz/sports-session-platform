package com.sportssession.platform.session.infrastructure;

import com.sportssession.platform.matchmaking.application.GlobalMatchmakingCourtSnapshot;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingSessionSnapshot;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingSessionSnapshotReader;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionParticipantSnapshot;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshot;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotException;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotFailureReason;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotReader;
import com.sportssession.platform.session.domain.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class MatchmakingSessionSnapshotReaderAdapter
        implements MatchmakingSessionSnapshotReader,
        GlobalMatchmakingSessionSnapshotReader {

    private final SessionRepository sessionRepository;
    private final SessionCourtRepository sessionCourtRepository;
    private final SessionParticipantRepository sessionParticipantRepository;

    public MatchmakingSessionSnapshotReaderAdapter(
            SessionRepository sessionRepository,
            SessionCourtRepository sessionCourtRepository,
            SessionParticipantRepository sessionParticipantRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionCourtRepository = sessionCourtRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MatchmakingSessionSnapshot load(
            UUID sessionId,
            UUID sessionCourtId
    ) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new MatchmakingSessionSnapshotException(
                        MatchmakingSessionSnapshotFailureReason.SESSION_NOT_FOUND,
                        sessionId,
                        sessionCourtId
                ))
                .toDomain();
        SessionCourtEntity sessionCourt = sessionCourtRepository
                .findByIdAndSessionId(sessionCourtId, sessionId)
                .orElseThrow(() -> new MatchmakingSessionSnapshotException(
                        MatchmakingSessionSnapshotFailureReason
                                .SESSION_COURT_NOT_FOUND_FOR_SESSION,
                        sessionId,
                        sessionCourtId
                ));
        List<MatchmakingSessionParticipantSnapshot> participants =
                participants(sessionId);

        return new MatchmakingSessionSnapshot(
                session.id(),
                session.sportCode(),
                session.matchFormat(),
                session.status(),
                sessionCourt.getId(),
                sessionCourt.getStatus(),
                participants
        );
    }

    @Override
    @Transactional(readOnly = true)
    public GlobalMatchmakingSessionSnapshot load(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new MatchmakingSessionSnapshotException(
                        MatchmakingSessionSnapshotFailureReason.SESSION_NOT_FOUND,
                        sessionId,
                        null
                ))
                .toDomain();
        List<GlobalMatchmakingCourtSnapshot> courts = sessionCourtRepository
                .findAllBySessionIdOrderByAddedAtAscIdAsc(sessionId)
                .stream()
                .map(SessionCourtEntity::toDomain)
                .map(court -> new GlobalMatchmakingCourtSnapshot(
                        court.id(),
                        court.status(),
                        court.addedAt()
                ))
                .toList();

        return new GlobalMatchmakingSessionSnapshot(
                session.id(),
                session.sportCode(),
                session.matchFormat(),
                session.status(),
                courts,
                participants(sessionId)
        );
    }

    private List<MatchmakingSessionParticipantSnapshot> participants(
            UUID sessionId
    ) {
        return sessionParticipantRepository
                .findAllBySessionIdOrderByPlayerIdAscIdAsc(sessionId)
                .stream()
                .map(participant ->
                        new MatchmakingSessionParticipantSnapshot(
                                participant.getId(),
                                participant.getPlayerId(),
                                participant.getStatus(),
                                participant.getWaitingSince()
                        ))
                .toList();
    }
}
