package com.relationshiptemperature.api.report.web;

import com.relationshiptemperature.api.auth.application.CurrentUserService;
import com.relationshiptemperature.api.common.api.ApiResponse;
import com.relationshiptemperature.api.relationship.application.RelationshipService;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.report.application.ReportService;
import com.relationshiptemperature.api.report.domain.RelationshipReport;
import com.relationshiptemperature.api.report.domain.ReportEvidence;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/relationships/{relationshipId}/report")
public class ReportController {

    private final CurrentUserService currentUserService;
    private final RelationshipService relationshipService;
    private final ReportService reportService;

    public ReportController(
            CurrentUserService currentUserService,
            RelationshipService relationshipService,
            ReportService reportService
    ) {
        this.currentUserService = currentUserService;
        this.relationshipService = relationshipService;
        this.reportService = reportService;
    }

    @GetMapping
    ApiResponse<ReportResponse> get(
            @PathVariable UUID relationshipId,
            @RequestParam(defaultValue = "8") int weeks
    ) {
        UUID userId = currentUserService.requireUserId();
        Relationship relationship = relationshipService.getOwned(userId, relationshipId);
        RelationshipReport report = reportService.latest(userId, relationshipId);
        List<ReportEvidence> evidences = reportService.evidences(report.getId());
        List<RelationshipReport> rawTrend = new ArrayList<>(reportService.trend(userId, relationshipId, weeks));
        Collections.reverse(rawTrend);
        return ApiResponse.of(ReportResponse.from(relationship, report, evidences, rawTrend));
    }

    record RelationshipIdentity(UUID id, String name, String initial, RelationshipType relationshipType) {}

    record Overall(int score, Integer change, String statusCode, String statusLabel) {}

    record EvidenceMetric(String name, Double currentValue, Double previousValue, String unit, String period) {}

    record EvidenceResponse(
            UUID id,
            String component,
            int score,
            String summary,
            EvidenceMetric metric
    ) {}

    record TrendPoint(LocalDate weekStart, String label, int score) {}

    record ReportResponse(
            UUID id,
            RelationshipIdentity relationship,
            Overall overall,
            RelationshipReport.PrqcScores prqc,
            List<EvidenceResponse> evidences,
            List<TrendPoint> trend,
            Instant analyzedAt,
            String modelVersion,
            String scoringPolicyVersion,
            String selfReportComparison,
            String disclaimer
    ) {
        static ReportResponse from(
                Relationship relationship,
                RelationshipReport report,
                List<ReportEvidence> evidences,
                List<RelationshipReport> trend
        ) {
            return new ReportResponse(
                    report.getId(),
                    new RelationshipIdentity(
                            relationship.getId(), relationship.getName(), relationship.getInitial(),
                            relationship.getRelationshipType()
                    ),
                    overall(report),
                    report.getPrqcScores(),
                    evidences.stream().map(ReportController::fromEvidence).toList(),
                    trend.stream().map(item -> new TrendPoint(
                            item.getWeekStart(), trendLabel(item.getWeekStart(), report.getWeekStart()),
                            item.getOverallScore()
                    )).toList(),
                    report.getAnalyzedAt(),
                    report.getModelVersion(),
                    report.getScoringPolicyVersion(),
                    report.getSelfReportComparison(),
                    report.getDisclaimer()
            );
        }

        private static Overall overall(RelationshipReport report) {
            return new Overall(
                    report.getOverallScore(), report.getScoreChange(),
                    report.getStatusCode().name(), report.getStatusLabel()
            );
        }

        private static String trendLabel(LocalDate weekStart, LocalDate latestWeekStart) {
            long weeksAgo = ChronoUnit.WEEKS.between(weekStart, latestWeekStart);
            if (weeksAgo == 0) return "이번 주";
            if (weeksAgo == 1) return "지난 주";
            return weeksAgo + "주 전";
        }
    }

    private static EvidenceResponse fromEvidence(ReportEvidence evidence) {
        ReportEvidence.Metric metric = evidence.getMetric();
        return new EvidenceResponse(
                evidence.getId(), evidence.getComponent().apiCode(), evidence.getScore(), evidence.getSummary(),
                metric == null ? null : new EvidenceMetric(
                        metric.name(), metric.currentValue(), metric.previousValue(), metric.unit(), metric.period()
                )
        );
    }
}
