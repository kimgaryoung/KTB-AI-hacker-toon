package com.relationshiptemperature.api.conversation.application;

import java.io.IOException;
import java.io.InputStream;

public interface ConversationStorage {

    StoredObject save(InputStream inputStream) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;

    record StoredObject(String storageKey, long sizeBytes, String sha256) {}
}
