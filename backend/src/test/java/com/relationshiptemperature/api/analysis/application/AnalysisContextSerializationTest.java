package com.relationshiptemperature.api.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AnalysisContextSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesCurrentAndChronologicalHistoricalAnalysisContextForAi() throws Exception {
        AiAnalysisClient.AnalysisContext context = new AiAnalysisClient.AnalysisContext(
                new AiAnalysisClient.UserContext(UUID.randomUUID(), "우", "Asia/Seoul"),
                new AiAnalysisClient.RelationshipContext(
                        UUID.randomUUID(), "민지", RelationshipType.FRIEND, "ANALYZING"
                ),
                new AiAnalysisClient.CurrentAnalysisContext(
                        UUID.randomUUID(),
                        new AiAnalysisClient.CheckInContext(
                                UUID.randomUUID(),
                                LocalDate.of(2026, 8, 17),
                                Instant.parse("2026-08-17T01:00:00Z"),
                                List.of(
                                        new AiAnalysisClient.CheckInAnswerContext("RELATIONSHIP_FEELING", 6),
                                        new AiAnalysisClient.CheckInAnswerContext("CONVERSATION_COMFORT", 4)
                                )
                        )
                ),
                List.of(new AiAnalysisClient.HistoricalAnalysisContext(
                        Instant.parse("2026-08-10T01:00:00Z"),
                        new AiAnalysisClient.ConversationContext(UUID.randomUUID(), List.of(
                                new AiAnalysisClient.ConversationMessageContext(
                                        "USER", Instant.parse("2026-08-10T00:00:00Z"), "지난 대화"
                                )
                        )),
                        new AiAnalysisClient.CheckInContext(
                                UUID.randomUUID(), LocalDate.of(2026, 8, 10), Instant.parse("2026-08-10T01:00:00Z"),
                                List.of(new AiAnalysisClient.CheckInAnswerContext("RELATIONSHIP_FEELING", 4))
                        ),
                        new AiAnalysisClient.PreviousAnalysisContext(
                                UUID.randomUUID(), Instant.parse("2026-08-10T02:00:00Z"), 58, -7,
                                new PrqcScores(55, 56, 57, 58, 59, 60),
                                List.of(new AiAnalysisClient.AnalysisEvidenceContext(
                                        "trust", 58, "응답 간격이 길어졌어요.", null
                                ))
                        )
                ))
        );

        String json = objectMapper.writeValueAsString(context);

        assertThat(json)
                .contains("\"displayName\":\"우\"")
                .contains("\"relationshipType\":\"FRIEND\"")
                .contains("\"weekStart\":\"2026-08-17\"")
                .contains("\"questionCode\":\"RELATIONSHIP_FEELING\"")
                .contains("\"score\":6")
                .contains("\"questionCode\":\"CONVERSATION_COMFORT\"")
                .contains("\"score\":4")
                .contains("\"history\"")
                .contains("\"inputAt\":\"2026-08-10T01:00:00Z\"")
                .contains("\"text\":\"지난 대화\"")
                .contains("\"overallScore\":58")
                .contains("\"summary\":\"응답 간격이 길어졌어요.\"");
    }
}
