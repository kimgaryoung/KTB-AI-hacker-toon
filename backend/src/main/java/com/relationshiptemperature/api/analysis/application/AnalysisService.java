package com.relationshiptemperature.api.analysis.application;

import com.relationshiptemperature.api.analysis.domain.AnalysisJob;
import com.relationshiptemperature.api.analysis.domain.AnalysisJobStatus;
import com.relationshiptemperature.api.analysis.repository.AnalysisJobRepository;
import com.relationshiptemperature.api.checkin.repository.CheckInRepository;
import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.conversation.domain.ConversationFileStatus;
import com.relationshiptemperature.api.conversation.repository.ConversationFileRepository;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.repository.RelationshipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalysisService {

    private final AnalysisJobRepository jobRepository;
    private final RelationshipRepository relationshipRepository;
    private final ConversationFileRepository fileRepository;
    private final CheckInRepository checkInRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AnalysisService(
            AnalysisJobRepository jobRepository,
            RelationshipRepository relationshipRepository,
            ConversationFileRepository fileRepository,
            CheckInRepository checkInRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.jobRepository = jobRepository;
        this.relationshipRepository = relationshipRepository;
        this.fileRepository = fileRepository;
        this.checkInRepository = checkInRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AnalysisJob start(UUID userId, UUID relationshipId, UUID conversationFileId, UUID checkInId) {
        Relationship relationship = relationshipRepository.findByIdAndUserId(relationshipId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RELATIONSHIP_NOT_FOUND));
        jobRepository.findFirstByRelationshipIdAndStatusInOrderByCreatedAtDesc(
                relationshipId,
                List.of(AnalysisJobStatus.QUEUED, AnalysisJobStatus.RUNNING)
        ).ifPresent(existing -> {
            throw new ApiException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        });
        fileRepository.findByIdAndUserIdAndRelationshipIdAndValidationStatus(
                conversationFileId, userId, relationshipId, ConversationFileStatus.VALID
        ).orElseThrow(() -> new ApiException(ErrorCode.INVALID_KAKAO_EXPORT));
        checkInRepository.findByIdAndUserIdAndRelationshipId(checkInId, userId, relationshipId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHECK_IN_INCOMPLETE));

        relationship.startAnalysis();
        AnalysisJob job = jobRepository.save(new AnalysisJob(userId, relationshipId, conversationFileId, checkInId));
        eventPublisher.publishEvent(new AnalysisRequestedEvent(job.getId()));
        return job;
    }

    public AnalysisJob getOwned(UUID userId, UUID jobId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
