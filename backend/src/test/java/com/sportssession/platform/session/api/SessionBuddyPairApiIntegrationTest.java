package com.sportssession.platform.session.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.session.infrastructure.SessionEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantRepository;
import com.sportssession.platform.session.infrastructure.SessionRepository;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import com.sportssession.platform.venue.domain.Venue;
import com.sportssession.platform.venue.infrastructure.VenueEntity;
import com.sportssession.platform.venue.infrastructure.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SessionBuddyPairApiIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SessionParticipantRepository participantRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private VenueRepository venueRepository;

    @BeforeEach
    void cleanDatabase() {
        participantRepository.deleteAll();
        sessionRepository.deleteAll();
        venueRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void createsAtomicBuddyPairAndExposesItOnParticipantReadModel()
            throws Exception {
        Fixture fixture = createFixture(SessionStatus.PLANNED, 2);

        MvcResult result = createBuddyPair(
                fixture.sessionId(),
                fixture.participantIds().get(0),
                fixture.participantIds().get(1)
        ).andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                ".*/api/sessions/[0-9a-f-]{36}/buddy-pairs/"
                                        + "[0-9a-f-]{36}"
                        )
                ))
                .andExpect(jsonPath("$.sessionId")
                        .value(fixture.sessionId().toString()))
                .andExpect(jsonPath("$.firstSessionParticipantId").value(
                        fixture.participantIds().get(0).toString()
                ))
                .andExpect(jsonPath("$.secondSessionParticipantId").value(
                        fixture.participantIds().get(1).toString()
                ))
                .andReturn();
        UUID buddyPairId = responseBuddyPairId(result);

        assertThat(participantRepository.findAll())
                .extracting(SessionParticipantEntity::getBuddyPairId)
                .containsOnly(buddyPairId);
        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/participants",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buddyPairId")
                        .value(buddyPairId.toString()))
                .andExpect(jsonPath("$[1].buddyPairId")
                        .value(buddyPairId.toString()));
    }

    @Test
    void inProgressSessionAllowsBuddyCreateAndRemove() throws Exception {
        Fixture fixture = createFixture(SessionStatus.IN_PROGRESS, 2);
        UUID buddyPairId = responseBuddyPairId(createBuddyPair(
                fixture.sessionId(),
                fixture.participantIds().get(0),
                fixture.participantIds().get(1)
        ).andExpect(status().isCreated()).andReturn());

        removeBuddyPair(fixture.sessionId(), buddyPairId)
                .andExpect(status().isNoContent());

        assertThat(participantRepository.findAll())
                .extracting(SessionParticipantEntity::getBuddyPairId)
                .containsOnlyNulls();
    }

    @Test
    void rejectsSelfPairWithoutChangingParticipant() throws Exception {
        Fixture fixture = createFixture(SessionStatus.PLANNED, 1);
        UUID participantId = fixture.participantIds().getFirst();

        createBuddyPair(fixture.sessionId(), participantId, participantId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Buddy Pair requires two different SessionParticipants"
                ));

        assertThat(participantRepository.findById(participantId).orElseThrow()
                .getBuddyPairId()).isNull();
    }

    @Test
    void rejectsParticipantFromDifferentSessionWithoutPartialAssignment()
            throws Exception {
        Fixture first = createFixture(SessionStatus.PLANNED, 1);
        Fixture second = createFixture(SessionStatus.PLANNED, 1);

        createBuddyPair(
                first.sessionId(),
                first.participantIds().getFirst(),
                second.participantIds().getFirst()
        ).andExpect(status().isNotFound());

        assertThat(participantRepository.findAll())
                .extracting(SessionParticipantEntity::getBuddyPairId)
                .containsOnlyNulls();
    }

    @Test
    void rejectsUnknownParticipantWithoutPartialAssignment() throws Exception {
        Fixture fixture = createFixture(SessionStatus.PLANNED, 1);

        createBuddyPair(
                fixture.sessionId(),
                fixture.participantIds().getFirst(),
                UUID.randomUUID()
        ).andExpect(status().isNotFound());

        assertThat(participantRepository.findAll())
                .extracting(SessionParticipantEntity::getBuddyPairId)
                .containsOnlyNulls();
    }

    @Test
    void rejectsNewPairWhenFirstParticipantAlreadyHasBuddy() throws Exception {
        Fixture fixture = createFixture(SessionStatus.PLANNED, 3);
        createBuddyPair(
                fixture.sessionId(),
                fixture.participantIds().get(0),
                fixture.participantIds().get(1)
        ).andExpect(status().isCreated());

        createBuddyPair(
                fixture.sessionId(),
                fixture.participantIds().get(0),
                fixture.participantIds().get(2)
        ).andExpect(status().isConflict());

        assertOriginalPairAndUnpairedThird(fixture);
    }

    @Test
    void rejectsNewPairWhenSecondParticipantAlreadyHasBuddy() throws Exception {
        Fixture fixture = createFixture(SessionStatus.PLANNED, 3);
        createBuddyPair(
                fixture.sessionId(),
                fixture.participantIds().get(0),
                fixture.participantIds().get(1)
        ).andExpect(status().isCreated());

        createBuddyPair(
                fixture.sessionId(),
                fixture.participantIds().get(2),
                fixture.participantIds().get(1)
        ).andExpect(status().isConflict());

        assertOriginalPairAndUnpairedThird(fixture);
    }

    @Test
    void removingUnknownOrOtherSessionBuddyPairReturnsNotFound()
            throws Exception {
        Fixture first = createFixture(SessionStatus.PLANNED, 2);
        Fixture second = createFixture(SessionStatus.PLANNED, 1);
        UUID buddyPairId = responseBuddyPairId(createBuddyPair(
                first.sessionId(),
                first.participantIds().get(0),
                first.participantIds().get(1)
        ).andExpect(status().isCreated()).andReturn());

        removeBuddyPair(first.sessionId(), UUID.randomUUID())
                .andExpect(status().isNotFound());
        removeBuddyPair(second.sessionId(), buddyPairId)
                .andExpect(status().isNotFound());

        assertThat(participantRepository.findAllBySessionIdOrderByJoinedAtAscIdAsc(
                        first.sessionId()
                )).extracting(SessionParticipantEntity::getBuddyPairId)
                .containsOnly(buddyPairId);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"COMPLETED", "CANCELLED"})
    void terminalSessionRejectsBuddyCreateAndRemove(SessionStatus terminalStatus)
            throws Exception {
        Fixture fixture = createFixture(SessionStatus.PLANNED, 2);
        UUID buddyPairId = responseBuddyPairId(createBuddyPair(
                fixture.sessionId(),
                fixture.participantIds().get(0),
                fixture.participantIds().get(1)
        ).andExpect(status().isCreated()).andReturn());
        transitionSession(fixture.sessionId(), terminalStatus);

        createBuddyPair(
                fixture.sessionId(),
                fixture.participantIds().get(0),
                fixture.participantIds().get(1)
        ).andExpect(status().isConflict());
        removeBuddyPair(fixture.sessionId(), buddyPairId)
                .andExpect(status().isConflict());

        assertThat(participantRepository.findAll())
                .extracting(SessionParticipantEntity::getBuddyPairId)
                .containsOnly(buddyPairId);
    }

    private void assertOriginalPairAndUnpairedThird(Fixture fixture) {
        List<SessionParticipantEntity> participants =
                participantRepository.findAllBySessionIdOrderByJoinedAtAscIdAsc(
                        fixture.sessionId()
                );
        UUID originalBuddyPairId = participants.get(0).getBuddyPairId();
        assertThat(originalBuddyPairId).isNotNull();
        assertThat(participants.get(1).getBuddyPairId())
                .isEqualTo(originalBuddyPairId);
        assertThat(participants.get(2).getBuddyPairId()).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions createBuddyPair(
            UUID sessionId,
            UUID firstParticipantId,
            UUID secondParticipantId
    ) throws Exception {
        return mockMvc.perform(post(
                        "/api/sessions/{sessionId}/buddy-pairs",
                        sessionId
                ).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateBuddyPairRequest(
                                firstParticipantId,
                                secondParticipantId
                        )
                )));
    }

    private org.springframework.test.web.servlet.ResultActions removeBuddyPair(
            UUID sessionId,
            UUID buddyPairId
    ) throws Exception {
        return mockMvc.perform(delete(
                "/api/sessions/{sessionId}/buddy-pairs/{buddyPairId}",
                sessionId,
                buddyPairId
        ));
    }

    private UUID responseBuddyPairId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        return UUID.fromString(response.get("buddyPairId").asText());
    }

    private Fixture createFixture(
            SessionStatus status,
            int participantCount
    ) {
        Venue venue = Venue.create(
                "Buddy Venue " + UUID.randomUUID(),
                null,
                true,
                NOW
        );
        UUID venueId = venueRepository.saveAndFlush(
                VenueEntity.from(venue)
        ).getId();
        Session session = Session.create(
                venueId,
                "Buddy Session " + UUID.randomUUID(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                NOW.plus(1, ChronoUnit.HOURS),
                NOW.plus(3, ChronoUnit.HOURS),
                NOW
        );
        if (status == SessionStatus.IN_PROGRESS) {
            session = session.start(NOW.plusSeconds(1));
        }
        UUID sessionId = sessionRepository.saveAndFlush(
                SessionEntity.from(session)
        ).getId();
        List<UUID> participantIds = java.util.stream.IntStream
                .range(0, participantCount)
                .mapToObj(index -> createParticipant(sessionId, index))
                .toList();
        return new Fixture(sessionId, participantIds);
    }

    private UUID createParticipant(UUID sessionId, int index) {
        Player player = Player.create(
                "Buddy Player " + index + " " + UUID.randomUUID(),
                NOW
        );
        UUID playerId = playerRepository.saveAndFlush(
                PlayerEntity.from(player)
        ).getId();
        SessionParticipant participant = SessionParticipant.register(
                sessionId,
                playerId,
                NOW.plusSeconds(10L + index)
        );
        return participantRepository.saveAndFlush(
                SessionParticipantEntity.from(participant)
        ).getId();
    }

    private void transitionSession(UUID sessionId, SessionStatus target) {
        SessionEntity entity = sessionRepository.findById(sessionId)
                .orElseThrow();
        Session started = entity.toDomain().start(NOW.plusSeconds(30));
        Session terminal = switch (target) {
            case COMPLETED -> started.complete(NOW.plusSeconds(40));
            case CANCELLED -> started.cancel(NOW.plusSeconds(40));
            default -> throw new IllegalArgumentException(
                    "Terminal status required"
            );
        };
        entity.applyRuntimeState(terminal);
        sessionRepository.saveAndFlush(entity);
    }

    private record Fixture(UUID sessionId, List<UUID> participantIds) {
    }
}
