package com.relationshiptemperature.api.common.error;

import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private static final String REQUEST_ID = "req_test_123";

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestApiController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new com.relationshiptemperature.api.common.web.RequestIdFilter())
                .build();
    }

    @Test
    void returnsValidationErrorForInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/test/widgets")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INVALID_REQUEST.defaultMessage()))
                .andExpect(jsonPath("$.error.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.error.fields[0].field").value("name"))
                .andExpect(jsonPath("$.error.fields[0].reason", not(startsWith("NotBlank"))));
    }

    @Test
    void returnsValidationErrorForInvalidQueryParameter() throws Exception {
        mockMvc.perform(get("/api/v1/test/widgets")
                        .header("X-Request-Id", REQUEST_ID)
                        .param("weeks", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.error.fields[0].field", containsString("weeks")));
    }

    @Test
    void returnsInvalidRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/test/widgets")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.requestId").value(REQUEST_ID));
    }

    @Test
    void returnsInvalidRequestForTypeMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/test/widgets")
                        .header("X-Request-Id", REQUEST_ID)
                        .param("weeks", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fields[0].field").value("weeks"));
    }

    @Test
    void returnsMethodNotAllowedInErrorEnvelope() throws Exception {
        mockMvc.perform(put("/api/v1/test/widgets")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.requestId").value(REQUEST_ID));
    }

    @Test
    void returnsUnsupportedMediaTypeInErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/test/widgets")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=test"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.error.requestId").value(REQUEST_ID));
    }

    @Test
    void returnsInternalErrorForUnexpectedException() throws Exception {
        mockMvc.perform(get("/api/v1/test/fail")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.requestId").value(REQUEST_ID));
    }

    @Validated
    @RestController
    @RequestMapping("/api/v1/test")
    static class TestApiController {
        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        @GetMapping("/widgets")
        TestResponse list(@RequestParam(defaultValue = "8") @Min(4) int weeks) {
            if (weeks < 4) {
                throw new ConstraintViolationException(validator.validate(new QueryParameters(weeks)));
            }
            return new TestResponse("ok");
        }

        @PostMapping(path = "/widgets", consumes = MediaType.APPLICATION_JSON_VALUE)
        TestResponse create(@Valid @RequestBody TestRequest request) {
            return new TestResponse(request.name());
        }

        @GetMapping("/fail")
        TestResponse fail() {
            throw new IllegalStateException("boom");
        }
    }

    record TestRequest(@NotBlank @Size(max = 10) String name) {}

    record QueryParameters(@Min(4) int weeks) {}

    record TestResponse(String value) {}
}
