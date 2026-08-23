package com.relationshiptemperature.api.consultation.application;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.consultation.application.ChatAiClient.ChatContext;
import com.relationshiptemperature.api.consultation.application.ChatAiClient.EvidenceContext;
import com.relationshiptemperature.api.consultation.application.ChatAiClient.HistoryMessage;
import com.relationshiptemperature.api.consultation.application.ChatAiClient.PrqcContext;
import com.relationshiptemperature.api.consultation.domain.ChatMessage;
import com.relationshiptemperature.api.consultation.domain.Consultation;
import com.relationshiptemperature.api.consultation.domain.MessageStatus;
import com.relationshiptemperature.api.consultation.repository.ChatMessageRepository;
import com.relationshiptemperature.api.consultation.repository.ConsultationRepository;
import com.relationshiptemperature.api.conversation.domain.ConversationMessage;
import com.relationshiptemperature.api.conversation.repository.ConversationMessageRepository;
import com.relationshiptemperature.api.relationship.application.RelationshipService;
import com.relationshiptemperature.api.report.application.ReportService;
import com.relationshiptemperature.api.report.domain.RelationshipReport;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import com.relationshiptemperature.api.report.domain.ReportEvidence;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final ChatMessageRepository messageRepository;
    private final RelationshipService relationshipService;
    private final ReportService reportService;
    private final ChatStreamService chatStreamService;
    private final ConversationMessageRepository conversationMessageRepository;

    public ConsultationService(
            ConsultationRepository consultationRepository,
            ChatMessageRepository messageRepository,
            RelationshipService relationshipService,
            ReportService reportService,
            ChatStreamService chatStreamService,
            ConversationMessageRepository conversationMessageRepository
    ) {
        this.consultationRepository = consultationRepository;
        this.messageRepository = messageRepository;
        this.relationshipService = relationshipService;
        this.reportService = reportService;
        this.chatStreamService = chatStreamService;
        this.conversationMessageRepository = conversationMessageRepository;
    }

    public Consultation create(UUID userId, UUID relationshipId) {
        relationshipService.getOwned(userId, relationshipId);
        RelationshipReport report = reportService.latest(userId, relationshipId);
        Consultation consultation = consultationRepository.save(new Consultation(userId, relationshipId, report.getId()));
        ChatMessage initial = messageRepository.save(ChatMessage.assistant(
                consultation.getId(), null,
                "새로운 상담을 시작했어요. 지금 가장 이야기하고 싶은 관계의 순간을 들려주세요.",
                MessageStatus.COMPLETED
        ));
        consultation.updatePreview(initial.getContent(), initial.getCreatedAt());
        return consultationRepository.save(consultation);
    }

    public List<Consultation> list(UUID userId) {
        return consultationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId.toString());
    }

    public Consultation getOwned(UUID userId, String consultationId) {
        return consultationRepository.findByIdAndUserId(consultationId, userId.toString())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public List<ChatMessage> messages(UUID userId, String consultationId) {
        getOwned(userId, consultationId);
        return messageRepository.findAllByConsultationIdOrderByCreatedAtAsc(consultationId);
    }

    public AcceptedMessage send(UUID userId, String consultationId, String content) {
        Consultation consultation = getOwned(userId, consultationId);
        if (messageRepository.existsByConsultationIdAndStatus(consultationId, MessageStatus.GENERATING)) {
            throw new ApiException(ErrorCode.CHAT_ALREADY_GENERATING);
        }

        String normalized = content.trim();
        ChatContext context = context(consultation, normalized);
        ChatMessage userMessage = messageRepository.save(ChatMessage.user(consultationId, normalized));
        ChatMessage assistant = messageRepository.save(ChatMessage.assistant(
                consultationId, userMessage.getId(), "", MessageStatus.GENERATING
        ));
        consultation.updatePreview(normalized, Instant.now());
        consultationRepository.save(consultation);

        chatStreamService.start(new ChatRequestedEvent(
                consultationId, userMessage.getId(), assistant.getId(), context
        ));
        return new AcceptedMessage(userMessage, assistant);
    }

    public void delete(UUID userId, String consultationId) {
        Consultation consultation = getOwned(userId, consultationId);
        messageRepository.deleteAllByConsultationId(consultationId);
        consultationRepository.delete(consultation);
    }

    private ChatContext context(Consultation consultation, String userMessage) {
        RelationshipReport report = reportService.getOwned(consultation.getUserId(), consultation.getReportId());
        PrqcScores scores = report.getPrqcScores();
        List<EvidenceContext> evidences = reportService.evidences(report.getId()).stream()
                .map(this::evidenceContext)
                .toList();
        List<ChatMessage> recent = new ArrayList<>(messageRepository
                .findTop20ByConsultationIdAndStatusOrderByCreatedAtDesc(
                        consultation.getId(), MessageStatus.COMPLETED
                ));
        Collections.reverse(recent);
        List<HistoryMessage> history = recent.stream()
                .map(item -> new HistoryMessage(item.getRole(), item.getContent()))
                .toList();
        List<ChatAiClient.ConversationMessageContext> conversationMessages =
                conversationMessageRepository
                        .findAllByRelationshipIdOrderBySentAtAscSequenceNumberAsc(consultation.getRelationshipId())
                        .stream()
                        .map(this::conversationMessageContext)
                        .toList();
        return new ChatContext(
                report.getId(), report.getOverallScore(), report.getScoreChange(),
                new PrqcContext(
                        scores.satisfaction(), scores.commitment(), scores.intimacy(),
                        scores.trust(), scores.passion(), scores.love()
                ),
                evidences,
                history,
                conversationMessages,
                userMessage
        );
    }

    private ChatAiClient.ConversationMessageContext conversationMessageContext(ConversationMessage message) {
        return new ChatAiClient.ConversationMessageContext(
                message.getParticipantRole().name(), message.getSentAt(), message.getContent()
        );
    }

    private EvidenceContext evidenceContext(ReportEvidence evidence) {
        return new EvidenceContext(
                evidence.getId().toString(), evidence.getComponent().apiCode(),
                evidence.getScore(), evidence.getSummary()
        );
    }

    public record AcceptedMessage(ChatMessage userMessage, ChatMessage assistantMessage) {}
}
