package com.example.knowledgeassistant.security;

import com.example.knowledgeassistant.service.CurrentUserProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
public class DatabaseCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<CurrentUser> findCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CurrentUser currentUser) {
            return Optional.of(currentUser);
        }
        return Optional.empty();
    }

    @Override
    public CurrentUser getCurrentUser() {
        return findCurrentUser().orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "未登录或登录已失效"));
    }
}
