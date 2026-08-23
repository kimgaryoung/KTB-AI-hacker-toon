package com.relationshiptemperature.api.conversation.repository;

import com.relationshiptemperature.api.conversation.domain.ConversationMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationMessageRepository extends MongoRepository<ConversationMessage, UUID> {

    List<ConversationMessage> findAllByConversationFileIdOrderBySequenceNumberAsc(UUID fileId);

    List<ConversationMessage> findAllByRelationshipId(UUID relationshipId);

    List<ConversationMessage> findAllByRelationshipIdOrderBySentAtAscSequenceNumberAsc(UUID relationshipId);

    void deleteAllByConversationFileId(UUID fileId);
}
