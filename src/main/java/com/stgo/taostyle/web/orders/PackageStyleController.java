package com.stgo.taostyle.web.orders;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.PackageStyle;


import com.stgo.taostyle.service.MainOrderService;
import com.stgo.taostyle.service.PackageStyleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.WebUtils;

@RequestMapping("/packagestyles")
@Controller
public class PackageStyleController {

    @Autowired
    private MainOrderService mainOrderService;
    @Autowired
    private PackageStyleService packageStyleService;

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
            uiModel.addAttribute("packagestyles",
                    packageStyleService.findPackageStyleEntriesByMainOrder(mainOrderId, firstResult, sizeNo));
            float nrOfPages = (float) packageStyleService.countPackageStyles() / sizeNo;
            uiModel.addAttribute("maxPages", (int) ((nrOfPages > (int) nrOfPages || nrOfPages == 0.0) ? nrOfPages + 1
                    : nrOfPages));
        } else {
            uiModel.addAttribute("packagestyles", packageStyleService.findAllPackageStylesByMainOrder(mainOrderId));
        }
        return "packagestyles/list";
    }

    @RequestMapping(params = "form", produces = "text/html")
    public String createForm(
            Model uiModel,
            HttpServletRequest request) {
        populateEditForm(uiModel, new PackageStyle());

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
        return "packagestyles/create";
    }

	@RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(@Valid PackageStyle packageStyle, BindingResult bindingResult, Model uiModel, HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, packageStyle);
            return "packagestyles/create";
        }
        uiModel.asMap().clear();
        packageStyleService.persist(packageStyle);
        return "redirect:/packagestyles/" + encodeUrlPathSegment(packageStyle.getId().toString(), httpServletRequest);
    }

	@RequestMapping(value = "/{id}", produces = "text/html")
    public String show(@PathVariable("id") Long id, Model uiModel) {
        uiModel.addAttribute("packagestyle", packageStyleService.findPackageStyle(id));
        uiModel.addAttribute("itemId", id);
        return "packagestyles/show";
    }

	@RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(@Valid PackageStyle packageStyle, BindingResult bindingResult, Model uiModel, HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, packageStyle);
            return "packagestyles/update";
        }
        uiModel.asMap().clear();
        packageStyleService.merge(packageStyle);
        return "redirect:/packagestyles/" + encodeUrlPathSegment(packageStyle.getId().toString(), httpServletRequest);
    }

	@RequestMapping(value = "/{id}", params = "form", produces = "text/html")
    public String updateForm(@PathVariable("id") Long id, Model uiModel) {
        populateEditForm(uiModel, packageStyleService.findPackageStyle(id));
        return "packagestyles/update";
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "text/html")
    public String delete(@PathVariable("id") Long id, @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size, Model uiModel) {
        PackageStyle packageStyle = packageStyleService.findPackageStyle(id);
        packageStyleService.remove(packageStyle);
        uiModel.asMap().clear();
        uiModel.addAttribute("page", (page == null) ? "1" : page.toString());
        uiModel.addAttribute("size", (size == null) ? "10" : size.toString());
        return "redirect:/packagestyles";
    }

	void populateEditForm(Model uiModel, PackageStyle packageStyle) {
        uiModel.addAttribute("packageStyle", packageStyle);
        uiModel.addAttribute("mainorders", mainOrderService.findAllMainOrders());
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
