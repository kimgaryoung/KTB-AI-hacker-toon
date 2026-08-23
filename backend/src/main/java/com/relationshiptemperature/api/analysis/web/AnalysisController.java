package com.relationshiptemperature.api.analysis.web;

import com.relationshiptemperature.api.analysis.application.AnalysisService;
import com.relationshiptemperature.api.analysis.domain.AnalysisJob;
import com.relationshiptemperature.api.analysis.domain.AnalysisJobStatus;
import com.relationshiptemperature.api.analysis.domain.AnalysisStage;
import com.relationshiptemperature.api.auth.application.CurrentUserService;
import com.relationshiptemperature.api.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final CurrentUserService currentUserService;
    private final AnalysisService analysisService;

    public AnalysisController(CurrentUserService currentUserService, AnalysisService analysisService) {
        this.currentUserService = currentUserService;
        this.analysisService = analysisService;
    }

    @PostMapping("/relationships/{relationshipId}/analyses")
    ResponseEntity<ApiResponse<AnalysisJobResponse>> start(
            @PathVariable UUID relationshipId,
            @Valid @RequestBody StartAnalysisRequest request
    ) {
        AnalysisJob job = analysisService.start(
                currentUserService.requireUserId(), relationshipId, request.conversationFileId(), request.checkInId()
        );
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/analysis-jobs/" + job.getId()))
                .body(ApiResponse.of(AnalysisJobResponse.from(job)));
    }

    @GetMapping("/analysis-jobs/{jobId}")
    ApiResponse<AnalysisJobResponse> get(@PathVariable UUID jobId) {
        return ApiResponse.of(AnalysisJobResponse.from(
                analysisService.getOwned(currentUserService.requireUserId(), jobId)
        ));
    }

    record StartAnalysisRequest(@NotNull UUID conversationFileId, @NotNull UUID checkInId) {}

    record Failure(String code, String message, Boolean retryable) {}

    record AnalysisJobResponse(
            UUID id,
            UUID relationshipId,
            AnalysisJobStatus status,
            AnalysisStage stage,
            int progress,
            Integer estimatedSecondsRemaining,
            UUID reportId,
            Failure failure,
            Instant createdAt,
            Instant updatedAt
    ) {
        static AnalysisJobResponse from(AnalysisJob job) {
            Failure failure = job.getFailureCode() == null ? null : new Failure(
                    job.getFailureCode(), job.getFailureMessage(), job.getFailureRetryable()
            );
            Integer remaining = job.getStatus() == AnalysisJobStatus.SUCCEEDED ? 0 : null;
            return new AnalysisJobResponse(
                    job.getId(), job.getRelationshipId(), job.getStatus(), job.getStage(), job.getProgress(),
                    remaining, job.getReportId(), failure, job.getCreatedAt(), job.getUpdatedAt()
            );
        }
    }
}
