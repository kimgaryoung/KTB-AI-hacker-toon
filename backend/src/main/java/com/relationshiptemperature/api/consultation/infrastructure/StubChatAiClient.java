package com.relationshiptemperature.api.consultation.infrastructure;

import com.relationshiptemperature.api.consultation.application.ChatAiClient;
import com.relationshiptemperature.api.consultation.domain.ChatMessage.EvidenceReference;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "stub", matchIfMissing = true)
public class StubChatAiClient implements ChatAiClient {

    @Override
    public ChatAnswer answer(ChatContext context) {
        List<EvidenceReference> refs = context.evidences().stream().limit(2)
                .map(item -> new EvidenceReference(item.evidenceId(), item.summary()))
                .toList();
        return new ChatAnswer(
                "그 마음을 알아차린 것만으로도 중요한 시작일 수 있어요. "
                        + "현재 리포트와 대화에서 보이는 패턴만으로 관계를 단정할 수는 없지만, "
                        + "어떤 순간에 그 감정이 가장 크게 느껴지는지 함께 살펴볼까요?",
                refs,
                null
        );
    }
}
