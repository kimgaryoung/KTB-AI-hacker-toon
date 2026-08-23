package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V3__add_report_week_and_snapshot_fields extends BaseJavaMigration {

    private static final String DISCLAIMER =
            "대화에서 관찰된 패턴을 바탕으로 한 참고 정보이며 관계를 진단하거나 단정하지 않습니다.";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addColumns(connection);
        backfillReports(connection);
        normalizeEvidenceComponents(connection);
        addConstraintsAndIndex(connection);
    }

    private void addColumns(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE relationship_reports ADD COLUMN week_start DATE");
            statement.execute("ALTER TABLE relationship_reports ADD COLUMN status_code VARCHAR(30)");
            statement.execute("ALTER TABLE relationship_reports ADD COLUMN status_label VARCHAR(50)");
            statement.execute("ALTER TABLE relationship_reports ADD COLUMN disclaimer VARCHAR(1000)");
        }
    }

    private void backfillReports(Connection connection) throws Exception {
        String selectSql = """
                SELECT r.id, r.overall_score, c.week_start
                FROM relationship_reports r
                JOIN analysis_jobs j ON j.id = r.analysis_job_id
                JOIN check_ins c ON c.id = j.check_in_id
                """;
        String updateSql = """
                UPDATE relationship_reports
                SET week_start = ?, status_code = ?, status_label = ?, disclaimer = ?
                WHERE id = ?
                """;
        try (Statement select = connection.createStatement();
             ResultSet rows = select.executeQuery(selectSql);
             PreparedStatement update = connection.prepareStatement(updateSql)) {
            while (rows.next()) {
                Status status = status(rows.getInt("overall_score"));
                update.setObject(1, rows.getObject("week_start"));
                update.setString(2, status.code());
                update.setString(3, status.label());
                update.setString(4, DISCLAIMER);
                update.setObject(5, rows.getObject("id"));
                update.executeUpdate();
            }
        }
    }

    private void normalizeEvidenceComponents(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE report_evidences SET component = UPPER(component)");
        }
    }

    private void addConstraintsAndIndex(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE relationship_reports ALTER COLUMN week_start SET NOT NULL");
            statement.execute("ALTER TABLE relationship_reports ALTER COLUMN status_code SET NOT NULL");
            statement.execute("ALTER TABLE relationship_reports ALTER COLUMN status_label SET NOT NULL");
            statement.execute("ALTER TABLE relationship_reports ALTER COLUMN disclaimer SET NOT NULL");
            statement.execute("""
                    ALTER TABLE relationship_reports
                    ADD CONSTRAINT ck_report_status_code CHECK (
                        status_code IN ('HEALTHY', 'GOOD', 'NEEDS_ATTENTION', 'CHANGE_DETECTED')
                    )
                    """);
            statement.execute("""
                    ALTER TABLE report_evidences
                    ADD CONSTRAINT ck_evidence_component CHECK (
                        component IN ('SATISFACTION', 'COMMITMENT', 'INTIMACY', 'TRUST', 'PASSION', 'LOVE')
                    )
                    """);
            statement.execute("""
                    CREATE INDEX idx_report_relationship_week
                    ON relationship_reports(relationship_id, week_start, analyzed_at)
                    """);
        }
    }

    private Status status(int score) {
        if (score >= 80) return new Status("HEALTHY", "건강한 관계");
        if (score >= 60) return new Status("GOOD", "양호");
        if (score >= 40) return new Status("NEEDS_ATTENTION", "주의 필요");
        return new Status("CHANGE_DETECTED", "변화 감지");
    }

    private record Status(String code, String label) {}
}
