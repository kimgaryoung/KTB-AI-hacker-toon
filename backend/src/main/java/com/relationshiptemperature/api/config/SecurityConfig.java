package com.relationshiptemperature.api.config;

import com.relationshiptemperature.api.auth.application.KakaoOAuth2UserService;
import com.relationshiptemperature.api.auth.web.OAuth2LoginFailureHandler;
import com.relationshiptemperature.api.auth.web.OAuth2LoginSuccessHandler;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            KakaoOAuth2UserService userService,
            OAuth2LoginSuccessHandler successHandler,
            OAuth2LoginFailureHandler failureHandler,
            ObjectMapper objectMapper
    ) throws Exception {
        HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
        csrfRepository.setHeaderName("X-CSRF-Token");

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/kakao/**",
                                "/oauth2/authorization/**",
                                "/actuator/health",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .ignoringRequestMatchers("/api/v1/auth/kakao/callback"))
                .oauth2Login(oauth -> oauth
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/v1/auth/kakao/callback"))
                        .userInfoEndpoint(userInfo -> userInfo.userService(userService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, objectMapper, ErrorCode.AUTH_REQUIRED, requestId(request)))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, objectMapper, accessDeniedCode(exception), requestId(request))))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .invalidateHttpSession(true)
                        .deleteCookies("rt_session"));

        return http.build();
    }

    private ErrorCode accessDeniedCode(AccessDeniedException exception) {
        return exception instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN;
    }

    private void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            ErrorCode code,
            String requestId
    ) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, code.defaultMessage(), requestId));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "unknown" : value.toString();
    }
}
