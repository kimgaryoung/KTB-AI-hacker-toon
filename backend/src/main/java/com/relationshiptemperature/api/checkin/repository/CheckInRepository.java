package com.relationshiptemperature.api.checkin.repository;

import com.relationshiptemperature.api.checkin.domain.CheckIn;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    Optional<CheckIn> findByRelationshipIdAndWeekStart(UUID relationshipId, LocalDate weekStart);

    Optional<CheckIn> findByIdAndUserIdAndRelationshipId(UUID id, UUID userId, UUID relationshipId);

    List<CheckIn> findAllByUserIdAndRelationshipIdOrderByWeekStartDesc(UUID userId, UUID relationshipId);
}
