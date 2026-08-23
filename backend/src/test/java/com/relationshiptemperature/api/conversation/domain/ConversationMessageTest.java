package com.relationshiptemperature.api.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationMessageTest {

    @Test
    void preservesNormalizedMessageValues() {
        UUID conversationFileId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        Instant sentAt = Instant.parse("2026-08-19T10:15:30Z");

        ConversationMessage message = new ConversationMessage(
                conversationFileId,
                relationshipId,
                3,
                sentAt,
                "민지",
                ConversationParticipantRole.SELF,
                "오늘 저녁에 이야기할래?"
        );

        assertThat(message.getConversationFileId()).isEqualTo(conversationFileId);
        assertThat(message.getRelationshipId()).isEqualTo(relationshipId);
        assertThat(message.getSequenceNumber()).isEqualTo(3);
        assertThat(message.getSentAt()).isEqualTo(sentAt);
        assertThat(message.getSenderName()).isEqualTo("민지");
        assertThat(message.getParticipantRole()).isEqualTo(ConversationParticipantRole.SELF);
        assertThat(message.getContent()).isEqualTo("오늘 저녁에 이야기할래?");
    }

    @Test
    void rejectsBlankSenderName() {
        assertThatIllegalArgumentException().isThrownBy(() -> messageWith("  ", "내용"));
    }

    @Test
    void rejectsBlankContent() {
        assertThatIllegalArgumentException().isThrownBy(() -> messageWith("민지", "\t"));
    }

    @Test
    void rejectsNegativeSequenceNumber() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ConversationMessage(
                UUID.randomUUID(), UUID.randomUUID(), -1, Instant.now(), "민지",
                ConversationParticipantRole.SELF, "내용"
        ));
    }

    @Test
    void rejectsMissingRequiredReferencesAndTimestamp() {
        assertThatNullPointerException().isThrownBy(() -> new ConversationMessage(
                null, UUID.randomUUID(), 0, Instant.now(), "민지", ConversationParticipantRole.SELF, "내용"
        ));
        assertThatNullPointerException().isThrownBy(() -> new ConversationMessage(
                UUID.randomUUID(), UUID.randomUUID(), 0, null, "민지", ConversationParticipantRole.SELF, "내용"
        ));
        assertThatNullPointerException().isThrownBy(() -> new ConversationMessage(
                UUID.randomUUID(), UUID.randomUUID(), 0, Instant.now(), "민지", null, "내용"
        ));
    }

    private ConversationMessage messageWith(String senderName, String content) {
        return new ConversationMessage(
                UUID.randomUUID(), UUID.randomUUID(), 0, Instant.now(), senderName,
                ConversationParticipantRole.OTHER, content
        );
    }
}
