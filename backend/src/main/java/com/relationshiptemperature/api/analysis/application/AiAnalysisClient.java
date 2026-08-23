package com.relationshiptemperature.api.analysis.application;

import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import com.relationshiptemperature.api.report.domain.ReportEvidence.Metric;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AiAnalysisClient {

    AnalysisResult analyze(AnalysisRequest request);

    record AnalysisRequest(
            UUID analysisId,
            UUID conversationFileId,
            RelationshipType relationshipType,
            AnalysisContext context
    ) {}

    /**
     * AI 분석에 필요한 최소한의 사용자·관계·체크인 맥락이다.
     * 카카오 식별자, OAuth 토큰, 프로필 이미지 URL 등 인증·불필요 개인정보는 포함하지 않는다.
     */
    record AnalysisContext(
            UserContext user,
            RelationshipContext relationship,
            CurrentAnalysisContext current,
            List<HistoricalAnalysisContext> history
    ) {}

    record UserContext(UUID userId, String displayName, String timezone) {}

    record RelationshipContext(
            UUID relationshipId,
            String name,
            RelationshipType relationshipType,
            String status
    ) {}

    /**
     * The current conversation contents are sent in the multipart {@code file} part.  This object
     * identifies that conversation and carries the check-in scores submitted with this analysis.
     */
    record CurrentAnalysisContext(UUID conversationFileId, CheckInContext checkIn) {}

    record CheckInContext(UUID checkInId, LocalDate weekStart, Instant inputAt, List<CheckInAnswerContext> answers) {}

    record CheckInAnswerContext(String questionCode, int score) {}

    /** A prior, successfully analysed relationship snapshot, ordered by check-in input date. */
    record HistoricalAnalysisContext(
            Instant inputAt,
            ConversationContext conversation,
            CheckInContext checkIn,
            PreviousAnalysisContext analysis
    ) {}

    record ConversationContext(UUID conversationFileId, List<ConversationMessageContext> messages) {}

    record ConversationMessageContext(String sender, Instant sentAt, String text) {}

    /**
     * The durable AI-analysis result available for a previous run. There is no separate report
     * narrative today; the evidence summaries are the stored analysis text.
     */
    record PreviousAnalysisContext(
            UUID reportId,
            Instant analyzedAt,
            int overallScore,
            Integer scoreChange,
            PrqcScores prqc,
            List<AnalysisEvidenceContext> evidences
    ) {}

    record AnalysisEvidenceContext(String component, int score, String summary, Metric metric) {}

    record AnalysisResult(
            String modelVersion,
            String promptVersion,
            int processedMessageCount,
            PrqcScores components,
            List<EvidenceResult> evidences,
            String selfReportComparison
    ) {}

    record EvidenceResult(String component, int score, String summary, Metric metric) {}
}
