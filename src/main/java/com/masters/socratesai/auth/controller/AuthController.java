package com.masters.socratesai.auth.controller;

import com.masters.socratesai.auth.dto.AuthResponse;
import com.masters.socratesai.auth.dto.LoginRequest;
import com.masters.socratesai.auth.dto.RegisterRequest;
import com.masters.socratesai.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) throws BadRequestException {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) throws BadRequestException {
        return authService.login(request);
    }
}