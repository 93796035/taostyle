package com.stgo.taostyle.web.orders;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import com.stgo.taostyle.service.MainOrderService;
import com.stgo.taostyle.service.MediaUploadService;
import com.stgo.taostyle.service.PersonService;
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
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.web.CC;
import com.stgo.taostyle.web.TaoUtil;

@RequestMapping("/mainorders")
@Controller

public class MainOrderController {
    @Autowired
    private PersonService personService;
    @Autowired
    private MediaUploadService mediaUploadService;
    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private MainOrderService mainOrderService;

    void populateEditForm(
            Model uiModel,
            MainOrder mainOrder,
            Person person) {
        uiModel.addAttribute("mainOrder", mainOrder);
        // addDateTimeFormatPatterns(uiModel);
        // uiModel.addAttribute("mediauploads", MediaUpload.findAllMediaUploads());
        List<UserAccount> tUsers = userAccountService.findAllUserAccountsByPerson(person);
        List<UserAccount> clients = new ArrayList<UserAccount>();
        List<UserAccount> useraccounts = new ArrayList<UserAccount>();
        if (tUsers != null) {
            for (int i = 0; i < tUsers.size(); i++) {
                UserAccount tUser = tUsers.get(i);
                if ("COMPANY_CLIENT".equals(tUser.getSecuritylevel())) {
                    clients.add(tUser);
                } else if (CC.ROLE_CLIENT.equals(tUser.getSecuritylevel())) {
                    useraccounts.add(tUser);
                }
            }
        }
        uiModel.addAttribute("useraccounts", useraccounts);
        uiModel.addAttribute("clients", clients);

        Date deliverDate = mainOrder.getDelieverdate();
        if (deliverDate != null && deliverDate.getYear() > 0) {
            StringBuilder tSB =
                    new StringBuilder((deliverDate.getMonth() + 1) + "/").append(deliverDate.getDate()).append("/")
                            .append(deliverDate.getYear() + 1900);
            uiModel.addAttribute("deliverdate", tSB.toString());
        } else
            uiModel.addAttribute("deliverdate", "");
    }

    @RequestMapping(value = "/{id}", params = "form", produces = "text/html")
    public String updateForm(
            @PathVariable("id")
            Long id,
            Model uiModel,
            HttpServletRequest request) {

        Person person = taoUtil.getCurPerson(request);
        populateEditForm(uiModel, mainOrderService.findMainOrder(id), person);

        if (request.getParameter("position") != null) {
            uiModel.addAttribute("position", request.getParameter("position"));
            uiModel.addAttribute("mediaUpload", new MediaUpload());
        }

        return "mainorders/update";
    }

    @RequestMapping(value = "/getImage/{id}")
    public void getImage(
            @PathVariable("id")
            String id,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setContentType("image/jpg");
        Person person = taoUtil.getCurPerson(request);
        MediaUpload tMedia = mediaUploadService.findMediaByKeyAndPerson(id, person);
        try {
            if (tMedia != null && tMedia.getContent() != null) {
                byte[] imageBytes = tMedia.getContent();
                response.getOutputStream().write(imageBytes);
                response.getOutputStream().flush();

            }
        } catch (Exception e) {
            System.out.println("[MainOrderController]Exception occured when fetching img of ID:" + id + "! (pid:" + person.getId() + " ) "  + e);
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
            uiModel.addAttribute("mainorders", mainOrderService.findMainOrderEntries(firstResult, sizeNo));
            float nrOfPages = (float) mainOrderService.countMainOrders() / sizeNo;
            uiModel.addAttribute("maxPages", (int) ((nrOfPages > (int) nrOfPages || nrOfPages == 0.0) ? nrOfPages + 1
                    : nrOfPages));
        } else {
            uiModel.addAttribute("mainorders", mainOrderService.findAllMainOrders());
        }
        addDateTimeFormatPatterns(uiModel);
        return "mainorders/list";
    }

    // if in jsp page "POST" is used, then have to use a params or a value as follow, otherwise will be fighting with
    // other method.
    // @RequestMapping(params = "forUpdate", method = RequestMethod.POST, produces = "text/html")
    // @RequestMapping(value = "/updateMainOrder",method = RequestMethod.POST)
    @RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(
            @Valid
            MainOrder mainOrder,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        if (bindingResult.getAllErrors().size() > 3) {
            return continiuWork(mainOrder, uiModel, "update", person);
        } else {
            uiModel.asMap().clear();

            String client = request.getParameter(CC.CLIENT);
            if (client.length() > 0) {
                int i = client.indexOf(" ");
                if (i == -1)
                    i = client.length();
                mainOrder.setClient(userAccountService.findUserAccountByName(TaoEncrypt.enrichName(client.substring(0, i),
                        person)));
            }

            String contactPerson = request.getParameter("contactPerson");
            if (contactPerson.length() > 0) {
                int i = contactPerson.indexOf(" ");
                if (i == -1)
                    i = contactPerson.length();
                mainOrder.setContactPerson(userAccountService.findUserAccountByName(TaoEncrypt.enrichName(
                        contactPerson.substring(0, i), person)));
            }

            String delieverdate = request.getParameter("delieverdate");
            String[] tAry = delieverdate.split("/");
            if (delieverdate.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(mainOrder, uiModel, "update", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    mainOrder.setDelieverdate(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(mainOrder, uiModel, "update", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    mainOrder.setDelieverdate(tCalendar.getTime());
                }
                mainOrderService.merge(mainOrder);
                return "redirect:/mainorders/" + mainOrder.getId().toString();
            } else {
                return continiuWork(mainOrder, uiModel, "update", person);
            }
        }
    }

    private String continiuWork(
            MainOrder mainOrder,
            Model uiModel,
            String pReturnPath,
            Person person) {
        uiModel.addAttribute("mainOrder", mainOrder);
        addDateTimeFormatPatterns(uiModel);
        // uiModel.addAttribute("mediauploads", MediaUpload.findAllMediaUploads());
        List<UserAccount> tUsers = userAccountService.findAllUserAccountsByPerson(person);
        List<UserAccount> clients = new ArrayList<UserAccount>();
        List<UserAccount> useraccounts = new ArrayList<UserAccount>();
        if (tUsers != null) {
            for (int i = 0; i < tUsers.size(); i++) {
                UserAccount tUser = tUsers.get(i);
                if ("[C]".equals(tUser.getCompanyname())) {
                    clients.add(tUser);
                } else if (CC.ROLE_CLIENT.equals(tUser.getSecuritylevel())) {
                    useraccounts.add(tUser);
                }
            }
        }
        uiModel.addAttribute("useraccounts", useraccounts);
        uiModel.addAttribute("clients", clients);
        // shall we do something with the date in request? or the modified date will all lost.
        return "mainorders/" + pReturnPath;
    }

    @RequestMapping(value = "/{id}", produces = "text/html")
    public String show(
            @PathVariable("id")
            Long id,
            Model uiModel) {
        MainOrder mainOrder = mainOrderService.findMainOrder(id);
        uiModel.addAttribute("mainorder", mainOrder);
        uiModel.addAttribute("itemId", id);
        Date deliverDate = mainOrder.getDelieverdate();
        if (deliverDate != null && deliverDate.getYear() > 0) {
            StringBuilder tSB =
                    new StringBuilder((deliverDate.getMonth() + 1) + "/").append(deliverDate.getDate()).append("/")
                            .append(deliverDate.getYear() + 1900);
            uiModel.addAttribute("deliverdate", tSB.toString());
        } else
            uiModel.addAttribute("deliverdate", "");
        return "mainorders/show";
    }

    @RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(
            @Valid
            MainOrder mainOrder,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        if (bindingResult.getAllErrors().size() > 3) {
            return continiuWork(mainOrder, uiModel, "create", person);
        } else {
            uiModel.asMap().clear();

            String client = request.getParameter(CC.CLIENT);
            if (client.length() > 0) {
                int i = client.indexOf(" ");
                if (i == -1)
                    i = client.length();
                mainOrder.setClient(userAccountService.findUserAccountByName(TaoEncrypt.enrichName(client.substring(0, i),
                        person)));
            }

            String contactPerson = request.getParameter("contactPerson");
            if (contactPerson.length() > 0) {
                int i = contactPerson.indexOf(" ");
                if (i == -1)
                    i = contactPerson.length();
                mainOrder.setContactPerson(userAccountService.findUserAccountByName(TaoEncrypt.enrichName(
                        contactPerson.substring(0, i), person)));
            }

            String delieverdate = request.getParameter("delieverdate");
            String[] tAry = delieverdate.split("/");
            if (delieverdate.length() == 0 || tAry.length == 3) {
                if (tAry.length == 3) {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(tAry[0]).intValue();
                        tIntAry[1] = Integer.valueOf(tAry[1]).intValue();
                        tIntAry[2] = Integer.valueOf(tAry[2]).intValue();
                    } catch (Exception e) {
                        return continiuWork(mainOrder, uiModel, "create", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    mainOrder.setDelieverdate(tCalendar.getTime());
                } else {
                    int[] tIntAry = new int[3];
                    try {
                        tIntAry[0] = Integer.valueOf(1);
                        tIntAry[1] = Integer.valueOf(1);
                        tIntAry[2] = Integer.valueOf(0);
                    } catch (Exception e) {
                        return continiuWork(mainOrder, uiModel, "create", person);
                    }
                    Calendar tCalendar = Calendar.getInstance();
                    tCalendar.set(tIntAry[2], tIntAry[0] - 1, tIntAry[1]);
                    mainOrder.setDelieverdate(tCalendar.getTime());
                }
                mainOrderService.persist(mainOrder);
                return "redirect:/mainorders/" + mainOrder.getId().toString();
            } else {
                return continiuWork(mainOrder, uiModel, "create", person);
            }
        }
    }

    @RequestMapping(params = "form", produces = "text/html")
    public String createForm(
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        populateEditForm(uiModel, new MainOrder(), person);
        List<String[]> dependencies = new ArrayList<String[]>();
        if (userAccountService.countUserAccountsByPerson(person) == 0) {
            dependencies.add(new String[] { "useraccount", "useraccounts" });
        }
        uiModel.addAttribute("dependencies", dependencies);
        return "mainorders/create";
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "text/html")
    public String delete(@PathVariable("id") Long id, @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size, Model uiModel) {
        MainOrder mainOrder = mainOrderService.findMainOrder(id);
        mainOrderService.remove(mainOrder);
        uiModel.asMap().clear();
        uiModel.addAttribute("page", (page == null) ? "1" : page.toString());
        uiModel.addAttribute("size", (size == null) ? "10" : size.toString());
        return "redirect:/mainorders";
    }

	void addDateTimeFormatPatterns(Model uiModel) {
        uiModel.addAttribute("mainOrder_delieverdate_date_format", DateTimeFormat.patternForStyle("M-", LocaleContextHolder.getLocale()));
    }
//
//	void populateEditForm(Model uiModel, MainOrder mainOrder) {
//        uiModel.addAttribute("mainOrder", mainOrder);
//        addDateTimeFormatPatterns(uiModel);
//        uiModel.addAttribute("mediauploads", mediaUploadService.findAllMediaUploads());
//        uiModel.addAttribute("people", personService.findAllPeople());
//        uiModel.addAttribute("useraccounts", userAccountService.findAllUserAccounts());
//    }
//
//	String encodeUrlPathSegment(String pathSegment, HttpServletRequest httpServletRequest) {
//        String enc = httpServletRequest.getCharacterEncoding();
//        if (enc == null) {
//            enc = WebUtils.DEFAULT_CHARACTER_ENCODING;
//        }
//        try {
//            pathSegment = UriUtils.encodePathSegment(pathSegment, enc);
//        } catch (UnsupportedEncodingException uee) {}
//        return pathSegment;
//    }
}
