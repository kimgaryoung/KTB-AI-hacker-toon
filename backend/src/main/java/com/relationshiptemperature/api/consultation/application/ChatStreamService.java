package com.relationshiptemperature.api.consultation.application;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.consultation.domain.ChatMessage;
import com.relationshiptemperature.api.consultation.domain.Consultation;
import com.relationshiptemperature.api.consultation.domain.MessageStatus;
import com.relationshiptemperature.api.consultation.repository.ChatMessageRepository;
import com.relationshiptemperature.api.consultation.repository.ConsultationRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);
    private static final long SSE_TIMEOUT_MILLIS = 120_000L;
    private static final int DELTA_SIZE = 24;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();
    private final ChatMessageRepository messageRepository;
    private final ConsultationRepository consultationRepository;
    private final ChatAiClient chatAiClient;

    public ChatStreamService(
            ChatMessageRepository messageRepository,
            ConsultationRepository consultationRepository,
            ChatAiClient chatAiClient
    ) {
        this.messageRepository = messageRepository;
        this.consultationRepository = consultationRepository;
        this.chatAiClient = chatAiClient;
    }

    public SseEmitter subscribe(String consultationId, String afterUserMessageId) {
        ChatMessage assistant = messageRepository
                .findByConsultationIdAndReplyToMessageId(consultationId, afterUserMessageId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitters.computeIfAbsent(assistant.getId(), ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> remove(assistant.getId(), emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());

        sendToEmitter(assistant.getId(), emitter, "heartbeat", Map.of("at", Instant.now()));
        ChatMessage latest = messageRepository.findById(assistant.getId()).orElse(assistant);
        if (latest.getStatus() == MessageStatus.COMPLETED) {
            sendToEmitter(latest.getId(), emitter, "assistant.completed", completedData(latest));
            emitter.complete();
        } else if (latest.getStatus() == MessageStatus.FAILED) {
            sendToEmitter(latest.getId(), emitter, "assistant.failed", failedData(latest));
            emitter.complete();
        }
        return emitter;
    }

    @Async("chatExecutor")
    public void start(ChatRequestedEvent event) {
        ChatMessage assistant = messageRepository.findById(event.assistantMessageId()).orElseThrow();
        try {
            send(event.assistantMessageId(), "assistant.started", Map.of("messageId", assistant.getId()));
            ChatAiClient.ChatAnswer answer = chatAiClient.answer(event.context());
            answer = suppressRepeatedSupportRecommendation(event.consultationId(), answer);
            validateAnswer(event.context(), answer);
            for (String delta : deltas(answer.content())) {
                send(event.assistantMessageId(), "assistant.delta", Map.of(
                        "messageId", assistant.getId(),
                        "delta", delta
                ));
            }
            assistant.complete(answer.content(), answer.evidenceRefs(), answer.safetyNotice());
            messageRepository.save(assistant);
            updateConsultationPreview(event.consultationId(), answer.content());
            send(event.assistantMessageId(), "assistant.completed", completedData(assistant));
            complete(event.assistantMessageId());
        } catch (Exception exception) {
            log.error("Chat generation failed assistantMessageId={}", assistant.getId(), exception);
            assistant.fail();
            messageRepository.save(assistant);
            send(event.assistantMessageId(), "assistant.failed", failedData(assistant));
            complete(event.assistantMessageId());
        }
    }

    @Scheduled(fixedRate = 15_000L)
    public void heartbeat() {
        emitters.forEach((messageId, subscribers) -> subscribers.forEach(emitter ->
                sendToEmitter(messageId, emitter, "heartbeat", Map.of("at", Instant.now()))
        ));
    }

    private void updateConsultationPreview(String consultationId, String content) {
        Consultation consultation = consultationRepository.findById(consultationId).orElse(null);
        if (consultation != null) {
            consultation.updatePreview(content, Instant.now());
            consultationRepository.save(consultation);
        }
    }

    private List<String> deltas(String content) {
        if (content == null || content.isEmpty()) return List.of();
        return java.util.stream.IntStream.iterate(0, start -> start < content.length(), start -> {
                    int end = Math.min(content.length(), start + DELTA_SIZE);
                    if (end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))) end--;
                    return end;
                })
                .mapToObj(start -> {
                    int end = Math.min(content.length(), start + DELTA_SIZE);
                    if (end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))) end--;
                    return content.substring(start, end);
                })
                .toList();
    }

    private Object completedData(ChatMessage message) {
        return Map.of("message", new CompletedMessage(
                message.getId(), message.getRole().name(), message.getContent(), message.getStatus().name(),
                message.getEvidenceRefs(), message.getSafetyNotice(), message.getCreatedAt()
        ));
    }

    private void validateAnswer(ChatAiClient.ChatContext context, ChatAiClient.ChatAnswer answer) {
        if (answer == null || answer.content() == null || answer.content().isBlank()
                || answer.content().length() > 20000) {
            throw new IllegalArgumentException("AI answer content is invalid");
        }
        Set<String> allowedEvidenceIds = context.evidences().stream()
                .map(ChatAiClient.EvidenceContext::evidenceId)
                .collect(java.util.stream.Collectors.toSet());
        if (answer.evidenceRefs() != null && answer.evidenceRefs().stream()
                .anyMatch(item -> !allowedEvidenceIds.contains(item.evidenceId()))) {
            throw new IllegalArgumentException("AI answer referenced unknown evidence");
        }
        if (answer.safetyNotice() != null
                && !Set.of("SUPPORT_RECOMMENDATION", "CRISIS_SUPPORT").contains(answer.safetyNotice().type())) {
            throw new IllegalArgumentException("AI answer safety notice type is invalid");
        }
    }

    private ChatAiClient.ChatAnswer suppressRepeatedSupportRecommendation(
            String consultationId, ChatAiClient.ChatAnswer answer
    ) {
        if (answer == null || answer.safetyNotice() == null
                || !"SUPPORT_RECOMMENDATION".equals(answer.safetyNotice().type())) {
            return answer;
        }
        boolean alreadyShown = messageRepository.findAllByConsultationIdOrderByCreatedAtAsc(consultationId).stream()
                .anyMatch(message -> message.getSafetyNotice() != null
                        && "SUPPORT_RECOMMENDATION".equals(message.getSafetyNotice().type()));
        if (!alreadyShown) {
            return answer;
        }
        return new ChatAiClient.ChatAnswer(answer.content(), answer.evidenceRefs(), null);
    }

    private Object failedData(ChatMessage message) {
        return Map.of(
                "messageId", message.getId(),
                "code", "AI_UNAVAILABLE",
                "message", "AI 답변을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                "retryable", true
        );
    }

    private void send(String messageId, String eventName, Object data) {
        List<SseEmitter> subscribers = emitters.get(messageId);
        if (subscribers == null) return;
        subscribers.forEach(emitter -> sendToEmitter(messageId, emitter, eventName, data));
    }

    private void sendToEmitter(String messageId, SseEmitter emitter, String eventName, Object data) {
        try {
            long sequence = sequences.computeIfAbsent(messageId, ignored -> new AtomicLong()).incrementAndGet();
            emitter.send(SseEmitter.event()
                    .id(messageId + ":" + sequence)
                    .name(eventName)
                    .data(data));
        } catch (IOException | IllegalStateException exception) {
            remove(messageId, emitter);
        }
    }

    private void complete(String messageId) {
        List<SseEmitter> subscribers = emitters.remove(messageId);
        sequences.remove(messageId);
        if (subscribers != null) subscribers.forEach(SseEmitter::complete);
    }

    private void remove(String messageId, SseEmitter emitter) {
        List<SseEmitter> subscribers = emitters.get(messageId);
        if (subscribers == null) return;
        subscribers.remove(emitter);
        if (subscribers.isEmpty()) {
            emitters.remove(messageId, subscribers);
        }
    }

    private record CompletedMessage(
            String id,
            String role,
            String content,
            String status,
            List<ChatMessage.EvidenceReference> evidenceRefs,
            ChatMessage.SafetyNotice safetyNotice,
            Instant createdAt
    ) {}
}
