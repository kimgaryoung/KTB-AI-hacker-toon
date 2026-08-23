package com.relationshiptemperature.api.report.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import com.relationshiptemperature.api.report.domain.RelationshipReport;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import com.relationshiptemperature.api.report.domain.ReportEvidence;
import com.relationshiptemperature.api.report.domain.ReportEvidence.Metric;
import com.relationshiptemperature.api.report.domain.ReportStatus;
import com.relationshiptemperature.api.report.repository.RelationshipReportRepository;
import com.relationshiptemperature.api.report.repository.ReportEvidenceRepository;
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
class ReportControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RelationshipRepository relationshipRepository;
    @Autowired ConversationFileRepository fileRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired AnalysisJobRepository jobRepository;
    @Autowired RelationshipReportRepository reportRepository;
    @Autowired ReportEvidenceRepository evidenceRepository;
    @Autowired ReportService reportService;

    @Test
    void storesPrqcEvidenceSnapshotAndCalculatesChangeAgainstPreviousWeek() {
        Fixture fixture = fixture("report-save", "주차 비교");
        LocalDate currentWeek = LocalDate.of(2026, 8, 17);
        createReport(fixture, currentWeek.minusWeeks(1), 52);
        CreatedReport current = createReport(fixture, currentWeek, 70);

        RelationshipReport saved = current.report();
        assertThat(saved.getOverallScore()).isEqualTo(70);
        assertThat(saved.getScoreChange()).isEqualTo(18);
        assertThat(saved.getWeekStart()).isEqualTo(currentWeek);
        assertThat(saved.getPrqcScores()).isEqualTo(new PrqcScores(70, 70, 70, 70, 70, 70));
        assertThat(saved.getStatusCode()).isEqualTo(ReportStatus.GOOD);
        assertThat(saved.getStatusLabel()).isEqualTo("양호");
        assertThat(saved.getDisclaimer()).isEqualTo(RelationshipReport.DEFAULT_DISCLAIMER);
        assertThat(saved.getSelfReportComparison()).isEqualTo("체크인 응답과 대화 분석을 비교한 설명이에요.");
        assertThat(evidenceRepository.findAllByReportId(saved.getId()))
                .singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.getComponent().apiCode()).isEqualTo("passion");
                    assertThat(evidence.getScore()).isEqualTo(70);
                    assertThat(evidence.getMetric().period()).isEqualTo("최근 4주 vs 이전 4주");
                });

        Relationship updated = relationshipRepository.findById(fixture.relationship().getId()).orElseThrow();
        assertThat(updated.getLatestScore()).isEqualTo(70);
        assertThat(updated.getLatestChange()).isEqualTo(18);

        RelationshipReport idempotent = reportService.create(
                current.job().getId(), current.checkIn().getId(), updated, analysisResult(90)
        );
        assertThat(idempotent.getId()).isEqualTo(saved.getId());
        assertThat(reportRepository.findAll()).hasSize(2);
        assertThat(evidenceRepository.findAll()).hasSize(2);
    }

    @Test
    void returnsLatestReportAndOneLatestPointPerWeekForFourAndEightWeekTrends() throws Exception {
        Fixture fixture = fixture("report-trend", "추이 관계");
        LocalDate currentWeek = LocalDate.of(2026, 8, 17);
        createReport(fixture, currentWeek.minusWeeks(5), 40);
        createReport(fixture, currentWeek.minusWeeks(1), 55);
        CheckIn currentCheckIn = saveCheckIn(fixture, currentWeek);
        createReport(fixture, currentCheckIn, 65);
        CreatedReport latest = createReport(fixture, currentCheckIn, 70);

        mockMvc.perform(get("/api/v1/relationships/{id}/report", fixture.relationship().getId())
                        .with(authentication(oauthAuthentication(fixture.user())))
                        .param("weeks", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(latest.report().getId().toString()))
                .andExpect(jsonPath("$.data.overall.score").value(70))
                .andExpect(jsonPath("$.data.overall.change").value(15))
                .andExpect(jsonPath("$.data.overall.statusCode").value("GOOD"))
                .andExpect(jsonPath("$.data.prqc.satisfaction").value(70))
                .andExpect(jsonPath("$.data.prqc.love").value(70))
                .andExpect(jsonPath("$.data.evidences[0].component").value("passion"))
                .andExpect(jsonPath("$.data.evidences[0].metric.currentValue").value(1.1))
                .andExpect(jsonPath("$.data.selfReportComparison").value("체크인 응답과 대화 분석을 비교한 설명이에요."))
                .andExpect(jsonPath("$.data.trend.length()").value(3))
                .andExpect(jsonPath("$.data.trend[0].weekStart").value(currentWeek.minusWeeks(5).toString()))
                .andExpect(jsonPath("$.data.trend[0].label").value("5주 전"))
                .andExpect(jsonPath("$.data.trend[1].label").value("지난 주"))
                .andExpect(jsonPath("$.data.trend[2].weekStart").value(currentWeek.toString()))
                .andExpect(jsonPath("$.data.trend[2].label").value("이번 주"))
                .andExpect(jsonPath("$.data.trend[2].score").value(70))
                .andExpect(jsonPath("$.data.disclaimer").value(RelationshipReport.DEFAULT_DISCLAIMER));

        mockMvc.perform(get("/api/v1/relationships/{id}/report", fixture.relationship().getId())
                        .with(authentication(oauthAuthentication(fixture.user())))
                        .param("weeks", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trend.length()").value(2))
                .andExpect(jsonPath("$.data.trend[0].label").value("지난 주"))
                .andExpect(jsonPath("$.data.trend[1].label").value("이번 주"));
    }

    @Test
    void returnsConflictWithoutCompletedReportAndHidesOtherUsersRelationship() throws Exception {
        Fixture owner = fixture("report-owner", "소유 관계");
        User other = userRepository.save(User.kakao("report-other", "다른 사용자", null));

        mockMvc.perform(get("/api/v1/relationships/{id}/report", owner.relationship().getId())
                        .with(authentication(oauthAuthentication(owner.user()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_REQUIRED"));

        mockMvc.perform(get("/api/v1/relationships/{id}/report", owner.relationship().getId())
                        .with(authentication(oauthAuthentication(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RELATIONSHIP_NOT_FOUND"));
    }

    @Test
    void rejectsTrendWindowOutsideFourToFiftyTwoWeeks() throws Exception {
        Fixture fixture = fixture("report-weeks", "기간 검증");
        createReport(fixture, LocalDate.of(2026, 8, 17), 60);

        mockMvc.perform(get("/api/v1/relationships/{id}/report", fixture.relationship().getId())
                        .with(authentication(oauthAuthentication(fixture.user())))
                        .param("weeks", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/relationships/{id}/report", fixture.relationship().getId())
                        .with(authentication(oauthAuthentication(fixture.user())))
                        .param("weeks", "53"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    private Fixture fixture(String subject, String relationshipName) {
        User user = userRepository.save(User.kakao(subject, subject, null));
        Relationship relationship = relationshipRepository.save(
                Relationship.draft(user.getId(), relationshipName, RelationshipType.FRIEND)
        );
        ConversationFile file = new ConversationFile(
                user.getId(), relationship.getId(), "talk.txt", "test/talk.txt", 100,
                "a".repeat(64), Instant.now().plusSeconds(3600)
        );
        file.validated(10, Instant.now().minusSeconds(60), Instant.now());
        return new Fixture(user, relationship, fileRepository.save(file));
    }

    private CreatedReport createReport(Fixture fixture, LocalDate weekStart, int score) {
        return createReport(fixture, saveCheckIn(fixture, weekStart), score);
    }

    private CreatedReport createReport(Fixture fixture, CheckIn checkIn, int score) {
        AnalysisJob job = jobRepository.save(new AnalysisJob(
                fixture.user().getId(), fixture.relationship().getId(), fixture.file().getId(), checkIn.getId()
        ));
        Relationship relationship = relationshipRepository.findById(fixture.relationship().getId()).orElseThrow();
        RelationshipReport report = reportService.create(
                job.getId(), checkIn.getId(), relationship, analysisResult(score)
        );
        return new CreatedReport(report, job, checkIn);
    }

    private CheckIn saveCheckIn(Fixture fixture, LocalDate weekStart) {
        return checkInRepository.save(new CheckIn(
                fixture.user().getId(), fixture.relationship().getId(), weekStart
        ));
    }

    private AnalysisResult analysisResult(int score) {
        PrqcScores scores = new PrqcScores(score, score, score, score, score, score);
        return new AnalysisResult(
                "prqc-2026-08-19.1", "relationship-evidence-1.0.0", 100, scores,
                List.of(new EvidenceResult(
                        "passion", score, "대화 빈도의 변화가 관찰됐어요.",
                        new Metric("weeklyConversationCount", 1.1, 3.2, "회/주", "최근 4주 vs 이전 4주")
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
    private record CreatedReport(RelationshipReport report, AnalysisJob job, CheckIn checkIn) {}
}
