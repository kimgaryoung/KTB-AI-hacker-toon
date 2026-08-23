package com.relationshiptemperature.api.consultation.repository;

import com.relationshiptemperature.api.consultation.domain.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.relationshiptemperature.api.consultation.domain.MessageStatus;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    List<ChatMessage> findAllByConsultationIdOrderByCreatedAtAsc(String consultationId);

    List<ChatMessage> findTop20ByConsultationIdAndStatusOrderByCreatedAtDesc(
            String consultationId, MessageStatus status
    );

    Optional<ChatMessage> findByConsultationIdAndReplyToMessageId(String consultationId, String replyToMessageId);

    boolean existsByConsultationIdAndStatus(String consultationId, MessageStatus status);

    void deleteAllByConsultationId(String consultationId);
}
