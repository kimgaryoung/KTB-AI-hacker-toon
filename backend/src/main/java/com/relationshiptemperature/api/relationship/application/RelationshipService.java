package com.relationshiptemperature.api.relationship.application;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.domain.RelationshipStatus;
import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.relationship.repository.RelationshipRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RelationshipService {

    private final RelationshipRepository relationshipRepository;

    public RelationshipService(RelationshipRepository relationshipRepository) {
        this.relationshipRepository = relationshipRepository;
    }

    @Transactional
    public Relationship create(UUID userId, String name, RelationshipType type) {
        return relationshipRepository.save(Relationship.draft(userId, normalizeName(name), type));
    }

    public List<Relationship> list(UUID userId, String search, RelationshipStatus status, Sort sort) {
        List<Relationship> relationships = search == null || search.isBlank()
                ? relationshipRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
                : relationshipRepository.findAllByUserIdAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(
                        userId,
                        search.trim()
                );
        return relationships.stream()
                .filter(relationship -> status == null || relationship.getStatus() == status)
                .sorted(sort.comparator())
                .toList();
    }

    public Relationship getOwned(UUID userId, UUID relationshipId) {
        return relationshipRepository.findByIdAndUserId(relationshipId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RELATIONSHIP_NOT_FOUND));
    }

    @Transactional
    public Relationship update(UUID userId, UUID relationshipId, String name, RelationshipType type) {
        if (name == null && type == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "수정할 이름 또는 관계 유형이 필요합니다.");
        }
        String normalizedName = name == null ? null : normalizeName(name);
        Relationship relationship = getOwned(userId, relationshipId);
        relationship.update(normalizedName, type);
        return relationship;
    }

    @Transactional
    public void delete(UUID userId, UUID relationshipId) {
        Relationship relationship = getOwned(userId, relationshipId);
        relationship.markDeleting();
        relationshipRepository.delete(relationship);
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "관계 대상 이름이 필요합니다.");
        }
        String normalized = name.trim();
        if (normalized.isEmpty() || normalized.length() > 50) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "관계 대상 이름은 공백 제거 후 1~50자여야 합니다.");
        }
        return normalized;
    }

    public enum Sort {
        ABS_CHANGE_DESC,
        SCORE_DESC,
        SCORE_ASC,
        UPDATED_DESC;

        private Comparator<Relationship> comparator() {
            Comparator<Relationship> updatedDesc = Comparator.comparing(
                    Relationship::getUpdatedAt,
                    Comparator.reverseOrder()
            );
            Comparator<Relationship> primary = switch (this) {
                case ABS_CHANGE_DESC -> Comparator.comparing(
                        relationship -> relationship.getLatestChange() == null
                                ? null
                                : Math.abs(relationship.getLatestChange()),
                        Comparator.nullsLast(Comparator.reverseOrder())
                );
                case SCORE_DESC -> Comparator.comparing(
                        Relationship::getLatestScore,
                        Comparator.nullsLast(Comparator.reverseOrder())
                );
                case SCORE_ASC -> Comparator.comparing(
                        Relationship::getLatestScore,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
                case UPDATED_DESC -> updatedDesc;
            };
            return this == UPDATED_DESC ? primary : primary.thenComparing(updatedDesc);
        }
    }
}
