package com.relationshiptemperature.api.auth.web;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.config.AppProperties;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class OAuthRedirectUriValidator {

    private static final String DASHBOARD_PATH = "/dashboard";

    private final URI frontendBaseUri;

    public OAuthRedirectUriValidator(AppProperties properties) {
        this.frontendBaseUri = URI.create(properties.frontendBaseUrl());
    }

    public String validate(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return null;
        }
        URI candidate;
        try {
            candidate = URI.create(redirectUri);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        if (!isSameOrigin(candidate)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return candidate.toString();
    }

    public String defaultSuccessRedirect() {
        return frontendBaseUri.resolve(DASHBOARD_PATH).toString();
    }

    private boolean isSameOrigin(URI candidate) {
        return candidate.isAbsolute()
                && frontendBaseUri.getScheme().equals(candidate.getScheme())
                && frontendBaseUri.getHost().equals(candidate.getHost())
                && effectivePort(frontendBaseUri) == effectivePort(candidate);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equals(uri.getScheme()) ? 443 : 80;
    }
}
