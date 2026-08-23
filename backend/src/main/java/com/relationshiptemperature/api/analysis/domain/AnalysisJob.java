package com.relationshiptemperature.api.analysis.domain;

import com.relationshiptemperature.api.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "analysis_jobs", indexes = {
        @Index(name = "idx_analysis_relationship_status", columnList = "relationship_id,status")
})
public class AnalysisJob extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "relationship_id", nullable = false)
    private UUID relationshipId;

    @Column(name = "conversation_file_id", nullable = false)
    private UUID conversationFileId;

    @Column(name = "check_in_id", nullable = false)
    private UUID checkInId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AnalysisStage stage;

    @Column(nullable = false)
    private int progress;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "failure_retryable")
    private Boolean failureRetryable;

    protected AnalysisJob() {
    }

    public AnalysisJob(UUID userId, UUID relationshipId, UUID conversationFileId, UUID checkInId) {
        this.userId = userId;
        this.relationshipId = relationshipId;
        this.conversationFileId = conversationFileId;
        this.checkInId = checkInId;
        this.status = AnalysisJobStatus.QUEUED;
        this.stage = AnalysisStage.LOADING_CONVERSATION;
        this.progress = 0;
    }

    public void progress(AnalysisStage stage, int progress) {
        if (status == AnalysisJobStatus.SUCCEEDED || status == AnalysisJobStatus.FAILED) {
            return;
        }
        this.status = AnalysisJobStatus.RUNNING;
        this.stage = stage;
        this.progress = Math.min(progress, 95);
    }

    public void complete(UUID reportId) {
        this.status = AnalysisJobStatus.SUCCEEDED;
        this.stage = AnalysisStage.CALCULATING_RELATIONSHIP_SCORE;
        this.progress = 100;
        this.reportId = reportId;
    }

    public void fail(String code, String message, boolean retryable) {
        this.status = AnalysisJobStatus.FAILED;
        this.failureCode = code;
        this.failureMessage = message;
        this.failureRetryable = retryable;
    }

    public UUID getUserId() { return userId; }
    public UUID getRelationshipId() { return relationshipId; }
    public UUID getConversationFileId() { return conversationFileId; }
    public UUID getCheckInId() { return checkInId; }
    public AnalysisJobStatus getStatus() { return status; }
    public AnalysisStage getStage() { return stage; }
    public int getProgress() { return progress; }
    public UUID getReportId() { return reportId; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public Boolean getFailureRetryable() { return failureRetryable; }
}
