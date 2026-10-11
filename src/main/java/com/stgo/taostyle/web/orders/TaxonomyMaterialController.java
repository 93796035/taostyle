package com.stgo.taostyle.web.orders;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import com.stgo.taostyle.service.MainOrderService;
import com.stgo.taostyle.service.TaxonomyMaterialService;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.WebUtils;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.TaxonomyMaterial;

@RequestMapping("/taxonomymaterials")
@Controller
public class TaxonomyMaterialController {

    @Autowired
    private MainOrderService mainOrderService;
    @Autowired
    private TaxonomyMaterialService taxonomyMaterialService;

    @RequestMapping(produces = "text/html")
    public String list(
            @RequestParam(value = "page", required = false)
            Integer page,
            @RequestParam(value = "size", required = false)
            Integer size,
            Model uiModel,
            HttpServletRequest request) {
        Long mainOrderId = Long.valueOf(request.getParameter("mainOrder"));
        uiModel.addAttribute("mainOrderId", mainOrderId);

        if (page != null || size != null) {
            int sizeNo = size == null ? 10 : size.intValue();
            final int firstResult = page == null ? 0 : (page.intValue() - 1) * sizeNo;
            uiModel.addAttribute("taxonomymaterials",
                    taxonomyMaterialService.findTaxonomyMaterialEntriesByMainOrder(mainOrderId, firstResult, sizeNo));
            float nrOfPages = (float) taxonomyMaterialService.countTaxonomyMaterials() / sizeNo;
            uiModel.addAttribute("maxPages", (int) ((nrOfPages > (int) nrOfPages || nrOfPages == 0.0) ? nrOfPages + 1
                    : nrOfPages));
        } else {
            uiModel.addAttribute("taxonomymaterials",
                    taxonomyMaterialService.findAllTaxonomyMaterialsByMainOrderID(mainOrderId));
        }
        return "taxonomymaterials/list";
    }

    @RequestMapping(params = "form", produces = "text/html")
    public String createForm(
            Model uiModel,
            HttpServletRequest request) {
        populateEditForm(uiModel, new TaxonomyMaterial());

        // because it's for small use, so do this way for now which wasted a findAll query.
        if (request.getParameter("mainOrderId") != null) {
            MainOrder mainOrder = mainOrderService.findMainOrder(Long.valueOf(request.getParameter("mainOrderId")));
            ArrayList<MainOrder> mainOrders = new ArrayList<MainOrder>();
            if (mainOrder != null)
                mainOrders.add(mainOrder);
            uiModel.addAttribute("mainorders", mainOrders);
        }

        List<String[]> dependencies = new ArrayList<String[]>();
        if (mainOrderService.countMainOrders() == 0) {
            dependencies.add(new String[] { "mainorder", "mainorders" });
        }
        uiModel.addAttribute("dependencies", dependencies);
        return "taxonomymaterials/create";
    }

    void populateEditForm(
            Model uiModel,
            TaxonomyMaterial taxonomyMaterial) {
        uiModel.addAttribute("taxonomyMaterial", taxonomyMaterial);
        uiModel.addAttribute("mainorders", mainOrderService.findAllMainOrders());
    }

	@RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(@Valid TaxonomyMaterial taxonomyMaterial, BindingResult bindingResult, Model uiModel, HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, taxonomyMaterial);
            return "taxonomymaterials/create";
        }
        uiModel.asMap().clear();
        taxonomyMaterialService.persist(taxonomyMaterial);
        return "redirect:/taxonomymaterials/" + encodeUrlPathSegment(taxonomyMaterial.getId().toString(), httpServletRequest);
    }

	@RequestMapping(value = "/{id}", produces = "text/html")
    public String show(@PathVariable("id") Long id, Model uiModel) {
        addDateTimeFormatPatterns(uiModel);
        uiModel.addAttribute("taxonomymaterial", taxonomyMaterialService.findTaxonomyMaterial(id));
        uiModel.addAttribute("itemId", id);
        return "taxonomymaterials/show";
    }

	@RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(@Valid TaxonomyMaterial taxonomyMaterial, BindingResult bindingResult, Model uiModel, HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, taxonomyMaterial);
            return "taxonomymaterials/update";
        }
        uiModel.asMap().clear();
        taxonomyMaterialService.merge(taxonomyMaterial);
        return "redirect:/taxonomymaterials/" + encodeUrlPathSegment(taxonomyMaterial.getId().toString(), httpServletRequest);
    }

	@RequestMapping(value = "/{id}", params = "form", produces = "text/html")
    public String updateForm(@PathVariable("id") Long id, Model uiModel) {
        populateEditForm(uiModel, taxonomyMaterialService.findTaxonomyMaterial(id));
        return "taxonomymaterials/update";
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "text/html")
    public String delete(@PathVariable("id") Long id, @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size, Model uiModel) {
        TaxonomyMaterial taxonomyMaterial = taxonomyMaterialService.findTaxonomyMaterial(id);
        taxonomyMaterialService.remove(taxonomyMaterial);
        uiModel.asMap().clear();
        uiModel.addAttribute("page", (page == null) ? "1" : page.toString());
        uiModel.addAttribute("size", (size == null) ? "10" : size.toString());
        return "redirect:/taxonomymaterials";
    }

	void addDateTimeFormatPatterns(Model uiModel) {
        uiModel.addAttribute("taxonomyMaterial_logtime_date_format", DateTimeFormat.patternForStyle("M-", LocaleContextHolder.getLocale()));
    }

	String encodeUrlPathSegment(String pathSegment, HttpServletRequest httpServletRequest) {
        String enc = httpServletRequest.getCharacterEncoding();
        if (enc == null) {
            enc = WebUtils.DEFAULT_CHARACTER_ENCODING;
        }
        try {
            pathSegment = UriUtils.encodePathSegment(pathSegment, enc);
        } catch (UnsupportedEncodingException uee) {}
        return pathSegment;
    }
}
