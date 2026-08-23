package com.relationshiptemperature.api.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relationshiptemperature.api.report.domain.ReportEvidence.Metric;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportEvidenceTest {

    @Test
    void mapsApiComponentAndStoresStructuredMetric() {
        Metric metric = new Metric("weeklyConversationCount", 1.1, 3.2, "회/주", "최근 4주 vs 이전 4주");
        ReportEvidence evidence = new ReportEvidence(
                UUID.randomUUID(), PrqcComponent.fromApiCode("passion"), 40, "대화 빈도 감소가 관찰됐어요.", metric
        );

        assertThat(evidence.getComponent()).isEqualTo(PrqcComponent.PASSION);
        assertThat(evidence.getComponent().apiCode()).isEqualTo("passion");
        assertThat(evidence.getMetric()).isEqualTo(metric);
    }

    @Test
    void rejectsInvalidEvidenceAndMetric() {
        assertThatThrownBy(() -> new ReportEvidence(
                UUID.randomUUID(), PrqcComponent.TRUST, 101, "근거", null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Metric("metric", null, null, "회", "최근 4주"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrqcComponent.fromApiCode("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
