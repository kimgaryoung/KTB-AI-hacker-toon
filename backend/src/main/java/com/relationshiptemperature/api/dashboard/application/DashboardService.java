package com.relationshiptemperature.api.dashboard.application;

import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.domain.RelationshipStatus;
import com.relationshiptemperature.api.relationship.repository.RelationshipRepository;
import com.relationshiptemperature.api.report.domain.RelationshipReport;
import com.relationshiptemperature.api.report.repository.RelationshipReportRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int SPARKLINE_WEEKS = 8;

    private final RelationshipRepository relationshipRepository;
    private final RelationshipReportRepository reportRepository;

    public DashboardService(
            RelationshipRepository relationshipRepository,
            RelationshipReportRepository reportRepository
    ) {
        this.relationshipRepository = relationshipRepository;
        this.reportRepository = reportRepository;
    }

    public DashboardView get(UUID userId, LocalDate weekOf, Sort requestedSort) {
        LocalDate start = weekOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        Sort sort = requestedSort == null ? Sort.ABS_CHANGE_DESC : requestedSort;

        Map<UUID, Relationship> relationshipsById = relationshipRepository
                .findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(relationship -> relationship.getStatus() != RelationshipStatus.DRAFT)
                .filter(relationship -> relationship.getStatus() != RelationshipStatus.DELETING)
                .collect(Collectors.toMap(
                        Relationship::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<UUID, List<Integer>> sparklines = sparklines(userId, start);
        List<DashboardItem> items = reportRepository.findLatestAsOfWeek(userId, start).stream()
                .filter(report -> relationshipsById.containsKey(report.getRelationshipId()))
                .map(report -> new DashboardItem(
                        relationshipsById.get(report.getRelationshipId()),
                        report,
                        sparklines.getOrDefault(report.getRelationshipId(), List.of())
                ))
                .sorted(sort.comparator())
                .toList();

        Integer averageScore = average(items.stream().map(item -> item.report().getOverallScore()).toList());
        Integer averageChange = average(items.stream()
                .map(item -> item.report().getScoreChange())
                .filter(java.util.Objects::nonNull)
                .toList());
        List<DashboardItem> largest = items.stream()
                .filter(item -> item.report().getScoreChange() != null)
                .sorted(largestChangeComparator())
                .limit(3)
                .toList();
        List<DashboardItem> attention = items.stream()
                .filter(DashboardService::needsAttention)
                .toList();

        return new DashboardView(start, end, items, averageScore, averageChange, largest, attention);
    }

    private Map<UUID, List<Integer>> sparklines(UUID userId, LocalDate selectedWeek) {
        LocalDate from = selectedWeek.minusWeeks(SPARKLINE_WEEKS - 1L);
        List<RelationshipReport> reports = reportRepository
                .findAllByUserIdAndWeekStartBetweenOrderByRelationshipIdAscWeekStartAscAnalyzedAtDesc(
                        userId, from, selectedWeek
                );

        Map<UUID, Map<LocalDate, RelationshipReport>> latestByRelationshipAndWeek = new LinkedHashMap<>();
        for (RelationshipReport report : reports) {
            latestByRelationshipAndWeek
                    .computeIfAbsent(report.getRelationshipId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(report.getWeekStart(), report);
        }

        Map<UUID, List<Integer>> result = new LinkedHashMap<>();
        latestByRelationshipAndWeek.forEach((relationshipId, weeklyReports) -> result.put(
                relationshipId,
                weeklyReports.values().stream().map(RelationshipReport::getOverallScore).toList()
        ));
        return result;
    }

    private Integer average(List<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        return (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElseThrow());
    }

    private static boolean needsAttention(DashboardItem item) {
        Integer change = item.report().getScoreChange();
        return item.report().getOverallScore() < 60 || (change != null && change <= -10);
    }

    private static Comparator<DashboardItem> largestChangeComparator() {
        return Comparator
                .comparingInt((DashboardItem item) -> Math.abs(item.report().getScoreChange()))
                .reversed()
                .thenComparing(item -> item.report().getAnalyzedAt(), Comparator.reverseOrder())
                .thenComparing(item -> item.relationship().getId());
    }

    public enum Sort {
        ABS_CHANGE_DESC,
        SCORE_DESC,
        SCORE_ASC,
        UPDATED_DESC;

        Comparator<DashboardItem> comparator() {
            Comparator<DashboardItem> analyzedDesc = Comparator.comparing(
                    item -> item.report().getAnalyzedAt(),
                    Comparator.reverseOrder()
            );
            Comparator<DashboardItem> primary = switch (this) {
                case ABS_CHANGE_DESC -> Comparator.comparing(
                        item -> item.report().getScoreChange() == null
                                ? null
                                : Math.abs(item.report().getScoreChange()),
                        Comparator.nullsLast(Comparator.reverseOrder())
                );
                case SCORE_DESC -> Comparator.comparing(
                        item -> item.report().getOverallScore(),
                        Comparator.reverseOrder()
                );
                case SCORE_ASC -> Comparator.comparingInt(item -> item.report().getOverallScore());
                case UPDATED_DESC -> analyzedDesc;
            };
            Comparator<DashboardItem> idOrder = Comparator.comparing(item -> item.relationship().getId());
            return this == UPDATED_DESC
                    ? primary.thenComparing(idOrder)
                    : primary.thenComparing(analyzedDesc).thenComparing(idOrder);
        }
    }

    public record DashboardItem(
            Relationship relationship,
            RelationshipReport report,
            List<Integer> sparkline
    ) {
        public DashboardItem {
            sparkline = List.copyOf(new ArrayList<>(sparkline));
        }
    }

    public record DashboardView(
            LocalDate startDate,
            LocalDate endDate,
            List<DashboardItem> relationships,
            Integer averageScore,
            Integer averageChange,
            List<DashboardItem> largestChanges,
            List<DashboardItem> needsAttention
    ) {}
}
