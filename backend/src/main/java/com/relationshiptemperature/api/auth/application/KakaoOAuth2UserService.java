package com.relationshiptemperature.api.auth.application;

import com.relationshiptemperature.api.auth.domain.User;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class KakaoOAuth2UserService extends DefaultOAuth2UserService {

    private final KakaoUserProvisioningService userProvisioningService;

    public KakaoOAuth2UserService(KakaoUserProvisioningService userProvisioningService) {
        this.userProvisioningService = userProvisioningService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User delegate = super.loadUser(userRequest);
        String subject = stringValue(delegate.getAttribute("id"), null);
        Map<String, Object> account = map(delegate.getAttribute("kakao_account"));
        Map<String, Object> profile = map(account.get("profile"));
        String nickname = stringValue(profile.get("nickname"), "카카오 사용자");
        String profileImageUrl = stringValue(profile.get("profile_image_url"), null);

        User user = userProvisioningService.upsert(subject, nickname, profileImageUrl);

        return new AppOAuth2User(user.getId(), subject, delegate.getAttributes(), delegate.getAuthorities());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
