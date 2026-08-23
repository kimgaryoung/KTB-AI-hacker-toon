package com.relationshiptemperature.api.conversation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "conversation_messages")
@CompoundIndexes({
        @CompoundIndex(
                name = "uk_conversation_message_file_sequence",
                def = "{'conversation_file_id': 1, 'sequence_number': 1}",
                unique = true
        )
})
public class ConversationMessage {

    @Id
    private UUID id;

    @Indexed
    @Field("conversation_file_id")
    private UUID conversationFileId;

    @Indexed
    @Field("relationship_id")
    private UUID relationshipId;

    @Field("sequence_number")
    private int sequenceNumber;

    @Field("sent_at")
    private Instant sentAt;

    @Field("sender_name")
    private String senderName;

    @Field("participant_role")
    private ConversationParticipantRole participantRole;

    private String content;

    protected ConversationMessage() {
    }

    public ConversationMessage(
            UUID conversationFileId,
            UUID relationshipId,
            int sequenceNumber,
            Instant sentAt,
            String senderName,
            ConversationParticipantRole participantRole,
            String content
    ) {
        this.id = UUID.randomUUID();
        this.conversationFileId = Objects.requireNonNull(conversationFileId, "conversationFileId");
        this.relationshipId = Objects.requireNonNull(relationshipId, "relationshipId");
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must not be negative");
        }
        this.sequenceNumber = sequenceNumber;
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
        this.senderName = requireNonBlank(senderName, "senderName");
        this.participantRole = Objects.requireNonNull(participantRole, "participantRole");
        this.content = requireNonBlank(content, "content");
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public UUID getId() { return id; }
    public UUID getConversationFileId() { return conversationFileId; }
    public UUID getRelationshipId() { return relationshipId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public Instant getSentAt() { return sentAt; }
    public String getSenderName() { return senderName; }
    public ConversationParticipantRole getParticipantRole() { return participantRole; }
    public String getContent() { return content; }
}
