package com.relationshiptemperature.api.support.domain;

import com.relationshiptemperature.api.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "support_resources")
public class SupportResource extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 10)
    private String region;

    @Column(length = 1000)
    private String url;

    @Column(length = 50)
    private String phone;

    @Column(length = 200)
    private String hours;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(nullable = false, length = 500)
    private String source;

    protected SupportResource() {
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getRegion() { return region; }
    public String getUrl() { return url; }
    public String getPhone() { return phone; }
    public String getHours() { return hours; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getSource() { return source; }
}
