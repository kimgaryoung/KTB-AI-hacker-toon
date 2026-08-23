package com.relationshiptemperature.api.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(
        String frontendBaseUrl,
        Storage storage,
        Ai ai,
        Upload upload,
        Retention retention
) {
    public record Storage(Path root) {}

    public record Ai(String mode, String baseUrl, String serviceToken, Duration timeout) {}

    public record Upload(long maxBytes, Set<String> allowedExtensions) {
        public Upload {
            allowedExtensions = allowedExtensions.stream()
                    .map(extension -> extension.toLowerCase(java.util.Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    public record Retention(Duration rawConversation) {}
}
