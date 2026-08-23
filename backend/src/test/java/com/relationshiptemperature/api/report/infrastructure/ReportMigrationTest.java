package com.relationshiptemperature.api.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ReportMigrationTest {

    @Test
    void backfillsWeekStatusDisclaimerAndNormalizesLegacyEvidenceComponent() throws Exception {
        String databaseName = "report_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        UUID userId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID checkInId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();

        migrate(url, "2");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO app_users (
                        id, kakao_subject, display_name, profile_image_url, timezone, created_at, updated_at
                    ) VALUES (
                        '%s', 'report-migration', '리포트', NULL, 'Asia/Seoul', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(userId));
            statement.executeUpdate("""
                    INSERT INTO relationships (
                        id, user_id, name, relationship_type, status, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '기존 관계', 'FRIEND', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(relationshipId, userId));
            statement.executeUpdate("""
                    INSERT INTO conversation_files (
                        id, user_id, relationship_id, original_file_name, storage_key, size_bytes, sha256,
                        validation_status, expires_at, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '%s', 'talk.txt', 'legacy/talk.txt', 100,
                        '%s', 'VALID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(fileId, userId, relationshipId, "a".repeat(64)));
            statement.executeUpdate("""
                    INSERT INTO check_ins (
                        id, user_id, relationship_id, week_start, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '%s', DATE '2026-08-17', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(checkInId, userId, relationshipId));
            statement.executeUpdate("""
                    INSERT INTO analysis_jobs (
                        id, user_id, relationship_id, conversation_file_id, check_in_id,
                        status, stage, progress, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '%s', '%s', '%s',
                        'SUCCEEDED', 'CALCULATING_RELATIONSHIP_SCORE', 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(jobId, userId, relationshipId, fileId, checkInId));
            statement.executeUpdate("""
                    INSERT INTO relationship_reports (
                        id, user_id, relationship_id, analysis_job_id, overall_score, score_change,
                        satisfaction, commitment, intimacy, trust, passion, love,
                        model_version, scoring_policy_version, analyzed_at, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '%s', '%s', 58, -15,
                        55, 45, 68, 72, 40, 58,
                        'prqc-2026-08-19.1', 'relationship-temperature-1.0.0',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(reportId, userId, relationshipId, jobId));
            statement.executeUpdate("""
                    INSERT INTO report_evidences (
                        id, report_id, component, score, summary, metric_name, current_value,
                        previous_value, metric_unit, metric_period, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', 'passion', 40, '관찰 근거', 'weeklyConversationCount', 1.1,
                        3.2, '회/주', '최근 4주 vs 이전 4주', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(evidenceId, reportId));
        }

        migrate(url, "3");

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet report = statement.executeQuery("""
                    SELECT week_start, status_code, status_label, disclaimer
                    FROM relationship_reports WHERE id = '%s'
                    """.formatted(reportId))) {
                assertThat(report.next()).isTrue();
                assertThat(report.getString("week_start")).isEqualTo("2026-08-17");
                assertThat(report.getString("status_code")).isEqualTo("NEEDS_ATTENTION");
                assertThat(report.getString("status_label")).isEqualTo("주의 필요");
                assertThat(report.getString("disclaimer")).isNotBlank();
            }
            try (ResultSet evidence = statement.executeQuery("""
                    SELECT component FROM report_evidences WHERE id = '%s'
                    """.formatted(evidenceId))) {
                assertThat(evidence.next()).isTrue();
                assertThat(evidence.getString("component")).isEqualTo("PASSION");
            }
        }
    }

    private void migrate(String url, String target) {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
