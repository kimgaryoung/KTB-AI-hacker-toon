package com.relationshiptemperature.api.consultation.application;

public record ChatRequestedEvent(
        String consultationId,
        String userMessageId,
        String assistantMessageId,
        ChatAiClient.ChatContext context
) {
}
