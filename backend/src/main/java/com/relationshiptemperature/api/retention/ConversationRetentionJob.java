package com.relationshiptemperature.api.retention;

import com.relationshiptemperature.api.conversation.application.ConversationStorage;
import com.relationshiptemperature.api.conversation.domain.ConversationFile;
import com.relationshiptemperature.api.conversation.repository.ConversationFileRepository;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConversationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ConversationRetentionJob.class);
    private final ConversationFileRepository fileRepository;
    private final ConversationStorage storage;

    public ConversationRetentionJob(ConversationFileRepository fileRepository, ConversationStorage storage) {
        this.fileRepository = fileRepository;
        this.storage = storage;
    }

    @Scheduled(cron = "${app.retention.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void deleteExpiredRawFiles() {
        for (ConversationFile file : fileRepository.findAllByExpiresAtBeforeAndRawDeletedAtIsNull(Instant.now())) {
            try {
                storage.delete(file.getStorageKey());
                file.markRawDeleted(Instant.now());
            } catch (IOException exception) {
                log.warn("Failed to delete expired conversation fileId={}", file.getId());
            }
        }
    }
}
