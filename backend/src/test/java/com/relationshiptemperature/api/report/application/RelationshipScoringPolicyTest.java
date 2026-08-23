package com.relationshiptemperature.api.report.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import org.junit.jupiter.api.Test;

class RelationshipScoringPolicyTest {

    private final RelationshipScoringPolicy policy = new RelationshipScoringPolicy();

    @Test
    void skeletonPolicyUsesRoundedSixComponentAverage() {
        PrqcScores scores = new PrqcScores(55, 45, 68, 72, 40, 58);

        int overall = policy.calculate(RelationshipType.FRIEND, scores);

        assertThat(overall).isEqualTo(56);
    }
}
