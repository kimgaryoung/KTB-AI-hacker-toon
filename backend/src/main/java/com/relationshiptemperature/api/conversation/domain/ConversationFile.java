package com.relationshiptemperature.api.conversation.domain;

import com.relationshiptemperature.api.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_files", indexes = {
        @Index(name = "idx_conversation_relationship", columnList = "relationship_id"),
        @Index(name = "idx_conversation_expiry", columnList = "expires_at")
})
public class ConversationFile extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "relationship_id", nullable = false)
    private UUID relationshipId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20)
    private ConversationFileStatus validationStatus;

    @Column(name = "message_count")
    private Integer messageCount;

    @Column(name = "conversation_started_at")
    private Instant conversationStartedAt;

    @Column(name = "conversation_ended_at")
    private Instant conversationEndedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "raw_deleted_at")
    private Instant rawDeletedAt;

    @Column(name = "self_participant_name", length = 100)
    private String selfParticipantName;

    @Column(name = "other_participant_name", length = 100)
    private String otherParticipantName;

    @Column(name = "test_fixture", nullable = false)
    private boolean testFixture;

    protected ConversationFile() {
    }

    public ConversationFile(
            UUID userId,
            UUID relationshipId,
            String originalFileName,
            String storageKey,
            long sizeBytes,
            String sha256,
            Instant expiresAt
    ) {
        this(userId, relationshipId, originalFileName, storageKey, sizeBytes, sha256, expiresAt, false);
    }

    public ConversationFile(
            UUID userId,
            UUID relationshipId,
            String originalFileName,
            String storageKey,
            long sizeBytes,
            String sha256,
            Instant expiresAt,
            boolean testFixture
    ) {
        this.userId = userId;
        this.relationshipId = relationshipId;
        this.originalFileName = originalFileName;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.expiresAt = expiresAt;
        this.testFixture = testFixture;
        this.validationStatus = ConversationFileStatus.VALIDATING;
    }

    public void validated(int messageCount, Instant startedAt, Instant endedAt) {
        this.validationStatus = ConversationFileStatus.VALID;
        this.messageCount = messageCount;
        this.conversationStartedAt = startedAt;
        this.conversationEndedAt = endedAt;
    }

    public void participants(String selfParticipantName, String otherParticipantName) {
        this.selfParticipantName = selfParticipantName;
        this.otherParticipantName = otherParticipantName;
    }

    public void markRawDeleted(Instant deletedAt) {
        this.storageKey = null;
        this.rawDeletedAt = deletedAt;
    }

    public UUID getUserId() { return userId; }
    public UUID getRelationshipId() { return relationshipId; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStorageKey() { return storageKey; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public ConversationFileStatus getValidationStatus() { return validationStatus; }
    public Integer getMessageCount() { return messageCount; }
    public Instant getConversationStartedAt() { return conversationStartedAt; }
    public Instant getConversationEndedAt() { return conversationEndedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRawDeletedAt() { return rawDeletedAt; }
    public String getSelfParticipantName() { return selfParticipantName; }
    public String getOtherParticipantName() { return otherParticipantName; }
    public boolean isTestFixture() { return testFixture; }
}
