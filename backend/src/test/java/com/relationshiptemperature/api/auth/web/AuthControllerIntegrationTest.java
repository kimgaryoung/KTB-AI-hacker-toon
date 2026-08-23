package com.relationshiptemperature.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.relationshiptemperature.api.auth.application.AppOAuth2User;
import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.auth.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Test
    void authorizeStoresAllowedRedirectUriAndStartsKakaoOAuth() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("redirectUri", "http://localhost:5173/relationships?tab=active"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/kakao"));
    }

    @Test
    void authorizeRejectsExternalRedirectUri() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .header("X-Request-Id", "req_auth_external")
                        .param("redirectUri", "https://evil.example/login"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.requestId").value("req_auth_external"));
    }

    @Test
    void kakaoOAuthAuthorizationRequestUsesStateAndPkce() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.startsWith("https://kauth.kakao.com/oauth/authorize")))
                .andExpect(header().string("Location", Matchers.containsString("state=")))
                .andExpect(header().string("Location", Matchers.containsString("code_challenge=")))
                .andExpect(header().string("Location", Matchers.containsString("code_challenge_method=S256")));
    }

    @Test
    void meReturnsCurrentUserAndCsrfToken() throws Exception {
        User user = saveUser("kakao-me");

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(oauthAuthentication(user))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.displayName").value("kakao-me"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.csrfToken").isNotEmpty());
    }

    @Test
    void meRequiresAuthenticationWithJsonError() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Request-Id", "req_auth_required"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.error.requestId").value("req_auth_required"));
    }

    @Test
    void stateChangingRequestsRequireValidCsrfWithJsonError() throws Exception {
        User user = saveUser("kakao-csrf");

        mockMvc.perform(delete("/api/v1/relationships/0198c8a7-0000-7000-8000-000000000001")
                        .header("X-Request-Id", "req_csrf")
                        .with(authentication(oauthAuthentication(user))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"))
                .andExpect(jsonPath("$.error.requestId").value("req_csrf"));
    }

    @Test
    void logoutInvalidatesSessionAndExpiresCookie() throws Exception {
        User user = saveUser("kakao-logout");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("rt_session", 0));
    }

    private User saveUser(String subject) {
        return userRepository.save(User.kakao(subject, subject, null));
    }

    private OAuth2AuthenticationToken oauthAuthentication(User user) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new AppOAuth2User(user.getId(), user.getKakaoSubject(), Map.of(), authorities);
        return new OAuth2AuthenticationToken(principal, authorities, "kakao");
    }
}
