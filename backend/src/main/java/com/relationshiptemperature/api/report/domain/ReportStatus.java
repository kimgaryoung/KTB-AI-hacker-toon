package com.relationshiptemperature.api.report.domain;

public enum ReportStatus {
    HEALTHY("건강한 관계"),
    GOOD("양호"),
    NEEDS_ATTENTION("주의 필요"),
    CHANGE_DETECTED("변화 감지");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ReportStatus fromScore(int score) {
        if (score >= 80) return HEALTHY;
        if (score >= 60) return GOOD;
        if (score >= 40) return NEEDS_ATTENTION;
        return CHANGE_DETECTED;
    }
}
