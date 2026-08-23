package com.relationshiptemperature.api.checkin.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CheckInMigrationTest {

    @Test
    void migratesLegacyScoreColumnsToAnswerRows() throws Exception {
        String databaseName = "checkin_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        UUID userId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID checkInId = UUID.randomUUID();

        migrate(url, "1");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO app_users (
                        id, kakao_subject, display_name, profile_image_url, timezone, created_at, updated_at
                    ) VALUES (
                        '%s', 'migration-user', '마이그레이션', NULL, 'Asia/Seoul', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(userId));
            statement.executeUpdate("""
                    INSERT INTO relationships (
                        id, user_id, name, relationship_type, status, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '기존 관계', 'FRIEND', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(relationshipId, userId));
            statement.executeUpdate("""
                    INSERT INTO check_ins (
                        id, user_id, relationship_id, week_start,
                        relationship_feeling, conversation_comfort, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '%s', DATE '2026-08-17', 6, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(checkInId, userId, relationshipId));
        }

        migrate(url, "2");

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            Map<String, Integer> scores = new HashMap<>();
            try (ResultSet rows = statement.executeQuery("""
                    SELECT question_code, score FROM check_in_answers WHERE check_in_id = '%s'
                    """.formatted(checkInId))) {
                while (rows.next()) {
                    scores.put(rows.getString("question_code"), rows.getInt("score"));
                }
            }
            assertThat(scores).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "RELATIONSHIP_FEELING", 6,
                    "CONVERSATION_COMFORT", 4
            ));

            try (ResultSet columns = statement.executeQuery("""
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_name = 'check_ins'
                      AND column_name IN ('relationship_feeling', 'conversation_comfort')
                    """)) {
                assertThat(columns.next()).isFalse();
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
