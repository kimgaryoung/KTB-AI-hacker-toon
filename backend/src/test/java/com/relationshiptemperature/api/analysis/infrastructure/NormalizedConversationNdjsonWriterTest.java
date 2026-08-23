package com.relationshiptemperature.api.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.relationshiptemperature.api.conversation.domain.ConversationMessage;
import com.relationshiptemperature.api.conversation.domain.ConversationParticipantRole;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class NormalizedConversationNdjsonWriterTest {

    private final NormalizedConversationNdjsonWriter writer = new NormalizedConversationNdjsonWriter();

    @Test
    void writesOrderedUtf8NdjsonWithStableIdsRolesTimesAndJsonEscaping() throws IOException {
        ConversationMessage first = message(0, ConversationParticipantRole.SELF, "안녕, \"준호\"\n오늘 어때?");
        ConversationMessage second = message(1, ConversationParticipantRole.OTHER, "괜찮아\t🙂");

        byte[] compressed = writer.writeGzip(List.of(first, second));

        assertThat(writer.writeGzip(List.of(first, second))).containsExactly(compressed);
        assertThat(decompress(compressed)).isEqualTo(
                "{\"messageId\":\"" + first.getId() + "\",\"sender\":\"SELF\","
                        + "\"sentAt\":\"2026-08-19T10:23:00Z\",\"text\":\"안녕, \\\"준호\\\"\\n오늘 어때?\"}\n"
                        + "{\"messageId\":\"" + second.getId() + "\",\"sender\":\"OTHER\","
                        + "\"sentAt\":\"2026-08-19T10:24:00Z\",\"text\":\"괜찮아\\t🙂\"}\n"
        );
    }

    private ConversationMessage message(int sequence, ConversationParticipantRole role, String content) {
        return new ConversationMessage(
                UUID.randomUUID(), UUID.randomUUID(), sequence,
                Instant.parse("2026-08-19T10:" + (23 + sequence) + ":00Z"),
                role == ConversationParticipantRole.SELF ? "민지" : "준호", role, content
        );
    }

    private String decompress(byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}
