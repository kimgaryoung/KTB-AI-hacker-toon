package com.relationshiptemperature.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.config.AppProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OAuthRedirectUriValidatorTest {

    private final OAuthRedirectUriValidator validator = new OAuthRedirectUriValidator(properties());

    @Test
    void acceptsSameOriginRedirectUri() {
        assertThat(validator.validate("http://localhost:5173/relationships?tab=active"))
                .isEqualTo("http://localhost:5173/relationships?tab=active");
    }

    @Test
    void rejectsDifferentOriginRedirectUri() {
        assertThatThrownBy(() -> validator.validate("https://evil.example/login"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsMalformedRedirectUri() {
        assertThatThrownBy(() -> validator.validate("http://[::1"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void returnsNullForMissingRedirectUri() {
        assertThat(validator.validate(null)).isNull();
        assertThat(validator.validate("  ")).isNull();
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
