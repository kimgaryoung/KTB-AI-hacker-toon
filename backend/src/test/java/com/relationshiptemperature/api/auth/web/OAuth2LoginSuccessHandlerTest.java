package com.relationshiptemperature.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.relationshiptemperature.api.config.AppProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OAuth2LoginSuccessHandlerTest {

    @Test
    void redirectsToStoredPostLoginRedirectAndRemovesIt() throws Exception {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(new OAuthRedirectUriValidator(properties()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("postLoginRedirectUri", "http://localhost:5173/relationships");

        handler.onAuthenticationSuccess(request, response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/relationships");
        assertThat(request.getSession().getAttribute("postLoginRedirectUri")).isNull();
    }

    @Test
    void redirectsToDashboardWhenNoStoredRedirectExists() throws Exception {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(new OAuthRedirectUriValidator(properties()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/dashboard");
    }

    private AppProperties properties() {
        return new AppProperties(
                "http://localhost:5173",
                new AppProperties.Storage(Path.of("./build/test-uploads")),
                new AppProperties.Ai("stub", "http://localhost:8000", "test-token", Duration.ofSeconds(5)),
                new AppProperties.Upload(50 * 1024 * 1024, Set.of("txt")),
                new AppProperties.Retention(Duration.ofHours(24))
        );
    }
}
