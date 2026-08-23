package com.relationshiptemperature.api.report.domain;

import com.relationshiptemperature.api.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "relationship_reports", indexes = {
        @Index(name = "idx_report_relationship_analyzed", columnList = "relationship_id,analyzed_at"),
        @Index(name = "idx_report_relationship_week", columnList = "relationship_id,week_start,analyzed_at"),
        @Index(name = "idx_report_user_week", columnList = "user_id,week_start,relationship_id,analyzed_at")
})
public class RelationshipReport extends BaseEntity {

    public static final String DEFAULT_DISCLAIMER =
            "대화에서 관찰된 패턴을 바탕으로 한 참고 정보이며 관계를 진단하거나 단정하지 않습니다.";

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "relationship_id", nullable = false)
    private UUID relationshipId;

    @Column(name = "analysis_job_id", nullable = false, unique = true)
    private UUID analysisJobId;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "score_change")
    private Integer scoreChange;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_code", nullable = false, length = 30)
    private ReportStatus statusCode;

    @Column(name = "status_label", nullable = false, length = 50)
    private String statusLabel;

    @Column(nullable = false, length = 1000)
    private String disclaimer;

    @Column(nullable = false)
    private int satisfaction;

    @Column(nullable = false)
    private int commitment;

    @Column(nullable = false)
    private int intimacy;

    @Column(nullable = false)
    private int trust;

    @Column(nullable = false)
    private int passion;

    @Column(nullable = false)
    private int love;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "scoring_policy_version", nullable = false, length = 100)
    private String scoringPolicyVersion;

    @Column(name = "self_report_comparison", nullable = false, length = 2000)
    private String selfReportComparison;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    protected RelationshipReport() {
    }

    public RelationshipReport(
            UUID userId,
            UUID relationshipId,
            UUID analysisJobId,
            int overallScore,
            Integer scoreChange,
            LocalDate weekStart,
            PrqcScores scores,
            String modelVersion,
            String scoringPolicyVersion,
            String selfReportComparison,
            Instant analyzedAt
    ) {
        validateScore(overallScore, "overallScore");
        if (scoreChange != null && (scoreChange < -100 || scoreChange > 100)) {
            throw new IllegalArgumentException("scoreChange must be between -100 and 100");
        }
        this.userId = userId;
        this.relationshipId = relationshipId;
        this.analysisJobId = analysisJobId;
        this.overallScore = overallScore;
        this.scoreChange = scoreChange;
        this.weekStart = Objects.requireNonNull(weekStart, "weekStart");
        this.statusCode = ReportStatus.fromScore(overallScore);
        this.statusLabel = statusCode.label();
        this.disclaimer = DEFAULT_DISCLAIMER;
        Objects.requireNonNull(scores, "scores");
        this.satisfaction = scores.satisfaction();
        this.commitment = scores.commitment();
        this.intimacy = scores.intimacy();
        this.trust = scores.trust();
        this.passion = scores.passion();
        this.love = scores.love();
        this.modelVersion = requireText(modelVersion, "modelVersion");
        this.scoringPolicyVersion = requireText(scoringPolicyVersion, "scoringPolicyVersion");
        this.selfReportComparison = selfReportComparison == null ? "" : selfReportComparison.trim();
        this.analyzedAt = Objects.requireNonNull(analyzedAt, "analyzedAt");
    }

    public UUID getUserId() { return userId; }
    public UUID getRelationshipId() { return relationshipId; }
    public UUID getAnalysisJobId() { return analysisJobId; }
    public int getOverallScore() { return overallScore; }
    public Integer getScoreChange() { return scoreChange; }
    public LocalDate getWeekStart() { return weekStart; }
    public ReportStatus getStatusCode() { return statusCode; }
    public String getStatusLabel() { return statusLabel; }
    public String getDisclaimer() { return disclaimer; }
    public PrqcScores getPrqcScores() {
        return new PrqcScores(satisfaction, commitment, intimacy, trust, passion, love);
    }
    public String getModelVersion() { return modelVersion; }
    public String getScoringPolicyVersion() { return scoringPolicyVersion; }
    public String getSelfReportComparison() { return selfReportComparison; }
    public Instant getAnalyzedAt() { return analyzedAt; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void validateScore(int score, String field) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
    }

    public record PrqcScores(int satisfaction, int commitment, int intimacy, int trust, int passion, int love) {
        public PrqcScores {
            validateScore(satisfaction, "satisfaction");
            validateScore(commitment, "commitment");
            validateScore(intimacy, "intimacy");
            validateScore(trust, "trust");
            validateScore(passion, "passion");
            validateScore(love, "love");
        }
    }
}
