package com.stgo.taostyle.backend.security;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.stgo.taostyle.service.UserAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.web.CC;
import com.stgo.taostyle.web.TaoUtil;
import org.springframework.stereotype.Component;

@Component
public class BigAuthenticationProcessingFilter extends AbstractAuthenticationProcessingFilter {

    private static final String DEFAULT_FILTER_PROCESSES_URL = "/resources/j_spring_security_check";

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private TaoEncrypt taoEncrypt;
    @Autowired
    private UserAccountService userAccountService;
    public BigAuthenticationProcessingFilter() {
        super(DEFAULT_FILTER_PROCESSES_URL);
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response) throws AuthenticationException, IOException, ServletException {

        if (!request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }

        String login_name = request.getParameter("j_username");
        String login_password = request.getParameter("j_password");
        login_name = login_name == null ? "" : login_name.trim();

        final boolean rememberMe = "on".equals(request.getParameter("j_rememberme"));
        if (!rememberMe) {
            final Cookie cookie = new Cookie("login_name", "");
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);

            final Cookie cookie2 = new Cookie("login_password", "");
            cookie2.setMaxAge(0);
            cookie2.setPath("/");
            response.addCookie(cookie2);
        }

        String enrichedName = taoEncrypt.enrichName(request, login_name);
        login_password = login_password == null ? "" : taoEncrypt.encryptPassword(login_password);
        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(enrichedName, login_password);
        final Authentication authentication = this.getAuthenticationManager().authenticate(authRequest);

        // update the new message amount infomation. @NOTE: have to do it before the login_name is transfered to UTF-8
        // code.

        // fix the client attr in session, it shouldn't be changed before logout.
        // it's done here instead of in onAuthenticationSuccess() mehtod, because here we have login_name.
        if (authentication.getAuthorities().toString().indexOf(CC.ROLE_ADMIN) < 0) {
            UserAccount useraccount = userAccountService.findUserAccountByName(enrichedName);
            request.getSession().setAttribute(CC.currentUser, useraccount);
        } else {
            // if it's not already switched to this client.
        	if(!authentication.getName().equals(enrichedName) && enrichedName.startsWith(authentication.getName())) {
        		taoUtil.switchClient(request, authentication.getName());
        	}
        }

        if (rememberMe) {
            try {
                login_name = URLEncoder.encode(login_name, "UTF-8");
                login_name = login_name.replaceAll("\\+", "%20"); // this is because the URLEncoder.encode transfer all
                                                                  // space to "+", we want it to be transfered to %20
                                                                  // so that it can be displayed as space again when it
                                                                  // is displayed on login window.
                final Cookie cookie = new Cookie("login_name", login_name);
                cookie.setMaxAge(31536000); // One year
                cookie.setPath("/");
                response.addCookie(cookie);

                login_password = URLEncoder.encode(login_password, "UTF-8");
                final Cookie cookie2 = new Cookie("login_password", login_password);
                cookie2.setMaxAge(31536000); // One year
                cookie2.setPath("/");
                response.addCookie(cookie2);
            } catch (UnsupportedEncodingException e) {
            }
        }

        return authentication;
    }
}
