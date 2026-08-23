package com.relationshiptemperature.api.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelationshipReportTest {

    @Test
    void snapshotsStatusWeekAndDisclaimerAtCreation() {
        RelationshipReport report = new RelationshipReport(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                82, 7, LocalDate.of(2026, 8, 17),
                new PrqcScores(80, 81, 82, 83, 84, 85),
                "prqc-2026-08-19.1", "relationship-temperature-1.0.0",
                "체크인과 대화 분석을 비교한 설명", Instant.now()
        );

        assertThat(report.getStatusCode()).isEqualTo(ReportStatus.HEALTHY);
        assertThat(report.getStatusLabel()).isEqualTo("건강한 관계");
        assertThat(report.getWeekStart()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(report.getDisclaimer()).isEqualTo(RelationshipReport.DEFAULT_DISCLAIMER);
        assertThat(report.getSelfReportComparison()).isEqualTo("체크인과 대화 분석을 비교한 설명");
    }

    @Test
    void rejectsOutOfRangePrqcAndOverallScores() {
        assertThatThrownBy(() -> new PrqcScores(101, 50, 50, 50, 50, 50))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RelationshipReport(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                -1, null, LocalDate.of(2026, 8, 17),
                new PrqcScores(50, 50, 50, 50, 50, 50),
                "model", "policy", "설명", Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
