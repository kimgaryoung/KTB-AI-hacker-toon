package com.relationshiptemperature.api.dashboard.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.relationshiptemperature.api.analysis.application.AiAnalysisClient.AnalysisResult;
import com.relationshiptemperature.api.analysis.application.AiAnalysisClient.EvidenceResult;
import com.relationshiptemperature.api.analysis.domain.AnalysisJob;
import com.relationshiptemperature.api.analysis.repository.AnalysisJobRepository;
import com.relationshiptemperature.api.auth.application.AppOAuth2User;
import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.auth.repository.UserRepository;
import com.relationshiptemperature.api.checkin.domain.CheckIn;
import com.relationshiptemperature.api.checkin.repository.CheckInRepository;
import com.relationshiptemperature.api.conversation.domain.ConversationFile;
import com.relationshiptemperature.api.conversation.repository.ConversationFileRepository;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.relationship.repository.RelationshipRepository;
import com.relationshiptemperature.api.report.application.ReportService;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import com.relationshiptemperature.api.report.domain.ReportEvidence.Metric;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RelationshipRepository relationshipRepository;
    @Autowired ConversationFileRepository fileRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired AnalysisJobRepository jobRepository;
    @Autowired ReportService reportService;

    @Test
    void aggregatesWeeklyCardsAverageTopChangesAttentionAndSparklines() throws Exception {
        User user = userRepository.save(User.kakao("dashboard-main", "대시보드", null));
        LocalDate selectedWeek = LocalDate.of(2026, 8, 17);

        Fixture a = fixture(user, "큰 상승");
        createReport(a, selectedWeek.minusWeeks(1), 50);
        CheckIn aCurrent = checkIn(a, selectedWeek);
        createReport(a, aCurrent, 75);
        createReport(a, aCurrent, 80);
        // 과거 주차를 뒤늦게 재분석해도 카드의 최신 주차가 되돌아가면 안 된다.
        createReport(a, selectedWeek.minusWeeks(2), 10);

        Fixture b = fixture(user, "낮고 하락");
        createReport(b, selectedWeek.minusWeeks(1), 80);
        createReport(b, selectedWeek, 55);

        Fixture c = fixture(user, "중간 상승");
        createReport(c, selectedWeek.minusWeeks(1), 40);
        createReport(c, selectedWeek, 60);

        Fixture d = fixture(user, "큰 하락");
        createReport(d, selectedWeek.minusWeeks(1), 80);
        createReport(d, selectedWeek, 70);

        Fixture e = fixture(user, "낮은 점수");
        createReport(e, selectedWeek, 50);

        Fixture f = fixture(user, "지난 분석");
        createReport(f, selectedWeek.minusWeeks(1), 75);

        Fixture future = fixture(user, "미래 분석");
        createReport(future, selectedWeek.plusWeeks(1), 90);

        relationshipRepository.save(Relationship.draft(user.getId(), "분석 전", RelationshipType.FAMILY));

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(authentication(oauthAuthentication(user)))
                        .param("weekOf", "2026-08-20")
                        .param("sort", "ABS_CHANGE_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.week.startDate").value("2026-08-17"))
                .andExpect(jsonPath("$.data.week.endDate").value("2026-08-23"))
                .andExpect(jsonPath("$.data.week.label").value("2026년 8월 3주차"))
                .andExpect(jsonPath("$.data.summary.relationshipCount").value(6))
                .andExpect(jsonPath("$.data.summary.averageScore").value(65))
                .andExpect(jsonPath("$.data.summary.averageChange").value(4))
                .andExpect(jsonPath("$.data.relationships.length()").value(6))
                .andExpect(jsonPath("$.data.relationships[0].name").value("큰 상승"))
                .andExpect(jsonPath("$.data.relationships[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.relationships[0].score").value(80))
                .andExpect(jsonPath("$.data.relationships[0].change").value(30))
                .andExpect(jsonPath("$.data.relationships[0].statusCode").value("HEALTHY"))
                .andExpect(jsonPath("$.data.relationships[0].sparkline.length()").value(3))
                .andExpect(jsonPath("$.data.relationships[0].sparkline[0]").value(10))
                .andExpect(jsonPath("$.data.relationships[0].sparkline[1]").value(50))
                .andExpect(jsonPath("$.data.relationships[0].sparkline[2]").value(80))
                .andExpect(jsonPath("$.data.relationships[0].lastAnalyzedAt").exists())
                .andExpect(jsonPath("$.data.largestChanges.length()").value(3))
                .andExpect(jsonPath("$.data.largestChanges[0].name").value("큰 상승"))
                .andExpect(jsonPath("$.data.largestChanges[0].change").value(30))
                .andExpect(jsonPath("$.data.largestChanges[1].name").value("낮고 하락"))
                .andExpect(jsonPath("$.data.largestChanges[1].change").value(-25))
                .andExpect(jsonPath("$.data.largestChanges[2].name").value("중간 상승"))
                .andExpect(jsonPath("$.data.largestChanges[2].change").value(20))
                .andExpect(jsonPath("$.data.needsAttention.length()").value(3))
                .andExpect(jsonPath("$.data.needsAttention[0].name").value("낮고 하락"))
                .andExpect(jsonPath("$.data.needsAttention[0].reasonCode").value("SCORE_AND_DROP"))
                .andExpect(jsonPath("$.data.needsAttention[1].name").value("큰 하락"))
                .andExpect(jsonPath("$.data.needsAttention[1].reasonCode").value("LARGE_DROP"))
                .andExpect(jsonPath("$.data.needsAttention[2].name").value("낮은 점수"))
                .andExpect(jsonPath("$.data.needsAttention[2].reasonCode").value("LOW_SCORE"))
                .andExpect(jsonPath("$.data.relationships[*].name")
                        .value(not(hasItems("미래 분석", "분석 전"))));
    }

    @Test
    void supportsScoreSortingAndDoesNotExposeAnotherUsersAggregate() throws Exception {
        User owner = userRepository.save(User.kakao("dashboard-sort-owner", "소유자", null));
        User other = userRepository.save(User.kakao("dashboard-sort-other", "다른 사용자", null));
        LocalDate week = LocalDate.of(2026, 8, 17);
        createReport(fixture(owner, "높음"), week, 80);
        createReport(fixture(owner, "낮음"), week, 40);

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(authentication(oauthAuthentication(owner)))
                        .param("weekOf", week.toString())
                        .param("sort", "SCORE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relationships[0].name").value("낮음"))
                .andExpect(jsonPath("$.data.relationships[1].name").value("높음"));

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(authentication(oauthAuthentication(other)))
                        .param("weekOf", week.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.relationshipCount").value(0))
                .andExpect(jsonPath("$.data.summary.averageScore").doesNotExist())
                .andExpect(jsonPath("$.data.summary.averageChange").doesNotExist())
                .andExpect(jsonPath("$.data.relationships.length()").value(0));
    }

    @Test
    void returnsBadRequestForInvalidWeekOrSort() throws Exception {
        User user = userRepository.save(User.kakao("dashboard-invalid", "검증", null));

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(authentication(oauthAuthentication(user)))
                        .param("weekOf", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fields[0].field").value("weekOf"));

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(authentication(oauthAuthentication(user)))
                        .param("sort", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fields[0].field").value("sort"));
    }

    private Fixture fixture(User user, String name) {
        Relationship relationship = relationshipRepository.save(
                Relationship.draft(user.getId(), name, RelationshipType.FRIEND)
        );
        ConversationFile file = new ConversationFile(
                user.getId(), relationship.getId(), "talk.txt", "dashboard/" + relationship.getId(), 100,
                "b".repeat(64), Instant.now().plusSeconds(3600)
        );
        file.validated(10, Instant.now().minusSeconds(60), Instant.now());
        return new Fixture(user, relationship, fileRepository.save(file));
    }

    private void createReport(Fixture fixture, LocalDate weekStart, int score) {
        createReport(fixture, checkIn(fixture, weekStart), score);
    }

    private void createReport(Fixture fixture, CheckIn checkIn, int score) {
        AnalysisJob job = jobRepository.save(new AnalysisJob(
                fixture.user().getId(), fixture.relationship().getId(), fixture.file().getId(), checkIn.getId()
        ));
        Relationship relationship = relationshipRepository.findById(fixture.relationship().getId()).orElseThrow();
        reportService.create(job.getId(), checkIn.getId(), relationship, analysisResult(score));
    }

    private CheckIn checkIn(Fixture fixture, LocalDate weekStart) {
        return checkInRepository.findByRelationshipIdAndWeekStart(fixture.relationship().getId(), weekStart)
                .orElseGet(() -> checkInRepository.save(new CheckIn(
                        fixture.user().getId(), fixture.relationship().getId(), weekStart
                )));
    }

    private AnalysisResult analysisResult(int score) {
        PrqcScores scores = new PrqcScores(score, score, score, score, score, score);
        return new AnalysisResult(
                "prqc-2026-08-19.1", "relationship-evidence-1.0.0", 100, scores,
                List.of(new EvidenceResult(
                        "passion", score, "대화 패턴 변화가 관찰됐어요.",
                        new Metric("weeklyConversationCount", 1.0, 2.0, "회/주", "최근 4주 vs 이전 4주")
                )),
                "체크인 응답과 대화 분석을 비교한 설명이에요."
        );
    }

    private OAuth2AuthenticationToken oauthAuthentication(User user) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new AppOAuth2User(user.getId(), user.getKakaoSubject(), Map.of(), authorities);
        return new OAuth2AuthenticationToken(principal, authorities, "kakao");
    }

    private record Fixture(User user, Relationship relationship, ConversationFile file) {}
}
