package com.relationshiptemperature.api.checkin.repository;

import com.relationshiptemperature.api.checkin.domain.CheckInAnswer;
import com.relationshiptemperature.api.checkin.domain.QuestionCode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInAnswerRepository extends JpaRepository<CheckInAnswer, UUID> {

    Optional<CheckInAnswer> findByCheckInIdAndQuestionCode(UUID checkInId, QuestionCode questionCode);

    List<CheckInAnswer> findAllByCheckInIdIn(Collection<UUID> checkInIds);
}
