package com.relationshiptemperature.api.analysis.infrastructure;

import com.relationshiptemperature.api.analysis.application.AiAnalysisClient;
import com.relationshiptemperature.api.config.AppProperties;
import com.relationshiptemperature.api.conversation.repository.ConversationMessageRepository;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import com.relationshiptemperature.api.report.domain.ReportEvidence.Metric;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "http")
public class HttpAiAnalysisClient implements AiAnalysisClient {

    private final RestClient restClient;
    private final ConversationMessageRepository messageRepository;
    private final NormalizedConversationNdjsonWriter writer;
    private final ObjectMapper objectMapper;

    public HttpAiAnalysisClient(
            RestClient.Builder builder,
            AppProperties properties,
            ConversationMessageRepository messageRepository,
            NormalizedConversationNdjsonWriter writer,
            ObjectMapper objectMapper
    ) {
        this.restClient = builder
                .baseUrl(properties.ai().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.ai().serviceToken())
                .build();
        this.messageRepository = messageRepository;
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        var messages = messageRepository.findAllByConversationFileIdOrderBySequenceNumberAsc(
                request.conversationFileId()
        );
        if (messages.isEmpty()) {
            throw new IllegalStateException("Conversation has no messages");
        }
        byte[] conversation = writer.writeGzip(messages);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("analysisId", request.analysisId().toString());
        body.add("relationshipType", request.relationshipType().name());
        body.add("format", "NORMALIZED_NDJSON_GZIP");
        body.add("formatVersion", "conversation-ndjson-1.0.0");
        body.add("sha256", sha256(conversation));
        body.add("context", jsonPart(request.context()));
        body.add("file", new ByteArrayResource(conversation) {
            @Override
            public String getFilename() {
                return "conversation.ndjson.gz";
            }
        });
        InternalResponse response = restClient.post()
                .uri("/internal/v1/prqc-analyses")
                .header("X-Request-Id", requestId())
                .header("Idempotency-Key", request.analysisId().toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(InternalResponse.class);
        if (response == null) {
            throw new IllegalStateException("AI server returned an empty response");
        }
        return new AnalysisResult(
                response.modelVersion(), response.promptVersion(), response.processedMessageCount(),
                response.components().toDomain(),
                response.evidences().stream().map(EvidenceDto::toDomain).toList(),
                response.selfReportComparison()
        );
    }

    private HttpEntity<String> jsonPart(AiAnalysisClient.AnalysisContext context) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(objectMapper.writeValueAsString(context), headers);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize AI analysis context", exception);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String requestId() {
        String value = MDC.get("requestId");
        return value == null ? "analysis-worker-" + UUID.randomUUID() : value;
    }

    record ComponentsDto(int satisfaction, int commitment, int intimacy, int trust, int passion, int love) {
        PrqcScores toDomain() {
            return new PrqcScores(satisfaction, commitment, intimacy, trust, passion, love);
        }
    }
    record MetricDto(String name, Double currentValue, Double previousValue, String unit, String period) {
        Metric toDomain() {
            return new Metric(name, currentValue, previousValue, unit, period);
        }
    }
    record EvidenceDto(String component, int score, String summary, MetricDto metric) {
        EvidenceResult toDomain() {
            return new EvidenceResult(component, score, summary, metric == null ? null : metric.toDomain());
        }
    }
    record InternalResponse(
            UUID analysisId,
            String modelVersion,
            String promptVersion,
            int processedMessageCount,
            ComponentsDto components,
            List<EvidenceDto> evidences,
            List<Object> warnings,
            String selfReportComparison,
            Instant completedAt
    ) {}
}
