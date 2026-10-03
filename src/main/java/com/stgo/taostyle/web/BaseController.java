package com.stgo.taostyle.web;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.stgo.taostyle.service.ProductService;
import com.stgo.taostyle.service.TextContentService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.TextContent;

@Component
public class BaseController {

    protected boolean dirtFlagCommonText = false; // a dirty flag to force the page to refresh.

    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private TextContentService textContentService;
    @Autowired
    private ProductService productService;

    protected void makesureCommonTextInitialized(
            Model model,
            HttpServletRequest request,
            String langPrf,
            Person person) {
        // get the session out
        HttpSession session = request.getSession();

        // if the language is switched, then reinit the texts on common area (header and footer)
        Object langInRequest = request.getParameter(CC.LANG); // language in request
        Object langInSession = session.getAttribute(CC.LANG); // language existing
        Object cachedRole = session.getAttribute("menuContentRole");
        String currentRole = getCurrentRole(session);

        TaoDebug.info(TaoDebug.getSB(session),
                "start to makesureCommonTextInitialized, langParameter: {}; tLangExisting: {}, dirtFlagCommonText: {}, client:{}",
                langInRequest, langInSession, dirtFlagCommonText, person.getName());

        if (dirtFlagCommonText) {
            dirtFlagCommonText = false;
            reinitCommonText(session, langPrf, person);
        } else if (cachedRole == null || !cachedRole.equals(currentRole)) {
            reinitCommonText(session, langPrf, person);
        } else if (langInRequest != null) { // there's lang in request (user might clicked the language button)
            if (!langInRequest.equals(langInSession)) { // and the new language is different from old one.
                session.setAttribute(CC.LANG, langInRequest); // then set the new one into session.
                reinitCommonText(session, langPrf, person);
            }
        } else { // there's no language in request. (user is not clicking language button)
            if (langInSession == null) { // and no language set yet.
                session.setAttribute(CC.LANG, langPrf.endsWith("_") ? langPrf.substring(0, langPrf.length() - 1) : langPrf);
                reinitCommonText(session, langPrf, person);
            } else {// idle too long time, the content in menuContent might lost. not sure if it's possible?
                List<List<String>> menuContent = (List<List<String>>) session.getAttribute("menuContent");
                if (CollectionUtils.isEmpty(menuContent) || menuContent.get(0).size() == 0) {
                    reinitCommonText(session, langPrf, person);
                }
            }
        }
    }

    private void reinitCommonText(
            HttpSession session,
            String langPrf,
            Person person) {

        TaoDebug.info(TaoDebug.getSB(session), "start to reinitCommonText, lang is {}, client is: {}", langPrf,
                person.getName());

        List<List<String>> menuContent = taoUtil.prepareMenuContent("menu_", langPrf, person);
        if (shouldHideServiceMenus(session, person)) {
            hideServiceMenus(menuContent, session);
        }
        session.setAttribute("menuContent", menuContent);
        session.setAttribute("menuContentRole", getCurrentRole(session));

        taoUtil.initText(session, "text_footContent", 3, langPrf, person);
        taoUtil.initText(session, "text_logContent", 3, langPrf, person);

        TextContent textContent = textContentService.findContentsByKeyAndPerson(langPrf + "footer_companyInfo", person);
        if (textContent != null) {
            session.setAttribute("footer_companyInfo", textContent.getContent());
        }

        textContent = textContentService.findContentsByKeyAndPerson(langPrf + "footer_copyright", person);
        if (textContent != null) {
            session.setAttribute("footer_copyright", textContent.getContent());
        }
    }

    private void hideServiceMenus(
            List<List<String>> menuContent,
            HttpSession session) {
        if (CollectionUtils.isEmpty(menuContent)) {
            return;
        }
        for (int i = 0; i < menuContent.size(); i++) {
            String contentType = resolveTopLevelContentType(session, i + 1);
            if (contentType != null && CC.SERVICE.equalsIgnoreCase(contentType)) {
                menuContent.set(i, java.util.Collections.emptyList());
            }
        }
    }

    private String resolveTopLevelContentType(
            HttpSession session,
            int idx) {
        for (int currentIdx = idx; currentIdx > 0; currentIdx--) {
            Object contentType = session.getAttribute("CONTENTTYPE_" + currentIdx);
            if (contentType != null) {
                return contentType.toString();
            }
        }
        return null;
    }

    private boolean isManagerOrAdmin(
            HttpSession session) {
        String role = getCurrentRole(session);
        if (role == null) {
            return false;
        }
        return role.contains(CC.ROLE_ADMIN) || role.contains(CC.ROLE_MANAGER);
    }

    private boolean shouldHideServiceMenus(
            HttpSession session,
            Person person) {
        return !isManagerOrAdmin(session) && isDepanneurSite(session, person);
    }

    private boolean isDepanneurSite(
            HttpSession session,
            Person person) {
        Object depanneurFlag = session.getAttribute(CC.is_depanneur_site);
        if (depanneurFlag != null) {
            String flagValue = depanneurFlag.toString();
            if (CC.TRUE.equalsIgnoreCase(flagValue) || "yes".equalsIgnoreCase(flagValue) || "1".equals(flagValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(flagValue) || "no".equalsIgnoreCase(flagValue) || "0".equals(flagValue)) {
                return false;
            }
        }
        return productService.existsProductForPerson(person);
    }

    private String getCurrentRole(
            HttpSession session) {
        Object userRole = session.getAttribute(CC.user_role);
        return userRole == null ? null : userRole.toString();
    }
}

