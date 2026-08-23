package com.relationshiptemperature.api.relationship.web;

import com.relationshiptemperature.api.auth.application.CurrentUserService;
import com.relationshiptemperature.api.common.api.ApiResponse;
import com.relationshiptemperature.api.common.api.PagedResponse;
import com.relationshiptemperature.api.relationship.application.RelationshipService;
import com.relationshiptemperature.api.relationship.application.RelationshipService.Sort;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.domain.RelationshipStatus;
import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/relationships")
public class RelationshipController {

    private final CurrentUserService currentUserService;
    private final RelationshipService relationshipService;

    public RelationshipController(CurrentUserService currentUserService, RelationshipService relationshipService) {
        this.currentUserService = currentUserService;
        this.relationshipService = relationshipService;
    }

    @GetMapping
    PagedResponse<RelationshipResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RelationshipStatus status,
            @RequestParam(defaultValue = "ABS_CHANGE_DESC") Sort sort
    ) {
        return PagedResponse.singlePage(relationshipService.list(
                        currentUserService.requireUserId(), search, status, sort
                ).stream()
                .map(RelationshipResponse::from)
                .toList());
    }

    @PostMapping
    ResponseEntity<ApiResponse<RelationshipResponse>> create(@Valid @RequestBody CreateRelationshipRequest request) {
        Relationship relationship = relationshipService.create(
                currentUserService.requireUserId(),
                request.name(),
                request.relationshipType()
        );
        return ResponseEntity.created(URI.create("/api/v1/relationships/" + relationship.getId()))
                .body(ApiResponse.of(RelationshipResponse.from(relationship)));
    }

    @GetMapping("/{relationshipId}")
    ApiResponse<RelationshipResponse> get(@PathVariable UUID relationshipId) {
        return ApiResponse.of(RelationshipResponse.from(
                relationshipService.getOwned(currentUserService.requireUserId(), relationshipId)
        ));
    }

    @PatchMapping("/{relationshipId}")
    ApiResponse<RelationshipResponse> update(
            @PathVariable UUID relationshipId,
            @Valid @RequestBody UpdateRelationshipRequest request
    ) {
        return ApiResponse.of(RelationshipResponse.from(relationshipService.update(
                currentUserService.requireUserId(),
                relationshipId,
                request.name(),
                request.relationshipType()
        )));
    }

    @DeleteMapping("/{relationshipId}")
    ResponseEntity<Void> delete(@PathVariable UUID relationshipId) {
        relationshipService.delete(currentUserService.requireUserId(), relationshipId);
        return ResponseEntity.accepted().build();
    }

    public record CreateRelationshipRequest(
            @NotBlank @Size(max = 50) String name,
            @NotNull RelationshipType relationshipType
    ) {}

    public record UpdateRelationshipRequest(
            @Size(min = 1, max = 50) String name,
            RelationshipType relationshipType
    ) {}

    public record RelationshipResponse(
            UUID id,
            String name,
            String initial,
            RelationshipType relationshipType,
            RelationshipStatus status,
            Integer score,
            Integer change,
            Instant lastAnalyzedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        static RelationshipResponse from(Relationship relationship) {
            return new RelationshipResponse(
                    relationship.getId(),
                    relationship.getName(),
                    relationship.getInitial(),
                    relationship.getRelationshipType(),
                    relationship.getStatus(),
                    relationship.getLatestScore(),
                    relationship.getLatestChange(),
                    relationship.getLastAnalyzedAt(),
                    relationship.getCreatedAt(),
                    relationship.getUpdatedAt()
            );
        }
    }
}
