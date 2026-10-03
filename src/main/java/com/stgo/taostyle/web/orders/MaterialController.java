package com.stgo.taostyle.web.orders;

import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.Material;
import com.stgo.taostyle.service.MainOrderService;
import com.stgo.taostyle.service.MaterialService;
import com.stgo.taostyle.service.TextContentService;
import com.stgo.taostyle.web.CC;
import com.stgo.taostyle.web.TaoUtil;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@RequestMapping("/materials")
@Controller

public class MaterialController {

    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private TextContentService textContentService;
    @Autowired
    private MaterialService materialService;
    @Autowired
    private MainOrderService mainOrderService;

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
            uiModel.addAttribute("materials",
                    materialService.findMaterialEntriesByMainOrderId(mainOrderId, firstResult, sizeNo));
            float nrOfPages = (float) materialService.countMaterials() / sizeNo;
            uiModel.addAttribute("maxPages", (int) ((nrOfPages > (int) nrOfPages || nrOfPages == 0.0) ? nrOfPages + 1
                    : nrOfPages));
        } else {
            uiModel.addAttribute("materials", materialService.findAllMaterialsByMainOrderId(mainOrderId));
        }
        return "materials/list";
    }

    @RequestMapping(params = "form", produces = "text/html")
    public String createForm(
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        populateEditForm(uiModel, person, new Material());

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
        return "materials/create";
    }

    void populateEditForm(
            Model model,
            Person person,
            Material material) {
        model.addAttribute("material", material);

        model.addAttribute("mainorders", mainOrderService.findMainOrdersByStatusLimitationAndPerson(CC.STATUS_PRINTED,  person,  "asc"));
    }

    @RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(
            @Valid
            Material material,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest request) {
        Material tMaterial = materialService.findMaterial(material.getId());
        MainOrder oldMainOrder = tMaterial.getMainOrder();
        int oldQty = tMaterial.getQuantity();
        int oldEndSubTota = tMaterial.getEndSubTotal();

        uiModel.asMap().clear();
        Long mainOrderId = Long.valueOf(request.getParameter("mainOrder"));
        if(material.getSubTotalInYuan() != null && !"0.00".equals(material.getSubTotalInYuan())){
            tMaterial.setEndSubTotal((int)(Double.parseDouble(material.getSubTotalInYuan()) * 100));
        }
        if(material.getQuantity() != oldQty) {
            tMaterial.setQuantity(material.getQuantity());
            if(material.getSubTotalInYuan() == null || "0.00".equals(material.getSubTotalInYuan()) || (int)(Double.parseDouble(material.getSubTotalInYuan()) * 100) == oldEndSubTota) {
                tMaterial.setEndSubTotal((int)(oldEndSubTota * material.getQuantity() / oldQty));
            }
        }
        tMaterial.setRemark(material.getRemark());
        MainOrder newMainOrder = mainOrderService.findMainOrder(mainOrderId);
        tMaterial.setMainOrder(newMainOrder);   //这货必须最后赋值，因为但凡你赋值个其他属性，就会发现这个属性被清空了。尚不知为啥，难道是因为这个属性是对象？还是因为这个属性
        materialService.persist(tMaterial);

        updateMainOrder(newMainOrder, request.getSession());
        if (!newMainOrder.getId().equals(oldMainOrder.getId())) {
            updateMainOrder(oldMainOrder, request.getSession());
        }
        return "redirect:/showDetailOrder/"
                + encodeUrlPathSegment(tMaterial.getMainOrder().getId().toString(), request);
    }

    private void updateMainOrder(MainOrder mainOrder, HttpSession session) {
        List<Material> materials = materialService.findAllMaterialsByMainOrder(mainOrder);

        double total = 0.0;
        for (Material m : materials) {
            total += m.getEndSubTotal();
        }
        mainOrder.setPayCondition("$" +String.format("%.2f", total/100.0));
        mainOrder.setTotal_after_tax("$" +String.format("%.2f",
                total * (1 +  (Float.valueOf((String) session.getAttribute("tax_rate")) / 100))/100.0));

        mainOrderService.persist(mainOrder);
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
            HttpServletRequest httpServletRequest) {
        Material material = materialService.findMaterial(id);
        MainOrder mainOrder = material.getMainOrder();
        String strFR =
                "redirect:/showDetailOrder/" + encodeUrlPathSegment(mainOrder.getId().toString(), httpServletRequest);
        materialService.remove(material);

        // check if there's no material left.
        List<Material> materials = materialService.findAllMaterialsByMainOrder(mainOrder);
        if (materials == null || materials.size() == 0) {
            mainOrderService.remove(mainOrder);
            return "redirect:/dashboard";
        } else {
            int totalPrice = 0;
            for (Material dish : materials) {
                totalPrice += dish.getEndSubTotal();
            }
            mainOrder.setPayCondition(mainOrder.getPayCondition().substring(0, 1) + totalPrice);
            mainOrderService.persist(mainOrder);
            uiModel.asMap().clear();
            return strFR;
        }
    }

    @RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(
            @Valid
            Material material,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest httpServletRequest) {
        Person person = taoUtil.getCurPerson(httpServletRequest);
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, person, material);
            // return "materials/create";
            return "materials/update";
        }
        uiModel.asMap().clear();
        // material.persist();
        materialService.merge(material);
        // return "redirect:/materials/" + encodeUrlPathSegment(material.getId().toString(), request);
        return "redirect:/mainorders/"
                + encodeUrlPathSegment(material.getMainOrder().getId().toString(), httpServletRequest);
    }

    @RequestMapping(value = "/{id}", params = "form", produces = "text/html")
    public String updateForm(
            @PathVariable("id")
            Long id,
            Model model,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        populateEditForm(model, person, materialService.findMaterial(id));

        String langPrf = taoUtil.getLangPrfWithDefault(request);
        List<String> quick_notes =
                textContentService.findAllMatchedContent(langPrf + "quick_note_%", "content", taoUtil.getCurPerson(request));
        model.addAttribute("quick_note", quick_notes);

        return "materials/update";
    }

	@RequestMapping(value = "/{id}", produces = "text/html")
    public String show(@PathVariable("id") Long id, Model uiModel) {
        uiModel.addAttribute("material", materialService.findMaterial(id));
        uiModel.addAttribute("itemId", id);
        return "materials/show";
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
