package com.relationshiptemperature.api.consultation.infrastructure;

import com.relationshiptemperature.api.config.AppProperties;
import com.relationshiptemperature.api.consultation.application.ChatAiClient;
import com.relationshiptemperature.api.consultation.domain.ChatMessage.EvidenceReference;
import com.relationshiptemperature.api.consultation.domain.ChatMessage.ResourceQuery;
import com.relationshiptemperature.api.consultation.domain.ChatMessage.SafetyNotice;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "http")
public class HttpChatAiClient implements ChatAiClient {

    private final RestClient restClient;

    public HttpChatAiClient(RestClient.Builder builder, AppProperties properties) {
        this.restClient = builder
                .baseUrl(properties.ai().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.ai().serviceToken())
                .build();
    }

    @Override
    public ChatAnswer answer(ChatContext context) {
        InternalResponse response = restClient.post()
                .uri("/internal/v1/consultation-answers")
                .header("X-Request-Id", requestId())
                .body(context)
                .retrieve()
                .body(InternalResponse.class);
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new IllegalStateException("AI server returned an empty consultation answer");
        }
        List<EvidenceReference> refs = response.evidenceRefs() == null ? List.of() : response.evidenceRefs();
        SafetyNotice notice = response.safetyNotice() == null ? null : response.safetyNotice().toDomain();
        return new ChatAnswer(response.content(), refs, notice);
    }

    private String requestId() {
        String value = MDC.get("requestId");
        return value == null ? "chat-worker" : value;
    }

    record SafetyNoticeDto(String type, String title, String message, ResourceQuery resourceQuery) {
        SafetyNotice toDomain() {
            return new SafetyNotice(type, title, message, resourceQuery);
        }
    }

    record InternalResponse(
            String content,
            List<EvidenceReference> evidenceRefs,
            SafetyNoticeDto safetyNotice
    ) {}
}
