package com.sportssession.platform.rating.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.rating.infrastructure.PlayerRatingRepository;
import com.sportssession.platform.rating.infrastructure.RatingEventRepository;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.infrastructure.SessionCourtEntity;
import com.sportssession.platform.session.infrastructure.SessionCourtRepository;
import com.sportssession.platform.session.infrastructure.SessionEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantRepository;
import com.sportssession.platform.session.infrastructure.SessionRepository;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import com.sportssession.platform.venue.domain.Court;
import com.sportssession.platform.venue.domain.Venue;
import com.sportssession.platform.venue.infrastructure.CourtEntity;
import com.sportssession.platform.venue.infrastructure.CourtRepository;
import com.sportssession.platform.venue.infrastructure.VenueEntity;
import com.sportssession.platform.venue.infrastructure.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RatingReadBoundariesIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompletedMatchRatingEvidenceReader evidenceReader;

    @Autowired
    private PlayerSkillLevelLookup skillLevelLookup;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionParticipantRepository sessionParticipantRepository;

    @Autowired
    private SessionCourtRepository sessionCourtRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSportProfileRepository profileRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @Autowired
    private RatingEventRepository ratingEventRepository;

    @BeforeEach
    void cleanDatabase() {
        ratingEventRepository.deleteAll();
        playerRatingRepository.deleteAll();
        matchParticipantRepository.deleteAll();
        matchRepository.deleteAll();
        sessionCourtRepository.deleteAll();
        sessionParticipantRepository.deleteAll();
        sessionRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void readsCompletedManualMatchWithScoresAndActualPlayerIds() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createManualMatch(fixture);
        start(matchId);
        complete(matchId, TeamSide.A, 21, 18);
        Instant persistedCompletedAt = matchRepository.findById(matchId)
                .orElseThrow()
                .toDomain()
                .completedAt();

        CompletedMatchRatingContext context = evidenceReader
                .findCompletedMatch(matchId)
                .orElseThrow();

        assertThat(context.matchId()).isEqualTo(matchId);
        assertThat(context.sessionId()).isEqualTo(fixture.sessionId());
        assertThat(context.resultVersion()).isEqualTo(1);
        assertThat(context.completedAt()).isEqualTo(persistedCompletedAt);
        assertThat(context.source()).isEqualTo(MatchSource.MANUAL);
        assertThat(context.sportCode()).isEqualTo(SportCode.BADMINTON);
        assertThat(context.matchFormat()).isEqualTo(MatchFormat.DOUBLES);
        assertThat(context.winnerTeam()).isEqualTo(TeamSide.A);
        assertThat(context.teamAScore()).isEqualTo(21);
        assertThat(context.teamBScore()).isEqualTo(18);
        assertThat(context.teamA()).containsExactly(
                evidence(fixture.playerIds().get(0), TeamSide.A, 1),
                evidence(fixture.playerIds().get(1), TeamSide.A, 2)
        );
        assertThat(context.teamB()).containsExactly(
                evidence(fixture.playerIds().get(2), TeamSide.B, 1),
                evidence(fixture.playerIds().get(3), TeamSide.B, 2)
        );
    }

    @Test
    void readsWinnerOnlyCompletedMatchWithNullScores() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createManualMatch(fixture);
        start(matchId);
        complete(matchId, TeamSide.B, null, null);

        CompletedMatchRatingContext context = evidenceReader
                .findCompletedMatch(matchId)
                .orElseThrow();

        assertThat(context.winnerTeam()).isEqualTo(TeamSide.B);
        assertThat(context.teamAScore()).isNull();
        assertThat(context.teamBScore()).isNull();
        assertThat(context.teamA()).hasSize(2);
        assertThat(context.teamB()).hasSize(2);
    }

    @Test
    void createdMatchIsNotCompletedEvidence() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createManualMatch(fixture);

        assertThat(evidenceReader.findCompletedMatch(matchId)).isEmpty();
    }

    @Test
    void playingMatchIsNotCompletedEvidence() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createManualMatch(fixture);
        start(matchId);

        assertThat(evidenceReader.findCompletedMatch(matchId)).isEmpty();
    }

    @Test
    void cancelledMatchIsNotCompletedEvidence() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createManualMatch(fixture);
        mockMvc.perform(post("/api/matches/{matchId}/cancel", matchId))
                .andExpect(status().isOk());

        assertThat(evidenceReader.findCompletedMatch(matchId)).isEmpty();
    }

    @Test
    void missingMatchIsNotCompletedEvidence() {
        assertThat(evidenceReader.findCompletedMatch(UUID.randomUUID())).isEmpty();
    }

    @Test
    void batchSkillLookupReturnsExactPersistedMappings() {
        List<SkillLevel> levels = List.of(
                SkillLevel.WEAK,
                SkillLevel.INTERMEDIATE_MINUS,
                SkillLevel.INTERMEDIATE_PLUS,
                SkillLevel.GOOD
        );
        List<UUID> playerIds = new ArrayList<>();
        for (SkillLevel level : levels) {
            playerIds.add(createPlayerWithProfile(level));
        }

        Map<UUID, SkillLevel> result = skillLevelLookup.requireSkillLevels(
                playerIds,
                SportCode.BADMINTON
        );

        assertThat(result).hasSize(4);
        for (int index = 0; index < playerIds.size(); index++) {
            assertThat(result.get(playerIds.get(index))).isEqualTo(levels.get(index));
        }
    }

    @Test
    void missingPlayerProfileIsAnExplicitIntegrityAnomaly() {
        UUID profiledPlayerId = createPlayerWithProfile(SkillLevel.INTERMEDIATE);
        UUID missingPlayerId = createPlayer();

        assertThatThrownBy(() -> skillLevelLookup.requireSkillLevels(
                List.of(profiledPlayerId, missingPlayerId),
                SportCode.BADMINTON
        )).isInstanceOfSatisfying(
                MissingPlayerRatingPriorException.class,
                exception -> {
                    assertThat(exception.missingPlayerIds())
                            .containsExactly(missingPlayerId);
                    assertThat(exception.sportCode())
                            .isEqualTo(SportCode.BADMINTON);
                }
        );
    }

    private RuntimeFixture createRuntimeFixture() {
        Instant now = Instant.now().minusSeconds(30);
        Venue venue = Venue.create("Venue " + UUID.randomUUID(), null, true, now);
        UUID venueId = venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
        Court court = Court.create(
                venueId,
                "Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                now
        );
        UUID courtId = courtRepository.saveAndFlush(CourtEntity.from(court)).getId();
        Session session = Session.create(
                venueId,
                "Rating Evidence Session",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS),
                now
        ).start(now.plusSeconds(1));
        UUID sessionId = sessionRepository
                .saveAndFlush(SessionEntity.from(session))
                .getId();
        UUID sessionCourtId = sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(SessionCourt.allocate(
                        sessionId,
                        courtId,
                        now.plusSeconds(2)
                ))
        ).getId();

        List<UUID> playerIds = new ArrayList<>();
        List<UUID> participantIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            UUID playerId = createPlayer();
            playerIds.add(playerId);
            SessionParticipant participant = SessionParticipant.register(
                    sessionId,
                    playerId,
                    now.plusSeconds(3)
            ).checkIn(now.plusSeconds(4));
            participantIds.add(sessionParticipantRepository.saveAndFlush(
                    SessionParticipantEntity.from(participant)
            ).getId());
        }
        return new RuntimeFixture(
                sessionId,
                sessionCourtId,
                playerIds,
                participantIds
        );
    }

    private UUID createManualMatch(RuntimeFixture fixture) throws Exception {
        List<Map<String, Object>> participants = List.of(
                assignment(fixture.participantIds().get(0), TeamSide.A, 1),
                assignment(fixture.participantIds().get(1), TeamSide.A, 2),
                assignment(fixture.participantIds().get(2), TeamSide.B, 1),
                assignment(fixture.participantIds().get(3), TeamSide.B, 2)
        );
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sessionCourtId", fixture.sessionCourtId());
        request.put("participants", participants);

        String response = mockMvc.perform(post(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return UUID.fromString(body.get("id").asText());
    }

    private void start(UUID matchId) throws Exception {
        mockMvc.perform(post("/api/matches/{matchId}/start", matchId))
                .andExpect(status().isOk());
    }

    private void complete(
            UUID matchId,
            TeamSide winnerTeam,
            Integer teamAScore,
            Integer teamBScore
    ) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("winnerTeam", winnerTeam);
        request.put("teamAScore", teamAScore);
        request.put("teamBScore", teamBScore);
        mockMvc.perform(post("/api/matches/{matchId}/complete", matchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private Map<String, Object> assignment(
            UUID participantId,
            TeamSide side,
            int slot
    ) {
        return Map.of(
                "sessionParticipantId", participantId,
                "teamSide", side,
                "teamSlot", slot
        );
    }

    private RatingParticipantEvidence evidence(
            UUID playerId,
            TeamSide side,
            int slot
    ) {
        return new RatingParticipantEvidence(playerId, side, slot);
    }

    private UUID createPlayerWithProfile(SkillLevel skillLevel) {
        UUID playerId = createPlayer();
        Instant now = Instant.now();
        profileRepository.saveAndFlush(PlayerSportProfileEntity.from(
                PlayerSportProfile.create(
                        playerId,
                        SportCode.BADMINTON,
                        skillLevel,
                        now
                )
        ));
        return playerId;
    }

    private UUID createPlayer() {
        Player player = Player.create("Player " + UUID.randomUUID(), Instant.now());
        return playerRepository.saveAndFlush(PlayerEntity.from(player)).getId();
    }

    private record RuntimeFixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> playerIds,
            List<UUID> participantIds
    ) {
        private RuntimeFixture {
            playerIds = List.copyOf(playerIds);
            participantIds = List.copyOf(participantIds);
        }
    }
}
