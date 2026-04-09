package com.masters.socratesai.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.auth.dto.LoginRequest;
import com.masters.socratesai.auth.dto.RegisterRequest;
import com.masters.socratesai.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldRejectInvalidLoginPayload() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectRegisterPayloadWithShortPassword() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("teacher@example.com");
        request.setPassword("123");
        request.setFullName("Teacher User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
