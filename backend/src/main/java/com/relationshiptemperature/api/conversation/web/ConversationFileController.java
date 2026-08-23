package com.relationshiptemperature.api.conversation.web;

import com.relationshiptemperature.api.auth.application.CurrentUserService;
import com.relationshiptemperature.api.common.api.ApiResponse;
import com.relationshiptemperature.api.conversation.application.ConversationFileService;
import com.relationshiptemperature.api.conversation.domain.ConversationFile;
import com.relationshiptemperature.api.conversation.domain.ConversationFileStatus;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ConversationFileController {

    private final CurrentUserService currentUserService;
    private final ConversationFileService fileService;

    public ConversationFileController(CurrentUserService currentUserService, ConversationFileService fileService) {
        this.currentUserService = currentUserService;
        this.fileService = fileService;
    }

    @PostMapping("/relationships/{relationshipId}/conversation-files")
    ResponseEntity<ApiResponse<ConversationFileResponse>> upload(
            @PathVariable UUID relationshipId,
            @RequestPart MultipartFile file,
            @RequestParam(defaultValue = "") String source,
            @RequestParam(required = false) String selfParticipantName
    ) {
        if (!"KAKAO_TALK".equals(source)) {
            throw new com.relationshiptemperature.api.common.error.ApiException(
                    com.relationshiptemperature.api.common.error.ErrorCode.UNSUPPORTED_FILE_TYPE
            );
        }
        ConversationFile uploaded = fileService.upload(
                currentUserService.requireUserId(), relationshipId, file, selfParticipantName
        );
        return ResponseEntity.created(URI.create("/api/v1/conversation-files/" + uploaded.getId()))
                .body(ApiResponse.of(ConversationFileResponse.from(uploaded)));
    }

    @GetMapping("/conversation-files/{fileId}")
    ApiResponse<ConversationFileResponse> get(@PathVariable UUID fileId) {
        return ApiResponse.of(ConversationFileResponse.from(
                fileService.getOwned(currentUserService.requireUserId(), fileId)
        ));
    }

    @DeleteMapping("/conversation-files/{fileId}")
    ResponseEntity<Void> delete(@PathVariable UUID fileId) {
        fileService.delete(currentUserService.requireUserId(), fileId);
        return ResponseEntity.noContent().build();
    }

    record ConversationFileResponse(
            UUID id,
            UUID relationshipId,
            String originalFileName,
            long sizeBytes,
            String source,
            String selfParticipantName,
            String otherParticipantName,
            boolean testFixture,
            ConversationFileStatus validationStatus,
            Integer messageCount,
            Instant conversationStartedAt,
            Instant conversationEndedAt,
            Instant expiresAt,
            Instant uploadedAt
    ) {
        static ConversationFileResponse from(ConversationFile file) {
            return new ConversationFileResponse(
                    file.getId(), file.getRelationshipId(), file.getOriginalFileName(), file.getSizeBytes(),
                    "KAKAO_TALK", file.getSelfParticipantName(), file.getOtherParticipantName(),
                    file.isTestFixture(),
                    file.getValidationStatus(), file.getMessageCount(),
                    file.getConversationStartedAt(), file.getConversationEndedAt(), file.getExpiresAt(), file.getCreatedAt()
            );
        }
    }
}
