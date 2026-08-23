package com.relationshiptemperature.api.consultation.application;

import com.relationshiptemperature.api.consultation.domain.ChatMessage.EvidenceReference;
import com.relationshiptemperature.api.consultation.domain.ChatMessage.SafetyNotice;
import com.relationshiptemperature.api.consultation.domain.ChatRole;
import java.util.List;
import java.util.UUID;

public interface ChatAiClient {

    ChatAnswer answer(ChatContext context);

    record ChatContext(
            UUID reportId,
            int overallScore,
            Integer scoreChange,
            PrqcContext prqc,
            List<EvidenceContext> evidences,
            List<HistoryMessage> recentMessages,
            List<ConversationMessageContext> conversationMessages,
            String userMessage
    ) {}

    record PrqcContext(int satisfaction, int commitment, int intimacy, int trust, int passion, int love) {}
    record EvidenceContext(String evidenceId, String component, int score, String summary) {}
    record HistoryMessage(ChatRole role, String content) {}
    record ConversationMessageContext(String sender, java.time.Instant sentAt, String text) {}
    record ChatAnswer(String content, List<EvidenceReference> evidenceRefs, SafetyNotice safetyNotice) {}
}
