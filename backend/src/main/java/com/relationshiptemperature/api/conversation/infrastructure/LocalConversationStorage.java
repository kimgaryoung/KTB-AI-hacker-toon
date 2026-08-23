package com.relationshiptemperature.api.conversation.infrastructure;

import com.relationshiptemperature.api.config.AppProperties;
import com.relationshiptemperature.api.conversation.application.ConversationStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LocalConversationStorage implements ConversationStorage {

    private final Path root;

    public LocalConversationStorage(AppProperties properties) throws IOException {
        this.root = properties.storage().root().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public StoredObject save(InputStream inputStream) throws IOException {
        String key = UUID.randomUUID() + ".txt";
        Path target = resolve(key);
        MessageDigest digest = sha256();
        try (OutputStream file = Files.newOutputStream(target);
             DigestOutputStream output = new DigestOutputStream(file, digest)) {
            inputStream.transferTo(output);
        }
        return new StoredObject(key, Files.size(target), HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return target;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
