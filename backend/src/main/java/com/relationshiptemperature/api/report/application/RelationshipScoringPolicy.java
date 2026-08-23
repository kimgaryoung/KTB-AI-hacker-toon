package com.relationshiptemperature.api.report.application;

import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import org.springframework.stereotype.Component;

@Component
public class RelationshipScoringPolicy {

    public static final String VERSION = "relationship-temperature-1.0.0";

    public int calculate(RelationshipType type, PrqcScores scores) {
        // TODO(scoring): 제품 합의가 끝나면 관계 유형별 가중치 테이블로 교체한다.
        double total = scores.satisfaction()
                + scores.commitment()
                + scores.intimacy()
                + scores.trust()
                + scores.passion()
                + scores.love();
        return (int) Math.round(total / 6.0);
    }
}
