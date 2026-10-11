package com.stgo.taostyle.web.orders;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import com.stgo.taostyle.service.MainOrderService;
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
import com.stgo.taostyle.domain.orders.ShipmentStyle;

@RequestMapping("/shipmentstyles")
@Controller
public class ShipmentStyleController {

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
//        Long mainOrderId = Long.valueOf(request.getParameter("mainOrder"));
//        uiModel.addAttribute("mainOrderId", mainOrderId);
//
//        if (page != null || size != null) {
//            int sizeNo = size == null ? 10 : size.intValue();
//            final int firstResult = page == null ? 0 : (page.intValue() - 1) * sizeNo;
//            List<ShipmentStyle> tShipmentStyles =
//                    ShipmentStyle.findShipmentStyleEntriesByMainOrder(mainOrderId, firstResult, sizeNo);
//            beautifyTheShipmentStyles(tShipmentStyles);
//            uiModel.addAttribute("shipmentstyles", tShipmentStyles);
//            float nrOfPages = (float) ShipmentStyle.countShipmentStyles() / sizeNo;
//            uiModel.addAttribute("maxPages", (int) ((nrOfPages > (int) nrOfPages || nrOfPages == 0.0) ? nrOfPages + 1
//                    : nrOfPages));
//        } else {
//            List<ShipmentStyle> tShipmentStyles = ShipmentStyle.findAllShipmentStylesByMainOrder(mainOrderId);
//            beautifyTheShipmentStyles(tShipmentStyles);
//            uiModel.addAttribute("shipmentstyles", tShipmentStyles);
//        }
//        // addDateTimeFormatPatterns(uiModel);
        return "shipmentstyles/list";
    }

    private void beautifyTheShipmentStyles(
            List<ShipmentStyle> tShipmentStyles) {
        for (int i = tShipmentStyles.size() - 1; i >= 0; i--) {
            tShipmentStyles.get(i).setMainOrderStr(tShipmentStyles.get(i).getMainOrder().getClientSideOrderNumber());
        }
    }

    @RequestMapping(params = "form", produces = "text/html")
    public String createForm(
            Model uiModel,
            HttpServletRequest request) {
        ShipmentStyle tShipmentStyle = new ShipmentStyle();
        populateEditForm(uiModel, tShipmentStyle);

        // because it's for small use, so do this way for now which wasted a findAll query.
        if (request.getParameter("mainOrderId") != null) {
            MainOrder mainOrder = mainOrderService.findMainOrder(Long.valueOf(request.getParameter("mainOrderId")));
            tShipmentStyle.setMainOrderStr(mainOrder.getClientSideOrderNumber());
        }

        List<String[]> dependencies = new ArrayList<String[]>();
        if (mainOrderService.countMainOrders() == 0) {
            dependencies.add(new String[] { "mainorder", "mainorders" });
        }
        uiModel.addAttribute("dependencies", dependencies);
        return "shipmentstyles/create";
    }

    @RequestMapping(value = "/{id}", produces = "text/html")
    public String show(
            @PathVariable("id")
            Long id,
            Model uiModel) {
//        ShipmentStyle shipmentStyle = ShipmentStyle.findShipmentStyle(id);
//        uiModel.addAttribute("shipmentstyle", shipmentStyle);
//        uiModel.addAttribute("itemId", id);
//        MainOrder tMainOrder = shipmentStyle.getMainOrder();
//        shipmentStyle.setMainOrderStr(tMainOrder.getClientSideOrderNumber());
//
//        Date onboardDate = shipmentStyle.getOnboardDate();
//        if (onboardDate != null && onboardDate.getYear() > 0) {
//            StringBuilder tSB =
//                    new StringBuilder((onboardDate.getMonth() + 1) + "/").append(onboardDate.getDate()).append("/")
//                            .append(onboardDate.getYear() + 1900);
//            uiModel.addAttribute("onboardDate", tSB.toString());
//        } else
//            uiModel.addAttribute("onboardDate", "");
//
//        Date outdoorDate = shipmentStyle.getOutdoorDate();
//        if (outdoorDate != null && outdoorDate.getYear() > 0) {
//            StringBuilder tSB =
//                    new StringBuilder((outdoorDate.getMonth() + 1) + "/").append(outdoorDate.getDate()).append("/")
//                            .append(outdoorDate.getYear() + 1900);
//            uiModel.addAttribute("outdoorDate", tSB.toString());
//        } else
//            uiModel.addAttribute("outdoorDate", "");

        return "shipmentstyles/show";
    }

    void populateEditForm(
            Model uiModel,
            ShipmentStyle shipmentStyle) {
        uiModel.addAttribute("shipmentStyle", shipmentStyle);

        Date outdoorDate = shipmentStyle.getOutdoorDate();
        if (outdoorDate != null && outdoorDate.getYear() > 0) {
            StringBuilder tSB =
                    new StringBuilder((outdoorDate.getMonth() + 1) + "/").append(outdoorDate.getDate()).append("/")
                            .append(outdoorDate.getYear() + 1900);
            uiModel.addAttribute("outdoorDate", tSB.toString());
        } else
            uiModel.addAttribute("outdoorDate", "");

        Date onboardDate = shipmentStyle.getOnboardDate();
        if (onboardDate != null && onboardDate.getYear() > 0) {
            StringBuilder tSB =
                    new StringBuilder((onboardDate.getMonth() + 1) + "/").append(onboardDate.getDate()).append("/")
                            .append(onboardDate.getYear() + 1900);
            uiModel.addAttribute("onboardDate", tSB.toString());
        } else
            uiModel.addAttribute("onboardDate", "");
    }

    @RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(
            @Valid
            ShipmentStyle shipmentStyle,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest httpServletRequest) {
        if (bindingResult.getErrorCount() > 3) {
            return continiuWork(shipmentStyle, uiModel, "update");
        } else {
            uiModel.asMap().clear();
            String outdoorDate = httpServletRequest.getParameter("outdoorDate");
            String[] tAry = outdoorDate.split("/");
            if (outdoorDate.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(shipmentStyle, uiModel, "update");
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    shipmentStyle.setOutdoorDate(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(shipmentStyle, uiModel, "update");
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    shipmentStyle.setOutdoorDate(tCalendar.getTime());
                }
            } else {
                return continiuWork(shipmentStyle, uiModel, "update");
            }

            String onboardDate = httpServletRequest.getParameter("onboardDate");
            tAry = onboardDate.split("/");
            if (onboardDate.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(shipmentStyle, uiModel, "update");
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    shipmentStyle.setOnboardDate(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(shipmentStyle, uiModel, "update");
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    shipmentStyle.setOnboardDate(tCalendar.getTime());
                }
            } else {
                return continiuWork(shipmentStyle, uiModel, "update");
            }
            shipmentStyle.merge();
            return "redirect:/shipmentstyles/" + shipmentStyle.getId().toString();
        }
    }

    private String continiuWork(
            ShipmentStyle shipmentStyle,
            Model uiModel,
            String pReturnPath) {
        populateEditForm(uiModel, shipmentStyle);
        return "shipmentstyles/" + pReturnPath;
    }

    @RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(
            @Valid
            ShipmentStyle shipmentStyle,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest httpServletRequest) {
        if (bindingResult.getErrorCount() > 3) {
            return continiuWork(shipmentStyle, uiModel, "create");
        } else {
            uiModel.asMap().clear();
            String clientSideOrderNumber = httpServletRequest.getParameter("mainOrderStr");
            shipmentStyle.setMainOrder(mainOrderService.findMainOrderByCON(clientSideOrderNumber).get(0));

            String outdoorDate = httpServletRequest.getParameter("outdoorDate");
            String[] tAry = outdoorDate.split("/");
            if (outdoorDate.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(shipmentStyle, uiModel, "create");
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    shipmentStyle.setOutdoorDate(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(shipmentStyle, uiModel, "create");
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    shipmentStyle.setOutdoorDate(tCalendar.getTime());
                }
            } else {
                return continiuWork(shipmentStyle, uiModel, "create");
            }

            String onboardDate = httpServletRequest.getParameter("onboardDate");
            tAry = onboardDate.split("/");
            if (onboardDate.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(shipmentStyle, uiModel, "create");
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    shipmentStyle.setOnboardDate(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(shipmentStyle, uiModel, "create");
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    shipmentStyle.setOnboardDate(tCalendar.getTime());
                }
            } else {
                return continiuWork(shipmentStyle, uiModel, "create");
            }
            shipmentStyle.persist();
            return "redirect:/shipmentstyles/" + shipmentStyle.getId().toString();
        }
    }

	@RequestMapping(value = "/{id}", params = "form", produces = "text/html")
    public String updateForm(@PathVariable("id") Long id, Model uiModel) {
//        populateEditForm(uiModel, ShipmentStyle.findShipmentStyle(id));
        return "shipmentstyles/update";
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "text/html")
    public String delete(@PathVariable("id") Long id, @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size, Model uiModel) {
//        ShipmentStyle shipmentStyle = ShipmentStyle.findShipmentStyle(id);
//        shipmentStyle.remove();
//        uiModel.asMap().clear();
//        uiModel.addAttribute("page", (page == null) ? "1" : page.toString());
//        uiModel.addAttribute("size", (size == null) ? "10" : size.toString());
        return "redirect:/shipmentstyles";
    }

	void addDateTimeFormatPatterns(Model uiModel) {
        uiModel.addAttribute("shipmentStyle_outdoordate_date_format", DateTimeFormat.patternForStyle("M-", LocaleContextHolder.getLocale()));
        uiModel.addAttribute("shipmentStyle_onboarddate_date_format", DateTimeFormat.patternForStyle("M-", LocaleContextHolder.getLocale()));
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
