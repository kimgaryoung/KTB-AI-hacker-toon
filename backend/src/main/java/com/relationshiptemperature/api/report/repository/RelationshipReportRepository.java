package com.relationshiptemperature.api.report.repository;

import com.relationshiptemperature.api.report.domain.RelationshipReport;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RelationshipReportRepository extends JpaRepository<RelationshipReport, UUID> {

    Optional<RelationshipReport> findByAnalysisJobId(UUID analysisJobId);

    Optional<RelationshipReport> findByIdAndUserId(UUID id, UUID userId);

    Optional<RelationshipReport> findFirstByRelationshipIdAndWeekStartOrderByAnalyzedAtDesc(
            UUID relationshipId,
            LocalDate weekStart
    );

    Optional<RelationshipReport> findFirstByRelationshipIdAndUserIdOrderByAnalyzedAtDesc(UUID relationshipId, UUID userId);

    List<RelationshipReport> findAllByRelationshipIdAndUserIdAndWeekStartBetweenOrderByWeekStartDescAnalyzedAtDesc(
            UUID relationshipId,
            UUID userId,
            LocalDate from,
            LocalDate to
    );

    List<RelationshipReport> findAllByRelationshipIdAndUserIdAndWeekStartBeforeOrderByWeekStartAscAnalyzedAtAsc(
            UUID relationshipId,
            UUID userId,
            LocalDate weekStart
    );

    @Query("""
            SELECT report
            FROM RelationshipReport report
            WHERE report.userId = :userId
              AND report.weekStart <= :weekStart
              AND report.weekStart = (
                  SELECT MAX(candidate.weekStart)
                  FROM RelationshipReport candidate
                  WHERE candidate.userId = :userId
                    AND candidate.relationshipId = report.relationshipId
                    AND candidate.weekStart <= :weekStart
              )
              AND report.analyzedAt = (
                  SELECT MAX(revision.analyzedAt)
                  FROM RelationshipReport revision
                  WHERE revision.userId = :userId
                    AND revision.relationshipId = report.relationshipId
                    AND revision.weekStart = report.weekStart
              )
            """)
    List<RelationshipReport> findLatestAsOfWeek(
            @Param("userId") UUID userId,
            @Param("weekStart") LocalDate weekStart
    );

    List<RelationshipReport> findAllByUserIdAndWeekStartBetweenOrderByRelationshipIdAscWeekStartAscAnalyzedAtDesc(
            UUID userId,
            LocalDate from,
            LocalDate to
    );
}
