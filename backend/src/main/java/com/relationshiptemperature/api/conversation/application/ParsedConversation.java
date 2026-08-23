package com.relationshiptemperature.api.conversation.application;

import com.relationshiptemperature.api.conversation.domain.ConversationParticipantRole;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ParsedConversation(
        List<ParsedMessage> messages,
        String selfParticipantName,
        String otherParticipantName
) {

    public ParsedConversation {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        selfParticipantName = requireNonBlank(selfParticipantName, "selfParticipantName");
        otherParticipantName = requireNonBlank(otherParticipantName, "otherParticipantName");
        if (selfParticipantName.equals(otherParticipantName)) {
            throw new IllegalArgumentException("participants must be distinct");
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
    }

    public record ParsedMessage(
            int sequenceNumber,
            Instant sentAt,
            String senderName,
            ConversationParticipantRole role,
            String content
    ) {

        public ParsedMessage {
            if (sequenceNumber < 0) {
                throw new IllegalArgumentException("sequenceNumber must not be negative");
            }
            sentAt = Objects.requireNonNull(sentAt, "sentAt");
            senderName = requireNonBlank(senderName, "senderName");
            role = Objects.requireNonNull(role, "role");
            content = requireNonBlank(content, "content");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
