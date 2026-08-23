package com.relationshiptemperature.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.relationshiptemperature.api.config.AppProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthControllerTest {

    @Test
    void authorizeStoresPostLoginRedirectUriInSession() {
        AuthController controller = new AuthController(null, new OAuthRedirectUriValidator(properties()));
        MockHttpServletRequest request = new MockHttpServletRequest();

        controller.authorize("http://localhost:5173/relationships?tab=active", request);

        assertThat(request.getSession().getAttribute("postLoginRedirectUri"))
                .isEqualTo("http://localhost:5173/relationships?tab=active");
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
