package com.facecook.auth.controller;

import com.facecook.auth.dto.AuthVerificationResponse;
import com.facecook.auth.dto.RequestCodeResponse;
import com.facecook.auth.service.AuthService;
import com.facecook.common.exception.GlobalExceptionHandler;
import com.facecook.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, WebConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void requestsVerificationCode() throws Exception {
        when(authService.requestCode(any())).thenReturn(new RequestCodeResponse(300, 30));

        mockMvc.perform(post("/api/auth/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "purpose": "signup"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.resendAfterSeconds").value(30));
    }

    @Test
    void returnsValidationErrorForInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "purpose": "signup"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void returnsValidationErrorForUnknownPurpose() throws Exception {
        mockMvc.perform(post("/api/auth/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "purpose": "reset-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void verifiesSignupResponse() throws Exception {
        when(authService.verifySignup(any())).thenReturn(
                new AuthVerificationResponse(1L, "user@example.com", "participant")
        );

        mockMvc.perform(post("/api/auth/verify-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "code": "123456",
                                  "agreedTerms": ["service", "privacy"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.role").value("participant"));
    }

    @Test
    void allowsLocalFrontendCorsPreflight() throws Exception {
        for (String origin : new String[]{"http://localhost:3000", "http://localhost:3001"}) {
            mockMvc.perform(options("/api/auth/request-code")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }
    }
}
