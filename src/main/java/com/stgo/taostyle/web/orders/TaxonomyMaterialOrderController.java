package com.stgo.taostyle.web.orders;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import com.stgo.taostyle.service.MainOrderService;
import com.stgo.taostyle.service.UserAccountService;
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
import com.stgo.taostyle.backend.security.TaoEncrypt;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.TaxonomyMaterialOrder;
import com.stgo.taostyle.web.TaoUtil;

@RequestMapping("/taxonomymaterialorders")
@Controller
public class TaxonomyMaterialOrderController {

    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private MainOrderService mainOrderService;

    void populateEditForm(
            Model uiModel,
            TaxonomyMaterialOrder taxonomyMaterialOrder,
            Person person) {
        if (taxonomyMaterialOrder.getMainOrder() != null) {
            taxonomyMaterialOrder.setMainOrderStr(taxonomyMaterialOrder.getMainOrder().getClientSideOrderNumber());
        }
        uiModel.addAttribute("taxonomyMaterialOrder", taxonomyMaterialOrder);
        List<MainOrder> tMainOrders = mainOrderService.findAllMainOrders();
        List<String> tMainOrderStrs = new ArrayList<String>();
        for (int i = tMainOrders.size() - 1; i >= 0; i--) {
            tMainOrderStrs.add(tMainOrders.get(i).getClientSideOrderNumber());
        }
        uiModel.addAttribute("mainorders", tMainOrderStrs);

        List<UserAccount> tUsers = userAccountService.findAllUserAccountsByPerson(person);
        List<UserAccount> suppliers = new ArrayList<UserAccount>();
        List<UserAccount> useraccounts = new ArrayList<UserAccount>();
        if (tUsers != null) {
            for (int i = 0; i < tUsers.size(); i++) {
                UserAccount tUser = tUsers.get(i);
                if ("FACTORY_SUPPLIER".equals(tUser.getSecuritylevel())) {
                    suppliers.add(tUser);
                } else if ("ROLE_SUPPLIER".equals(tUser.getSecuritylevel())) {
                    useraccounts.add(tUser);
                }
            }
        }
        uiModel.addAttribute("factorys", suppliers);
        uiModel.addAttribute("suppliers", useraccounts);

        Date orderDate = taxonomyMaterialOrder.getOrderDate();
        if (orderDate != null && orderDate.getYear() > 0) {
            StringBuilder tSB =
                    new StringBuilder((orderDate.getMonth() + 1) + "/").append(orderDate.getDate()).append("/")
                            .append(orderDate.getYear() + 1900);
            uiModel.addAttribute("orderDate", tSB.toString());
        } else
            uiModel.addAttribute("orderDate", "");

        Date recd_date = taxonomyMaterialOrder.getRecd_date();
        if (recd_date != null && recd_date.getYear() > 0) {
            StringBuilder tSB =
                    new StringBuilder((recd_date.getMonth() + 1) + "/").append(recd_date.getDate()).append("/")
                            .append(recd_date.getYear() + 1900);
            uiModel.addAttribute("recd_date", tSB.toString());
        } else
            uiModel.addAttribute("recd_date", "");
    }

    @RequestMapping(value = "/{id}", produces = "text/html")
    public String show(
            @PathVariable("id")
            Long id,
            Model uiModel) {
        // addDateTimeFormatPatterns(uiModel);
        TaxonomyMaterialOrder taxonomyMaterialOrder = TaxonomyMaterialOrder.findTaxonomyMaterialOrder(id);
        uiModel.addAttribute("taxonomymaterialorder", taxonomyMaterialOrder);
        uiModel.addAttribute("itemId", id);

        MainOrder tMainOrder = taxonomyMaterialOrder.getMainOrder();
        taxonomyMaterialOrder.setMainOrderStr(tMainOrder.getClientSideOrderNumber());

        Date orderDate = taxonomyMaterialOrder.getOrderDate();
        if (orderDate != null && orderDate.getYear() > 0) {
            StringBuilder tSB =
                    new StringBuilder((orderDate.getMonth() + 1) + "/").append(orderDate.getDate()).append("/")
                            .append(orderDate.getYear() + 1900);
            uiModel.addAttribute("orderDate", tSB.toString());
        } else
            uiModel.addAttribute("orderDate", "");

        Date recd_date = taxonomyMaterialOrder.getRecd_date();
        if (recd_date != null && recd_date.getYear() > 0) {
            StringBuilder tSB =
                    new StringBuilder((recd_date.getMonth() + 1) + "/").append(recd_date.getDate()).append("/")
                            .append(recd_date.getYear() + 1900);
            uiModel.addAttribute("recd_date", tSB.toString());
        } else
            uiModel.addAttribute("recd_date", "");

        return "taxonomymaterialorders/show";
    }

    @RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(
            @Valid
            TaxonomyMaterialOrder taxonomyMaterialOrder,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        if (bindingResult.getErrorCount() > 5) {
            return continiuWork(taxonomyMaterialOrder, uiModel, "update", person);
        } else {
            uiModel.asMap().clear();

            String client = request.getParameter("factory");
            if (client.length() > 0) {
                int i = client.indexOf(" ");
                if (i == -1)
                    i = client.length();
                taxonomyMaterialOrder.setFactory(userAccountService.findUserAccountByName(TaoEncrypt.enrichName(
                        client.substring(0, i), person)));
            }

            String contactPerson = request.getParameter("supplier");
            if (contactPerson.length() > 0) {
                int i = contactPerson.indexOf(" ");
                if (i == -1)
                    i = contactPerson.length();
                taxonomyMaterialOrder.setSupplier(userAccountService.findUserAccountByName(TaoEncrypt.enrichName(
                        contactPerson.substring(0, i), person)));
            }

            String orderDate = request.getParameter("orderDate");
            String[] tAry = orderDate.split("/");
            if (orderDate.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(taxonomyMaterialOrder, uiModel, "update", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    taxonomyMaterialOrder.setOrderDate(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(taxonomyMaterialOrder, uiModel, "update", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    taxonomyMaterialOrder.setOrderDate(tCalendar.getTime());
                }
            } else {
                return continiuWork(taxonomyMaterialOrder, uiModel, "update", person);
            }

            String recd_date = request.getParameter("recd_date");
            tAry = recd_date.split("/");
            if (recd_date.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(taxonomyMaterialOrder, uiModel, "update", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    taxonomyMaterialOrder.setRecd_date(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(taxonomyMaterialOrder, uiModel, "update", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    taxonomyMaterialOrder.setRecd_date(tCalendar.getTime());
                }
            } else {
                return continiuWork(taxonomyMaterialOrder, uiModel, "update", person);
            }
            taxonomyMaterialOrder.merge();
            return "redirect:/taxonomymaterialorders/" + taxonomyMaterialOrder.getId().toString();
        }
    }

    private String continiuWork(
            TaxonomyMaterialOrder taxonomyMaterialOrder,
            Model uiModel,
            String pReturnPath,
            Person person) {
        populateEditForm(uiModel, taxonomyMaterialOrder, person);
        return "taxonomymaterialorders/" + pReturnPath;
    }

    @RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(
            @Valid
            TaxonomyMaterialOrder taxonomyMaterialOrder,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        if (bindingResult.getErrorCount() > 5) {
            return continiuWork(taxonomyMaterialOrder, uiModel, "create", person);
        } else {
            uiModel.asMap().clear();
            String mainOrderStr = request.getParameter("mainOrder");
            taxonomyMaterialOrder.setMainOrder(mainOrderService.findMainOrderByCON(mainOrderStr).get(0));

            String client = request.getParameter("factory");
            if (client.length() > 0) {
                int i = client.indexOf(" ");
                if (i == -1)
                    i = client.length();
                taxonomyMaterialOrder.setFactory(userAccountService.findUserAccountByName(TaoEncrypt.enrichName(
                        client.substring(0, i), person)));
            }

            String contactPerson = request.getParameter("supplier");
            if (contactPerson.length() > 0) {
                int i = contactPerson.indexOf(" ");
                if (i == -1)
                    i = contactPerson.length();
                taxonomyMaterialOrder.setSupplier(userAccountService.findUserAccountByName(TaoEncrypt.enrichName(
                        contactPerson.substring(0, i), person)));
            }

            String orderDate = request.getParameter("orderDate");
            String[] tAry = orderDate.split("/");
            if (orderDate.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(taxonomyMaterialOrder, uiModel, "create", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    taxonomyMaterialOrder.setOrderDate(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(taxonomyMaterialOrder, uiModel, "create", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    taxonomyMaterialOrder.setOrderDate(tCalendar.getTime());
                }
            } else {
                return continiuWork(taxonomyMaterialOrder, uiModel, "create", person);
            }

            String recd_date = request.getParameter("recd_date");
            tAry = recd_date.split("/");
            if (recd_date.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(taxonomyMaterialOrder, uiModel, "create", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    taxonomyMaterialOrder.setRecd_date(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(taxonomyMaterialOrder, uiModel, "create", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    taxonomyMaterialOrder.setRecd_date(tCalendar.getTime());
                }
            } else {
                return continiuWork(taxonomyMaterialOrder, uiModel, "create", person);
            }
            taxonomyMaterialOrder.persist();
            return "redirect:/taxonomymaterialorders/" + taxonomyMaterialOrder.getId().toString();
        }
    }

    private void beautifyTheTaxonomyMaterialOrders(
            List<TaxonomyMaterialOrder> tTaxonomyMaterialOrders) {
        for (int i = tTaxonomyMaterialOrders.size() - 1; i >= 0; i--) {
            if (tTaxonomyMaterialOrders.get(i).getMainOrder() != null)
                tTaxonomyMaterialOrders.get(i).setMainOrderStr(
                        tTaxonomyMaterialOrders.get(i).getMainOrder().getClientSideOrderNumber());
        }
    }

    @RequestMapping(produces = "text/html")
    public String list(
            @RequestParam(value = "page", required = false)
            Integer page,
            @RequestParam(value = "size", required = false)
            Integer size,
            Model uiModel) {
        if (page != null || size != null) {
            int sizeNo = size == null ? 10 : size.intValue();
            final int firstResult = page == null ? 0 : (page.intValue() - 1) * sizeNo;
            List<TaxonomyMaterialOrder> tList =
                    TaxonomyMaterialOrder.findTaxonomyMaterialOrderEntries(firstResult, sizeNo);
            beautifyTheTaxonomyMaterialOrders(tList);
            uiModel.addAttribute("taxonomymaterialorders", tList);
            float nrOfPages = (float) TaxonomyMaterialOrder.countTaxonomyMaterialOrders() / sizeNo;
            uiModel.addAttribute("maxPages", (int) ((nrOfPages > (int) nrOfPages || nrOfPages == 0.0) ? nrOfPages + 1
                    : nrOfPages));
        } else {
            List<TaxonomyMaterialOrder> tList = TaxonomyMaterialOrder.findAllTaxonomyMaterialOrders();
            beautifyTheTaxonomyMaterialOrders(tList);
            uiModel.addAttribute("taxonomymaterialorders", tList);
        }
        addDateTimeFormatPatterns(uiModel);
        return "taxonomymaterialorders/list";
    }

    @RequestMapping(value = "/{id}", params = "form", produces = "text/html")
    public String updateForm(
            @PathVariable("id")
            Long id,
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        populateEditForm(uiModel, TaxonomyMaterialOrder.findTaxonomyMaterialOrder(id), person);
        return "taxonomymaterialorders/update";
    }

    @RequestMapping(params = "form", produces = "text/html")
    public String createForm(
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        populateEditForm(uiModel, new TaxonomyMaterialOrder(), person);
        List<String[]> dependencies = new ArrayList<String[]>();
        if (userAccountService.countUserAccountsByPerson(person) == 0) {
            dependencies.add(new String[] { "mainOrder", "useraccounts" });
        }
        uiModel.addAttribute("dependencies", dependencies);
        return "taxonomymaterialorders/create";
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "text/html")
    public String delete(@PathVariable("id") Long id, @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size, Model uiModel) {
        TaxonomyMaterialOrder taxonomyMaterialOrder = TaxonomyMaterialOrder.findTaxonomyMaterialOrder(id);
        taxonomyMaterialOrder.remove();
        uiModel.asMap().clear();
        uiModel.addAttribute("page", (page == null) ? "1" : page.toString());
        uiModel.addAttribute("size", (size == null) ? "10" : size.toString());
        return "redirect:/taxonomymaterialorders";
    }

	void addDateTimeFormatPatterns(Model uiModel) {
        uiModel.addAttribute("taxonomyMaterialOrder_orderdate_date_format", DateTimeFormat.patternForStyle("M-", LocaleContextHolder.getLocale()));
        uiModel.addAttribute("taxonomyMaterialOrder_recd_date_date_format", DateTimeFormat.patternForStyle("M-", LocaleContextHolder.getLocale()));
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
