package com.stgo.taostyle.web;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;


import com.stgo.taostyle.service.CustomizeService;
import com.stgo.taostyle.service.FeatureService;
import com.stgo.taostyle.service.ServiceService;
import com.stgo.taostyle.service.TextContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.stgo.taostyle.domain.Customize;
import com.stgo.taostyle.domain.Feature;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.Service;
import com.stgo.taostyle.domain.TextContent;

@RequestMapping("/feature")
@Controller
public class FeatureController {

    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private CustomizeService customizeService;
    @Autowired
    private TextContentService textContentService;
    @Autowired
    private ServiceService serviceService;
    @Autowired
    private FeatureService featureService;

    @RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(
            HttpServletRequest request) {
        String langPrf = taoUtil.getLangPrfWithDefault(request);
        //generate featured------------------------------------
        Feature feature = new Feature();
        Person person = taoUtil.getCurPerson(request);
        feature.setPerson(person);
        String menuIdx = request.getSession().getAttribute(CC.menuIdx).toString();
        feature.setMenuIdx(menuIdx);
        String refMenuIdx = request.getParameter("refMenuIdx");
        feature.setRefMenuIdx(refMenuIdx);
        //generate group title----------------------------
        StringBuilder posInPage = new StringBuilder(langPrf);
        int[] refMenuIdxes = taoUtil.splitMenuIdxToAry(refMenuIdx);
        String refMenu = taoUtil.completeMenuIdx(refMenuIdxes[0], refMenuIdxes[1], refMenuIdxes[2], posInPage.toString(), person);
        posInPage.append("service_").append(refMenu).append("_title");
        TextContent serviceTitle = textContentService.findContentsByKeyAndPerson(posInPage.toString(), person);

        int[] menuIdxes = taoUtil.splitMenuIdxToAry(menuIdx);
        String menu = taoUtil.completeMenuIdx(menuIdxes[0], menuIdxes[1], menuIdxes[2], posInPage.toString(), person);
        String keyForGroupTitle = langPrf + "feature_" + menu + "-" + refMenuIdx; //user menu + "-" + refMenu as the featured title's key.
        TextContent groupTitle = textContentService.findContentsByKeyAndPerson(keyForGroupTitle + "_groupTitle", person);
        if (groupTitle == null) {
            groupTitle = new TextContent();
            groupTitle.setPerson(person);
            groupTitle.setPosInPage(keyForGroupTitle + "_groupTitle");
            groupTitle.setContent(serviceTitle == null ? "" : serviceTitle.getContent());
            textContentService.persist(groupTitle);
        }

        List<String> mediaKeys = serviceService.listAllServiceKeyByKeyAndPerson(refMenuIdx, person);
        StringBuilder sb = new StringBuilder();
        
        String[] keys = new String[200];

        for (String key : mediaKeys) {
        	int p = key.lastIndexOf("_");
        	int a = Integer.valueOf(key.substring(p + 1));
        	keys[a] = key;
        }
        for (int i = 0; i < 200; i++) {
        	if(keys[i] != null) {
	            sb.append(keys[i]);
	            sb.append(',');
        	}
        }
        feature.setItemsToShow(sb.toString());

        featureService.persist(feature);
        return "redirect:/menu_" + menuIdx;
    }

    @RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(
            @Valid
            Customize customize,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, customize);
            return "customizes/update";
        }
        uiModel.asMap().clear();
        Person person = taoUtil.getCurPerson(request);
        customize.setPerson(person);
        customizeService.merge(customize);
        request.getSession().setAttribute(customize.getCusKey(), customize.getCusValue());
        return "redirect:/menu_" + request.getSession().getAttribute(CC.menuIdx);
    }

    void populateEditForm(
            Model uiModel,
            Customize customize) {
        uiModel.addAttribute("customize", customize);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "text/html")
    public String delete(
            @PathVariable("id")
            Long id,
            @RequestParam(value = "page", required = false)
            Integer page,
            @RequestParam(value = "size", required = false)
            Integer size,
            Model uiModel,
            HttpServletRequest request) {
        Customize customize = customizeService.findCustomize(id);
        customizeService.remove(customize);
        request.getSession().removeAttribute(customize.getCusKey());
        uiModel.asMap().clear();
        return "redirect:/customizes";
    }
}


