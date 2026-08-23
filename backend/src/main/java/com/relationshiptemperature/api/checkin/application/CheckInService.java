package com.relationshiptemperature.api.checkin.application;

import com.relationshiptemperature.api.checkin.domain.CheckIn;
import com.relationshiptemperature.api.checkin.domain.CheckInAnswer;
import com.relationshiptemperature.api.checkin.domain.QuestionCode;
import com.relationshiptemperature.api.checkin.repository.CheckInAnswerRepository;
import com.relationshiptemperature.api.checkin.repository.CheckInRepository;
import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.relationship.application.RelationshipService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final CheckInAnswerRepository answerRepository;
    private final RelationshipService relationshipService;

    public CheckInService(
            CheckInRepository checkInRepository,
            CheckInAnswerRepository answerRepository,
            RelationshipService relationshipService
    ) {
        this.checkInRepository = checkInRepository;
        this.answerRepository = answerRepository;
        this.relationshipService = relationshipService;
    }

    @Transactional
    public SaveResult save(
            UUID userId,
            UUID relationshipId,
            LocalDate submittedOn,
            Map<QuestionCode, Integer> scores
    ) {
        relationshipService.getOwned(userId, relationshipId);
        validateScores(scores);

        LocalDate weekStart = submittedOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        CheckIn checkIn = checkInRepository.findByRelationshipIdAndWeekStart(relationshipId, weekStart)
                .orElse(null);
        boolean created = checkIn == null;
        if (created) {
            checkIn = checkInRepository.save(new CheckIn(userId, relationshipId, weekStart));
        }

        UUID checkInId = checkIn.getId();
        for (QuestionCode code : QuestionCode.values()) {
            CheckInAnswer answer = answerRepository.findByCheckInIdAndQuestionCode(checkInId, code)
                    .orElseGet(() -> new CheckInAnswer(checkInId, code, scores.get(code)));
            answer.updateScore(scores.get(code));
            answerRepository.save(answer);
        }
        // 갱신 감사 시각은 flush 시점에 채워지므로 응답을 만들기 전에 반영한다.
        answerRepository.flush();

        return new SaveResult(toView(checkIn, answers(checkInId)), created);
    }

    public List<CheckInView> history(
            UUID userId,
            UUID relationshipId,
            LocalDate from,
            LocalDate to
    ) {
        relationshipService.getOwned(userId, relationshipId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.");
        }

        List<CheckIn> checkIns = checkInRepository
                .findAllByUserIdAndRelationshipIdOrderByWeekStartDesc(userId, relationshipId)
                .stream()
                .filter(checkIn -> from == null || !checkIn.getWeekStart().isBefore(from))
                .filter(checkIn -> to == null || !checkIn.getWeekStart().isAfter(to))
                .toList();
        if (checkIns.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<CheckInAnswer>> answersByCheckIn = answerRepository.findAllByCheckInIdIn(
                        checkIns.stream().map(CheckIn::getId).toList()
                ).stream()
                .collect(Collectors.groupingBy(CheckInAnswer::getCheckInId));
        return checkIns.stream()
                .map(checkIn -> toView(checkIn, answersByCheckIn.getOrDefault(checkIn.getId(), List.of())))
                .toList();
    }

    private List<CheckInAnswer> answers(UUID checkInId) {
        return answerRepository.findAllByCheckInIdIn(List.of(checkInId));
    }

    private void validateScores(Map<QuestionCode, Integer> scores) {
        if (scores.size() != QuestionCode.values().length) {
            throw new ApiException(ErrorCode.CHECK_IN_INCOMPLETE);
        }
        for (QuestionCode code : QuestionCode.values()) {
            Integer score = scores.get(code);
            if (score == null || score < 1 || score > 7) {
                throw new ApiException(ErrorCode.CHECK_IN_INCOMPLETE);
            }
        }
    }

    private CheckInView toView(CheckIn checkIn, List<CheckInAnswer> answers) {
        Map<QuestionCode, Integer> scores = new EnumMap<>(QuestionCode.class);
        answers.stream()
                .sorted(Comparator.comparing(CheckInAnswer::getQuestionCode))
                .forEach(answer -> scores.put(answer.getQuestionCode(), answer.getScore()));
        Instant updatedAt = answers.stream()
                .map(CheckInAnswer::getUpdatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(checkIn.getUpdatedAt());
        return new CheckInView(
                checkIn.getId(),
                checkIn.getRelationshipId(),
                Map.copyOf(scores),
                checkIn.getWeekStart(),
                checkIn.getCreatedAt(),
                updatedAt
        );
    }

    public record SaveResult(CheckInView checkIn, boolean created) {}

    public record CheckInView(
            UUID id,
            UUID relationshipId,
            Map<QuestionCode, Integer> scores,
            LocalDate weekStart,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
