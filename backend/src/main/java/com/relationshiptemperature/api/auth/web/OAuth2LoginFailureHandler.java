package com.relationshiptemperature.api.auth.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final OAuthRedirectUriValidator redirectUriValidator;

    public OAuth2LoginFailureHandler(OAuthRedirectUriValidator redirectUriValidator) {
        this.redirectUriValidator = redirectUriValidator;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        response.sendRedirect(UriComponentsBuilder
                .fromUriString(redirectUriValidator.defaultSuccessRedirect())
                .replacePath("/login")
                .replaceQueryParam("error", "oauth_failed")
                .build()
                .toUriString());
    }
}
