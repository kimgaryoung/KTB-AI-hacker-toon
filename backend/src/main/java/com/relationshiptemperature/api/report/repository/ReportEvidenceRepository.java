package com.relationshiptemperature.api.report.repository;

import com.relationshiptemperature.api.report.domain.ReportEvidence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence, UUID> {

    List<ReportEvidence> findAllByReportId(UUID reportId);
}
