package com.relationshiptemperature.api.consultation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.consultation.domain.ChatMessage;
import com.relationshiptemperature.api.consultation.domain.Consultation;
import com.relationshiptemperature.api.consultation.domain.MessageStatus;
import com.relationshiptemperature.api.consultation.repository.ChatMessageRepository;
import com.relationshiptemperature.api.consultation.repository.ConsultationRepository;
import com.relationshiptemperature.api.conversation.repository.ConversationMessageRepository;
import com.relationshiptemperature.api.relationship.application.RelationshipService;
import com.relationshiptemperature.api.report.application.ReportService;
import com.relationshiptemperature.api.report.domain.RelationshipReport;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import com.relationshiptemperature.api.report.domain.ReportEvidence;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsultationServiceTest {

    private ConsultationRepository consultationRepository;
    private ChatMessageRepository messageRepository;
    private RelationshipService relationshipService;
    private ReportService reportService;
    private ChatStreamService chatStreamService;
    private ConversationMessageRepository conversationMessageRepository;
    private ConsultationService service;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        relationshipService = mock(RelationshipService.class);
        reportService = mock(ReportService.class);
        chatStreamService = mock(ChatStreamService.class);
        conversationMessageRepository = mock(ConversationMessageRepository.class);
        service = new ConsultationService(
                consultationRepository, messageRepository, relationshipService, reportService,
                chatStreamService, conversationMessageRepository
        );
        when(consultationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsMongoConsultationWithPinnedLatestReportAndInitialMessage() {
        UUID userId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        RelationshipReport report = report(userId, relationshipId);
        when(reportService.latest(userId, relationshipId)).thenReturn(report);

        Consultation created = service.create(userId, relationshipId);

        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getRelationshipId()).isEqualTo(relationshipId);
        assertThat(created.getReportId()).isEqualTo(report.getId());
        assertThat(created.getLastMessagePreview()).contains("새로운 상담");
        verify(relationshipService).getOwned(userId, relationshipId);
    }

    @Test
    void storesMessagesAndBuildsAiContextFromPinnedReportEvidenceAndHistory() {
        UUID userId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        RelationshipReport report = report(userId, relationshipId);
        Consultation consultation = new Consultation(userId, relationshipId, report.getId());
        ChatMessage history = ChatMessage.assistant(
                consultation.getId(), null, "이전 답변", MessageStatus.COMPLETED
        );
        ReportEvidence evidence = new ReportEvidence(
                report.getId(), com.relationshiptemperature.api.report.domain.PrqcComponent.TRUST,
                70, "신뢰 관련 응답 패턴이 관찰됐어요.", null
        );
        when(consultationRepository.findByIdAndUserId(consultation.getId(), userId.toString()))
                .thenReturn(Optional.of(consultation));
        when(messageRepository.existsByConsultationIdAndStatus(consultation.getId(), MessageStatus.GENERATING))
                .thenReturn(false);
        when(reportService.getOwned(userId, report.getId())).thenReturn(report);
        when(reportService.evidences(report.getId())).thenReturn(List.of(evidence));
        when(messageRepository.findTop20ByConsultationIdAndStatusOrderByCreatedAtDesc(
                consultation.getId(), MessageStatus.COMPLETED
        )).thenReturn(List.of(history));
        when(conversationMessageRepository.findAllByRelationshipIdOrderBySentAtAscSequenceNumberAsc(
                relationshipId
        )).thenReturn(List.of());

        ConsultationService.AcceptedMessage accepted = service.send(
                userId, consultation.getId(), "  답장이 늦으면 불안해요.  "
        );

        assertThat(accepted.userMessage().getContent()).isEqualTo("답장이 늦으면 불안해요.");
        assertThat(accepted.assistantMessage().getStatus()).isEqualTo(MessageStatus.GENERATING);
        assertThat(accepted.assistantMessage().getReplyToMessageId()).isEqualTo(accepted.userMessage().getId());

        ArgumentCaptor<ChatRequestedEvent> captor = ArgumentCaptor.forClass(ChatRequestedEvent.class);
        verify(chatStreamService).start(captor.capture());
        ChatAiClient.ChatContext context = captor.getValue().context();
        assertThat(context.reportId()).isEqualTo(report.getId());
        assertThat(context.overallScore()).isEqualTo(70);
        assertThat(context.prqc().trust()).isEqualTo(70);
        assertThat(context.evidences()).extracting(ChatAiClient.EvidenceContext::summary)
                .containsExactly("신뢰 관련 응답 패턴이 관찰됐어요.");
        assertThat(context.recentMessages()).extracting(ChatAiClient.HistoryMessage::content)
                .containsExactly("이전 답변");
        assertThat(context.conversationMessages()).isEmpty();
    }

    @Test
    void rejectsSecondMessageWhileAssistantIsGenerating() {
        UUID userId = UUID.randomUUID();
        Consultation consultation = new Consultation(userId, UUID.randomUUID(), UUID.randomUUID());
        when(consultationRepository.findByIdAndUserId(consultation.getId(), userId.toString()))
                .thenReturn(Optional.of(consultation));
        when(messageRepository.existsByConsultationIdAndStatus(consultation.getId(), MessageStatus.GENERATING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.send(userId, consultation.getId(), "새 메시지"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.CHAT_ALREADY_GENERATING);
    }

    private RelationshipReport report(UUID userId, UUID relationshipId) {
        return new RelationshipReport(
                userId, relationshipId, UUID.randomUUID(), 70, 5,
                LocalDate.of(2026, 8, 17), new PrqcScores(70, 70, 70, 70, 70, 70),
                "prqc-test", "policy-test", "체크인과 대화 분석을 비교한 설명", Instant.now()
        );
    }
}
