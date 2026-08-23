package com.relationshiptemperature.api.report.application;

import com.relationshiptemperature.api.analysis.application.AiAnalysisClient.AnalysisResult;
import com.relationshiptemperature.api.analysis.application.AiAnalysisClient.EvidenceResult;
import com.relationshiptemperature.api.checkin.domain.CheckIn;
import com.relationshiptemperature.api.checkin.repository.CheckInRepository;
import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.repository.RelationshipRepository;
import com.relationshiptemperature.api.report.domain.PrqcComponent;
import com.relationshiptemperature.api.report.domain.RelationshipReport;
import com.relationshiptemperature.api.report.domain.ReportEvidence;
import com.relationshiptemperature.api.report.repository.RelationshipReportRepository;
import com.relationshiptemperature.api.report.repository.ReportEvidenceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final RelationshipReportRepository reportRepository;
    private final ReportEvidenceRepository evidenceRepository;
    private final RelationshipScoringPolicy scoringPolicy;
    private final RelationshipRepository relationshipRepository;
    private final CheckInRepository checkInRepository;

    public ReportService(
            RelationshipReportRepository reportRepository,
            ReportEvidenceRepository evidenceRepository,
            RelationshipScoringPolicy scoringPolicy,
            RelationshipRepository relationshipRepository,
            CheckInRepository checkInRepository
    ) {
        this.reportRepository = reportRepository;
        this.evidenceRepository = evidenceRepository;
        this.scoringPolicy = scoringPolicy;
        this.relationshipRepository = relationshipRepository;
        this.checkInRepository = checkInRepository;
    }

    @Transactional
    public RelationshipReport create(
            UUID jobId,
            UUID checkInId,
            Relationship relationship,
            AnalysisResult result
    ) {
        RelationshipReport existing = reportRepository.findByAnalysisJobId(jobId).orElse(null);
        if (existing != null) {
            return existing;
        }
        validateResult(result);
        CheckIn checkIn = checkInRepository.findByIdAndUserIdAndRelationshipId(
                        checkInId, relationship.getUserId(), relationship.getId()
                )
                .orElseThrow(() -> new ApiException(ErrorCode.CHECK_IN_INCOMPLETE));

        int overall = scoringPolicy.calculate(relationship.getRelationshipType(), result.components());
        LocalDate weekStart = checkIn.getWeekStart();
        Integer previous = reportRepository.findFirstByRelationshipIdAndWeekStartOrderByAnalyzedAtDesc(
                        relationship.getId(), weekStart.minusWeeks(1)
                )
                .map(RelationshipReport::getOverallScore)
                .orElse(null);
        Integer change = previous == null ? null : overall - previous;
        Instant analyzedAt = Instant.now();
        RelationshipReport report = reportRepository.save(new RelationshipReport(
                relationship.getUserId(), relationship.getId(), jobId, overall, change, weekStart,
                result.components(), result.modelVersion(), RelationshipScoringPolicy.VERSION,
                result.selfReportComparison(), analyzedAt
        ));

        List<ReportEvidence> evidences = result.evidences().stream()
                .map(evidence -> toEvidence(report.getId(), result, evidence))
                .toList();
        evidenceRepository.saveAll(evidences);
        relationship.completeAnalysis(overall, change, analyzedAt);
        relationshipRepository.save(relationship);
        return report;
    }

    public RelationshipReport latest(UUID userId, UUID relationshipId) {
        return reportRepository.findFirstByRelationshipIdAndUserIdOrderByAnalyzedAtDesc(relationshipId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_REQUIRED));
    }

    public RelationshipReport getOwned(UUID userId, UUID reportId) {
        return reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_REQUIRED));
    }

    public List<RelationshipReport> trend(UUID userId, UUID relationshipId, int weeks) {
        if (weeks < 4 || weeks > 52) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "추이 조회 기간은 4주에서 52주 사이여야 합니다.");
        }
        RelationshipReport latest = latest(userId, relationshipId);
        LocalDate to = latest.getWeekStart();
        LocalDate from = to.minusWeeks(weeks - 1L);
        List<RelationshipReport> reports = reportRepository
                .findAllByRelationshipIdAndUserIdAndWeekStartBetweenOrderByWeekStartDescAnalyzedAtDesc(
                        relationshipId, userId, from, to
                );

        Map<LocalDate, RelationshipReport> latestByWeek = new LinkedHashMap<>();
        reports.forEach(report -> latestByWeek.putIfAbsent(report.getWeekStart(), report));
        return List.copyOf(latestByWeek.values());
    }

    public List<ReportEvidence> evidences(UUID reportId) {
        return evidenceRepository.findAllByReportId(reportId).stream()
                .sorted(Comparator.comparing(ReportEvidence::getComponent))
                .toList();
    }

    private ReportEvidence toEvidence(UUID reportId, AnalysisResult result, EvidenceResult evidence) {
        PrqcComponent component = PrqcComponent.fromApiCode(evidence.component());
        if (evidence.score() != component.scoreOf(result.components())) {
            throw new IllegalArgumentException("Evidence score must match its PRQC component score");
        }
        return new ReportEvidence(reportId, component, evidence.score(), evidence.summary(), evidence.metric());
    }

    private void validateResult(AnalysisResult result) {
        if (result == null || result.components() == null || result.evidences() == null
                || result.evidences().isEmpty()) {
            throw new IllegalArgumentException("Analysis result must include PRQC scores and evidence");
        }
        if (result.modelVersion() == null || result.modelVersion().isBlank()) {
            throw new IllegalArgumentException("Analysis modelVersion must not be blank");
        }
    }
}
