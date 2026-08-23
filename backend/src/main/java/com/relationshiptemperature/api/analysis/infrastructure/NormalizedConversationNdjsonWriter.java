package com.relationshiptemperature.api.analysis.infrastructure;

import com.relationshiptemperature.api.conversation.domain.ConversationMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;
import org.springframework.stereotype.Component;

/** Serializes normalized messages into the versioned PRQC NDJSON representation. */
@Component
public class NormalizedConversationNdjsonWriter {

    public byte[] writeGzip(List<ConversationMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        StringBuilder ndjson = new StringBuilder();
        for (ConversationMessage message : messages) {
            Objects.requireNonNull(message, "messages must not contain null");
            ndjson.append('{')
                    .append("\"messageId\":").append(quote(message.getId().toString()))
                    .append(',')
                    .append("\"sender\":").append(quote(message.getParticipantRole().name()))
                    .append(',')
                    .append("\"sentAt\":").append(quote(message.getSentAt().toString()))
                    .append(',')
                    .append("\"text\":").append(quote(message.getContent()))
                    .append('}')
                    .append('\n');
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(ndjson.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize normalized conversation", exception);
        }
        return output.toByteArray();
    }

    private String quote(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        appendUnicodeEscape(escaped, character);
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private void appendUnicodeEscape(StringBuilder escaped, char character) {
        final char[] hex = "0123456789abcdef".toCharArray();
        escaped.append("\\u")
                .append(hex[(character >>> 12) & 0xf])
                .append(hex[(character >>> 8) & 0xf])
                .append(hex[(character >>> 4) & 0xf])
                .append(hex[character & 0xf]);
    }
}
