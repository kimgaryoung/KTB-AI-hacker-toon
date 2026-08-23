package com.relationshiptemperature.api.checkin.domain;

import com.relationshiptemperature.api.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "check_in_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_checkin_answer_question",
                columnNames = {"check_in_id", "question_code"}
        ),
        indexes = @Index(name = "idx_checkin_answer_checkin", columnList = "check_in_id"))
public class CheckInAnswer extends BaseEntity {

    @Column(name = "check_in_id", nullable = false)
    private UUID checkInId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_code", nullable = false, length = 50)
    private QuestionCode questionCode;

    @Column(nullable = false)
    private int score;

    protected CheckInAnswer() {
    }

    public CheckInAnswer(UUID checkInId, QuestionCode questionCode, int score) {
        this.checkInId = checkInId;
        this.questionCode = questionCode;
        updateScore(score);
    }

    public void updateScore(int score) {
        if (score < 1 || score > 7) {
            throw new IllegalArgumentException("Check-in score must be between 1 and 7");
        }
        this.score = score;
    }

    public UUID getCheckInId() { return checkInId; }
    public QuestionCode getQuestionCode() { return questionCode; }
    public int getScore() { return score; }
}
