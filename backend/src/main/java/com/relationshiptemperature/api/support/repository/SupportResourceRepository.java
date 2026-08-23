package com.relationshiptemperature.api.support.repository;

import com.relationshiptemperature.api.support.domain.SupportResource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportResourceRepository extends JpaRepository<SupportResource, UUID> {

    List<SupportResource> findAllByRegionAndCategoryOrderByNameAsc(String region, String category);
}
