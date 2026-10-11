package com.stgo.taostyle.config.spring;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import com.stgo.taostyle.backend.security.BigAuthenticationFailureHandler;
import com.stgo.taostyle.backend.security.BigAuthenticationProcessingFilter;
import com.stgo.taostyle.backend.security.BigAuthenticationSuccessHandler;
import com.stgo.taostyle.backend.security.UserDetailsAuthenticationProvider;
import com.stgo.taostyle.web.CustomizeController;
import com.stgo.taostyle.web.UserAccountController;

@Configuration
public class SecurityConfig {

    @Autowired
    AuthenticationManager authenticationManager;

    @Bean
    @Scope("singleton")
    AuthenticationProvider backendAuthenticationProvider() {
        return new UserDetailsAuthenticationProvider();
    }

    @Bean
    @Scope("singleton")
    LoginUrlAuthenticationEntryPoint loginUrlAuthenticationEntryPoint() {
        return new LoginUrlAuthenticationEntryPoint("/login");
    }

    @Bean
    @Scope("singleton")
    AbstractAuthenticationProcessingFilter bigAuthenticationProcessingFilter() {
        assert (authenticationManager != null) : "AuthenticationManager should not be null";
        BigAuthenticationProcessingFilter filter = new BigAuthenticationProcessingFilter();
        filter.setAuthenticationManager(authenticationManager);
        filter.setAuthenticationFailureHandler(authenticationFailureHandler());
        filter.setAuthenticationSuccessHandler(authenticationSuccessHandler());
        return filter;
    }

    @Bean
    @Scope("singleton")
    // We just need to set the default failure url here
            AuthenticationFailureHandler authenticationFailureHandler() {
        return new BigAuthenticationFailureHandler();
    }

    @Bean
    @Scope("singleton")
    // We just need to set the default success url here
            AuthenticationSuccessHandler authenticationSuccessHandler() {
        BigAuthenticationSuccessHandler handler = new BigAuthenticationSuccessHandler();
        return handler;
    }

    @Bean
    @Scope("singleton")
    SimpleUrlLogoutSuccessHandler logoutSuccessHandler() {
        SimpleUrlLogoutSuccessHandler handler = new KeepCurrentUserInSessionLogoutSuccessHandler();
        // handler.setUseReferer(true);
        return handler;
    }

    @Bean(autowire = Autowire.BY_NAME)
    @Scope("singleton")
    SpringApplicationContext springApplicationContext() {
        return new SpringApplicationContext();
    }

    @Bean(autowire = Autowire.BY_NAME)
    @Scope("singleton")
    UserAccountController userAccountController() {
        return new UserAccountController();
    }

    @Bean(autowire = Autowire.BY_NAME)
    @Scope("singleton")
    CustomizeController customizeController() {
        return new CustomizeController();
    }
}
