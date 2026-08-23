package com.relationshiptemperature.api.auth.domain;

import com.relationshiptemperature.api.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(name = "uk_user_kakao_subject", columnNames = "kakao_subject"))
public class User extends BaseEntity {

    @Column(name = "kakao_subject", nullable = false, length = 100)
    private String kakaoSubject;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    @Column(nullable = false, length = 50)
    private String timezone;

    protected User() {
    }

    private User(String kakaoSubject, String displayName, String profileImageUrl) {
        this.kakaoSubject = kakaoSubject;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.timezone = "Asia/Seoul";
    }

    public static User kakao(String kakaoSubject, String displayName, String profileImageUrl) {
        return new User(kakaoSubject, displayName, profileImageUrl);
    }

    public void updateProfile(String displayName, String profileImageUrl) {
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
    }

    public String getKakaoSubject() {
        return kakaoSubject;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getTimezone() {
        return timezone;
    }
}
