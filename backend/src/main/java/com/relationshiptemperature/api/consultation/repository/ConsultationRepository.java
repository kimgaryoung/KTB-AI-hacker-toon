package com.relationshiptemperature.api.consultation.repository;

import com.relationshiptemperature.api.consultation.domain.Consultation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConsultationRepository extends MongoRepository<Consultation, String> {

    Optional<Consultation> findByIdAndUserId(String id, String userId);

    List<Consultation> findAllByUserIdOrderByUpdatedAtDesc(String userId);
}
