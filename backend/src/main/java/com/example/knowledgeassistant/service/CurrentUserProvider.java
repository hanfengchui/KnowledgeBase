package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.security.CurrentUser;

import java.util.Optional;

public interface CurrentUserProvider {

    Optional<CurrentUser> findCurrentUser();

    CurrentUser getCurrentUser();
}
