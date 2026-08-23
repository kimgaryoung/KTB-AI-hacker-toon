package com.relationshiptemperature.api.conversation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ConversationMessageMigrationTest {

    @Test
    void addsParticipantNamesAndStoresMessagesInSequenceOrderWithUniquePositions() throws Exception {
        String databaseName = "conversation_message_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        UUID userId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        migrate(url, "4");
        insertConversationFile(url, userId, relationshipId, fileId);
        migrate(url, "5");

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE conversation_files
                    SET self_participant_name = '민지', other_participant_name = '준호'
                    WHERE id = '%s'
                    """.formatted(fileId));
            statement.executeUpdate(messageInsert(UUID.randomUUID(), fileId, relationshipId, 2, "준호", "OTHER", "두 번째"));
            statement.executeUpdate(messageInsert(UUID.randomUUID(), fileId, relationshipId, 1, "민지", "SELF", "첫 번째"));

            try (ResultSet file = statement.executeQuery("""
                    SELECT self_participant_name, other_participant_name
                    FROM conversation_files WHERE id = '%s'
                    """.formatted(fileId))) {
                assertThat(file.next()).isTrue();
                assertThat(file.getString("self_participant_name")).isEqualTo("민지");
                assertThat(file.getString("other_participant_name")).isEqualTo("준호");
            }
            try (ResultSet messages = statement.executeQuery("""
                    SELECT sequence_number, content FROM conversation_messages
                    WHERE conversation_file_id = '%s'
                    ORDER BY sequence_number ASC
                    """.formatted(fileId))) {
                assertThat(messages.next()).isTrue();
                assertThat(messages.getInt("sequence_number")).isEqualTo(1);
                assertThat(messages.getString("content")).isEqualTo("첫 번째");
                assertThat(messages.next()).isTrue();
                assertThat(messages.getInt("sequence_number")).isEqualTo(2);
                assertThat(messages.getString("content")).isEqualTo("두 번째");
            }

            assertThatThrownBy(() -> statement.executeUpdate(
                    messageInsert(UUID.randomUUID(), fileId, relationshipId, 1, "민지", "SELF", "중복")
            )).isInstanceOf(Exception.class);

            statement.executeUpdate("DELETE FROM conversation_files WHERE id = '%s'".formatted(fileId));
            try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM conversation_messages")) {
                assertThat(count.next()).isTrue();
                assertThat(count.getInt(1)).isZero();
            }
        }
    }

    private void insertConversationFile(String url, UUID userId, UUID relationshipId, UUID fileId) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO app_users (
                        id, kakao_subject, display_name, timezone, created_at, updated_at
                    ) VALUES (
                        '%s', 'conversation-message-migration', '민지', 'Asia/Seoul', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(userId));
            statement.executeUpdate("""
                    INSERT INTO relationships (
                        id, user_id, name, relationship_type, status, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '준호', 'FRIEND', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(relationshipId, userId));
            statement.executeUpdate("""
                    INSERT INTO conversation_files (
                        id, user_id, relationship_id, original_file_name, storage_key, size_bytes, sha256,
                        validation_status, expires_at, created_at, updated_at
                    ) VALUES (
                        '%s', '%s', '%s', 'talk.txt', 'uploads/talk.txt', 100, '%s', 'VALID',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(fileId, userId, relationshipId, "a".repeat(64)));
        }
    }

    private String messageInsert(
            UUID messageId,
            UUID fileId,
            UUID relationshipId,
            int sequenceNumber,
            String senderName,
            String participantRole,
            String content
    ) {
        return """
                INSERT INTO conversation_messages (
                    id, conversation_file_id, relationship_id, sequence_number, sent_at, sender_name,
                    participant_role, content, created_at, updated_at
                ) VALUES (
                    '%s', '%s', '%s', %d, CURRENT_TIMESTAMP, '%s', '%s', '%s',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(messageId, fileId, relationshipId, sequenceNumber, senderName, participantRole, content);
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
