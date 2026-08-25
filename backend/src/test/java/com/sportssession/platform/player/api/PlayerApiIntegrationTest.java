package com.sportssession.platform.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlayerApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSportProfileRepository profileRepository;

    @BeforeEach
    void cleanDatabase() {
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void createPlayerSucceedsAndPersistsPlayerWithSportProfile() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlayerJson("Player A")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        ".*/api/players/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.displayName").value("Player A"))
                .andExpect(jsonPath("$.sportProfiles[0].sport").value("BADMINTON"))
                .andExpect(jsonPath("$.sportProfiles[0].skillLevel")
                        .value("INTERMEDIATE"))
                .andReturn();

        UUID playerId = responsePlayerId(result);
        assertThat(playerRepository.findById(playerId)).isPresent();
        assertThat(profileRepository.findAllByPlayerIdOrderByCreatedAtAsc(playerId))
                .singleElement()
                .satisfies(profile -> {
                    assertThat(profile.getPlayerId()).isEqualTo(playerId);
                    assertThat(profile.getSportCode().name()).isEqualTo("BADMINTON");
                    assertThat(profile.getSkillLevel().name()).isEqualTo("INTERMEDIATE");
                });
    }

    @Test
    void blankDisplayNameIsRejectedWithoutPersistence() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlayerJson("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.displayName")
                        .value("displayName must not be blank"));

        assertThat(playerRepository.count()).isZero();
        assertThat(profileRepository.count()).isZero();
    }

    @Test
    void unsupportedSportIsRejected() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Player A",
                                  "sport": "TENNIS",
                                  "skillLevel": "INTERMEDIATE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Malformed request or unsupported sport/skillLevel value"));
    }

    @Test
    void getExistingPlayerSucceeds() throws Exception {
        UUID playerId = createPlayer("Player B");

        mockMvc.perform(get("/api/players/{playerId}", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(playerId.toString()))
                .andExpect(jsonPath("$.displayName").value("Player B"))
                .andExpect(jsonPath("$.sportProfiles.length()").value(1));
    }

    @Test
    void getNonexistentPlayerReturnsNotFound() throws Exception {
        UUID missingPlayerId = UUID.randomUUID();

        mockMvc.perform(get("/api/players/{playerId}", missingPlayerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Player not found: " + missingPlayerId));
    }

    @Test
    void listAndCaseInsensitiveNameSearchReturnMatchingPlayers() throws Exception {
        createPlayer("Lan Anh");
        createPlayer("Minh Khoa");

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/players").queryParam("name", "LAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Lan Anh"));
    }

    @Test
    void duplicateDisplayNamesAreAllowed() throws Exception {
        createPlayer("Same Name");
        createPlayer("Same Name");

        assertThat(playerRepository.count()).isEqualTo(2);
        assertThat(profileRepository.count()).isEqualTo(2);
    }

    private UUID createPlayer(String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlayerJson(displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return responsePlayerId(result);
    }

    private UUID responsePlayerId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(response.get("id").asText());
    }

    private String validPlayerJson(String displayName) throws Exception {
        return objectMapper.writeValueAsString(new CreatePlayerRequest(
                displayName,
                com.sportssession.platform.shared.domain.SportCode.BADMINTON,
                com.sportssession.platform.player.domain.SkillLevel.INTERMEDIATE));
    }
}
