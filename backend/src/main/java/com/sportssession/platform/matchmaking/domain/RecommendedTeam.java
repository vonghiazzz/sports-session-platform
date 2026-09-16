package com.sportssession.platform.matchmaking.domain;

import com.sportssession.platform.match.domain.TeamSide;

import java.math.BigDecimal;
import java.util.Objects;

public record RecommendedTeam(
        TeamSide teamSide,
        RecommendedPlayer slot1,
        RecommendedPlayer slot2,
        BigDecimal ratingTotal
) {
    public RecommendedTeam {
        Objects.requireNonNull(teamSide, "teamSide is required");
        Objects.requireNonNull(slot1, "slot1 is required");
        Objects.requireNonNull(slot2, "slot2 is required");
        Objects.requireNonNull(ratingTotal, "ratingTotal is required");
        if (slot1.teamSide() != teamSide || slot1.teamSlot() != 1) {
            throw new IllegalArgumentException(
                    "slot1 must belong to this Team at slot 1");
        }
        if (slot2.teamSide() != teamSide || slot2.teamSlot() != 2) {
            throw new IllegalArgumentException(
                    "slot2 must belong to this Team at slot 2");
        }
        if (slot1.playerId().equals(slot2.playerId())) {
            throw new IllegalArgumentException(
                    "Team Players must be unique");
        }
        if (slot1.playerId().toString().compareTo(slot2.playerId().toString()) >= 0) {
            throw new IllegalArgumentException(
                    "Team Players must use canonical slot order");
        }
        if (ratingTotal.compareTo(
                slot1.ratingValue().add(slot2.ratingValue())
        ) != 0) {
            throw new IllegalArgumentException(
                    "ratingTotal must equal the sum of Team Ratings");
        }
    }
}
