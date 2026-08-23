package com.relationshiptemperature.api.auth.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthRedirectUriValidator redirectUriValidator;

    public OAuth2LoginSuccessHandler(OAuthRedirectUriValidator redirectUriValidator) {
        this.redirectUriValidator = redirectUriValidator;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        response.sendRedirect(resolveRedirectUri(request));
    }

    private String resolveRedirectUri(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return redirectUriValidator.defaultSuccessRedirect();
        }
        Object value = session.getAttribute(AuthController.POST_LOGIN_REDIRECT_URI);
        session.removeAttribute(AuthController.POST_LOGIN_REDIRECT_URI);
        return value instanceof String redirectUri ? redirectUri : redirectUriValidator.defaultSuccessRedirect();
    }
}
