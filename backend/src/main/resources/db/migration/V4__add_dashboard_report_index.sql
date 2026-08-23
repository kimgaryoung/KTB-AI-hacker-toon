CREATE INDEX idx_report_user_week
    ON relationship_reports(user_id, week_start, relationship_id, analyzed_at);
