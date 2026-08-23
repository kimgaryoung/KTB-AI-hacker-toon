package com.relationshiptemperature.api.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisJobTest {

    @Test
    void estimatedProgressNeverReachesOneHundredBeforeCompletion() {
        AnalysisJob job = new AnalysisJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        job.progress(AnalysisStage.CALCULATING_RELATIONSHIP_SCORE, 100);

        assertThat(job.getProgress()).isEqualTo(95);
        assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
    }

    @Test
    void completionSetsOneHundredAndReportId() {
        AnalysisJob job = new AnalysisJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID reportId = UUID.randomUUID();

        job.complete(reportId);

        assertThat(job.getProgress()).isEqualTo(100);
        assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
        assertThat(job.getReportId()).isEqualTo(reportId);
    }
}
