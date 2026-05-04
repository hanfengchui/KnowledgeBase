package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.dto.AuthMeResponse;
import com.example.knowledgeassistant.dto.LoginRequest;
import com.example.knowledgeassistant.dto.LoginResponse;
import com.example.knowledgeassistant.dto.SwitchTenantRequest;
import com.example.knowledgeassistant.security.CurrentUser;
import com.example.knowledgeassistant.security.JwtClaims;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse switchTenant(CurrentUser currentUser, SwitchTenantRequest request);

    AuthMeResponse me(CurrentUser currentUser);

    void logout(CurrentUser currentUser, String rawToken);

    CurrentUser loadCurrentUser(JwtClaims claims);
}
