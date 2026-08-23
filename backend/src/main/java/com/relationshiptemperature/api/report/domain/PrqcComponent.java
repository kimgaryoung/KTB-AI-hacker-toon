package com.relationshiptemperature.api.report.domain;

import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;

public enum PrqcComponent {
    SATISFACTION("satisfaction") {
        @Override
        public int scoreOf(PrqcScores scores) { return scores.satisfaction(); }
    },
    COMMITMENT("commitment") {
        @Override
        public int scoreOf(PrqcScores scores) { return scores.commitment(); }
    },
    INTIMACY("intimacy") {
        @Override
        public int scoreOf(PrqcScores scores) { return scores.intimacy(); }
    },
    TRUST("trust") {
        @Override
        public int scoreOf(PrqcScores scores) { return scores.trust(); }
    },
    PASSION("passion") {
        @Override
        public int scoreOf(PrqcScores scores) { return scores.passion(); }
    },
    LOVE("love") {
        @Override
        public int scoreOf(PrqcScores scores) { return scores.love(); }
    };

    private final String apiCode;

    PrqcComponent(String apiCode) {
        this.apiCode = apiCode;
    }

    public String apiCode() {
        return apiCode;
    }

    public abstract int scoreOf(PrqcScores scores);

    public static PrqcComponent fromApiCode(String value) {
        if (value != null) {
            for (PrqcComponent component : values()) {
                if (component.apiCode.equalsIgnoreCase(value) || component.name().equalsIgnoreCase(value)) {
                    return component;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported PRQC component: " + value);
    }
}
