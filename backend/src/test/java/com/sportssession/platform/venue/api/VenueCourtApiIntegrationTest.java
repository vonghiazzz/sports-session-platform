package com.sportssession.platform.venue.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import com.sportssession.platform.venue.infrastructure.CourtRepository;
import com.sportssession.platform.venue.infrastructure.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class VenueCourtApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private CourtRepository courtRepository;

    @BeforeEach
    void cleanDatabase() {
        courtRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void createVenueSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venueJson("Central Sports Hall", "District 1", true)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        ".*/api/venues/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.name").value("Central Sports Hall"))
                .andExpect(jsonPath("$.locationText").value("District 1"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        UUID venueId = responseId(result);
        assertThat(venueRepository.findById(venueId))
                .isPresent()
                .get()
                .satisfies(venue -> {
                    assertThat(venue.getName()).isEqualTo("Central Sports Hall");
                    assertThat(venue.getLocationText()).isEqualTo("District 1");
                    assertThat(venue.isActive()).isTrue();
                });
    }

    @Test
    void getVenueSucceeds() throws Exception {
        UUID venueId = createVenue("Venue A", "Location A", true);

        mockMvc.perform(get("/api/venues/{venueId}", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(venueId.toString()))
                .andExpect(jsonPath("$.name").value("Venue A"))
                .andExpect(jsonPath("$.locationText").value("Location A"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unknownVenueReturnsNotFound() throws Exception {
        UUID missingVenueId = UUID.randomUUID();

        mockMvc.perform(get("/api/venues/{venueId}", missingVenueId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Venue not found: " + missingVenueId));
    }

    @Test
    void listVenuesReturnsCreatedVenues() throws Exception {
        createVenue("Venue A", null, true);
        createVenue("Venue B", "Location B", false);

        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Venue A"))
                .andExpect(jsonPath("$[1].name").value("Venue B"));
    }

    @Test
    void createCourtSucceeds() throws Exception {
        UUID venueId = createVenue("Venue A", null, true);

        MvcResult result = mockMvc.perform(post("/api/venues/{venueId}/courts", venueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtJson("Court 1", true)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        ".*/api/courts/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.name").value("Court 1"))
                .andExpect(jsonPath("$.sport").value("BADMINTON"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        assertThat(courtRepository.findById(responseId(result))).isPresent();
    }

    @Test
    void courtBelongsToCorrectVenue() throws Exception {
        UUID venueId = createVenue("Venue A", null, true);
        UUID courtId = createCourt(venueId, "Court 1");

        assertThat(courtRepository.findById(courtId))
                .isPresent()
                .get()
                .satisfies(court -> assertThat(court.getVenueId()).isEqualTo(venueId));

        mockMvc.perform(get("/api/courts/{courtId}", courtId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venueId").value(venueId.toString()));
    }

    @Test
    void sameCourtNameIsAllowedAcrossDifferentVenues() throws Exception {
        UUID firstVenueId = createVenue("Venue A", null, true);
        UUID secondVenueId = createVenue("Venue B", null, true);

        createCourt(firstVenueId, "Court 1");
        createCourt(secondVenueId, "Court 1");

        assertThat(courtRepository.count()).isEqualTo(2);
        assertThat(courtRepository.findAllByVenueIdOrderByCreatedAtAscIdAsc(firstVenueId))
                .singleElement()
                .satisfies(court -> assertThat(court.getName()).isEqualTo("Court 1"));
        assertThat(courtRepository.findAllByVenueIdOrderByCreatedAtAscIdAsc(secondVenueId))
                .singleElement()
                .satisfies(court -> assertThat(court.getName()).isEqualTo("Court 1"));
    }

    @Test
    void duplicateCourtNameWithinSameVenueReturnsConflict() throws Exception {
        UUID venueId = createVenue("Venue A", null, true);
        createCourt(venueId, "Court 1");

        mockMvc.perform(post("/api/venues/{venueId}/courts", venueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtJson("Court 1", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Court name already exists in Venue "
                                + venueId + ": Court 1"));

        assertThat(courtRepository.count()).isEqualTo(1);
    }

    @Test
    void blankCourtNameReturnsBadRequest() throws Exception {
        UUID venueId = createVenue("Venue A", null, true);

        mockMvc.perform(post("/api/venues/{venueId}/courts", venueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtJson("   ", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name")
                        .value("name must not be blank"));

        assertThat(courtRepository.count()).isZero();
    }

    @Test
    void courtCreationForUnknownVenueReturnsNotFound() throws Exception {
        UUID missingVenueId = UUID.randomUUID();

        mockMvc.perform(post("/api/venues/{venueId}/courts", missingVenueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtJson("Court 1", true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Venue not found: " + missingVenueId));

        assertThat(courtRepository.count()).isZero();
    }

    @Test
    void courtCreationForInactiveVenueReturnsConflict() throws Exception {
        UUID venueId = createVenue("Inactive Venue", null, false);

        mockMvc.perform(post("/api/venues/{venueId}/courts", venueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtJson("Court 1", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot create a court for inactive Venue: " + venueId));

        assertThat(courtRepository.count()).isZero();
    }

    @Test
    void listVenueCourtsReturnsOnlyCourtsFromThatVenue() throws Exception {
        UUID firstVenueId = createVenue("Venue A", null, true);
        UUID secondVenueId = createVenue("Venue B", null, true);
        createCourt(firstVenueId, "Court 1");
        createCourt(firstVenueId, "Court 2");
        createCourt(secondVenueId, "Court 3");

        mockMvc.perform(get("/api/venues/{venueId}/courts", firstVenueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].venueId").value(firstVenueId.toString()))
                .andExpect(jsonPath("$[1].venueId").value(firstVenueId.toString()));
    }

    @Test
    void unknownCourtReturnsNotFound() throws Exception {
        UUID missingCourtId = UUID.randomUUID();

        mockMvc.perform(get("/api/courts/{courtId}", missingCourtId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Court not found: " + missingCourtId));
    }

    private UUID createVenue(String name, String locationText, boolean active) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venueJson(name, locationText, active)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID createCourt(UUID venueId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/venues/{venueId}/courts", venueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtJson(name, true)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID responseId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(response.get("id").asText());
    }

    private String venueJson(String name, String locationText, boolean active)
            throws Exception {
        return objectMapper.writeValueAsString(
                new CreateVenueRequest(name, locationText, active));
    }

    private String courtJson(String name, boolean active) throws Exception {
        return objectMapper.writeValueAsString(
                new CreateCourtRequest(name, SportCode.BADMINTON, active));
    }
}
