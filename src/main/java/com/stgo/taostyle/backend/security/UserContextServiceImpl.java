package com.stgo.taostyle.backend.security;

import java.io.Serializable;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

@Service("userContextService")
public class UserContextServiceImpl implements UserContextService, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public User getCurrentUser() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return null;
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof User)) {
            return null;
        }
        return (User) principal;
    }

    @Override
    public String getCurrentUserName() {
        final User currentUser = getCurrentUser();
        if (currentUser == null) {
            return null;
        } else {
            return currentUser.getUsername();
        }
    }

}
