package com.stgo.taostyle.web;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.stgo.taostyle.service.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import com.stgo.taostyle.backend.security.TaoEncrypt;
import com.stgo.taostyle.domain.Customize;
import com.stgo.taostyle.domain.Feature;
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.MultiLanguageabl;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.Service;
import com.stgo.taostyle.domain.TextContent;
import com.stgo.taostyle.domain.UserAccount;

@Component
public class TaoUtil {

    @Autowired
    private PersonService personService;
    @Autowired
    private CustomizeService customizeService;
    @Autowired
    private TaoDebug taoDebug;
    @Autowired
    private MediaUploadService mediaUploadService;
    @Autowired
    private TextContentService textContentService;
    @Autowired
    private FeatureService featureService;
    @Autowired
    private ServiceService serviceService;
    public static final String PREPARE_NEXT_PAGE = "prepareNextPage";

    public static final String INFO_COLLECT_FORM_CUST_PAGE_PATH = "InfoCollectionFormCustPagePath";
    private static Person advPerson;

    public Person getAdam() {
        if (personService.getAdam() == null) {
            Person adam = new Person();
            adam.setName("sharethegoodones");
            adam.setPassword("PQlS9ShB//w=");
            adam.setRecordStatus(0);
            adam.setVersion(0);
            personService.persist(adam);
            initializeForBasePerson(adam);
        }
        return personService.getAdam();
    }

    public Person getEve() {
        if (personService.getEve() == null) {
            Person eve = new Person();
            eve.setName(CC.EVE);
            eve.setPassword("PYOorpG3ao9sL7+PIqYhzg==");
            eve.setRecordStatus(0);
            eve.setVersion(0);
            personService.persist(eve);
            initializeForBasePerson(eve);
        }
        return personService.getEve();
    }

    public static boolean hasNotLoggedIn(
            HttpServletRequest request) {
        Object userRole = request.getSession().getAttribute(CC.user_role);
        return userRole == null || "".equals(userRole);
    }

    // to deal with the case like https://tuyi.sharethegoodones.com
    public String getAppNameInRequest(
            HttpServletRequest request) {
        if (request.getRequestURL() == null) {
            TaoDebug.warn(TaoDebug.getSB(request.getSession()), "request.getRequestURL() menthod returned null!", "");
            return null;
        }

        String personName = request.getRequestURL().toString().toLowerCase();
        TaoDebug.info(TaoDebug.getSB(request.getSession()), "Client side url got in request is:{}", personName);

        if (personName.contains(CC.DOMAIN_NAME)) {
            personName = personName.substring(personName.indexOf(CC.DOMAIN_NAME) + CC.DOMAIN_NAME.length());
            personName = personName.substring(personName.indexOf("/") + 1); //directly return the string after "sharethegoodones.com/"
            return personName.contains("/") ? personName.substring(0, personName.indexOf("/")) : personName;
        }else if(personName.contains("localhost")){
            personName = personName.substring(personName.indexOf("taostyle") + "taostyle".length());
            personName = personName.substring(personName.indexOf("/") + 1);
            return personName.contains("/") ? personName.substring(0, personName.indexOf("/")) : personName;
        }else {
            personName = personName.substring(personName.indexOf("://") + 3);
            int p = personName.indexOf("/");
            if(p > -1){
                personName = personName.substring(0, p);
            }
            return getMatchingPersonName(request, personName);
        }
    }

    private String getMatchingPersonName(HttpServletRequest request, String personName) {
        String obj = customizeService.getAppWebsiteMap();
        int p = obj.indexOf(personName);
        if(p > -1){
            p = obj.indexOf(",", p);
            return obj.substring(p + 1, obj.indexOf(";", p));
        }
        return null;
    }

    private void initializeForBasePerson(
            Person person) {
        for (CC.customizes obj : CC.customizes.values()) {
            Customize customize = new Customize();
            customize.setCusKey(obj.name());
            customize.setCusValue(obj.getValue());
            customize.setPerson(person);
            customizeService.persist(customize);
        }
    }

    private static int defaultNumOfMenu = 20;

    //don't use this empty_line, because it will generate a too big area, and as a result the scroll bar will keep visible.
    public static byte[] EMPTY_LINE = new byte[] { -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0,
            7, -48, 0, 0, 0, 1, 8, 6, 0, 0, 0, -122, -3, 53, 125, 0, 0, 0, 4, 103, 65, 77, 65, 0, 0, -79, -114, 124, -5,
            81, -109, 0, 0, 0, 32, 99, 72, 82, 77, 0, 0, 122, 37, 0, 0, -128, -125, 0, 0, -7, -1, 0, 0, -128, -24, 0, 0,
            117, 48, 0, 0, -22, 96, 0, 0, 58, -105, 0, 0, 23, 111, -105, -87, -103, -44, 0, 0, 0, 34, 73, 68, 65, 84,
            120, -100, -20, -63, 1, 1, 0, 0, 8, -128, -96, -6, 63, -38, -122, 4, 108, 53, 0, 0, 0, 0, 0, 0, 0, -16, -35,
            9, 48, 0, -93, 56, 2, -1, 29, 4, -21, -60, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126 };
    public static byte[] EMPTY_POINT = new byte[] { -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0,
            0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, -60, -119, 0, 0, 0, 4, 103, 65, 77, 65, 0, 0, -79, -114, 124, -5,
            81, -109, 0, 0, 0, 32, 99, 72, 82, 77, 0, 0, 122, 37, 0, 0, -128, -125, 0, 0, -7, -1, 0, 0, -128, -24, 0, 0,
            117, 48, 0, 0, -22, 96, 0, 0, 58, -105, 0, 0, 23, 111, -105, -87, -103, -44, 0, 0, 0, 16, 73, 68, 65, 84,
            120, -100, 98, -8, -1, -1, 63, 3, 64, -128, 1, 0, 8, -4, 2, -2, 51, -93, -30, -103, 0, 0, 0, 0, 73, 69, 78,
            68, -82, 66, 96, -126 };

    public boolean switchClient(
            HttpServletRequest request,
            String clientName) {
        String app_name_inRequest = getAppNameInRequest(request);
        if (app_name_inRequest != null) {
            TaoDebug.info(TaoDebug.getSB(request.getSession()), "person_name found in request is:{}", app_name_inRequest);
            if(clientName == null || clientName.length() == 0) {
                clientName = app_name_inRequest;
            }else if(!app_name_inRequest.equalsIgnoreCase(clientName) && customizeService.getKnownRobotNames().contains(";"+clientName+";")){
                TaoDebug.info(new StringBuilder("client name in request ({}) not match with the one in parameter({})"), app_name_inRequest, clientName);
            }
        }
        if(clientName.contains(".")) {
        	clientName = clientName.substring(0, clientName.indexOf("."));
        }
        Person currentPerson = getCurPerson(request);
        if (currentPerson != null && currentPerson.getName().equals(clientName)) {
            TaoDebug.info(TaoDebug.getSB(request.getSession()), "stopped switching, because they are same person!");
        } else {
            taoDebug.info(request, "start to switchClient to client : {} from current person: {}",currentPerson.getName(), clientName);
            currentPerson = personService.findPersonByName(clientName);
            if (currentPerson != null) {
                request.getSession().setAttribute(CC.CLIENT, currentPerson);
                reInitSession(request.getSession(), currentPerson);
                return true;
            } else { // means null in session and null in database.
                TaoDebug.warn(TaoDebug.getSB(request.getSession()), "no such a person in db:{}, will stop switching",
                        clientName);
            }
        }
        return false;
    }

    // PkeyString must be page persition like *_n_n_n...
    public static boolean isTHUMNeeded(
            HttpServletRequest request,
            String keyString,
            Person person) {
        if (keyString.indexOf('_') == 2) { // check if the key has lang prefix.
            keyString = keyString.substring(3);
        }
        if (keyString.startsWith("product_")) {
            return !(keyString.endsWith("_2d") || keyString.endsWith("_3d"));
        }

        Object isThumNeeded = request.getSession().getAttribute(CC.THUMNEEDED_ + keyString);
        if (isThumNeeded == null) {
            return keyString.contains("gallery_") || keyString.contains("service_");
        } else {
            return "YES".equalsIgnoreCase(isThumNeeded.toString());
        }
    }

    // PkeyString must be page persition like *_n_n_n...
    public static boolean isLangPrfNeededForMedia(
            HttpServletRequest request,
            String keyString) {
        if (keyString.indexOf('_') == 2) { // check if the key has lang prefix.
            keyString = keyString.substring(3);
        }
        Object tCustomize = request.getSession().getAttribute(CC.LANGUAGE_PRF_NEEDED_ + keyString);
        return tCustomize != null && "YES".equalsIgnoreCase(tCustomize.toString());
    }

    // so be noticed that the menu_1 must be for main page. if you have to make it have sub menu, then start from menu_1
    // the pkeyString can be start with "menu_", but still can not be string like "en_menu_", or will cause confusing.
    public static void getIndexFromString(
            String pKeyString) {
        String[] idxs = pKeyString.split("_");
        int idx = 0;
        int tSubIdx = 0;
        int tSubSubIdx = 0;
        for (int i = 0; i < idxs.length; i++) {
            if (StringUtils.isNumeric(idxs[i])) {
                if (idx == 0)
                    idx = Integer.parseInt(idxs[i]);
                else if (tSubIdx == 0)
                    tSubIdx = Integer.parseInt(idxs[i]);
                else
                    tSubSubIdx = Integer.parseInt(idxs[i]);
            }
        }
    }

    public static String getContentType(
            HttpServletRequest request,
            int idx,
            int subIdx,
            int subSubIdx) {
        String contentType = findContentType(request.getSession(), idx, subIdx, subSubIdx);
        return contentType == null ? CC.HTML : contentType;
    }

    private static String findContentType(
            HttpSession session,
            int idx,
            int subIdx,
            int subSubIdx) {
        String contentType = findContentTypeWithAncestors(session, idx, subIdx, subSubIdx);
        if (contentType != null) {
            return contentType;
        }

        if (subSubIdx > 0) {
            for (int previousSubSubIdx = subSubIdx - 1; previousSubSubIdx > 0; previousSubSubIdx--) {
                contentType = findContentType(session, idx, subIdx, previousSubSubIdx);
                if (contentType != null) {
                    return contentType;
                }
            }
            return findContentType(session, idx, subIdx, 0);
        }

        if (subIdx > 0) {
            for (int previousSubIdx = subIdx - 1; previousSubIdx > 0; previousSubIdx--) {
                contentType = findContentType(session, idx, previousSubIdx, 0);
                if (contentType != null) {
                    return contentType;
                }
            }
            return findContentType(session, idx, 0, 0);
        }

        for (int previousIdx = idx - 1; previousIdx > 0; previousIdx--) {
            contentType = findContentType(session, previousIdx, 0, 0);
            if (contentType != null) {
                return contentType;
            }
        }

        return null;
    }

    private static String findContentTypeWithAncestors(
            HttpSession session,
            int idx,
            int subIdx,
            int subSubIdx) {

        StringBuilder sb = new StringBuilder("CONTENTTYPE_" + idx);
        if (subIdx > 0) {
            sb.append("_" + subIdx);
            if (subSubIdx > 0) {
                sb.append("_" + subSubIdx);
            }
        }
        String contentTypeKey = sb.toString();
        Object customize = session.getAttribute(contentTypeKey);

        while (customize == null) {
            int pos = contentTypeKey.lastIndexOf('_');
            if (pos < 0) {
                break;
            }

            contentTypeKey = contentTypeKey.substring(0, pos);
            customize = session.getAttribute(contentTypeKey);
        }

        return customize == null ? null : customize.toString();
    }

    public String getLangPrfWithDefault(HttpServletRequest request) {
    	//get the lang in request or session.
        HttpSession session = request.getSession();
		Object lang = request.getParameter(CC.LANG) != null ? request.getParameter(CC.LANG)
                : session.getAttribute(CC.LANG);
		//if null, get the lang from cookie.
        if (lang == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (c != null && c.getName().equals("lang_" + getCurPerson(request).getName())) {
                        lang = c.getValue();
                        break;
                    }
                }
            }
        }
    	//still null, then get the language setting in client side explorer.
        if(lang == null) {
        	lang =  request.getLocale().getLanguage();
        }
    	//@note: the lang in session or cookie might be wrong, if user switching from one site to another inside stgo,
        //or recently web site changed lang support after last cookie saved...anyway, the target web site might not support the original lang....
    	if(!CC.TRUE.equalsIgnoreCase((String)session.getAttribute(CC.ShowAboveMenu)) || !isLanguageSupported(session, lang)) {
    		lang = "en";
    	}
        return lang.toString() + "_";
    }

    private static boolean isLanguageSupported(
            HttpSession session,
            Object lang) {
    	if(!CC.TRUE.equalsIgnoreCase((String)session.getAttribute(CC.ShowAboveMenu))) {
    		return false;
    	}
        if (lang == null || !(lang instanceof String)) {
            return false;
        }
        // check if it match with any one supported.
        int i = 1;
        while (true) {
            Object supportedLang = session.getAttribute(CC.LANG + i);
            if (supportedLang == null) {
                break;
            } else if (lang.equals(supportedLang)) {
                return true;
            }
            i++;
        }
        return false;
    }

    public String initSubPage(
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            String langPrf,
            int idx,
            int subIdx,
            int subSubIdx,
            Person person) {

        taoDebug.info(request, "start to initLeftMenuBar for: menu_{}, for person: {}", idx);
        initLeftMenuBar(request, model, "menu_" + idx + "_", langPrf, person);
        String menuIdx = null;
        String returnPath = null;
        HttpSession session = request.getSession();
        Object userRole = session.getAttribute(CC.user_role);
        session.setAttribute("moneyShow", "true");

        switch (getContentType(request, idx, subIdx, subSubIdx)) {
            case CC.HTML:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initHTMLSubPage for: submenu_{}, for client: {}", subIdx, person.getName());
                menuIdx = initHTMLSubPage(request, model, langPrf, idx, subIdx, subSubIdx, person);
                returnPath = "/generalHTMLPage";
                break;
            case CC.GALLERY:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initGallerySubPage for: submenu_{}, for client: {}", subIdx, person.getName());
                menuIdx = initGallerySubPage(request, model, langPrf, idx, subIdx, subSubIdx, person);
                session.setAttribute(CC.service_height, getAffectedItemHeight(menuIdx, person, session));
                returnPath = "/generalGalleryPage";
                break;
            case CC.CATALOG:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initCatalogSubPage for: submenu_{}, for client: {}", subIdx, person.getName());
                menuIdx = initCatalogSubPage(request, model, langPrf, idx, subIdx, subSubIdx, person);
                session.setAttribute(CC.service_height, getAffectedItemHeight(menuIdx, person, session));
                returnPath = "/generalCatalogPage";
                break;
            case CC.FEATURE:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initFeatureSubPage for: submenu_{}, for client: {}", subIdx, person.getName());
                menuIdx = initFeatureSubPage(request, model, langPrf, idx, subIdx, subSubIdx, person);
                session.setAttribute(CC.service_height, getAffectedItemHeight(menuIdx, person, session));

                returnPath = "/generalFeaturePage";
                break;
            case CC.PRODUCT:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initProductSubPage for: submenu_{}, for client: {}", subIdx, person.getName());
                menuIdx = initProductSubPage(model, langPrf, idx, subIdx, subSubIdx, person);
                session.setAttribute(CC.service_height, getAffectedItemHeight(menuIdx, person, session));
                returnPath = "/generalProductPage";
                break;
            case CC.SERVICE:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initServiceSubPage for: submenu_{}, for client: {}", subIdx,
                        session.getAttribute(CC.CLIENT));
                menuIdx = initServiceSubPage(request, model, langPrf, idx, subIdx, subSubIdx, person);

                session.setAttribute(CC.service_height, getAffectedItemHeight(menuIdx, person, session));
                
                returnPath = "/generalServicePage";
                break;
            case CC.HTML_SERVICE:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initServiceSubPage for: submenu_{}, for client: {}", subIdx,
                        session.getAttribute(CC.CLIENT));
                menuIdx = initHtmlServiceSubPage(request, model, langPrf, idx, subIdx, subSubIdx, person);

                session.setAttribute(CC.service_height, getAffectedItemHeight(menuIdx, person, session));
                
                returnPath = "/generalHtmlServicePage";
                break;
            case CC.FORM:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initFORMeSubPage for: submenu_{}, for client: {}", subIdx,
                        session.getAttribute(CC.CLIENT));
                menuIdx = initFormSubPage(request, model, langPrf, idx, subIdx, subSubIdx, person);
                returnPath = "/generalFormPage";
                break;
            case CC.RESEARCH:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initResearchSubPage for: submenu_{}, for client: {}", subIdx,
                        session.getAttribute(CC.CLIENT));
                menuIdx = initResearchSubPage(request, model, langPrf, idx, subIdx, subSubIdx, person);
                returnPath = "/generalResearchPage";
                break;
            case CC.LOCATION:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initLocationPage for: submenu_{}, for client: {}", subIdx,
                        session.getAttribute(CC.CLIENT));
                menuIdx = initLocationPage(session, model, langPrf, idx, subIdx, subSubIdx, person);
                returnPath = "/contactus";
                break;
            case CC.TABLESELECTION:
                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initServiceSubPage for: submenu_{}, for client: {}", subIdx,
                        session.getAttribute(CC.CLIENT));
                menuIdx = initTableSelectionPage(session, model, langPrf, idx, subIdx, subSubIdx, person);
                returnPath = "/tableSelection";
                break;
            default: //MAINPAGE
                String contentType = getContentType(request, idx, subIdx, subSubIdx).toString();
                if(contentType.startsWith("http") ){
                    try {
                        response.sendRedirect(contentType);
                    } catch (IOException e) {
                        TaoDebug.error("exception when redirect to other web site:" + contentType);
                    }
                    return "index"; // return after redirect
                }

                TaoDebug.info(TaoDebug.getSB(session),
                        "start to initModelUiForMainPage for: SUBmenu:{}, for client: {}", subIdx,
                        session.getAttribute(CC.CLIENT));
                menuIdx = initModelUiForMainPage(request, model, langPrf, idx, subIdx, subSubIdx, person);
                returnPath = "index";
        }

        if(menuIdx != null && menuIdx.endsWith(PREPARE_NEXT_PAGE)) {
            menuIdx = menuIdx.substring(0, menuIdx.indexOf(PREPARE_NEXT_PAGE));
            prepareNextPageInfo(model, langPrf, idx, subIdx+ 1, subSubIdx, person, returnPath); //why do I do this?
        }

        session.setAttribute(CC.menuIdx, menuIdx);
        taoDebug.info(request, "before return to page, returnPath:{}, person: {}", returnPath);
        return returnPath;
    }

	private void prepareNextPageInfo(Model model, String langPrf, int idx, int subIdx, int subSubIdx,
			Person person, String returnPath) {
		for(; idx < 11; idx++) {
			for(; subIdx <= defaultNumOfMenu; subIdx++) {
				String completeMenuIdx = completeMenuIdx(idx, subIdx, subSubIdx, langPrf, person);
				completeMenuIdx = "menu_" + completeMenuIdx.substring(0, completeMenuIdx.length() - 2);
				TextContent tContent = textContentService.findContentsByKeyAndPerson(
						langPrf + completeMenuIdx, person);
				if(tContent != null) {
			        model.addAttribute("nextPage", completeMenuIdx);
			        return;
				}
				if(subIdx == defaultNumOfMenu) {
					subIdx = 0;
					break;
				}
			}
		}
	}

    private String initProductSubPage(
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {

        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        String key = "product_" + menuIdx;

        TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + key + "_content", person);
        model.addAttribute("subpageContent", tContent == null ? "" : tContent.getContent());
        long serviceAmount = mediaUploadService.countMediaUploadsByKeyAndPerson(key, person) / 2;
        model.addAttribute("serviceAmount", serviceAmount);

        model.addAttribute("descriptions", prepareDescriptions(langPrf, key, serviceAmount, person));
        return menuIdx;
    }

    private String initFeatureSubPage(
            HttpServletRequest request,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {
        HttpSession session = request.getSession();
        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);

        List<Feature> features = featureService.findAllFeaturesByMenuIdxAndPerson(menuIdx, person);
        if (features != null) {
            List<List<String>> serviceKeyLists = new ArrayList<List<String>>();
            List<List<String>> descriptions = new ArrayList<List<String>>();
            List<List<String>> prices  = new ArrayList<List<String>>();
            List<List<String>> visibleStatusList = new ArrayList<List<String>>();
            List<String> service_heights = new ArrayList<String>();
            List<String> refList = new ArrayList<String>();
            List<String> menus = new ArrayList<String>();
            List<String> groupTitles = new ArrayList<String>();
            String menuPRF = langPrf + "menu_";

            for (Feature feature : features) {
                // get all visible images.
                String itemToShow = feature.getItemsToShow();
                itemToShow = itemToShow.replace("service_", "");
                String[] itemsStrs = StringUtils.split(itemToShow, ',');
                Set<String> selectedItemSet = new HashSet<String>();
                if (itemsStrs != null) {
                    for (String item : itemsStrs) {
                        if (StringUtils.isNotBlank(item)) {
                            selectedItemSet.add(item.trim());
                        }
                    }
                }
                	
                String ref = feature.getRefMenuIdx();
                refList.add(ref);
                String menuKey = menuPRF + ref;
                TextContent textContent = textContentService.findContentsByKeyAndPerson(menuKey, person);
                menus.add(textContent == null ? "" : textContent.getContent());
                
                String featurePrf = langPrf + "feature_" + menuIdx + "-"+ ref + "_groupTitle";
                textContent = textContentService.findContentsByKeyAndPerson(featurePrf, person);
                if (textContent != null) {
                	groupTitles.add(textContent.getContent());
                }else {
                	String featureTitlekey = !ref.endsWith("_0") ? langPrf + "service_" + ref + "_0_title" : langPrf + "service_" + ref + "_title";
                	textContent = textContentService.findContentsByKeyAndPerson(featureTitlekey, person);
                	groupTitles.add(textContent != null ? textContent.getContent() : "");
                }
                
                List<String> visibleStatus = new ArrayList<>();
                if (isAboveManager(session) || isPrinter(session)) {
                    List<String> keys = serviceService.listAllServiceKeyByKeyAndPerson(ref, person);
                    keys = reOrder(keys);
                    serviceKeyLists.add(keys);

                    fillInDescriptions(langPrf, person, descriptions, keys);
                    fillInPrices(person, session, prices, keys);
                    boolean isEmpty = true;
                    if (isPrinter(session)) {
                        UserAccount userAccount = (UserAccount) session.getAttribute(CC.currentUser);
                        String nameStrInService = "," + TaoEncrypt.stripUserName(userAccount.getLoginname()) + ",";

                        for (String item : keys) {
                            if (item.startsWith("service_")) {
                                item = item.substring(8);
                            }
                            Service service = serviceService.findServiceByCatalogAndPerson(item, person);
                            if (service != null && service.getC3() != null) {
                                if (service.getC3().contains(nameStrInService)) {
                                    isEmpty = false;
                                    visibleStatus.add("1");
                                } else {
                                    visibleStatus.add(null);
                                }
                            } else {
                                visibleStatus.add(null);
                            }
                        }
                    } else {
                        for (String item : keys) {
                            if (selectedItemSet.contains(item)) {
                                isEmpty = false;
                                visibleStatus.add("1");
                            } else {
                                visibleStatus.add(null);
                            }
                        }
                    }
                    visibleStatusList.add(isEmpty ? null : visibleStatus);
                } else {
                    List<String> imageKeys = Arrays.asList(itemsStrs);
                    serviceKeyLists.add(imageKeys);
                    fillInDescriptions(langPrf, person, descriptions, imageKeys);
                    fillInPrices(person, session, prices, imageKeys);
                    boolean isEmpty = true;
                    @SuppressWarnings("unchecked")
                    HashMap<String, Integer> selectedItems = (HashMap<String, Integer>) session.getAttribute(CC.selectedItems);
                    if (selectedItems != null) {
                        for (String item : imageKeys) {
                            item = strip(item);
                            int number = selectedItems.getOrDefault(item, 0);
                            if (number > 0) {
                                isEmpty = false;
                                visibleStatus.add(String.valueOf(number));
                            } else {
                                visibleStatus.add(null);
                            }
                        }
                        visibleStatusList.add(isEmpty ? null : visibleStatus);
                    } else {
                        visibleStatusList.add(null);
                    }
                }
                //prepare for the item height of this feature secion.
                service_heights.add(String.valueOf(getAffectedItemHeight(ref, person, session)));
            }

            model.addAttribute("features", features);
            model.addAttribute("imageKeys", serviceKeyLists);
            model.addAttribute("descriptions", descriptions);
            model.addAttribute("prices", prices);
            model.addAttribute("visibleStatusList", visibleStatusList);
            model.addAttribute("service_heights", service_heights);
            model.addAttribute("groupTitles", groupTitles);

            // available menu to remove.
            model.addAttribute("refForDelete", refList);
            model.addAttribute("menus", menus);
            model.addAttribute("show_status_message", session.getAttribute(langPrf + CC.show_status_message1));
            model.addAttribute("show_status_category", session.getAttribute(langPrf + CC.show_status_category1));

            initWithAllNotes(langPrf, session, null);
        }

        if (isAboveManager(session)) {
            // available menu to ref.
        	List<String> menuTypes = new ArrayList<>();
        	menuTypes.add(CC.SERVICE);
        	menuTypes.add(CC.HTML_SERVICE);
            List<String> menusForRef = fetchAllMenuByType(menuTypes, request, langPrf, person);
            model.addAttribute("menusForRef", menusForRef);
        }
        return menuIdx;
    }

    private int getAffectedItemHeight(String menuKey, Person person, HttpSession session) {
    	if(menuKey.endsWith("_0")) {
    		menuKey = menuKey.substring(0, menuKey.length() - 2);
    	}
    	int p = menuKey.indexOf("menu_");
    	if(p > 0) {
    		menuKey = menuKey.substring(p + 5);
    	}
    	
    	Customize customize = getCustomizeValue(CC.service_height, menuKey, person);
		//the height fetched out, is actually thump image maximum height. need to add the text and check box, because some time we upload a vertical photo, then it will fill the whole area.
    	//be noticed that some image is vertical, then the whole area will be fill, that's why we want the Row height bigger than image maximum height.
    	//Note: if the photo is horenzantal, and too narrow to fill the area, you may see too big empty space left below the check box.
    	Customize showCBox = getCustomizeValue(CC.show_service_cBox, menuKey, person);
		if(showCBox != null) {
			return Integer.parseInt(customize.getCusValue()) + 25 +
                    ("true".equalsIgnoreCase(showCBox.getCusValue()) || (isAboveManager(session))  ? 25 : 0);
		}else {
			return Integer.parseInt(customize.getCusValue()) + 25;
		}
	}

	private Customize getCustomizeValue(String cusKey, String menuKey, Person person) {
		Customize customize = customizeService.findCustomizeByKeyAndPerson(cusKey + "_" + menuKey, person);
		String value = customize != null ? customize.getCusValue() : null;
		if(value == null || "".equals(value.trim()) || "null".equals(value.toLowerCase())){
			customize = customizeService.findCustomizeByKeyAndPerson(cusKey, person);
    		value = customize != null ? customize.getCusValue() : null;
    		if(value == null || "".equals(value.trim()) || "null".equals(value.toLowerCase())){
    			customize = customizeService.findCustomizeByKeyAndPerson(cusKey, personService.getAdam());
    		}
		}
		return customize;
	}

	private static List<String> reOrder(List<String> pKeys) {
    	
    	 String[] keys = new String[200];

         for (String key : pKeys) {
         	int p = key.lastIndexOf("_");
         	int a = Integer.valueOf(key.substring(p + 1));
         	keys[a] = key;
         }
         
         List<String> keysFR = new ArrayList<>();
         for (int i = 0; i < 200; i++) {
         	if(keys[i] != null) {
         		keysFR.add(keys[i]);
         	}
         }
         
		return keysFR;
	}

	private static String strip(
            String key) {
        int i = key.indexOf('_');
        if (i > 3) {
            key = key.substring(i + 1);
        }
        return key;
    }

    public List<String> fetchAllMenuByType(
            List<String> menuType,
            HttpServletRequest request,
            String langPrf,
            Person person) {
        String menuKey = langPrf + "menu_";
        int posOfIdxStart = menuKey.length();
        List<String> menusForRef = textContentService.findAllMatchedContent(menuKey, CC.TextContentKey, person);
        for (int i = menusForRef.size() - 1; i >= 0; i--) {
            String menuStr = menusForRef.get(i).substring(posOfIdxStart);

            int[] menuIdxes = TaoUtil.splitMenuIdxToAry(menuStr);

            String contentTyep = TaoUtil.getContentType(request, menuIdxes[0], menuIdxes[1], menuIdxes[2]);
            if (!menuType.contains(contentTyep)) {							//because we are using contains to compair......
                menusForRef.remove(i);
            } else {
                menusForRef.set(i, menuStr);
            }
        }
        return menusForRef;
    }

    private static boolean isAboveManager(
            HttpSession session) {
        Object userRole = session.getAttribute(CC.user_role);
        if (userRole != null) {
            String role = userRole.toString();
            return role.contains(CC.ROLE_ADMIN) || role.contains(CC.ROLE_MANAGER);
        } else {
            return false;
        }
    }

    private static boolean isPrinter(
            HttpSession session) {
        Object userRole = session.getAttribute(CC.user_role);
        if (userRole != null) {
            String role = userRole.toString();
            return role.contains(CC.ROLE_PRINTER);
        } else {
            return false;
        }
    }

    public void fillInPrices(Person person,
                                   HttpSession session,
                                   List<List<String>> prices,
                                   List<String> imageKeys) {
        List<String> list = new ArrayList<String>();
        for (String key : imageKeys) {
            List<Service> services = serviceService.findAllMatchedServices(key, person);
            if(services.size() > 1){
                TaoDebug.error("expected only one record but found" + services.size() + " for key:" + key
                        + " for user:" + person.getId(), null);
            }
            list.add(resolveVisiblePrice(services.isEmpty() ? null : services.get(0), session));
        }
        prices.add(list);
    }

    //imageKeys should be simplest format---means no "serverce"
    public void fillInDescriptions(
            String langPrf,
            Person person,
            List<List<String>> descriptions,
            List<String> imageKeys) {
        descriptions.add(prepareDescriptions(langPrf, imageKeys , imageKeys.size(), person));
    }

    public static int[] splitMenuIdxToAry(
            String menuIdxString) {
    	if(menuIdxString.startsWith("menu_")) {
    		menuIdxString = menuIdxString.substring(5);
    	}
        int[] menuIdxes = new int[3];
        for (int i = 0; i < 3; i++) {
            int septatorIdx = menuIdxString.indexOf("_"); // get the menuIdx
            if (septatorIdx > 0) {
                menuIdxes[i] = Integer.valueOf(menuIdxString.substring(0, septatorIdx));
                menuIdxString = menuIdxString.substring(septatorIdx + 1);
            } else {
                Pattern pattern = Pattern.compile("^\\d+"); // match leading digits
                Matcher matcher = pattern.matcher(menuIdxString.substring(0));
                if (matcher.find()) {
                    menuIdxes[i] = Integer.valueOf(matcher.group());
                }
                break;
            }
        }
        return menuIdxes;
    }

    private String initLocationPage(
            HttpSession session,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {
        String completeMenuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        initSubpageContent(model, "location_" + completeMenuIdx, langPrf, person);
        prepareContents(model, "text_Contact_Titles", langPrf, person);
        int length = prepareContents(model, "text_Contact_Contents", langPrf, person);
        if(length == 0) {
        	completeMenuIdx = null; //this is the way to tell front end that the url is not valid. we got nothing from db base on the url.
        }
        Object[] Xs = new Object[length];
        Object[] Ys = new Object[length];
        Object[] maps = new Object[length];
        for (int i = 0; i < length; i++) {
            Xs[i] = session.getAttribute(CC.MAP_POS_X + i);
            Ys[i] = session.getAttribute(CC.MAP_POS_Y + i);
            maps[i] = session.getAttribute(CC.MAP_POS + i);
        }
        model.addAttribute(CC.MAP_POS_X, Xs);
        model.addAttribute(CC.MAP_POS_Y, Ys);
        model.addAttribute(CC.MAP_POS, maps);

        return completeMenuIdx;
    }
    
    private String initTableSelectionPage(
            HttpSession session,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {
        
        
        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        String key = "gallery_" + menuIdx;

        initSubpageContent(model, "tables_" + menuIdx, langPrf, person);
        // for gallery should always divided by 2, because it always has thumb.
        long pictureAmount = mediaUploadService.countMediaUploadsByKeyAndPerson(key, person) / 2;
        model.addAttribute("pictureAmount", pictureAmount);

        TaoDebug.info(TaoDebug.getSB(session),
                "start to initGallerySubPage, key is {}, pictureAmount is {}", key, pictureAmount);
        return menuIdx;
        
    }

    private String initModelUiForMainPage(
            HttpServletRequest request,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {
        // "infratherapie""web-slim""christmas"
        initTextArray(model, "text_on_slid", 10, langPrf, person);

        // "infratherapie""web-slim""christmas"
        initTexts(model, "text_promotion", 3, langPrf, person);
        initTexts(model, "text_firstLineTitle", 3, langPrf, person);
        initTexts(model, "text_firstLineContent", 3, langPrf, person);
        initTexts(model, "text_subTitle", 4, langPrf, person);
        initTexts(model, "text_categoryTitle", 4, langPrf, person);
        initTexts(model, "text_categoryContent", 4, langPrf, person);
        initTexts(model, "text_LogTitle", 3, langPrf, person);
        initTexts(model, "text_logContent", 3, langPrf, person);

        model.addAttribute("slide_superBig_amount", findIndexOfTheLastSlidPicture(request, person, langPrf));
        String completedMenuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        taoDebug.info(request, "start to return completedMenuIdx : {}, for person: {}", completedMenuIdx);
        return completedMenuIdx;
    }

    // initialise the left bar menu and main content.
    private String initHTMLSubPage(
            HttpServletRequest request,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {

        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        String prf2 = "html_" + menuIdx;

        TaoDebug.info(TaoDebug.getSB(request.getSession()), "start to initHTMLSubPage, prf2 is {}, langPrf is {}", prf2,
                langPrf);
        initSubpageContent(model, prf2, langPrf, person);
        return menuIdx;
    }

    /**
     * menuIdx's format must be like x_y_z, x_y_0 or x_0_0, because later will use like to find out all matching images.
     * 
     * @param pMenuIdx
     * @param pSubMenuIdx
     * @param pSubSubMenuIdx
     * @param langPrf
     * @param person
     * @return
     */
    public String completeMenuIdx(
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            String langPrf,
            Person person) {
        String menuIdx = pMenuIdx + "_";
        if (pSubMenuIdx == 0) {
            pSubMenuIdx = hasSubMenu(langPrf, pMenuIdx, 0, person); // which one is the first not null item.
        }

        menuIdx = menuIdx + pSubMenuIdx + "_";
        if (pSubMenuIdx > 0) {
            if (pSubSubMenuIdx > 0) { // if it's initialising a sub page of sub menu, return relevant page directly.
                menuIdx = menuIdx + pSubSubMenuIdx;
            } else {
                int tIdx = hasSubMenu(langPrf, pMenuIdx, pSubMenuIdx, person); // which one is the first not null item.
                menuIdx = menuIdx + tIdx; // if it's the sub menu clicked, then check if it has sub sub menu, return
                                          // the page of first sub sub menu.
            }
        } else {
            menuIdx = menuIdx + "0";
        }

        return menuIdx;
    }

    // initialise the left bar menu and main content.
    private String initGallerySubPage(
            HttpServletRequest request,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {

        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        String key = "gallery_" + menuIdx;

        key = TaoUtil.isLangPrfNeededForMedia(request, key) ? langPrf + key : key;
        // for gallery should always divided by 2, because it always has thumb.
        long pictureAmount = mediaUploadService.countMediaUploadsByKeyAndPerson(key, person) / 2;
        model.addAttribute("pictureAmount", pictureAmount);

        TaoDebug.info(TaoDebug.getSB(request.getSession()),
                "start to initGallerySubPage, key is {}, pictureAmount is {}", key, pictureAmount);
        return menuIdx;
    }

    // initialise the left bar menu and main content.
    private String initCatalogSubPage(
            HttpServletRequest request,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {

        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        String key = "service_" + menuIdx;

        // List tProducts = Product.findProductsByCatalogAndPerson(key, person);
        //
        // tProducts = languageRelevantProcess(tProducts, langPrf);
        //
        // model.addAttribute("products", tProducts);
        // model.addAttribute("productAmount", tProducts.size());

        // TaoDebug.info(TaoDebug.getSB(request.getSession()), "start to initCatalogSubPage, menukey is {},
        // productAmount is {}", key, tProducts.size());
        TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + key + "_title", person);
        model.addAttribute("subpageContent", tContent == null ? "" : tContent.getContent());

        long serviceAmount = mediaUploadService.countMediaUploadsByKeyAndPerson(key, person) / 2;
        model.addAttribute("serviceAmount", serviceAmount);

        model.addAttribute("descriptions", prepareDescriptions(langPrf, key, serviceAmount, person));

        // the selection status
        HttpSession session = request.getSession();
        if (!isAboveManager(session)) {
            List<String> visibleStatus = new ArrayList<String>();
            if (isPrinter(session)) {
                List<String> imageKeys = mediaUploadService.listAllMediaUploadsKeyByKeyAndPerson(key, person);
                imageKeys = TaoImage.stripThumOrderAndValidate(imageKeys);

                boolean isEmpty = true;
                UserAccount userAccount = (UserAccount) session.getAttribute(CC.currentUser);
                String nameStrInService = "," + TaoEncrypt.stripUserName(userAccount.getLoginname()) + ",";

                for (String item : imageKeys) {
                    if (item.startsWith("service_")) {
                        item = item.substring(8);
                    }
                    Service service = serviceService.findServiceByCatalogAndPerson(item, person);
                    if (service != null && service.getC3() != null) {
                        if (service.getC3().contains(nameStrInService)) {
                            isEmpty = false;
                            visibleStatus.add("1");
                        } else {
                            visibleStatus.add(null);
                        }
                    } else {
                        visibleStatus.add(null);
                    }
                }

                model.addAttribute("visibleStatusList", isEmpty ? null : visibleStatus);
            } else {
                List<String> visibleStatusList = buildVisibleStatusList(menuIdx, serviceAmount, getSelectedItemsMap(session));
                model.addAttribute("visibleStatusList", visibleStatusList.isEmpty() ? null : visibleStatusList);
            }
        }

        model.addAttribute("show_status_message", session.getAttribute(langPrf + CC.show_status_message1));
        model.addAttribute("show_status_category", session.getAttribute(langPrf + CC.show_status_category1));
        TaoDebug.info(TaoDebug.getSB(request.getSession()),
                "completed initCatelogSubPage, menukey is {}, serviceAmount is {}", key, serviceAmount);
        return menuIdx;
    }

    // initialise the left bar menu and main content.
    private String initServiceSubPage(
            HttpServletRequest request,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {
        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        String key = "service_" + menuIdx;

        TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + key + "_title", person);
        model.addAttribute("subpageContent", tContent == null ? "" : tContent.getContent());

        long serviceAmount = serviceService.countServicesByKeyAndPerson(key, person);
        model.addAttribute("serviceAmount", serviceAmount);

        model.addAttribute("descriptions", prepareDescriptions(langPrf, key, serviceAmount, person));

        model.addAttribute("prices", preparePrices(langPrf, key, serviceAmount, person, request.getSession()));

        // the selection status
        HttpSession session = request.getSession();
        if (!isAboveManager(session)) {
            List<String> visibleStatus = new ArrayList<String>();
            boolean isEmpty = true;
            if (isPrinter(session)) {
                List<String> imageKeys = serviceService.listAllServiceKeyByKeyAndPerson(key, person);

                UserAccount userAccount = (UserAccount) session.getAttribute(CC.currentUser);
                String nameStrInService = "," + TaoEncrypt.stripUserName(userAccount.getLoginname()) + ",";

                for (String item : imageKeys) {
                    if (item.startsWith("service_")) {
                        item = item.substring(8);
                    }
                    Service service = serviceService.findServiceByCatalogAndPerson(item, person);
                    if (service != null && service.getC3() != null) {
                        if (service.getC3().contains(nameStrInService)) {
                            isEmpty = false;
                            visibleStatus.add("1");
                        } else {
                            visibleStatus.add(null);
                        }
                    } else {
                        visibleStatus.add(null);
                    }
                }
            } else {
                Map<String, Integer> selectedItemsMap = getSelectedItemsMap(session);
                for (int i = 1; i <= serviceAmount; i++) {
                    int number = selectedItemsMap.getOrDefault(menuIdx + "_" + i, 0);
                    if (number > 0) {
                        isEmpty = false;
                        visibleStatus.add(String.valueOf(number));
                    } else {
                        visibleStatus.add(null);
                    }
                }
            }

            if(isEmpty) {//why if no item selected, we should return a "PREPARE_NEXT_PAGE"?
//            	menuIdx = menuIdx + PREPARE_NEXT_PAGE;
            	model.addAttribute("visibleStatusList", null);
            }else {
            	model.addAttribute("visibleStatusList", visibleStatus);
            }
        }

        model.addAttribute("show_status_message", session.getAttribute(langPrf + CC.show_status_message1));
        model.addAttribute("show_status_category", session.getAttribute(langPrf + CC.show_status_category1));
        initWithAllNotes(langPrf, session, null);
        TaoDebug.info(TaoDebug.getSB(request.getSession()),
                "completed initServiceSubPage, menukey is {}, serviceAmount is {}", key, serviceAmount);
        return menuIdx;
    }
    
    private String initHtmlServiceSubPage(
            HttpServletRequest request,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {
        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        String key = "service_" + menuIdx;


        TaoDebug.info(TaoDebug.getSB(request.getSession()), "start to initHTMLServiceSubPage, menuIdx is {}, langPrf is {}", menuIdx, langPrf);
        //for the title.
        TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + key + "_title", person);
        String htmlStr = tContent == null ? "" : tContent.getContent();
        model.addAttribute("subpageContent", htmlStr);
        
        StringBuilder sb = new StringBuilder("<div ccp_infra_version=\"3\" ccp_infra_timestamp=\"1597804130458\" ccp_infra_user_hash=\"3445629718\" ccp_infra_copy_id=\"a4cd1a70-6f98-4997-bb45-d5df1687da84\" data-ccp-timestamp=\"1597804130458\">");
//        long serviceAmount = MediaUpload.countMediaUploadsByKeyAndPerson(key, person) / 2;
        long serviceAmount = serviceService.countServicesByKeyAndPerson(key, person);
        model.addAttribute("serviceAmount", serviceAmount);

        List<String> contnets = prepareDescriptions(langPrf, key, serviceAmount, person);
        model.addAttribute("descriptions", contnets);
        for (String string : contnets) {
        	if(string != null) {
	        	//if user modified which the pencil of specific service, the <div> could be removed....then the display might be wrong.....and the
	        	if(!string.startsWith("<div")) {
	        		string = "<div>" + string + "</div>";
	        	}
	        	sb.append(string);
        	}
		}

        //if it's not admin, init the selection status, otherwise, init the html part.
        HttpSession session = request.getSession();
        if (!isAboveManager(session)) {
            List<String> visibleStatus = new ArrayList<String>();
            
            List<String> visibleStatusList = buildVisibleStatusList(menuIdx, serviceAmount, getSelectedItemsMap(session));
            model.addAttribute("visibleStatusList", visibleStatusList.isEmpty() ? null : visibleStatusList);
        }else {
            model.addAttribute("htmlServiceContent",  sb.append("</div>").toString());
        }

        model.addAttribute("show_status_message", session.getAttribute(langPrf + CC.show_status_message1));
        model.addAttribute("show_status_category", session.getAttribute(langPrf + CC.show_status_category1));
        TaoDebug.info(TaoDebug.getSB(request.getSession()),
                "completed initServiceSubPage, menukey is {}, serviceAmount is {}", key, serviceAmount);
        
        //init the content in htmlArea.
       return menuIdx;
    }

    public String initFormSubPage(
            HttpServletRequest request,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {
        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        String key = "service_" + menuIdx;

        TaoDebug.info(TaoDebug.getSB(request.getSession()), "start to initFormSubPage, menuIdx is {}, langPrf is {}", menuIdx, langPrf);
        //for the title.
        TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + key + "_title", person);
        String htmlStr = tContent == null ? "" : tContent.getContent();
        model.addAttribute("subpageContent", htmlStr);

        if(htmlStr.contains( "Ria ")){
            model.addAttribute("submitPath", "services/createRiaRecord");
        }else if(htmlStr.toLowerCase().contains( " recharge")){
            model.addAttribute("submitPath", "services/createRechargeRecord");
        }
        
        StringBuilder sb = new StringBuilder("<div ccp_infra_version=\"3\" ccp_infra_timestamp=\"1597804130458\" ccp_infra_user_hash=\"3445629718\" ccp_infra_copy_id=\"a4cd1a70-6f98-4997-bb45-d5df1687da84\" data-ccp-timestamp=\"1597804130458\">");
//        long serviceAmount = MediaUpload.countMediaUploadsByKeyAndPerson(key, person) / 2;
        long serviceAmount = serviceService.countServicesByKeyAndPerson(key, person);
        model.addAttribute("questionAmount", serviceAmount);

        List<String> contents = prepareDescriptions(langPrf, key, serviceAmount, person);
        List<String> questions = new ArrayList<>();
        //prepare the the questions
        for (String string : contents) {
        	if(string != null && string.startsWith("<div>") && string.endsWith("</div>")) {
	        	//if user modified which the pencil of specific service, the <div> could be removed....then the display might be wrong.....and the
	        	string = string.substring(5, string.length() - 6);
	        	questions.add(string);
	        	sb.append(string);
        	}
		}
        model.addAttribute("questions", questions);
        List<String> names = new ArrayList<String>();
        List<String> types = new ArrayList<String>();
        prepareAndFillNamesAndTypes(key, person, names, types);
        model.addAttribute("formNames", names);
        model.addAttribute("formTypes", types);
        
        //prepare the the quick input
        for (String string : contents) {
        	if(string != null) {
	        	//if user modified which the pencil of specific service, the <div> could be removed....then the display might be wrong.....and the
	        	string = "<div>" + string + "</div>";
	        	sb.append(string);
        	}
		}

        //if it's not admin, init the selection status, otherwise, init the html part.
        HttpSession session = request.getSession();
        if (isAboveManager(session)) {
            model.addAttribute("htmlServiceContent",  sb.append("</div>").toString());
        }

//        model.addAttribute("show_status_message", session.getAttribute(langPrf + CC.show_status_message1));
//        model.addAttribute("show_status_category", session.getAttribute(langPrf + CC.show_status_category1));
        TaoDebug.info(TaoDebug.getSB(request.getSession()),
                "completed initFormSubPage, menukey is {}, serviceAmount is {}", key, serviceAmount);
        
        //init the content in htmlArea.
       return menuIdx;
    }
    
    // initialise the left bar menu and main content.
    private String initResearchSubPage(
            HttpServletRequest request,
            Model model,
            String langPrf,
            int pMenuIdx,
            int pSubMenuIdx,
            int pSubSubMenuIdx,
            Person person) {
        String menuIdx = completeMenuIdx(pMenuIdx, pSubMenuIdx, pSubSubMenuIdx, langPrf, person);
        String key = "service_" + menuIdx;															//no big difference, so still use service as prefix. so if one day user change it to server page, no need to input again.

        TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + key + "_title", person);
        model.addAttribute("subpageContent", tContent == null ? "" : tContent.getContent());
        
        tContent = textContentService.findContentsByKeyAndPerson(langPrf + key + "_conclusion", person);
        model.addAttribute("conclusion", tContent == null ? "" : tContent.getContent());

        long serviceAmount = mediaUploadService.countMediaUploadsByKeyAndPerson(key, person) / 2;
        model.addAttribute("serviceAmount", serviceAmount);

        model.addAttribute("descriptions", prepareDescriptions(langPrf, key, serviceAmount, person));

        // the selection status
        HttpSession session = request.getSession();
        if (!isAboveManager(session)) {
            List<String> visibleStatus = new ArrayList<String>();
            if (isPrinter(session)) {
                List<String> imageKeys = serviceService.listAllServiceKeyByKeyAndPerson(key, person);

                boolean isEmpty = true;
                UserAccount userAccount = (UserAccount) session.getAttribute(CC.currentUser);
                String nameStrInService = "," + TaoEncrypt.stripUserName(userAccount.getLoginname()) + ",";

                for (String item : imageKeys) {
                    if (item.startsWith("service_")) {
                        item = item.substring(8);
                    }
                    Service service = serviceService.findServiceByCatalogAndPerson(item, person);
                    if (service != null && service.getC3() != null) {
                        if (service.getC3().contains(nameStrInService)) {
                            isEmpty = false;
                            visibleStatus.add("1");
                        } else {
                            visibleStatus.add(null);
                        }
                    } else {
                        visibleStatus.add(null);
                    }
                }

                model.addAttribute("visibleStatusList", isEmpty ? null : visibleStatus);
            } else {
                List<String> visibleStatusList = buildVisibleStatusList(menuIdx, serviceAmount, getSelectedItemsMap(session));
                model.addAttribute("visibleStatusList", visibleStatusList.isEmpty() ? null : visibleStatusList);
            }
        }

        model.addAttribute("show_status_message", session.getAttribute(langPrf + CC.show_status_message1));
        model.addAttribute("show_status_category", session.getAttribute(langPrf + CC.show_status_category1));
        TaoDebug.info(TaoDebug.getSB(request.getSession()),
                "completed initServiceSubPage, menukey is {}, serviceAmount is {}", key, serviceAmount);
        return menuIdx;
    }
    
    private static Map<String, Integer> getSelectedItemsMap(HttpSession session) {
        Object rawSelectedItems = session.getAttribute(CC.selectedItems);
        if (rawSelectedItems instanceof Map<?, ?>) {
            Map<String, Integer> normalized = new LinkedHashMap<String, Integer>();
            Map<?, ?> rawMap = (Map<?, ?>) rawSelectedItems;
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                    normalized.put((String) entry.getKey(), ((Number) entry.getValue()).intValue());
                }
            }
            return normalized;
        }
        if (rawSelectedItems instanceof String) {
            return parseLegacySelectedItems((String) rawSelectedItems);
        }
        return new LinkedHashMap<String, Integer>();
    }

    private static Map<String, Integer> parseLegacySelectedItems(String selectedItems) {
        Map<String, Integer> selectedItemsMap = new LinkedHashMap<String, Integer>();
        if (selectedItems == null || selectedItems.trim().isEmpty()) {
            return selectedItemsMap;
        }
        String[] parts = selectedItems.split(",");
        for (String part : parts) {
            String itemKey = part == null ? null : part.trim();
            if (itemKey == null || itemKey.isEmpty()) {
                continue;
            }
            selectedItemsMap.put(itemKey, selectedItemsMap.getOrDefault(itemKey, 0) + 1);
        }
        return selectedItemsMap;
    }

    private static List<String> buildVisibleStatusList(String menuIdx, long serviceAmount, Map<String, Integer> selectedItemsMap) {
        List<String> visibleStatusList = new ArrayList<String>();
        boolean hasSelectedItems = false;
        for (int i = 1; i <= serviceAmount; i++) {
            int number = selectedItemsMap.getOrDefault(menuIdx + "_" + i, 0);
            if (number > 0) {
                hasSelectedItems = true;
                visibleStatusList.add(String.valueOf(number));
            } else {
                visibleStatusList.add(null);
            }
        }
        return hasSelectedItems ? visibleStatusList : Collections.<String>emptyList();
    }

    private static List<Integer> getSelectedNotesOfCurService(HttpSession session, List<List<Integer>> selectedNote, String key) {
        int index = 0;
        for (String selectedItemKey : getSelectedItemsMap(session).keySet()) {
            if (key.equals(selectedItemKey)) {
                return TaoUtil.initAndReturnSelectedNote(selectedNote, index);
            }
            index++;
        }
        return new ArrayList<Integer>();
    }

    private static int countStr(
            String item,
            String selectedItems) {
        int i = 0;
        int p = selectedItems.indexOf(item);
        while (p > -1) {
            i++;
            selectedItems = selectedItems.substring(p + item.length() - 1);
            p = selectedItems.indexOf(item);
        }
        return i;
    }

    // to decide which part in name and description should be displayed according to the given language.
    public static List<MultiLanguageabl> languageRelevantProcess(
            List<MultiLanguageabl> pMultLanItems,
            String tLanguage) {

        if (tLanguage == null || "null".equals(tLanguage))
            tLanguage = "en_"; // in case that no language has been selected.

        if (!tLanguage.trim().endsWith("_"))
            tLanguage = tLanguage.trim() + "_"; // in case that "en" has been input as parameter.

        // set the name_display of the products, so it can display a language relevant name.
        for (int i = pMultLanItems.size() - 1; i >= 0; i--) {
            String tName = pMultLanItems.get(i).getName();
            String tDescript = pMultLanItems.get(i).getDescription();
            String[] tNames = tName.split("~");
            String[] tDescripts = tDescript.split("~");

            boolean matched = false;
            for (int j = 0; j < tNames.length; j++) {
                if (tNames[j].trim().toLowerCase().startsWith(tLanguage.toLowerCase())) {
                    pMultLanItems.get(i).setLocalName(tNames[j].trim().substring(3));
                    matched = true;
                    break; // go and work on next product.
                }
            }
            if (!matched) // no match, then use the first name.
                pMultLanItems.get(i).setLocalName(tNames[0].indexOf("_") == 2 ? tNames[0].substring(3) : tNames[0]);

            matched = false;
            for (int j = 0; j < tDescripts.length; j++) {
                if (tDescripts[j].trim().toLowerCase().startsWith(tLanguage.toLowerCase())) {
                    pMultLanItems.get(i).setLocalDescription(tDescripts[j].trim().substring(3));
                    matched = true;
                    break; // go and work on next product.
                }
            }
            if (!matched) // no match, then use the first name.
                pMultLanItems.get(i).setLocalDescription(
                        tDescripts[0].indexOf("_") == 2 ? tDescripts[0].substring(3) : tDescripts[0]);
        }
        return pMultLanItems;
    }

    public List<String> preparePrices(
            String langPrf,
            String key,
            long serviceAmount,
            Person person,
            HttpSession session) {
        List<String> prices = new ArrayList<String>();
        if(key.startsWith("service_")){
            key = key.substring(8) + "_%";
        }
        List<Service> list = serviceService.findAllMatchedServices(key, person);
        HashMap<String, String> map = new HashMap<String, String>();
        for (Service service : list) {
            String posInPage = service.getC1();
            posInPage = posInPage.substring(posInPage.lastIndexOf('_') + 1);
            if(!map.containsKey(posInPage)) {
                map.put(posInPage, resolveVisiblePrice(service, session));
            }else {
                TaoDebug.error("duplicated key appeared in search reuslt list with key:{}, under person{}", service.getC1(), person.getId());
            }
        }
        for (int i = 0; i < serviceAmount; i++) {
            prices.add(map.get(String.valueOf(i + 1)));
        }
        return prices;
    }

    private String resolveVisiblePrice(Service service, HttpSession session) {
        if (service == null) {
            return "999.99";
        }

        if (isAboveManager(session)) {
            return buildManagerPriceLine(service);
        }

        boolean partnerView = isPartnerOrClient(session);
        String altPrice = service.getDescription();
        if (partnerView && StringUtils.isNotBlank(altPrice)) {
            return altPrice;
        }

        Integer price = service.getPrice();
        if (price != null) {
            return formatCentPrice(price);
        }

        if (StringUtils.isNotBlank(altPrice)) {
            return altPrice;
        }
        return "999.99";
    }

    private String buildManagerPriceLine(Service service) {
        List<String> fields = new ArrayList<String>();
        fields.add(formatCentPrice(service.getPrice()));
        fields.add(formatCentPrice(service.getCost()));
        fields.add(formatIntegerValue(service.getStock()));
        fields.add(formatIntegerValue(service.getWeeklySalesQty()));
        return String.join(" / ", fields);
    }

    private boolean isPartnerOrClient(HttpSession session) {
        if (session == null) {
            return false;
        }
        Object userRole = session.getAttribute(CC.user_role);
        if (userRole == null) {
            return false;
        }
        String role = userRole.toString();
        return role.contains(CC.ROLE_PARTNER) || role.contains(CC.ROLE_CLIENT);
    }

    private String formatCentPrice(Integer priceInCent) {
        if (priceInCent == null) {
            return "";
        }
        return String.format(java.util.Locale.US, "%.2f", priceInCent / 100.0);
    }
    private String formatIntegerValue(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    public List<String> prepareDescriptions(
            String langPrf,
            List<String> imageKeys,
            long serviceAmount,
            Person person) {

        List<String> descriptions = new ArrayList<>();
        HashMap<String, String> map = new HashMap<>();

        List<String> fallbackOrder = new ArrayList<>();
        fallbackOrder.add(langPrf);
        if (!"zh_".equals(langPrf)) fallbackOrder.add("zh_");
        if (!"en_".equals(langPrf)) fallbackOrder.add("en_");
        if (!"fr_".equals(langPrf)) fallbackOrder.add("fr_");

        for (String imageKey : imageKeys) {
            String baseKey = "service_"+imageKey + "_description";
            TextContent content = null;
            for (String langTry : fallbackOrder) {
                content = textContentService.findContentsByKeyAndPerson(langTry + baseKey, person);
                if (content != null) break;
            }
            if (content != null) {
                map.put(imageKey, content.getContent());
            } else {
                map.put(imageKey, ""); // fallback to empty string if all fail
            }
        }

        // Build descriptions in imageKeys order
        for (int i = 0; i < serviceAmount; i++) {
            if (i < imageKeys.size()) {
                descriptions.add(map.getOrDefault(imageKeys.get(i), ""));
            } else {
                descriptions.add(""); // pad missing description
            }
        }

        return descriptions;
    }

    //also used when editing the html_service content.
    public List<String> prepareDescriptions(
            String langPrf,
            String key,
            long serviceAmount,
            Person person) {
        List<String> descriptions = new ArrayList<String>();
        HashMap<String, String> map = new HashMap<String, String>();
        List<TextContent> list = textContentService.findAllMatchedTextContents(langPrf + key + "_%_description", "posInPage", person);
        if(list == null || list.size() == 0) {
        	list = textContentService.findAllMatchedTextContents("en_" + key + "_%_description", "posInPage", person);
        }
        if(list == null || list.size() == 0) {
        	list = textContentService.findAllMatchedTextContents("zh_" + key + "_%_description", "posInPage", person);
        }
        if(list == null || list.size() == 0) {
        	list = textContentService.findAllMatchedTextContents("fr_" + key + "_%_description", "posInPage", person);
        }
        for (TextContent textContent : list) {
            String posInPage = textContent.getPosInPage();
            if (posInPage.endsWith("_description")) {
                posInPage = posInPage.substring(0, posInPage.length() - 12);
                posInPage = posInPage.substring(posInPage.lastIndexOf('_') + 1);
                if(!map.containsKey(posInPage)) {
                	map.put(posInPage, textContent.getContent());
                }else {
                	TaoDebug.error("duplicated key appeared in search reuslt list with key:{}, under person{}", textContent.getPosInPage(), person.getId());
                }
            }else {
            	TaoDebug.error("searched with '_%_description', while found result not end with it:{}", posInPage);
            }
        }
        for (int i = 0; i < serviceAmount; i++) {
            descriptions.add(map.get(String.valueOf(i + 1)));
        }
        return descriptions;
    }

    //also used when editing the html_service content.
    public void prepareAndFillNamesAndTypes(String key, Person person, List<String> formNames,  List<String> formTypes) {
        if(key.startsWith("service_")) {
        	key = key.substring(8);
        }else if(key.startsWith("menu_")) {
        	key = key.substring(5);
        }
        List<Service> list = serviceService.findAllMatchedServices( key + "_%", person);
        for (Service service : list) {
            String formName = service.getDescription();
            if(formName != null && formName.length() > 0) {
            	formNames.add(formName);
            	formTypes.add((service.getName().startsWith("*") || service.getName().endsWith("*"))? formName + "*" : formName);
            }else {
            	formNames.add(service.getName());
            	formTypes.add(service.getName());
            }
        }
    }
    
    private int findIndexOfTheLastSlidPicture(
            HttpServletRequest request,
            Person person,
            String langPrf) {
        int biggestNumber = 0;
        String slide_superBig = TaoUtil.isLangPrfNeededForMedia(request, "slide_superBig") ? langPrf + "slide_superBig"
                : "slide_superBig";
        List<MediaUpload> mediaUploads = mediaUploadService.listAllMediaUploadsByKeyAndPerson(slide_superBig, person);
        if(mediaUploads.size() == 0){
            mediaUploads = mediaUploadService.listAllMediaUploadsByKeyAndPerson(slide_superBig, getEve());
        }
        for (MediaUpload mediaUpload : mediaUploads) {
            String filePath = mediaUpload.getFilepath();
            int index = Integer.valueOf(filePath.substring(filePath.lastIndexOf('_') + 1));

            biggestNumber = biggestNumber > index ? biggestNumber : index;
        }
        return biggestNumber;
    }

    /**
     * to check if a sub menu has sub sub menu.
     * 
     * @NOTE: for database, we use menu_x_y_z as key to get the string displayed on menu out. while in left bar, we can
     *        not use menu_x_y_z directly, because we can not use ${'menu_" + menuIdx + "_y_z" to generate the key
     *        string out. so, for work out: we used subMenu_x_y_z as key of model property instead of key of database to
     *        transfer the string on sub menu and "sub sub menu". and in back end, we can generate the key whatever we
     *        want.
     */
    private int hasSubMenu(
            String pLanguage,
            int pMenuIdx,
            int pSubMenuIdx,
            Person person) {
        String tKey = pLanguage + "menu_" + pMenuIdx + "_";
        if (pSubMenuIdx != 0) {
            tKey = tKey + pSubMenuIdx + "_";
        }

        for (int i = 1; i <= defaultNumOfMenu; i++) {
            TextContent tContent = textContentService.findContentsByKeyAndPerson(tKey + i, person);
            if (tContent != null) {
                return i;
            }
        }
        return 0;
    }

    public void reInitSession(
            HttpSession session,
            Person person) {
        // clean current configuration
        TaoDebug.info(TaoDebug.getSB(session), "start to reInitSession for client : {}", person.getName());
        Object user_role = session.getAttribute(CC.user_role);
        if (session.getAttribute(CC.CLIENT) != null) {
            cleanSessionAttributes(session);
        }
        // Initialise with advPerson's configuration.
        List<Customize> customizes = customizeService.findAllCustomizesByPerson(personService.findPerson(1l));
        for (Customize customize : customizes) {
            session.setAttribute(customize.getCusKey(), customize.getCusValue());
        }
        // overwrite with user's special configuration.
        if (!personService.findPerson(1l).getName().equals(person.getName())) {
            removeSensitiveDemoConfigurations(session);
            customizes = customizeService.findAllCustomizesByPerson(person);

            TaoDebug.info(session, "start to add special attribute from person : {}");
            for (Customize customize : customizes) {
                session.setAttribute(customize.getCusKey(), customize.getCusValue());
            }
        }
        session.setAttribute(CC.CLIENT, person);
        session.setAttribute(CC.user_role, user_role);
        TaoDebug.setDebugFlag(session, (String) session.getAttribute(CC.debugFlag), person);
        // Why do we want to set the session to be never time out??? session.setMaxInactiveInterval(-1);
    }

    private void cleanSessionAttributes(
            HttpSession session) {
        TaoDebug.info(TaoDebug.getSB(session), "start to cleanSessionAttributes");

        Enumeration<String> enumeration = session.getAttributeNames();
        while (enumeration.hasMoreElements())
            session.removeAttribute(enumeration.nextElement());
    }

    private void removeSensitiveDemoConfigurations(
            HttpSession session) {
        TaoDebug.info(TaoDebug.getSB(session), "start to removeSensitiveDemoConfigurations.");
        session.removeAttribute(CC.app_ContentManager);
        session.removeAttribute(CC.app_ManagerEmail);
        session.removeAttribute(CC.app_name);
        session.removeAttribute(CC.app_WebsiteAddress);
        session.removeAttribute(CC.flash_1_URL);
        for (int i = 1; i <= defaultNumOfMenu; i++) {
            session.removeAttribute(CC.MAP_POS_X + i);
            session.removeAttribute("CONTENTTYPE_" + i);
        }
    }

    /**
     * for left menu bar, it doesn't care about which menu nor which language, it always display the 100 value from
     * controller. which have key from leftMenuText_1~leftMenyText_10, and each one has 10 sub
     * leftMenuText_n_1~leftMenuText_n_10. for building the link to url, it need menuIdx which will be set into the
     * model by relevant controller.
     * 
     * @param: pKey
     *             are supposed to be like "menu_[n]_" .
     */
    private void initLeftMenuBar(
            HttpServletRequest request,
            Model model,
            String pKey,
            String pLanguage,
            Person person) {
        TaoDebug.info(TaoDebug.getSB(request.getSession()), "start to initLeftMenuBar, pKey is: {}, pLang is {}", pKey,
                pLanguage);
        List<List<String>> subMenu = prepareMenuContent(pKey, pLanguage, person);
        model.addAttribute("subMenu", subMenu);
        int indexOfSecondDash = pKey.indexOf('_', 5);
        model.addAttribute("topIdx", pKey.substring(0, indexOfSecondDash + 1));
    }

    public List<List<String>> prepareMenuContent(
            String keyPrf,
            String langPrf,
            Person person) {
        List<List<String>> listForReturn = new ArrayList<List<String>>();
        for (int i = 1; i <= defaultNumOfMenu; i++) {
            List<String> subMenus = new ArrayList<String>();
            if (!keyPrf.endsWith("_")) {
                keyPrf = keyPrf + "_";
            }
            String tKey = keyPrf + i;
            TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + tKey, person);
            tContent = tContent == null? textContentService.findContentsByKeyAndPerson(langPrf + tKey, getEve()) : tContent;
            if (tContent != null && !"".equals(tContent.getContent())) {
                subMenus.add(tContent.getContent());
                if (!"".equals(tContent.getContent().trim())) {
                    for (int j = 1; j <= 30; j++) {
                        tContent = textContentService.findContentsByKeyAndPerson(langPrf + tKey + "_" + j, person);
                        if (tContent != null && StringUtils.isNotBlank(tContent.getContent())) {
                            for (int pos = subMenus.size(); pos < j; pos++) {
                                subMenus.add("");
                            }
                            subMenus.add(tContent.getContent());
                        }else{
                            break;
                        }
                    }
                }
            }

            if (subMenus.size() != 0) {
                for (int pos = listForReturn.size() + 1; pos < i; pos++) {
                    listForReturn.add(new ArrayList<String>());
                }
                listForReturn.add(subMenus);
            }
        }
        return listForReturn;
    }

    private void initSubpageContent(
            Model model,
            String key,
            String langPrf,
            Person person) {
        TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + key + "_content", person);
        if (tContent != null)
            model.addAttribute("subpageContent", tContent.getContent());
        else
            model.addAttribute("subpageContent", "");
    }

    public void initTexts(
            Model model,
            String pKey,
            int pNum,
            String langPrf,
            Person person) {
        for (int i = 0; i <= pNum; i++) {
            String tKey = pKey + i;
            TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + tKey, person);
            tContent = (tContent == null && mediaUploadService.findMediaByKeyAndPerson("slide_superBig_1", person) == null)?
                    textContentService.findContentsByKeyAndPerson(langPrf + tKey, getEve()) : tContent;
            if (tContent != null)
                model.addAttribute(tKey, tContent.getContent());
            else
                model.addAttribute(tKey, "");
        }
    }

    public int prepareContents(
            Model model,
            String pKey,
            String LangPrf,
            Person person) {
        List<TextContent> textContents = textContentService.findAllMatchedTextContents(LangPrf + pKey + "_%", "posInPage", person);
        if (textContents != null) {
            String[] contents = new String[20];
            for (TextContent textContent : textContents) {
                String key = textContent.getPosInPage();
                int p = key.lastIndexOf('_');
                int i = Integer.valueOf(key.substring(p + 1));
                contents[i] = textContent.getContent();
            }
            Object[] validAry = vacuumArray(contents);
            if (validAry != null) {
                model.addAttribute(pKey, validAry);
                return validAry.length;
            } else {
                model.addAttribute(pKey, new String[0]);
                return 0;
            }
        }
        return 0;
    }

    public static Object[] vacuumArray(
            Object[] contents) {
        for (int i = contents.length - 1; i >= 0; i--) {
            if (contents[i] != null) {
                Object[] a = new Object[i + 1];
                System.arraycopy(contents, 0, a, 0, a.length);
                return a;
            }
        }
        return null;
    }

    public void initText(
            HttpSession session,
            String pKey,
            int pNum,
            String langPrf,
            Person person) {
        for (int i = 1; i <= pNum; i++) {
            String tKey = pKey + i;
            TextContent tContent = textContentService.findContentsByKeyAndPerson(langPrf + tKey, person);
            if (tContent != null)
                session.setAttribute(tKey, tContent.getContent());
            else
                session.setAttribute(tKey, "");
        }
    }

    public void initTextArray(
            Model model,
            String pKey,
            int pNum,
            String pLanguage,
            Person person) {
        String[] strArray = new String[pNum];
        for (int i = 0; i < pNum; i++) {
            String tKey = pKey + "_" + (i + 1);
            TextContent tContent = textContentService.findContentsByKeyAndPerson(pLanguage + tKey, person);
            tContent = (tContent == null
                    && tKey.contains("text_on_slid")
                    && mediaUploadService.findMediaByKeyAndPerson("slide_superBig_1", person) == null) ?
                    textContentService.findContentsByKeyAndPerson(pLanguage + tKey, getEve()) : tContent;

            if (tContent != null)
                strArray[i] = tContent.getContent();
            else
                strArray[i] = "";
        }
        model.addAttribute(pKey, strArray);
    }

    // will return 1st:current 2nd:get from_app_name 3nd:for_demo
    @SuppressWarnings("deprecation")
	public Person getCurPerson(HttpServletRequest request) {
        Person person = (Person) request.getSession().getAttribute(CC.CLIENT);
        if (person == null) {
        	
            // trying to save it back--we normally have a copy of CC.client's value in app_name.
            // thought some time app_name will be used to save the domain name (when generate QRCode, we use app_name).
        	String app_name = (String)request.getSession().getAttribute(CC.app_name);
            if (app_name != null) {
            	if(app_name.contains(".")) {
            		app_name = app_name.substring(0, app_name.indexOf("."));
            	}
                person = personService.findPersonByName(app_name.toString());
            }
            if(person == null) {
	        	Cookie[] cookies = request.getCookies();
	            if (cookies != null) {
	                for (Cookie c : cookies) {
	                    if (c != null && c.getName().equals("site")) {
                            try {
                                person = personService.findPersonByName(c.getValue());
                                if (person != null) {
                                    request.getSession().setAttribute(CC.CLIENT, person);
                                    reInitSession(request.getSession(), person);
                                }
                            }catch(Exception e){
                                request.getSession().setAttribute(CC.CLIENT, person);
                                reInitSession(request.getSession(), person);
                            }
	                    }
	                }
	            }
            }
            // if the app_name property in session also changed, then have to use adv person :(
            if (person == null) {
                person = personService.findPerson(1l); // This could have caused a issue.
                request.getSession().setAttribute(CC.CLIENT, person);
                reInitSession(request.getSession(), person);
                TaoDebug.warn(TaoDebug.getSB(request.getSession()),
                        "no name found in both CLIENT and app_name, have to get AdvPerson", "");
            }
            request.getSession().setAttribute(CC.CLIENT, person);
        }
        return person;
    }

    public static String fetchProductName(String description,  String[] moneyLetters, HttpSession session) {
        if (StringUtils.isBlank(description)) {
            return "";
        }
        int p = -1;
        for (String moneyLetter : moneyLetters) {
            p = description.indexOf(moneyLetter);
            if (p > -1) {
            	break;
            }
		}
        String html = p > -1 ? description.substring(0, p) : description;
        html =  trimContentFromHTML(html);
        
    	String ignorFlag1 = (String)session.getAttribute(CC.IgnoreFlag1);
    	String ignorFlag2 = (String)session.getAttribute(CC.IgnoreFlag2);
    	if(ignorFlag2 == null || ignorFlag2.length() == 0) {
    		ignorFlag2 = ignorFlag1;
    	}
    	if(ignorFlag1 != null) {
    		p = html.indexOf(ignorFlag1);
	    	while(p >= 0 && html.indexOf(ignorFlag2, p + ignorFlag1.length()) > p) {
	    		html = html.substring(0, p) + html.substring(html.indexOf(ignorFlag2) + ignorFlag2.length());
	    		p = html.indexOf(ignorFlag1);
	    	}
    	}
        return html;
    }

    public static String fetchProductPrice(String description, String[] moneyLetters) {
        
		String html = fetchContentAfterMoneyLetter(description, moneyLetters);
        int p = html.indexOf(" ");		//following the price, there could be categories: beg bow, small bowl... 
        if(p > 0) {
        	html = html.substring(0, p);
        }
        if(html.length() ==0) {
        	html = "0";
        }
        return trimContentFromHTML(html);
    }
    
    public static List<String> fetchCategories(String description, String[] moneyLetters) {

		String html = fetchContentAfterMoneyLetter(description, moneyLetters);
        int p = html.indexOf(" ");		//following the price, there could be categories: beg bow, small bowl... 

        List<String> list = new ArrayList<>();
        if(p > -1) {
	        html = html.substring(p+1);
	        String[] categories = html.split(";");
	        for (String categorie : categories) {
	        	list.add(trimContentFromHTML(categorie));
			}
        }
        return list;
    }
    
	private static String fetchContentAfterMoneyLetter(String description, String[] moneyLetters) {
		if (StringUtils.isBlank(description)) {
            return "";
        }
        int p = -1;
        for (String moneyLetter : moneyLetters) {
            p = description.lastIndexOf(moneyLetter);
            if (p > -1) {
            	break;
            }
		}
        
        String html = p > -1 ? description.substring(p + 1) : "";
		return html.trim();
	}

    // return the trimed content from html format text.
    public static String trimContentFromHTML(
            String html) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int start = html.indexOf("<");
            int end = html.indexOf(">");
            if (start < 0) {
                break;
            } else if (end > start) {
                sb.append(html.substring(0, start));
                html = html.substring(end + 1);
            }
        }

        html = sb.toString() + html;
        while (html.startsWith("&nbsp;")) {
            html = html.substring(6).trim();
        }
        while (html.endsWith("&nbsp;")) {
            html = html.substring(0, html.length() - 6).trim();
        }

        return html.trim();
    }

    public static String formateDate(
            HttpServletRequest request,
            Date date) {
        if (date == null) {
            return null;
        }
        Object dateFormate = request.getSession().getAttribute("DateFormat");
        String format = dateFormate != null ? dateFormate.toString() : "hh:mm";
        SimpleDateFormat df5 = new SimpleDateFormat(format, Locale.getDefault());
        return df5.format(date);
    }

    public static String cleanUpTel(
            String tel) {
        tel = StringUtils.remove(tel, " ");
        tel = StringUtils.remove(tel, "(");
        tel = StringUtils.remove(tel, ")");
        tel = StringUtils.remove(tel, "-");
        tel = StringUtils.remove(tel, ".");
        tel = StringUtils.remove(tel, ",");

        tel = StringUtils.remove(tel, " ");
        tel = StringUtils.remove(tel, "\uFF08");
        tel = StringUtils.remove(tel, "\uFF09");
        tel = StringUtils.remove(tel, "\u2014");
        tel = StringUtils.remove(tel, "\u3002");
        tel = StringUtils.remove(tel, ",");
        return tel;
    }

	public String getNoteJson(HttpServletRequest request, String key) {
		HttpSession session = request.getSession();
		Person person = getCurPerson(request);
		Service service = serviceService.findServiceByCatalogAndPerson(key, person);
		
		//if it's for manager or above, then return all available notes including public (which defined by for_demo)
		//and private, which defined by this person. in another word, all note will be availabe note, waiting for manager to choose into service as final available note to customer.
		if(TaoUtil.isAboveManager(request.getSession())) {
			initWithAllNotes(getLangPrfWithDefault(request), session, service);
		}else {	//if it's not for manager, but for normal customer, then return the notes for only this service.
			StringBuilder allAvailableNotes = new StringBuilder();
			if(service.getC2() != null) {
				String allNotes = (String) session.getAttribute(getLangPrfWithDefault(request) + CC.notes);
				if(allNotes == null) {
					allNotes = "";
				}else {
                    allNotes = allNotes.replace('\uFF1B', ';').trim();
					if(!allNotes.endsWith(";")) {
						allNotes += ";";
					}
				}
				String[] notesStrAry = allNotes.split(";");
				
				String[] noteIdxs = service.getC2().split(";"); //c2 saves only the index, because need to distinguish language.
				for(String idx : noteIdxs) {
					try {		//some time, the c2 might be like "null;null;1;4;6;", so need to ignore the case when TODO: remove this check when bug fixed.
						int i = Integer.valueOf(idx);
						if(i < notesStrAry.length) {
							allAvailableNotes.append(notesStrAry[i]);
							allAvailableNotes.append(";");
						}
					}catch(Exception e) {}
				}
			}
			service.setC2(allAvailableNotes.toString());	//temperally replace the c2 with language string for front end to display.
			
			//prepare c3
			List<List<Integer>> selectedNote = (List<List<Integer>>) request.getSession().getAttribute(CC.selectedNotes);
			if(selectedNote == null) {
				selectedNote = new ArrayList<List<Integer>>();
			}
			//fetch out the selected note status of the service item which matching to the parameter: keyStr
			List<Integer> selectedNotesOfCurService = getSelectedNotesOfCurService(request.getSession(), selectedNote, key);
			StringBuilder visibleNoteStr = new StringBuilder();
			for (int i = 0; i < selectedNotesOfCurService.size(); i++) { //from 0 to allNotes.length - 1, if it appear in c2 string (not the temperal C2, but the content in sb) 
				visibleNoteStr.append(selectedNotesOfCurService.get(i)).append(";");
			}
			service.setC3(visibleNoteStr.toString());
		}

		session.setAttribute("curObj",key);
		return service.toJson();
	}

	private static void initWithAllNotes(String langPrf, HttpSession session, Service service) {
		String noteOfTheItems = (String) session.getAttribute(langPrf + CC.notes);
		if(noteOfTheItems == null) {
			noteOfTheItems = "";
		}else {
            noteOfTheItems = noteOfTheItems.replace('\uFF1B', ';').trim();
			if(!noteOfTheItems.endsWith(";")) {
				noteOfTheItems += ";";
			}
		}
		String[] notesAry = noteOfTheItems.split(";");
		
		String[] selectedNoteList = new String[notesAry.length];
		
		session.setAttribute("availableNotes",notesAry);
		session.setAttribute("visibleNoteList",selectedNoteList);
		
		if(service != null) {
			String selectedItemStr = service.getC2();
			if(selectedItemStr != null && selectedItemStr.length() > 1) {
				String[] selectedItems = selectedItemStr.split(";");
				for (String string : selectedItems) {
					try {
						Integer idx = Integer.valueOf(string);
						selectedNoteList[idx] = "1";
					} catch (Exception e) {}
				}
			}
			
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < selectedNoteList.length; i++) {
				sb.append(selectedNoteList[i]);
				sb.append(";");
			}
			
			service.setC2(noteOfTheItems);
			service.setC3(sb.toString());
		}
	}

	//find out the place where to add /n base on the width setting and character is western or Chinese....
	public static String addingEnterIntoContent(String content, String width) {
		if(width == null || width.trim().length() == 0) {
			width = "38";
		}else {
			int p = width.indexOf(",");
			width = width.substring(p + 1);
		}
		int w = Integer.valueOf(width);
		
		int curW = 0;
		List<Integer> positions = new ArrayList<>();
		for(int i = 0; i < content.length(); i++) {
			//put the width check fist, so only when the length reach the width, and there are more character, add the /n.
			if(curW == w) {	
				int pos = latestChineseOrSpace(content, i);
				positions.add(pos);//put the /n in last space or chinese character.....
				curW = 0;
			}else if(curW > w){//this is the case that when last character is Chinese.
				positions.add(Integer.valueOf(i - 1));
				curW = 0;
			}
			
			if(isChinese(content.charAt(i))) {
				curW++;
				curW++;
			}else {
				curW++;
			}
		}
		for(int i = positions.size()-1; i > -1; i--) {
			int pos = positions.get(i);
			content = content.substring(0, pos) + "\n" + content.substring(pos);
		}
		
		return content;
	}

	private static int latestChineseOrSpace(String content, int lastPos) {
		int i = lastPos;
		for(; i > 0; i--) {
			char c = content.charAt(i-1);
			if(c == ' ' || isChinese(c)) {
				return i;
			}
		}
		return lastPos;
	}

	public static boolean isChinese(char word) {
		return  (Character.UnicodeBlock.of(word) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS);
	}

	public static List<Integer> initAndReturnSelectedNote(List<List<Integer>> selectedNotes, int idx) {
		for(int i = 0; i <= idx; i++) {
			if(i == selectedNotes.size()) {
				List<Integer> list = new ArrayList<Integer>();
				selectedNotes.add(list);
			}
		}
		return selectedNotes.get(idx);
	}
    public static String getCleanContent(String content) {
        content = content.trim();
        if (content.startsWith("\"")) {
            content = content.substring(1);
        }
        if (content.endsWith("\"")) {
            content = content.substring(0, content.length() - 1);
        }
        return content;
    }

    public String getMenuIdxFromImageKey(String imageKey) {
        if (imageKey == null) return "";
        if (imageKey.startsWith("service_")) {
            imageKey = imageKey.substring(8); // strip service_ prefix
        }
        int p = imageKey.lastIndexOf('_');
        if (p == -1) return imageKey;
        String menuIdx = imageKey.substring(0, p);
        // Remove up to two trailing _0 segments
        if (menuIdx.endsWith("_0")) {
            menuIdx = menuIdx.substring(0, menuIdx.length() - 2);
            if (menuIdx.endsWith("_0")) {
                menuIdx = menuIdx.substring(0, menuIdx.length() - 2);
            }
        }
        return menuIdx;
    }

}
