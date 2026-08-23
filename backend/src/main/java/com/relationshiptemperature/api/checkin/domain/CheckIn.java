package com.relationshiptemperature.api.checkin.domain;

import com.relationshiptemperature.api.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "check_ins",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_checkin_relationship_week",
                columnNames = {"relationship_id", "week_start"}
        ),
        indexes = @Index(name = "idx_checkin_user_relationship", columnList = "user_id,relationship_id"))
public class CheckIn extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "relationship_id", nullable = false)
    private UUID relationshipId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    protected CheckIn() {
    }

    public CheckIn(UUID userId, UUID relationshipId, LocalDate weekStart) {
        this.userId = userId;
        this.relationshipId = relationshipId;
        this.weekStart = weekStart;
    }

    public UUID getUserId() { return userId; }
    public UUID getRelationshipId() { return relationshipId; }
    public LocalDate getWeekStart() { return weekStart; }
}
