package com.relationshiptemperature.api.dashboard.web;

import com.relationshiptemperature.api.auth.application.CurrentUserService;
import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.common.api.ApiResponse;
import com.relationshiptemperature.api.dashboard.application.DashboardService;
import com.relationshiptemperature.api.dashboard.application.DashboardService.DashboardItem;
import com.relationshiptemperature.api.dashboard.application.DashboardService.DashboardView;
import com.relationshiptemperature.api.dashboard.application.DashboardService.Sort;
import com.relationshiptemperature.api.relationship.domain.RelationshipStatus;
import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final CurrentUserService currentUserService;
    private final DashboardService dashboardService;

    public DashboardController(CurrentUserService currentUserService, DashboardService dashboardService) {
        this.currentUserService = currentUserService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    ApiResponse<DashboardResponse> get(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf,
            @RequestParam(defaultValue = "ABS_CHANGE_DESC") Sort sort
    ) {
        User user = currentUserService.requireUser();
        LocalDate selectedDate = weekOf == null
                ? LocalDate.now(ZoneId.of(user.getTimezone()))
                : weekOf;
        return ApiResponse.of(DashboardResponse.from(
                dashboardService.get(user.getId(), selectedDate, sort)
        ));
    }

    record Week(LocalDate startDate, LocalDate endDate, String label) {}
    record Summary(int relationshipCount, Integer averageScore, Integer averageChange) {}
    record RelationshipCard(
            UUID id,
            String name,
            String initial,
            RelationshipType relationshipType,
            RelationshipStatus status,
            Integer score,
            String statusCode,
            String statusLabel,
            Integer change,
            Instant lastAnalyzedAt,
            List<Integer> sparkline
    ) {}
    record LargestChange(UUID relationshipId, String name, Integer change, List<Integer> sparkline) {}
    record Attention(UUID relationshipId, String name, Integer score, String reasonCode, String reasonLabel) {}
    record DashboardResponse(
            Week week,
            Summary summary,
            List<RelationshipCard> relationships,
            List<LargestChange> largestChanges,
            List<Attention> needsAttention
    ) {
        static DashboardResponse from(DashboardView view) {
            return new DashboardResponse(
                    new Week(view.startDate(), view.endDate(), weekLabel(view.startDate())),
                    new Summary(view.relationships().size(), view.averageScore(), view.averageChange()),
                    view.relationships().stream().map(DashboardResponse::card).toList(),
                    view.largestChanges().stream().map(item -> new LargestChange(
                            item.relationship().getId(), item.relationship().getName(),
                            item.report().getScoreChange(), item.sparkline()
                    )).toList(),
                    view.needsAttention().stream().map(DashboardResponse::attention).toList()
            );
        }

        private static RelationshipCard card(DashboardItem item) {
            return new RelationshipCard(
                    item.relationship().getId(), item.relationship().getName(), item.relationship().getInitial(),
                    item.relationship().getRelationshipType(), item.relationship().getStatus(),
                    item.report().getOverallScore(), item.report().getStatusCode().name(),
                    item.report().getStatusLabel(), item.report().getScoreChange(),
                    item.report().getAnalyzedAt(), item.sparkline()
            );
        }

        private static Attention attention(DashboardItem item) {
            int score = item.report().getOverallScore();
            Integer change = item.report().getScoreChange();
            boolean both = score < 60 && change != null && change <= -10;
            if (both) {
                return new Attention(
                        item.relationship().getId(), item.relationship().getName(), score,
                        "SCORE_AND_DROP", "낮은 점수와 큰 하락이 함께 관찰됨"
                );
            }
            if (score < 60) {
                return new Attention(
                        item.relationship().getId(), item.relationship().getName(), score,
                        "LOW_SCORE", "점수가 주의 기준보다 낮음"
                );
            }
            return new Attention(
                    item.relationship().getId(), item.relationship().getName(), score,
                    "LARGE_DROP", "전주 대비 큰 하락이 관찰됨"
            );
        }

        private static String weekLabel(LocalDate startDate) {
            int weekOfMonth = ((startDate.getDayOfMonth() - 1) / 7) + 1;
            return "%d년 %d월 %d주차".formatted(
                    startDate.getYear(), startDate.getMonthValue(), weekOfMonth
            );
        }
    }
}
