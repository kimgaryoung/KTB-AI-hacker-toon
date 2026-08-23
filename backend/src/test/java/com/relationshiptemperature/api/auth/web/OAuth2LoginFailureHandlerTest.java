package com.relationshiptemperature.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.relationshiptemperature.api.config.AppProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OAuth2LoginFailureHandlerTest {

    @Test
    void redirectsToFrontendLoginWithOauthFailureCode() throws Exception {
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(
                new OAuthRedirectUriValidator(properties())
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, null);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/login?error=oauth_failed");
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
