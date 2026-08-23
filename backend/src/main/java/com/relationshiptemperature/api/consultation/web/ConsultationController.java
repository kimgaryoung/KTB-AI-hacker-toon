package com.relationshiptemperature.api.consultation.web;

import com.relationshiptemperature.api.auth.application.CurrentUserService;
import com.relationshiptemperature.api.common.api.ApiResponse;
import com.relationshiptemperature.api.common.api.PagedResponse;
import com.relationshiptemperature.api.consultation.application.ChatStreamService;
import com.relationshiptemperature.api.consultation.application.ConsultationService;
import com.relationshiptemperature.api.consultation.domain.ChatMessage;
import com.relationshiptemperature.api.consultation.domain.ChatRole;
import com.relationshiptemperature.api.consultation.domain.Consultation;
import com.relationshiptemperature.api.consultation.domain.MessageStatus;
import com.relationshiptemperature.api.relationship.application.RelationshipService;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/consultations")
public class ConsultationController {

    private final CurrentUserService currentUserService;
    private final ConsultationService consultationService;
    private final RelationshipService relationshipService;
    private final ChatStreamService chatStreamService;

    public ConsultationController(
            CurrentUserService currentUserService,
            ConsultationService consultationService,
            RelationshipService relationshipService,
            ChatStreamService chatStreamService
    ) {
        this.currentUserService = currentUserService;
        this.consultationService = consultationService;
        this.relationshipService = relationshipService;
        this.chatStreamService = chatStreamService;
    }

    @GetMapping
    PagedResponse<ConsultationResponse> list() {
        UUID userId = currentUserService.requireUserId();
        return PagedResponse.singlePage(consultationService.list(userId).stream()
                .map(item -> response(item, relationshipService.getOwned(userId, item.getRelationshipId())))
                .toList());
    }

    @PostMapping
    ResponseEntity<ApiResponse<ConsultationResponse>> create(@Valid @RequestBody CreateConsultationRequest request) {
        UUID userId = currentUserService.requireUserId();
        Consultation consultation = consultationService.create(userId, request.relationshipId());
        Relationship relationship = relationshipService.getOwned(userId, request.relationshipId());
        return ResponseEntity.created(URI.create("/api/v1/consultations/" + consultation.getId()))
                .body(ApiResponse.of(response(consultation, relationship)));
    }

    @GetMapping("/{consultationId}")
    ApiResponse<ConsultationResponse> get(@PathVariable String consultationId) {
        UUID userId = currentUserService.requireUserId();
        Consultation consultation = consultationService.getOwned(userId, consultationId);
        return ApiResponse.of(response(
                consultation,
                relationshipService.getOwned(userId, consultation.getRelationshipId())
        ));
    }

    @DeleteMapping("/{consultationId}")
    ResponseEntity<Void> delete(@PathVariable String consultationId) {
        consultationService.delete(currentUserService.requireUserId(), consultationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{consultationId}/messages")
    PagedResponse<MessageResponse> messages(@PathVariable String consultationId) {
        return PagedResponse.singlePage(consultationService.messages(
                currentUserService.requireUserId(), consultationId
        ).stream().map(MessageResponse::from).toList());
    }

    @PostMapping("/{consultationId}/messages")
    ResponseEntity<ApiResponse<MessageAcceptedResponse>> send(
            @PathVariable String consultationId,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        ConsultationService.AcceptedMessage accepted = consultationService.send(
                currentUserService.requireUserId(), consultationId, request.content()
        );
        return ResponseEntity.accepted().body(ApiResponse.of(new MessageAcceptedResponse(
                MessageResponse.from(accepted.userMessage()),
                MessageResponse.from(accepted.assistantMessage()),
                "/api/v1/consultations/" + consultationId + "/events?after=" + accepted.userMessage().getId()
        )));
    }

    @GetMapping(path = "/{consultationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(
            @PathVariable String consultationId,
            @RequestParam String after,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId
    ) {
        consultationService.getOwned(currentUserService.requireUserId(), consultationId);
        return chatStreamService.subscribe(consultationId, after);
    }

    record CreateConsultationRequest(@NotNull UUID relationshipId) {}
    record CreateMessageRequest(@NotBlank @Size(max = 4000) String content) {}
    record RelationshipIdentity(UUID id, String name, String initial, String relationshipType) {}
    record ConsultationResponse(
            String id,
            RelationshipIdentity relationship,
            UUID reportId,
            String lastMessagePreview,
            Instant lastMessageAt,
            int unreadCount,
            Instant createdAt,
            Instant updatedAt
    ) {}
    record EvidenceReference(String evidenceId, String label) {}
    record ResourceQuery(String category, String region) {}
    record SafetyNotice(String type, String title, String message, ResourceQuery resourceQuery) {}
    record MessageResponse(
            String id,
            ChatRole role,
            String content,
            MessageStatus status,
            List<EvidenceReference> evidenceRefs,
            SafetyNotice safetyNotice,
            Instant createdAt
    ) {
        static MessageResponse from(ChatMessage message) {
            SafetyNotice notice = message.getSafetyNotice() == null ? null : new SafetyNotice(
                    message.getSafetyNotice().type(), message.getSafetyNotice().title(),
                    message.getSafetyNotice().message(),
                    message.getSafetyNotice().resourceQuery() == null ? null : new ResourceQuery(
                            message.getSafetyNotice().resourceQuery().category(),
                            message.getSafetyNotice().resourceQuery().region()
                    )
            );
            return new MessageResponse(
                    message.getId(), message.getRole(), message.getContent(), message.getStatus(),
                    message.getEvidenceRefs().stream()
                            .map(item -> new EvidenceReference(item.evidenceId(), item.label()))
                            .toList(),
                    notice,
                    message.getCreatedAt()
            );
        }
    }
    record MessageAcceptedResponse(
            MessageResponse userMessage,
            MessageResponse assistantMessage,
            String streamUrl
    ) {}

    private ConsultationResponse response(Consultation consultation, Relationship relationship) {
        return new ConsultationResponse(
                consultation.getId(),
                new RelationshipIdentity(
                        relationship.getId(), relationship.getName(), relationship.getInitial(),
                        relationship.getRelationshipType().name()
                ),
                consultation.getReportId(), consultation.getLastMessagePreview(), consultation.getLastMessageAt(), 0,
                consultation.getCreatedAt(), consultation.getUpdatedAt()
        );
    }
}
