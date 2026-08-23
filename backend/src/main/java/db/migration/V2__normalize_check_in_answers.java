package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V2__normalize_check_in_answers extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        createAnswerTable(connection);
        migrateExistingAnswers(connection);
        dropLegacyScoreColumns(connection);
    }

    private void createAnswerTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE check_in_answers (
                        id UUID PRIMARY KEY,
                        check_in_id UUID NOT NULL REFERENCES check_ins(id) ON DELETE CASCADE,
                        question_code VARCHAR(50) NOT NULL,
                        score INTEGER NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT uk_checkin_answer_question UNIQUE (check_in_id, question_code),
                        CONSTRAINT ck_checkin_answer_score CHECK (score BETWEEN 1 AND 7)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX idx_checkin_answer_checkin ON check_in_answers(check_in_id)
                    """);
        }
    }

    private void migrateExistingAnswers(Connection connection) throws Exception {
        String selectSql = """
                SELECT id, relationship_feeling, conversation_comfort, created_at, updated_at
                FROM check_ins
                """;
        String insertSql = """
                INSERT INTO check_in_answers (
                    id, check_in_id, question_code, score, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Statement select = connection.createStatement();
             ResultSet rows = select.executeQuery(selectSql);
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            while (rows.next()) {
                UUID checkInId = uuid(rows.getObject("id"));
                insertAnswer(
                        insert,
                        checkInId,
                        "RELATIONSHIP_FEELING",
                        rows.getInt("relationship_feeling"),
                        rows.getObject("created_at"),
                        rows.getObject("updated_at")
                );
                insertAnswer(
                        insert,
                        checkInId,
                        "CONVERSATION_COMFORT",
                        rows.getInt("conversation_comfort"),
                        rows.getObject("created_at"),
                        rows.getObject("updated_at")
                );
            }
        }
    }

    private void insertAnswer(
            PreparedStatement insert,
            UUID checkInId,
            String questionCode,
            int score,
            Object createdAt,
            Object updatedAt
    ) throws Exception {
        insert.setObject(1, UUID.randomUUID());
        insert.setObject(2, checkInId);
        insert.setString(3, questionCode);
        insert.setInt(4, score);
        insert.setObject(5, createdAt);
        insert.setObject(6, updatedAt);
        insert.executeUpdate();
    }

    private void dropLegacyScoreColumns(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE check_ins DROP COLUMN relationship_feeling");
            statement.execute("ALTER TABLE check_ins DROP COLUMN conversation_comfort");
        }
    }

    private UUID uuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
