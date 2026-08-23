package com.relationshiptemperature.api.analysis.repository;

import com.relationshiptemperature.api.analysis.domain.AnalysisJob;
import com.relationshiptemperature.api.analysis.domain.AnalysisJobStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {

    Optional<AnalysisJob> findByIdAndUserId(UUID id, UUID userId);

    Optional<AnalysisJob> findFirstByRelationshipIdAndStatusInOrderByCreatedAtDesc(
            UUID relationshipId,
            Collection<AnalysisJobStatus> statuses
    );
}
