package com.example.knowledgeassistant.controller;

import com.example.knowledgeassistant.dto.AuthMeResponse;
import com.example.knowledgeassistant.dto.LoginRequest;
import com.example.knowledgeassistant.dto.LoginResponse;
import com.example.knowledgeassistant.dto.LogoutResponse;
import com.example.knowledgeassistant.dto.SwitchTenantRequest;
import com.example.knowledgeassistant.security.CurrentUser;
import com.example.knowledgeassistant.service.AuthService;
import com.example.knowledgeassistant.service.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService authService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/switch-tenant")
    public LoginResponse switchTenant(@Valid @RequestBody SwitchTenantRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        return authService.switchTenant(currentUser, request);
    }

    @GetMapping("/me")
    public AuthMeResponse me() {
        return authService.me(currentUserProvider.getCurrentUser());
    }

    @PostMapping("/logout")
    public LogoutResponse logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "未提供有效的访问令牌");
        }
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        authService.logout(currentUser, authorizationHeader.substring(7).trim());
        return new LogoutResponse("已退出登录");
    }
}
