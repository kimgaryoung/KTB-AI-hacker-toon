package com.relationshiptemperature.api.relationship.domain;

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
@Table(name = "relationships", indexes = {
        @Index(name = "idx_relationship_user_status", columnList = "user_id,status"),
        @Index(name = "idx_relationship_user_name", columnList = "user_id,name")
})
public class Relationship extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 30)
    private RelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RelationshipStatus status;

    @Column(name = "latest_score")
    private Integer latestScore;

    @Column(name = "latest_change")
    private Integer latestChange;

    @Column(name = "last_analyzed_at")
    private Instant lastAnalyzedAt;

    protected Relationship() {
    }

    private Relationship(UUID userId, String name, RelationshipType relationshipType) {
        this.userId = userId;
        this.name = name;
        this.relationshipType = relationshipType;
        this.status = RelationshipStatus.DRAFT;
    }

    public static Relationship draft(UUID userId, String name, RelationshipType relationshipType) {
        return new Relationship(userId, name, relationshipType);
    }

    public void update(String name, RelationshipType relationshipType) {
        if (name != null) {
            this.name = name;
        }
        if (relationshipType != null) {
            this.relationshipType = relationshipType;
        }
    }

    public void startAnalysis() {
        this.status = RelationshipStatus.ANALYZING;
    }

    public void completeAnalysis(int score, Integer change, Instant analyzedAt) {
        this.status = RelationshipStatus.ACTIVE;
        this.latestScore = score;
        this.latestChange = change;
        this.lastAnalyzedAt = analyzedAt;
    }

    public void failAnalysis() {
        this.status = RelationshipStatus.ANALYSIS_FAILED;
    }

    public void markDeleting() {
        this.status = RelationshipStatus.DELETING;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getInitial() {
        if (name == null || name.isBlank()) {
            return "?";
        }
        int[] visibleCodePoints = name.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .toArray();
        if (visibleCodePoints.length == 0) {
            return "?";
        }
        int index = visibleCodePoints.length >= 2 ? 1 : 0;
        return new String(visibleCodePoints, index, 1);
    }

    public RelationshipType getRelationshipType() {
        return relationshipType;
    }

    public RelationshipStatus getStatus() {
        return status;
    }

    public Integer getLatestScore() {
        return latestScore;
    }

    public Integer getLatestChange() {
        return latestChange;
    }

    public Instant getLastAnalyzedAt() {
        return lastAnalyzedAt;
    }
}
