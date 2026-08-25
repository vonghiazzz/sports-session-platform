package com.sportssession.platform.venue.api;

import com.sportssession.platform.shared.domain.SportCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCourtRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 120, message = "name must not exceed 120 characters")
        String name,

        @NotNull(message = "sport is required")
        SportCode sport,

        Boolean active
) {
    boolean activeOrDefault() {
        return active == null || active;
    }
}
