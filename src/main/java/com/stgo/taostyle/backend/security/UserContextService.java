package com.stgo.taostyle.backend.security;

import org.springframework.security.core.userdetails.User;

public interface UserContextService {

    /**
     * @return the current user if logged in, or null
     */
    User getCurrentUser();

    String getCurrentUserName();

}
