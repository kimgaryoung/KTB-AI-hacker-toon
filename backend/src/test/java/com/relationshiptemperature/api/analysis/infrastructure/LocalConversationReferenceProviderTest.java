package com.relationshiptemperature.api.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.relationshiptemperature.api.analysis.application.ConversationReferenceProvider.ConversationReference;
import com.relationshiptemperature.api.conversation.domain.ConversationMessage;
import com.relationshiptemperature.api.conversation.domain.ConversationParticipantRole;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalConversationReferenceProviderTest {

    @Test
    void createsReferenceFromOrderedNormalizedRowsWithExactArtifactMetadata() throws Exception {
        UUID fileId = UUID.randomUUID();
        ConversationMessage first = new ConversationMessage(
                fileId, UUID.randomUUID(), 0, Instant.parse("2026-08-19T10:23:00Z"),
                "민지", ConversationParticipantRole.SELF, "첫 메시지"
        );
        ConversationMessage second = new ConversationMessage(
                fileId, UUID.randomUUID(), 1, Instant.parse("2026-08-19T10:24:00Z"),
                "준호", ConversationParticipantRole.OTHER, "두 번째 메시지"
        );
        NormalizedConversationNdjsonWriter writer = new NormalizedConversationNdjsonWriter();
        LocalConversationReferenceProvider provider = new LocalConversationReferenceProvider(
                ignored -> List.of(first, second), writer
        );

        ConversationReference reference = provider.create(fileId);
        byte[] bytes = provider.artifact(fileId);

        assertThat(reference.url()).isEqualTo("local://conversation-references/" + fileId + ".ndjson.gz");
        assertThat(reference.format()).isEqualTo("NORMALIZED_NDJSON_GZIP");
        assertThat(reference.formatVersion()).isEqualTo("conversation-ndjson-1.0.0");
        assertThat(reference.contentEncoding()).isEqualTo("gzip");
        assertThat(reference.sizeBytes()).isEqualTo(bytes.length);
        assertThat(reference.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}
