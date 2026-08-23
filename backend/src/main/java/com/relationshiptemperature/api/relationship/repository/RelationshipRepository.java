package com.relationshiptemperature.api.relationship.repository;

import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.domain.RelationshipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelationshipRepository extends JpaRepository<Relationship, UUID> {

    Optional<Relationship> findByIdAndUserId(UUID id, UUID userId);

    List<Relationship> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<Relationship> findAllByUserIdAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(UUID userId, String name);

    boolean existsByIdAndUserIdAndStatus(UUID id, UUID userId, RelationshipStatus status);
}
