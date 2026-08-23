package com.relationshiptemperature.api.consultation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_messages")
@CompoundIndex(name = "idx_message_consultation_created", def = "{'consultationId': 1, 'createdAt': 1}")
public class ChatMessage {

    @Id
    private String id;
    private String consultationId;
    private String replyToMessageId;
    private ChatRole role;
    private String content;
    private MessageStatus status;
    private List<EvidenceReference> evidenceRefs;
    private SafetyNotice safetyNotice;
    private Instant createdAt;
    private Instant updatedAt;

    protected ChatMessage() {
    }

    public static ChatMessage user(String consultationId, String content) {
        return new ChatMessage(consultationId, null, ChatRole.USER, content, MessageStatus.COMPLETED);
    }

    public static ChatMessage assistant(String consultationId, String replyToMessageId, String content, MessageStatus status) {
        return new ChatMessage(consultationId, replyToMessageId, ChatRole.ASSISTANT, content, status);
    }

    private ChatMessage(String consultationId, String replyToMessageId, ChatRole role, String content, MessageStatus status) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.consultationId = consultationId;
        this.replyToMessageId = replyToMessageId;
        this.role = role;
        this.content = content;
        this.status = status;
        this.evidenceRefs = List.of();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void complete(String content, List<EvidenceReference> evidenceRefs, SafetyNotice safetyNotice) {
        if (content == null || content.isBlank() || content.length() > 20000) {
            throw new IllegalArgumentException("AI message content must be between 1 and 20000 characters");
        }
        this.content = content;
        this.status = MessageStatus.COMPLETED;
        this.evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        this.safetyNotice = safetyNotice;
        this.updatedAt = Instant.now();
    }

    public void fail() {
        this.status = MessageStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getConsultationId() { return consultationId; }
    public String getReplyToMessageId() { return replyToMessageId; }
    public ChatRole getRole() { return role; }
    public String getContent() { return content; }
    public MessageStatus getStatus() { return status; }
    public List<EvidenceReference> getEvidenceRefs() { return evidenceRefs == null ? List.of() : evidenceRefs; }
    public SafetyNotice getSafetyNotice() { return safetyNotice; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public record EvidenceReference(String evidenceId, String label) {}
    public record ResourceQuery(String category, String region) {}
    public record SafetyNotice(String type, String title, String message, ResourceQuery resourceQuery) {}
}
