package com.stgo.taostyle.config.spring;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.service.PersonService;
import com.stgo.taostyle.service.UserAccountService;
import com.stgo.taostyle.web.CC;
import com.stgo.taostyle.web.TaoDebug;
import com.stgo.taostyle.web.TaoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class KeepCurrentUserInSessionLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

    @Autowired
    private PersonService personService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private TaoUtil taoUtil;
    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        // to make the page stay on the client's web site, instead of displaying for_demo's web site.
        if (authentication == null) {
            TaoDebug.error(TaoDebug.getSB(request.getSession()), "non-null authentication expected when {}",
                    "onLogoutSuccess");
        } else {
            Person person = personService.findPersonByName(authentication.getName());
            if (person == null) {
                UserAccount userAccount = userAccountService.findUserAccountByName(authentication.getName());
                person = userAccount.getPerson();
            }
            request.getSession().setAttribute(CC.CLIENT, person);
            taoUtil.reInitSession(request.getSession(), person);
        }
        super.handle(request, response, authentication);
    }
}
