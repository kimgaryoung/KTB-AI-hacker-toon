package com.relationshiptemperature.api.analysis.application;

import java.util.UUID;

public interface ConversationReferenceProvider {

    String NORMALIZED_NDJSON_GZIP = "NORMALIZED_NDJSON_GZIP";
    String NORMALIZED_NDJSON_FORMAT_VERSION = "conversation-ndjson-1.0.0";
    String GZIP_CONTENT_ENCODING = "gzip";

    ConversationReference create(UUID conversationFileId);

    record ConversationReference(
            String url,
            String format,
            String formatVersion,
            String contentEncoding,
            long sizeBytes,
            String sha256
    ) {}
}
