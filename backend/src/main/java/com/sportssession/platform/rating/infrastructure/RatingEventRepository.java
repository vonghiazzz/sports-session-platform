package com.sportssession.platform.rating.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RatingEventRepository
        extends JpaRepository<RatingEventEntity, UUID> {
}
