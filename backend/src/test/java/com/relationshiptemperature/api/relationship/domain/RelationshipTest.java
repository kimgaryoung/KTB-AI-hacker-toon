package com.relationshiptemperature.api.relationship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelationshipTest {

    @Test
    void managesAnalysisLifecycleAndLatestScore() {
        Relationship relationship = Relationship.draft(UUID.randomUUID(), "홍길동", RelationshipType.FRIEND);
        Instant analyzedAt = Instant.parse("2026-08-19T06:20:00Z");

        assertThat(relationship.getStatus()).isEqualTo(RelationshipStatus.DRAFT);

        relationship.startAnalysis();
        assertThat(relationship.getStatus()).isEqualTo(RelationshipStatus.ANALYZING);

        relationship.completeAnalysis(78, 6, analyzedAt);
        assertThat(relationship.getStatus()).isEqualTo(RelationshipStatus.ACTIVE);
        assertThat(relationship.getLatestScore()).isEqualTo(78);
        assertThat(relationship.getLatestChange()).isEqualTo(6);
        assertThat(relationship.getLastAnalyzedAt()).isEqualTo(analyzedAt);

        relationship.startAnalysis();
        relationship.failAnalysis();
        assertThat(relationship.getStatus()).isEqualTo(RelationshipStatus.ANALYSIS_FAILED);
    }

    @Test
    void usesSecondKoreanCharacterAsInitial() {
        Relationship relationship = Relationship.draft(UUID.randomUUID(), "홍길동", RelationshipType.COWORKER);
        Relationship spaced = Relationship.draft(UUID.randomUUID(), "큰 상승", RelationshipType.FRIEND);

        assertThat(relationship.getInitial()).isEqualTo("길");
        assertThat(spaced.getInitial()).isEqualTo("상");
    }
}
