package com.relationshiptemperature.api.support.web;

import com.relationshiptemperature.api.common.api.ApiResponse;
import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.support.domain.SupportResource;
import com.relationshiptemperature.api.support.repository.SupportResourceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support-resources")
public class SupportResourceController {

    private static final Set<String> SUPPORTED_CATEGORIES = Set.of(
            "MENTAL_HEALTH_COUNSELING", "RELATIONSHIP_COUNSELING", "CRISIS_SUPPORT"
    );

    private final SupportResourceRepository repository;

    public SupportResourceController(SupportResourceRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    ApiResponse<List<SupportResourceResponse>> list(
            @RequestParam(defaultValue = "KR") String region,
            @RequestParam(defaultValue = "MENTAL_HEALTH_COUNSELING") String category
    ) {
        String normalizedRegion = region.trim().toUpperCase(Locale.ROOT);
        String normalizedCategory = category.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CATEGORIES.contains(normalizedCategory) || normalizedRegion.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "지원 리소스 조회 조건이 올바르지 않습니다.");
        }
        return ApiResponse.of(repository.findAllByRegionAndCategoryOrderByNameAsc(normalizedRegion, normalizedCategory).stream()
                .map(SupportResourceResponse::from)
                .toList());
    }

    record SupportResourceResponse(
            UUID id,
            String name,
            String description,
            String category,
            String region,
            String url,
            String phone,
            String hours,
            Instant verifiedAt,
            String source
    ) {
        static SupportResourceResponse from(SupportResource resource) {
            return new SupportResourceResponse(
                    resource.getId(), resource.getName(), resource.getDescription(), resource.getCategory(),
                    resource.getRegion(), resource.getUrl(), resource.getPhone(), resource.getHours(),
                    resource.getVerifiedAt(), resource.getSource()
            );
        }
    }
}
