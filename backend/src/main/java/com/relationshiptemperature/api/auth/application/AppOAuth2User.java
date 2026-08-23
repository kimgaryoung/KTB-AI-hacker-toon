package com.relationshiptemperature.api.auth.application;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public record AppOAuth2User(
        UUID userId,
        String name,
        Map<String, Object> attributes,
        Collection<? extends GrantedAuthority> authorities
) implements OAuth2User, Serializable {

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return name;
    }
}
