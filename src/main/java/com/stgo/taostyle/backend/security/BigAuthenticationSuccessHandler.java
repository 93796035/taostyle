package com.stgo.taostyle.backend.security;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.web.CC;

@Component
public class BigAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UserContextService userContextService;

    private RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        request.getSession().setAttribute(CC.user_role, authentication.getAuthorities().toString());
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        // looks like when clicking log out, the savedRequest will be updated with current page (if the current page
        // need authentication) or with null.
        if (savedRequest == null) {
            // when use Chinese name to login, the userContextService.getCurrentUserName() will be transfered into weird
            // string
            // by getRedirectStrategy().sendRedirect. so we dare not to use the personal space as default login success
            // page.
            Object menuIdx = request.getSession().getAttribute(CC.menuIdx);
            String targetUrl = menuIdx == null ? "/" : "/menu_" + menuIdx.toString();
            Object curUser = request.getSession().getAttribute(CC.currentUser);
            if (curUser != null && curUser instanceof UserAccount) {
                String securityLevel = ((UserAccount) curUser).getSecuritylevel();
                if (securityLevel != null && ("ROLE_EMPLOYEE".equals(securityLevel) || "ROLE_MANAGER".equals(securityLevel))) {
                    targetUrl = "/dashboard";
                }
            }
            // to change the code of targetUrl back to "ISO-8859-1", other wise the chinese user name will be lost by
            // method sendRedirect
            byte tByteAry[];
            tByteAry = targetUrl.getBytes("UTF-8");
            targetUrl = new String(tByteAry, "ISO-8859-1");
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
            return;
        }
        String targetUrlParameter = getTargetUrlParameter();
        if (isAlwaysUseDefaultTargetUrl()
                || (targetUrlParameter != null && StringUtils.hasText(request.getParameter(targetUrlParameter)))) {
            requestCache.removeRequest(request, response);
            super.onAuthenticationSuccess(request, response, authentication);

            return;
        }

        clearAuthenticationAttributes(request);

        // Use the DefaultSavedRequest URL
        String targetUrl = savedRequest.getRedirectUrl();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
