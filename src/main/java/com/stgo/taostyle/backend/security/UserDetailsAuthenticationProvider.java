package com.stgo.taostyle.backend.security;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import com.stgo.taostyle.service.PersonService;
import com.stgo.taostyle.service.UserAccountService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.web.CC;
import org.springframework.stereotype.Component;

@Component
public class UserDetailsAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

    public static final Marker WS_MARKER = MarkerFactory.getMarker("WS");
    private static final Logger log = LoggerFactory.getLogger(UserDetailsAuthenticationProvider.class);

    private static final DateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");

    private ApplicationContext applicationContext;
    @Autowired
    private PersonService personService;
    @Autowired
    private UserAccountService userAccountService;
    @Override
    protected void additionalAuthenticationChecks(
            UserDetails userDetails,
            UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
    }

    @Override
    public UserDetails retrieveUser(
            String userName,
            UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {

        log.debug("Authenticating {} against server", userName);

        @SuppressWarnings("unchecked")
        String password = authentication.getCredentials().toString();

        if (StringUtils.isEmpty(password)) {
            throw new BadCredentialsException("Please enter password");
        }

        // check if the username and password match
        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        if (!userName.contains(CC.SAVED_NAME_STR)) {
            Person person = personService.findPersonByName(userName);
            if (person != null && password.equals(person.getPassword())) {
                authorities.add(new SimpleGrantedAuthority(CC.ROLE_ADMIN));
            } else {
                throw new BadCredentialsException("The password is not correct. Please try again.");
            }
        } else {
            // not possible and not allowed for 2 user with same name and password.
            UserAccount tUserAccount = userAccountService.findUserAccountByNameAndPassword(userName, password);
            if (tUserAccount == null) {
            	Person person = null;
            	if(userName.endsWith("*1")) { 	//give another chance, in case maybe user lost connection and fell back to for_demo space.
            		String personName = userName.substring(0, userName.length() - 2);
            		return retrieveUser(personName, authentication);
            	}else {
            		throw new BadCredentialsException(
                                "The user dose not exist or the password is not correct. Please check the input and try again.");
            	}
            }
            authorities.add(new SimpleGrantedAuthority(tUserAccount.getSecuritylevel()));
        }
        User tUser = new User(userName, password, true, true, true, true, authorities);

        log.debug("Login user: {}", userName);

        return tUser;
    }
}
