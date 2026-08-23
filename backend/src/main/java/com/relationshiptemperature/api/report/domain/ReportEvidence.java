package com.relationshiptemperature.api.report.domain;

import com.relationshiptemperature.api.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "report_evidences", indexes = @Index(name = "idx_evidence_report", columnList = "report_id"))
public class ReportEvidence extends BaseEntity {

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrqcComponent component;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "metric_name", length = 100)
    private String metricName;

    @Column(name = "current_value")
    private Double currentValue;

    @Column(name = "previous_value")
    private Double previousValue;

    @Column(name = "metric_unit", length = 30)
    private String metricUnit;

    @Column(name = "metric_period", length = 100)
    private String metricPeriod;

    protected ReportEvidence() {
    }

    public ReportEvidence(UUID reportId, PrqcComponent component, int score, String summary, Metric metric) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Evidence score must be between 0 and 100");
        }
        if (summary == null || summary.isBlank() || summary.length() > 1000) {
            throw new IllegalArgumentException("Evidence summary must be between 1 and 1000 characters");
        }
        this.reportId = Objects.requireNonNull(reportId, "reportId");
        this.component = Objects.requireNonNull(component, "component");
        this.score = score;
        this.summary = summary;
        if (metric != null) {
            this.metricName = metric.name();
            this.currentValue = metric.currentValue();
            this.previousValue = metric.previousValue();
            this.metricUnit = metric.unit();
            this.metricPeriod = metric.period();
        }
    }

    public UUID getReportId() { return reportId; }
    public PrqcComponent getComponent() { return component; }
    public int getScore() { return score; }
    public String getSummary() { return summary; }
    public Metric getMetric() {
        return metricName == null ? null : new Metric(metricName, currentValue, previousValue, metricUnit, metricPeriod);
    }

    public record Metric(String name, Double currentValue, Double previousValue, String unit, String period) {
        public Metric {
            if (name == null || name.isBlank() || name.length() > 100) {
                throw new IllegalArgumentException("Metric name must be between 1 and 100 characters");
            }
            if (currentValue == null) {
                throw new IllegalArgumentException("Metric currentValue must not be null");
            }
            if (unit == null || unit.isBlank() || unit.length() > 30) {
                throw new IllegalArgumentException("Metric unit must be between 1 and 30 characters");
            }
            if (period == null || period.isBlank() || period.length() > 100) {
                throw new IllegalArgumentException("Metric period must be between 1 and 100 characters");
            }
        }
    }
}
