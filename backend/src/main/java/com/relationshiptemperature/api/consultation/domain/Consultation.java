package com.relationshiptemperature.api.consultation.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "consultations")
@CompoundIndex(name = "idx_consultation_user_updated", def = "{'userId': 1, 'updatedAt': -1}")
public class Consultation {

    @Id
    private String id;
    private String userId;
    private String relationshipId;
    private String reportId;
    private String lastMessagePreview;
    private Instant lastMessageAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected Consultation() {
    }

    public Consultation(UUID userId, UUID relationshipId, UUID reportId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.userId = userId.toString();
        this.relationshipId = relationshipId.toString();
        this.reportId = reportId.toString();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updatePreview(String content, Instant time) {
        this.lastMessagePreview = content.length() <= 160 ? content : content.substring(0, 160);
        this.lastMessageAt = time;
        this.updatedAt = time;
    }

    public String getId() { return id; }
    public UUID getUserId() { return UUID.fromString(userId); }
    public UUID getRelationshipId() { return UUID.fromString(relationshipId); }
    public UUID getReportId() { return UUID.fromString(reportId); }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
