package com.relationshiptemperature.api.conversation.repository;

import com.relationshiptemperature.api.conversation.domain.ConversationFile;
import com.relationshiptemperature.api.conversation.domain.ConversationFileStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationFileRepository extends JpaRepository<ConversationFile, UUID> {

    Optional<ConversationFile> findByIdAndUserId(UUID id, UUID userId);

    Optional<ConversationFile> findByRelationshipIdAndSha256(UUID relationshipId, String sha256);

    Optional<ConversationFile> findByIdAndUserIdAndRelationshipIdAndValidationStatus(
            UUID id,
            UUID userId,
            UUID relationshipId,
            ConversationFileStatus status
    );

    Optional<ConversationFile> findFirstByRelationshipIdAndValidationStatusOrderByCreatedAtDesc(
            UUID relationshipId,
            ConversationFileStatus status
    );

    List<ConversationFile> findAllByExpiresAtBeforeAndRawDeletedAtIsNull(Instant expiresAt);
}
