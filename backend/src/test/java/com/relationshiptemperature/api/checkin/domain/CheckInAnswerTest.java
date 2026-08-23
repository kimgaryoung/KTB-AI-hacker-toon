package com.relationshiptemperature.api.checkin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckInAnswerTest {

    @Test
    void acceptsScoresFromOneToSeven() {
        CheckInAnswer answer = new CheckInAnswer(UUID.randomUUID(), QuestionCode.RELATIONSHIP_FEELING, 1);

        answer.updateScore(7);

        assertThat(answer.getScore()).isEqualTo(7);
    }

    @Test
    void rejectsScoreOutsideOneToSeven() {
        assertThatThrownBy(() -> new CheckInAnswer(
                UUID.randomUUID(), QuestionCode.CONVERSATION_COMFORT, 0
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
