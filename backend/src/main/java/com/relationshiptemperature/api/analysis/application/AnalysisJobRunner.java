package com.relationshiptemperature.api.analysis.application;

import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.auth.repository.UserRepository;
import com.relationshiptemperature.api.analysis.domain.AnalysisJob;
import com.relationshiptemperature.api.analysis.domain.AnalysisStage;
import com.relationshiptemperature.api.analysis.repository.AnalysisJobRepository;
import com.relationshiptemperature.api.checkin.domain.CheckIn;
import com.relationshiptemperature.api.checkin.domain.CheckInAnswer;
import com.relationshiptemperature.api.checkin.repository.CheckInAnswerRepository;
import com.relationshiptemperature.api.checkin.repository.CheckInRepository;
import com.relationshiptemperature.api.conversation.repository.ConversationMessageRepository;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.repository.RelationshipRepository;
import com.relationshiptemperature.api.report.application.ReportService;
import com.relationshiptemperature.api.report.domain.RelationshipReport;
import com.relationshiptemperature.api.report.repository.RelationshipReportRepository;
import com.relationshiptemperature.api.report.repository.ReportEvidenceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AnalysisJobRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobRunner.class);

    private final AnalysisJobRepository jobRepository;
    private final RelationshipRepository relationshipRepository;
    private final UserRepository userRepository;
    private final CheckInRepository checkInRepository;
    private final CheckInAnswerRepository checkInAnswerRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final RelationshipReportRepository reportRepository;
    private final ReportEvidenceRepository evidenceRepository;
    private final AiAnalysisClient aiAnalysisClient;
    private final ReportService reportService;

    public AnalysisJobRunner(
            AnalysisJobRepository jobRepository,
            RelationshipRepository relationshipRepository,
            UserRepository userRepository,
            CheckInRepository checkInRepository,
            CheckInAnswerRepository checkInAnswerRepository,
            ConversationMessageRepository conversationMessageRepository,
            RelationshipReportRepository reportRepository,
            ReportEvidenceRepository evidenceRepository,
            AiAnalysisClient aiAnalysisClient,
            ReportService reportService
    ) {
        this.jobRepository = jobRepository;
        this.relationshipRepository = relationshipRepository;
        this.userRepository = userRepository;
        this.checkInRepository = checkInRepository;
        this.checkInAnswerRepository = checkInAnswerRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.reportRepository = reportRepository;
        this.evidenceRepository = evidenceRepository;
        this.aiAnalysisClient = aiAnalysisClient;
        this.reportService = reportService;
    }

    @Async("analysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void run(AnalysisRequestedEvent event) {
        AnalysisJob job = jobRepository.findById(event.jobId()).orElseThrow();
        Relationship relationship = relationshipRepository.findById(job.getRelationshipId()).orElseThrow();
        User user = userRepository.findById(job.getUserId()).orElseThrow();
        CheckIn checkIn = checkInRepository.findById(job.getCheckInId()).orElseThrow();
        try {
            update(job, AnalysisStage.LOADING_CONVERSATION, 10);
            update(job, AnalysisStage.ANALYZING_MESSAGE_PATTERNS, 30);
            update(job, AnalysisStage.ANALYZING_EMOTIONAL_FLOW, 60);
            AiAnalysisClient.AnalysisResult result = analyzeWithRetry(new AiAnalysisClient.AnalysisRequest(
                    job.getId(),
                    job.getConversationFileId(),
                    relationship.getRelationshipType(),
                    new AiAnalysisClient.AnalysisContext(
                            new AiAnalysisClient.UserContext(
                                    user.getId(), user.getDisplayName(), user.getTimezone()
                            ),
                            new AiAnalysisClient.RelationshipContext(
                                    relationship.getId(),
                                    relationship.getName(),
                                    relationship.getRelationshipType(),
                                    relationship.getStatus().name()
                            ),
                            new AiAnalysisClient.CurrentAnalysisContext(
                                    job.getConversationFileId(),
                                    checkInContext(checkIn, answersFor(List.of(checkIn)).getOrDefault(checkIn.getId(), List.of()))
                            ),
                            history(job, checkIn)
                    )
            ));
            update(job, AnalysisStage.CALCULATING_PRQC, 80);
            update(job, AnalysisStage.CALCULATING_RELATIONSHIP_SCORE, 95);
            RelationshipReport report = reportService.create(
                    job.getId(), job.getCheckInId(), relationship, result
            );
            job.complete(report.getId());
            jobRepository.save(job);
        } catch (Exception exception) {
            log.error("Analysis failed jobId={}", job.getId(), exception);
            job.fail("ANALYSIS_UNAVAILABLE", "일시적으로 분석할 수 없어요. 잠시 후 다시 시도해 주세요.", true);
            relationship.failAnalysis();
            jobRepository.save(job);
            relationshipRepository.save(relationship);
        }
    }

    private void update(AnalysisJob job, AnalysisStage stage, int progress) {
        job.progress(stage, progress);
        jobRepository.save(job);
    }

    /**
     * Builds chronological context from completed reports before the current check-in week. Every
     * entry keeps the conversation, scores, and stored evidence text from the same prior run.
     */
    private List<AiAnalysisClient.HistoricalAnalysisContext> history(AnalysisJob currentJob, CheckIn currentCheckIn) {
        List<RelationshipReport> reports = reportRepository
                .findAllByRelationshipIdAndUserIdAndWeekStartBeforeOrderByWeekStartAscAnalyzedAtAsc(
                        currentJob.getRelationshipId(), currentJob.getUserId(), currentCheckIn.getWeekStart()
                );
        if (reports.isEmpty()) {
            return List.of();
        }

        Map<UUID, AnalysisJob> jobs = jobRepository.findAllById(reports.stream()
                        .map(RelationshipReport::getAnalysisJobId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(AnalysisJob::getId, Function.identity()));
        Map<UUID, CheckIn> checkIns = checkInRepository.findAllById(jobs.values().stream()
                        .map(AnalysisJob::getCheckInId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(CheckIn::getId, Function.identity()));
        Map<UUID, List<CheckInAnswer>> answers = answersFor(new java.util.ArrayList<>(checkIns.values()));

        return reports.stream()
                .map(report -> historicalContext(report, jobs.get(report.getAnalysisJobId()), checkIns, answers))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(AiAnalysisClient.HistoricalAnalysisContext::inputAt)
                        .thenComparing(item -> item.analysis().analyzedAt()))
                .toList();
    }

    private AiAnalysisClient.HistoricalAnalysisContext historicalContext(
            RelationshipReport report,
            AnalysisJob job,
            Map<UUID, CheckIn> checkIns,
            Map<UUID, List<CheckInAnswer>> answers
    ) {
        if (job == null) {
            return null;
        }
        CheckIn checkIn = checkIns.get(job.getCheckInId());
        if (checkIn == null) {
            return null;
        }
        List<AiAnalysisClient.ConversationMessageContext> messages = conversationMessageRepository
                .findAllByConversationFileIdOrderBySequenceNumberAsc(job.getConversationFileId())
                .stream()
                .map(message -> new AiAnalysisClient.ConversationMessageContext(
                        message.getParticipantRole().name(), message.getSentAt(), message.getContent()
                ))
                .toList();
        List<AiAnalysisClient.AnalysisEvidenceContext> evidences = evidenceRepository.findAllByReportId(report.getId())
                .stream()
                .map(evidence -> new AiAnalysisClient.AnalysisEvidenceContext(
                        evidence.getComponent().apiCode(), evidence.getScore(), evidence.getSummary(), evidence.getMetric()
                ))
                .toList();
        return new AiAnalysisClient.HistoricalAnalysisContext(
                checkIn.getCreatedAt(),
                new AiAnalysisClient.ConversationContext(job.getConversationFileId(), messages),
                checkInContext(checkIn, answers.getOrDefault(checkIn.getId(), List.of())),
                new AiAnalysisClient.PreviousAnalysisContext(
                        report.getId(), report.getAnalyzedAt(), report.getOverallScore(), report.getScoreChange(),
                        report.getPrqcScores(), evidences
                )
        );
    }

    private Map<UUID, List<CheckInAnswer>> answersFor(List<CheckIn> checkIns) {
        if (checkIns.isEmpty()) {
            return Map.of();
        }
        return checkInAnswerRepository.findAllByCheckInIdIn(checkIns.stream().map(CheckIn::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(CheckInAnswer::getCheckInId));
    }

    private AiAnalysisClient.CheckInContext checkInContext(CheckIn checkIn, List<CheckInAnswer> answers) {
        return new AiAnalysisClient.CheckInContext(
                checkIn.getId(), checkIn.getWeekStart(), checkIn.getCreatedAt(), answers.stream()
                        .map(answer -> new AiAnalysisClient.CheckInAnswerContext(
                                answer.getQuestionCode().name(), answer.getScore()
                        ))
                        .toList()
        );
    }

    private AiAnalysisClient.AnalysisResult analyzeWithRetry(AiAnalysisClient.AnalysisRequest request) {
        long[] delays = {0L, 2_000L, 5_000L};
        for (int attempt = 0; attempt < delays.length; attempt++) {
            try {
                if (delays[attempt] > 0) {
                    Thread.sleep(delays[attempt]);
                }
                return aiAnalysisClient.analyze(request);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Analysis retry interrupted", exception);
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                boolean retryable = status == 429 || status == 503 || status == 504;
                if (!retryable || attempt == delays.length - 1) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Unreachable analysis retry state");
    }
}
