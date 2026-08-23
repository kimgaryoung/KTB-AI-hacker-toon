package com.relationshiptemperature.api.analysis.infrastructure;

import com.relationshiptemperature.api.analysis.application.ConversationReferenceProvider;
import com.relationshiptemperature.api.analysis.application.ConversationReferenceProvider.ConversationReference;
import com.relationshiptemperature.api.conversation.domain.ConversationMessage;
import com.relationshiptemperature.api.conversation.repository.ConversationMessageRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Builds an in-process reference for local AI/stub development without exposing a download route.
 * Production HTTP mode remains backed by the fail-closed Object Storage provider.
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "local")
public class LocalConversationReferenceProvider implements ConversationReferenceProvider {

    private final Function<UUID, List<ConversationMessage>> messageLoader;
    private final NormalizedConversationNdjsonWriter writer;
    private final Map<UUID, byte[]> artifacts = new ConcurrentHashMap<>();

    @Autowired
    public LocalConversationReferenceProvider(
            ConversationMessageRepository messageRepository,
            NormalizedConversationNdjsonWriter writer
    ) {
        this(messageRepository::findAllByConversationFileIdOrderBySequenceNumberAsc, writer);
    }

    public LocalConversationReferenceProvider(
            Function<UUID, List<ConversationMessage>> messageLoader,
            NormalizedConversationNdjsonWriter writer
    ) {
        this.messageLoader = Objects.requireNonNull(messageLoader, "messageLoader");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public ConversationReference create(UUID conversationFileId) {
        Objects.requireNonNull(conversationFileId, "conversationFileId");
        byte[] compressed = writer.writeGzip(messageLoader.apply(conversationFileId));
        artifacts.put(conversationFileId, compressed.clone());
        return new ConversationReference(
                "local://conversation-references/" + conversationFileId + ".ndjson.gz",
                NORMALIZED_NDJSON_GZIP,
                NORMALIZED_NDJSON_FORMAT_VERSION,
                GZIP_CONTENT_ENCODING,
                compressed.length,
                sha256(compressed)
        );
    }

    /** Returns the last generated artifact to an in-process local/stub caller. */
    public byte[] artifact(UUID conversationFileId) {
        byte[] artifact = artifacts.get(conversationFileId);
        if (artifact == null) {
            throw new IllegalStateException("Conversation reference has not been created");
        }
        return artifact.clone();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
