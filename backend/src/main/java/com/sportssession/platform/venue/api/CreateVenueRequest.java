package com.sportssession.platform.venue.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVenueRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 120, message = "name must not exceed 120 characters")
        String name,

        @Size(max = 500, message = "locationText must not exceed 500 characters")
        String locationText,

        Boolean active
) {
    boolean activeOrDefault() {
        return active == null || active;
    }
}
