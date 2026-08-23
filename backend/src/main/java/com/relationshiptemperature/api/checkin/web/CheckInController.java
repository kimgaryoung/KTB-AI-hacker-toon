package com.relationshiptemperature.api.checkin.web;

import com.relationshiptemperature.api.auth.application.CurrentUserService;
import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.checkin.application.CheckInService;
import com.relationshiptemperature.api.checkin.application.CheckInService.CheckInView;
import com.relationshiptemperature.api.checkin.application.CheckInService.SaveResult;
import com.relationshiptemperature.api.checkin.domain.QuestionCode;
import com.relationshiptemperature.api.common.api.ApiResponse;
import com.relationshiptemperature.api.common.api.PagedResponse;
import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/relationships/{relationshipId}/check-ins")
public class CheckInController {

    private final CurrentUserService currentUserService;
    private final CheckInService checkInService;

    public CheckInController(CurrentUserService currentUserService, CheckInService checkInService) {
        this.currentUserService = currentUserService;
        this.checkInService = checkInService;
    }

    @PostMapping
    ResponseEntity<ApiResponse<CheckInResponse>> create(
            @PathVariable UUID relationshipId,
            @Valid @RequestBody CreateCheckInRequest request
    ) {
        User user = currentUserService.requireUser();
        SaveResult result = checkInService.save(
                user.getId(),
                relationshipId,
                LocalDate.now(ZoneId.of(user.getTimezone())),
                scores(request.answers())
        );
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.of(CheckInResponse.from(result.checkIn())));
    }

    @GetMapping
    PagedResponse<CheckInResponse> history(
            @PathVariable UUID relationshipId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<CheckInResponse> responses = checkInService.history(
                        currentUserService.requireUserId(), relationshipId, from, to
                ).stream()
                .map(CheckInResponse::from)
                .toList();
        return PagedResponse.singlePage(responses);
    }

    private Map<QuestionCode, Integer> scores(List<Answer> answers) {
        Map<QuestionCode, Integer> scores = new EnumMap<>(QuestionCode.class);
        for (Answer answer : answers) {
            if (scores.put(answer.questionCode(), answer.score()) != null) {
                throw new ApiException(ErrorCode.CHECK_IN_INCOMPLETE, "체크인 질문은 한 번씩만 응답해야 합니다.");
            }
        }
        if (scores.size() != QuestionCode.values().length) {
            throw new ApiException(ErrorCode.CHECK_IN_INCOMPLETE);
        }
        return scores;
    }

    public record Answer(
            @NotNull QuestionCode questionCode,
            @Min(1) @Max(7) int score
    ) {}

    public record CreateCheckInRequest(@NotEmpty List<@Valid Answer> answers) {}

    public record CheckInResponse(
            UUID id,
            UUID relationshipId,
            List<Answer> answers,
            LocalDate weekStart,
            Instant createdAt,
            Instant updatedAt
    ) {
        static CheckInResponse from(CheckInView checkIn) {
            List<Answer> answers = new ArrayList<>();
            for (QuestionCode code : QuestionCode.values()) {
                Integer score = checkIn.scores().get(code);
                if (score != null) {
                    answers.add(new Answer(code, score));
                }
            }
            return new CheckInResponse(
                    checkIn.id(),
                    checkIn.relationshipId(),
                    List.copyOf(answers),
                    checkIn.weekStart(),
                    checkIn.createdAt(),
                    checkIn.updatedAt()
            );
        }
    }
}
