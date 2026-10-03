package com.stgo.taostyle.web;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;

import com.paypal.OrderDetail;
import com.paypal.PaymentServices;
import com.paypal.base.rest.PayPalRESTException;
import com.stgo.taostyle.domain.*;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.Material;
import com.stgo.taostyle.domain.orders.TaxonomyMaterial;
import com.stgo.taostyle.service.*;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;


@Controller
@RequestMapping("/services")
public class ServiceController extends BaseController {
    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private MediaUploadService mediaUploadService;
    @Autowired
    private ServiceService serviceService;
    @Autowired
    TaoSecurity taoSecurity;
    @Autowired
    private MainOrderService mainOrderService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private CustomizeService customizeService;
    @Autowired
    private TaxonomyMaterialService taxonomyMaterialService;

    @Autowired
    private TextContentService textContentService;
    @Autowired
    private MaterialService materialService;
    @Autowired
    private HttpSession session;

    String MODE = "live";
    @PostMapping("/addToCart")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToCart(@RequestBody Map<String, Object> requestPayload) {
        String menuIdx = (String) requestPayload.get("menuIdx");
        String serviceName = (String) requestPayload.get("serviceName");
        String selectedOption = (String) requestPayload.get("selectedOption");
        int quantity = Integer.parseInt(requestPayload.get("quantity").toString());

        Material material = new Material();
        //person
        int p = menuIdx.indexOf("service_");
        material.setLocation(menuIdx.substring(p + 8));
        material.setPerson((Person)session.getAttribute(CC.CLIENT));
        //name
        String moneyLetter = (String) session.getAttribute("moneyLetter");
        if(moneyLetter == null){
            moneyLetter = "$";
        }
        String productName = taoUtil.fetchProductName(serviceName, moneyLetter.split(","), session);
        material.setPortionName(productName);
        //price
        String[] moneyLetters = moneyLetter.split(",");
        material.setEndSubTotal((int)(Double.parseDouble(taoUtil.fetchProductPrice(serviceName, moneyLetters)) * 100));
        // this one could be added by employee....material.setRemark(remark);
        // 用哪个打印机打印暂且不用设置。material.setMenFu(menfu);
        //option/note
        material.setRemark(selectedOption);
        //quantity
        material.setQuantity(quantity);

        materialService.persist(material);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/createAnOrder/{orderedItems}", headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> createMainOrder(@PathVariable("orderedItems") HashMap<String, Integer>  orderedItems, Model model,
                                                  HttpServletRequest request, HttpServletResponse response) {
        if (taoSecurity.isHecker(request)) {
            session.setAttribute(CC.totalPrice, 0.00f);
            session.setAttribute(CC.itemNumber, 0);
            session.setAttribute(CC.selectedItems, null);
            TextContent textContent = new TextContent();
            textContent.setContent("Your action is logged!");
            return new ResponseEntity<String>(textContent.toJson(), HttpStatus.ALREADY_REPORTED);
        }

        // get current user info ready
        Person person = taoUtil.getCurPerson(request);
        UserAccount curUser = (UserAccount) session.getAttribute(CC.currentUser);

        // valid current selection items.
        @SuppressWarnings("unchecked")
        HashMap<String, Integer> selectedItems = orderedItems;
        if (selectedItems == null || selectedItems.isEmpty()) {
            if (curUser == null || !CC.ROLE_EMPLOYEE.equals(curUser.getSecuritylevel())) {
                TextContent textContent = new TextContent();
                textContent.setContent("Nothing selected yet!");
                return new ResponseEntity<String>(textContent.toJson(), HttpStatus.BAD_REQUEST);
            }
        }

        // fetch out the info in requestPayload, which format is:var source = tableID + "," +
        // name + "," + phoneNumber + "," + address + "," + arrive + "," + remark;
        String[] params = StringUtils.split(request.getParameter("source"), "zSTGOz");
        String sizeTable = TaoUtil.getCleanContent(params[0]);
        String name = TaoUtil.getCleanContent(params[1]);
        String tel = TaoUtil.getCleanContent(params[2]);
        String address = TaoUtil.getCleanContent(params[3]);
        String delieverdate = TaoUtil.getCleanContent(params[4]);
        String remark = TaoUtil.getCleanContent(params[5]);

        saveIntoCookie(name, tel, address, delieverdate, remark, response);

        // try to identify the customer
        UserAccount client = null;
        if (curUser != null) {
            client = curUser;
        } else if (12 > tel.length() && tel.length() > 6) {
            client = userAccountService.findUserAccountsByNameAndTell(name, tel);
        } else {
            // means, user has logged in, so was not asked to input name, tel..while when he
            // click send, it's already
            // time out... throw new RuntimeException("time out!, please log in again.");
            // now even logged in user should also input address and new tell, because might
            // ordered for his mother.
        }

        // validate sizeTable
        if ("not_on_site".equals(sizeTable) && !"did-not-input".equals(name) && !"null".equals(name)) {
            sizeTable = name;
        } else if ("not_on_site".equals(sizeTable) && client != null) {
            sizeTable = client.getLoginname();
        }

        // -----------persist the MainOrder-----------------
        MainOrder sourceAndNewMainOrder = new MainOrder();
        sourceAndNewMainOrder.setPerson(person);
        sourceAndNewMainOrder.setClientSideOrderNumber(session.getId());
        sourceAndNewMainOrder.setContactPerson(null);// it will be set value when a employee processed.
        sourceAndNewMainOrder.setClient(client);// it will be null if a non registered user put an order.
        sourceAndNewMainOrder.setSizeTable(sizeTable);
        sourceAndNewMainOrder.setModel(name);
        sourceAndNewMainOrder.setSampleRequirement(tel);
        sourceAndNewMainOrder.setClientSideModelNumber(address);
        sourceAndNewMainOrder.setQuantity(selectedItems.values().stream().mapToInt(Integer::intValue).sum());

        if ("n".equals(delieverdate)) {
            sourceAndNewMainOrder.setDelieverdate(new Date());
        } else if (!"did-not-input".equals(delieverdate) && !"null".equals(delieverdate)) {
            delieverdate = delieverdate.trim().toLowerCase();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                    delieverdate.endsWith("am") || delieverdate.endsWith("pm") ? "HH:mm a" : "HH:mm");
            try {
                Date targetDate = simpleDateFormat.parse(delieverdate);
                int targetHour = targetDate.getHours();
                long time = targetDate.getTime();

                Date now = new Date();
                int nowHour = now.getHours();
                if (delieverdate.endsWith("am") || delieverdate.endsWith("pm")) {
                    if (targetHour < 12 && delieverdate.endsWith("pm")) {
                        time += 12 * 60 * 60 * 1000;
                    }
                } else {
                    if (nowHour > targetHour && targetHour < 12) {
                        time += 12 * 60 * 60 * 1000;
                    }
                }
                // @ comment out, this is a bad idea. will cause confusion, and we need the
                // arrive time on bill.
                // Long preparetime = Long.valueOf((String)
                // session.getAttribute("prepare_time"));
                // if (preparetime != null) {
                // time -= preparetime * 60 * 1000;
                targetDate = new Date(time);
                // }
                sourceAndNewMainOrder.setDelieverdate(targetDate);
            } catch (Exception e) {
                // do nothing.
            }
        }
        sourceAndNewMainOrder.setRecordStatus(0);
        sourceAndNewMainOrder.setRemark(remark); // like medium or well down, spicy or super spicy, will be add when employee
        releaseOrderNumberToMainOrder(person, sourceAndNewMainOrder);

        // -----------persist the materials-----------------
        List<Material> materials = new ArrayList<Material>();

        String langPrf = (String) session.getAttribute("lang_printer") + "_";
        String moneyLetter = (String) session.getAttribute("moneyLetter");
        if (moneyLetter == null) {
            moneyLetter = "$";
        }

        // ----------------re-update the mainOrder-----------------------
        // @NOTE: before, it means this is actually a customer changed place and
        // employee,
        // now because it's possible that all the dish's price are 0, so we need another
        // way to allow user to change seat.
        OrderCalculationResult calc = saveServicesAsOutputs(request, person, langPrf, selectedItems, sourceAndNewMainOrder, materials, moneyLetter);
        if (calc.getQty() == 0 && curUser != null && CC.ROLE_EMPLOYEE.equals(curUser.getSecuritylevel())) {
            sourceAndNewMainOrder.setRecordStatus(CC.STATUS_CHANGED_PLACE);
            mainOrderService.persist(sourceAndNewMainOrder); // create a new one to merge.
        } else {
            sourceAndNewMainOrder.setPayCondition("$" + String.format("%.2f", calc.getTotal() / 100.0));
            sourceAndNewMainOrder.setTotal_after_tax("$" + String.format("%.2f", (calc.getTotal() + calc.getTax()) / 100.0));

            if (CC.TRUE.equals(session.getAttribute(CC.auto_combine_rec))) {
                MainOrder existingSameSourceMainOrder = searchSameSourceMainOrder(sourceAndNewMainOrder, person,
                        session.getAttribute(CC.limit_same_source));
                if (existingSameSourceMainOrder != null && existingSameSourceMainOrder.getRecordStatus() == 0) {
                    // add the new order into existing one.
                    sourceAndNewMainOrder = doCombineAction(request, sourceAndNewMainOrder, materials,
                            existingSameSourceMainOrder);
                } else {
                    mainOrderService.persist(sourceAndNewMainOrder);
                }
            } else {
                mainOrderService.persist(sourceAndNewMainOrder);
            }
        }

        // Send Email to Store
        int printerWidth = 48;
        try {
            printerWidth = Integer.valueOf((String)session.getAttribute(CC.PrinterWidth));
        }catch(Exception exp) {
            //do nothing
        }
        List<String> contents = generateOrderMessage(
                sourceAndNewMainOrder, materials, printerWidth, sizeTable, address, tel, delieverdate, remark,
                (String)session.getAttribute(CC.TIMEZONE));
        sendOutEmail(contents);

        sendToCloudPrinter(contents);

        //---------------------------
        logVisiting(request, person, sourceAndNewMainOrder);

        //---------------------------
        resetSelectionStatus();

        // notify the seesions to update
        RefreshNoticeCenter.broadcast("wufangshenming", person.getId().toString());
        // ----------------return-----------------------
        if (CC.TRUE.equals(session.getAttribute("support_note"))
                || (curUser != null && CC.ROLE_EMPLOYEE.equals(curUser.getSecuritylevel()))) {
            TextContent textContent = new TextContent();
            textContent.setContent(sourceAndNewMainOrder.getId().toString());
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json; charset=utf-8");
            return new ResponseEntity<String>(textContent.toJson(), headers, HttpStatus.OK);
        } else {
            return new ResponseEntity<String>(HttpStatus.OK);
        }
    }

    @PostMapping(value = "/combineAnOrder/{mainOrderID}", headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> combineAnOrder(@PathVariable("mainOrderID") String mainOrderID, Model model,
                                                 HttpServletRequest request) {

        Person person = taoUtil.getCurPerson(request);
        Long id = Long.valueOf(mainOrderID);
        MainOrder sourceMainOrder = mainOrderService.findMainOrder(id);
        List<Material> materials = materialService.findAllMaterialsByMainOrder(sourceMainOrder);

        String targetOrderInfo = request.getParameter("targetOrder");

        MainOrder mainOrder = mainOrderService.findMainOrder(Long.valueOf(targetOrderInfo));
        mainOrder = doCombineAction(request, sourceMainOrder, materials, mainOrder);
        mainOrderService.persist(mainOrder);
        mainOrderService.remove(sourceMainOrder);

        return new ResponseEntity<String>(HttpStatus.OK);
    }

    @PostMapping(value = "/createRiaRecord")
    public String createRiaRecord(Model model, HttpServletRequest request, HttpServletResponse response) {
        // get current user info ready
        session = request.getSession();
        Person person = taoUtil.getCurPerson(request);
        UserAccount curUser = (UserAccount) session.getAttribute(CC.currentUser);
        String langPrf = (String) session.getAttribute("lang_printer") + "_";

        if (taoSecurity.isHecker(request)) {
            session.setAttribute(CC.totalPrice, 0.00f);
            session.setAttribute(CC.itemNumber, 0);
            session.setAttribute(CC.selectedItems, null);
            TextContent textContent = new TextContent();
            textContent.setContent("Your action is logged!");
            return "dataAccessFailure";
        }

        //!!!!what the hell??? if I write as List<String> formNames = TaoUtil.prepareNames((String)session.getAttribute(INFO_COLLECT_FORM_CUST_PAGE_PATH), person);
        //it will not work---after debugging this line, the formNames will be empty!!!!
        List<String> formNames = new ArrayList<String>();
        List<String> formTypes = new ArrayList<String>();
        taoUtil.prepareAndFillNamesAndTypes("menu_10_7", person, formNames, formTypes);

        MainOrder sourceAndNewMainOrder = new MainOrder();
        sourceAndNewMainOrder.setPerson(person);

        Map<String, String> formData = new HashMap<>();
        for (String fieldName : formNames) {
            String value = taoUtil.getCleanContent(request.getParameter(fieldName));
            formData.put(getMatchedKey(fieldName), value); // 统一用小写，方便比较
        }

        // 逐个提取
        String name = formData.getOrDefault("name", "did-not-input");
        String tel = formData.getOrDefault("tel", "did-not-input");
        String amount = formData.getOrDefault("amount", null); // 可选
        String address = formData.getOrDefault("delivery number", "did-not-input"); // 注意Delivery Address是两个词
        String deliverydate  = formData.getOrDefault("delivery name", "n");
        String remark = formData.getOrDefault("currency", null);

        // try to identify the customer
        UserAccount client = identifyTheCustomer(curUser, name, tel);

        // validate sizeTable
        String sizeTable = name;
        if ("not_on_site".equals(sizeTable) && client != null) {
            sizeTable = client.getLoginname();
        }

        // -----------Prepare the MainOrder-----------------
        sourceAndNewMainOrder.setClient(client);// it will be null if a non registered user put an order.
        sourceAndNewMainOrder.setSizeTable(sizeTable);
        sourceAndNewMainOrder.setModel(name);
        sourceAndNewMainOrder.setSampleRequirement(tel);
        sourceAndNewMainOrder.setClientSideModelNumber(address);
        sourceAndNewMainOrder.setDelieverdate(new Date());
        sourceAndNewMainOrder.setRemark(remark);
        sourceAndNewMainOrder.setPayCondition("$" + String.format("%.2f", amount));
        sourceAndNewMainOrder.setTotal_after_tax("$" + String.format("%.2f",  amount));
        sourceAndNewMainOrder.setQuantity(1);
        mainOrderService.persist(sourceAndNewMainOrder);
        return "redirect:/menu_9_9_0";
    }

    @PostMapping(value = "/createRechargeRecord")
    public String createRechargeRecord(Model model, HttpServletRequest request, HttpServletResponse response) {
        // get current user info ready
        session = request.getSession();
        Person person = taoUtil.getCurPerson(request);
        UserAccount curUser = (UserAccount) session.getAttribute(CC.currentUser);

        if (taoSecurity.isHecker(request)) {
            session.setAttribute(CC.totalPrice, 0.00f);
            session.setAttribute(CC.itemNumber, 0);
            session.setAttribute(CC.selectedItems, null);
            TextContent textContent = new TextContent();
            textContent.setContent("Your action is logged!");
            return "dataAccessFailure";
        }

        //!!!!what the hell??? if I write as List<String> formNames = TaoUtil.prepareNames((String)session.getAttribute(INFO_COLLECT_FORM_CUST_PAGE_PATH), person);
        //it will not work---after debugging this line, the formNames will be empty!!!!
        List<String> formNames = new ArrayList<String>();
        List<String> formTypes = new ArrayList<String>();
        taoUtil.prepareAndFillNamesAndTypes("menu_10_8", person, formNames, formTypes);

        MainOrder sourceAndNewMainOrder = new MainOrder();
        sourceAndNewMainOrder.setPerson(person);

        Map<String, String> formData = new HashMap<>();
        for (String fieldName : formNames) {
            String value = taoUtil.getCleanContent(request.getParameter(fieldName));
            formData.put(getMatchedKey(fieldName), value); // 统一用小写，方便比较
        }
        // 逐个提取
        String name = formData.getOrDefault("name", "did-not-input");
        String tel = formData.getOrDefault("tel", "did-not-input");
        String amount = formData.getOrDefault("Amount*", null); // 可选
        String remark = formData.getOrDefault("Carrier*", null);

        // try to identify the customer
        UserAccount client = identifyTheCustomer(curUser, name, tel);

        // validate sizeTable
        String sizeTable = name;
        if ("not_on_site".equals(sizeTable) && client != null) {
            sizeTable = client.getLoginname();
        }

        // -----------Prepare the MainOrder-----------------
        sourceAndNewMainOrder.setClient(client);// it will be null if a non registered user put an order.
        sourceAndNewMainOrder.setSizeTable(sizeTable);
        sourceAndNewMainOrder.setModel(name);
        sourceAndNewMainOrder.setSampleRequirement(tel);
        sourceAndNewMainOrder.setDelieverdate(new Date());
        sourceAndNewMainOrder.setRemark(remark);
        sourceAndNewMainOrder.setPayCondition("$" +amount);
        sourceAndNewMainOrder.setTotal_after_tax("$" +  amount);
        sourceAndNewMainOrder.setQuantity(1);
        sourceAndNewMainOrder.setRecordStatus(0);
        mainOrderService.persist(sourceAndNewMainOrder);

        return "redirect:/menu_1_1_0";	//have to return some thing system knows, it's meaning less while if I return "", will cause exception.
    }

    @PostMapping(value = "/createAnOrderFromForm")
    public String createMainOrderFromUCF(Model model, HttpServletRequest request, HttpServletResponse response) {
        // get current user info ready
        session = request.getSession();
        Person person = taoUtil.getCurPerson(request);
        UserAccount curUser = (UserAccount) session.getAttribute(CC.currentUser);
        String langPrf = (String) session.getAttribute("lang_printer") + "_";

        @SuppressWarnings("unchecked")
        HashMap<String, Integer> selectedItems = (HashMap<String, Integer>) session.getAttribute(CC.selectedItems);
        if (selectedItems == null || selectedItems.isEmpty()) {
            selectedItems = new HashMap<>();
            List<Material> materials = materialService.findAllMaterialsByPersonAndStatus(person, 0);
            for (Material material : materials) {
                selectedItems.put(material.getLocation(), material.getQuantity());
            }
        }

        if (taoSecurity.isHecker(request)) {
            session.setAttribute(CC.totalPrice, 0.00f);
            session.setAttribute(CC.itemNumber, 0);
            session.setAttribute(CC.selectedItems, null);
            TextContent textContent = new TextContent();
            textContent.setContent("Your action is logged!");
            return "dataAccessFailure";
        }

        //!!!!what the hell??? if I write as List<String> formNames = TaoUtil.prepareNames((String)session.getAttribute(INFO_COLLECT_FORM_CUST_PAGE_PATH), person);
        //it will not work---after debugging this line, the formNames will be empty!!!!
        List<String> formNames = new ArrayList<String>();
        List<String> formTypes = new ArrayList<String>();
        taoUtil.prepareAndFillNamesAndTypes((String)session.getAttribute(taoUtil.INFO_COLLECT_FORM_CUST_PAGE_PATH), person, formNames, formTypes);

        MainOrder sourceAndNewMainOrder = new MainOrder();
        sourceAndNewMainOrder.setPerson(person);
        sourceAndNewMainOrder.setClientSideOrderNumber(session.getId());
        sourceAndNewMainOrder.setContactPerson(null);// it will be set value when a employee processed.
        sourceAndNewMainOrder.setRecordStatus(0);
        sourceAndNewMainOrder.setQuantity(selectedItems.values().stream().mapToInt(Integer::intValue).sum());

        Map<String, String> formData = new HashMap<>();
        for (String fieldName : formNames) {
            String value = taoUtil.getCleanContent(request.getParameter(fieldName));
            formData.put(getMatchedKey(fieldName), value); // 统一用小写，方便比较
        }

        // 逐个提取
        String name = formData.getOrDefault("name", "did-not-input");
        String tel = formData.getOrDefault("tel", "did-not-input");
        String email = formData.getOrDefault("email", null); // 可选
        String address = formData.getOrDefault("delivery address", "did-not-input"); // 注意Delivery Address是两个词
        String deliverydate  = formData.getOrDefault("delivery date", "n");
        String remark = formData.getOrDefault("note", null);

        // try to identify the customer
        UserAccount client = identifyTheCustomer(curUser, name, tel);

        // validate sizeTable
        String sizeTable = name;
        if ("not_on_site".equals(sizeTable) && client != null) {
            sizeTable = client.getLoginname();
        }

        // -----------Prepare the MainOrder-----------------
        sourceAndNewMainOrder.setClient(client);// it will be null if a non registered user put an order.
        sourceAndNewMainOrder.setSizeTable(sizeTable);
        sourceAndNewMainOrder.setModel(name);
        sourceAndNewMainOrder.setSampleRequirement(tel);
        sourceAndNewMainOrder.setClientSideModelNumber(address);

        if ("n".equals(deliverydate )) {
            sourceAndNewMainOrder.setDelieverdate(new Date());
        } else if (!"did-not-input".equals(deliverydate ) && !"null".equals(deliverydate )) {
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                        deliverydate .endsWith("am") || deliverydate .endsWith("pm") ? "HH:mm a" : "HH:mm");
                Date targetDate = simpleDateFormat.parse(deliverydate );
                int targetHour = targetDate.getHours();
                long time = targetDate.getTime();

                Date now = new Date();
                int nowHour = now.getHours();
                if (deliverydate .endsWith("am") || deliverydate .endsWith("pm")) {
                    if (targetHour < 12 && deliverydate .endsWith("pm")) {
                        time += 12 * 60 * 60 * 1000;
                    }
                } else {
                    if (nowHour > targetHour && targetHour < 12) {
                        time += 12 * 60 * 60 * 1000;
                    }
                }
                sourceAndNewMainOrder.setDelieverdate(new Date(time));
            } catch (Exception e) {
                // do nothing
            }
        }

        sourceAndNewMainOrder.setRemark(remark);
        mainOrderService.persist(sourceAndNewMainOrder);

        // -----------persist the materials-----------------
        List<Material> materials = new ArrayList<>();
        String moneyLetter = (String) session.getAttribute("moneyLetter");
        if (moneyLetter == null) {
            moneyLetter = "$";
        }
        // ----------------re-update the mainOrder with total and -----------------------
        OrderCalculationResult calc = saveServicesAsOutputs(request, person, langPrf, selectedItems, sourceAndNewMainOrder, materials, moneyLetter);
        // @NOTE: before, it means this is actually a customer changed place and
        // employee,
        // now because it's possible that all the dish's price are 0, so we need another
        // way to allow user to change seat.
        if (calc.getQty() == 0 && curUser != null && CC.ROLE_EMPLOYEE.equals(curUser.getSecuritylevel())) {
            sourceAndNewMainOrder.setRecordStatus(CC.STATUS_CHANGED_PLACE);
            mainOrderService.persist(sourceAndNewMainOrder); // create a new one to merge.
        } else {
            sourceAndNewMainOrder.setPayCondition("$" + String.format("%.2f", calc.getTotal() / 100.0));
            sourceAndNewMainOrder.setTotal_after_tax("$" + String.format("%.2f",  (calc.getTotal() + calc.getTax()) / 100.0));
            if (CC.TRUE.equals(session.getAttribute(CC.auto_combine_rec))) {
                MainOrder existingSameSourceMainOrder = searchSameSourceMainOrder(sourceAndNewMainOrder, person,
                        session.getAttribute(CC.limit_same_source));
                if (existingSameSourceMainOrder != null && existingSameSourceMainOrder.getRecordStatus() == 0) {
                    // add the new order into existing one.
                    sourceAndNewMainOrder = doCombineAction(request, sourceAndNewMainOrder, materials,
                            existingSameSourceMainOrder);
                } else {
                    mainOrderService.persist(sourceAndNewMainOrder);
                }
            } else {
                mainOrderService.persist(sourceAndNewMainOrder);
            }
        }

        //prepare the contents for email and print.==============================================================
        int printerWidth = 48;
        try {printerWidth = Integer.valueOf((String)session.getAttribute(CC.PrinterWidth));	}catch(Exception exp){}

        //---------------------------
        logVisiting(request, person, sourceAndNewMainOrder);

        //check if user want to paynow.....then direct to paypal page, and waiting for the second round to come back to this method with payment_id in url.
        if("paynow".equals(request.getParameter("action"))) {
            String payCondition = sourceAndNewMainOrder.getPayCondition();
            payCondition = payCondition.substring(payCondition.indexOf(moneyLetter) + 1);
            String taxRateStr = (String) request.getSession().getAttribute("tax_rate");
            //suppose product price are tax included.
            float total = Float.parseFloat(payCondition);
            float taxRate = taxRateStr != null ? Float.parseFloat(taxRateStr) : 0.0f;
            // 格式化金额，保留两位小数
            OrderDetail orderDetail = new OrderDetail("",
                   payCondition, "0", String.valueOf(total * taxRate), String.valueOf(total * taxRate + total));

            try {
                PaymentServices paymentServices = new PaymentServices();
                String approvalLink = paymentServices.authorizePayment(session, orderDetail, MODE);

                if(sizeTable != null && address != null && tel != null && deliverydate != null) {
                    List<String> contents = generateOrderMessage(
                            sourceAndNewMainOrder, materials, printerWidth, sizeTable, address, tel, deliverydate, remark,
                            (String) session.getAttribute(CC.TIMEZONE));

                    session.setAttribute(CC.ContentToPrint, contents);
                }
                session.setAttribute(CC.MainOrder, sourceAndNewMainOrder.getId());
                response.sendRedirect(approvalLink);
            } catch (Exception ex) {
                request.setAttribute("errorMessage", ex.getMessage());
                ex.printStackTrace();
                try {
                    request.getRequestDispatcher("error.jsp").forward(request, response);
                } catch (ServletException | IOException e) {
                    TaoDebug.error("Exception when paynow requestPayload send", e);
                }
            }
            return "redirect:/menu_9_9_0";	//have to return some thing system knows, it's meaning less while if I return "", will cause exception.
        }else {

            //---------------------------- give the MainOrder a order number to make it live
            releaseOrderNumberToMainOrder(person, sourceAndNewMainOrder);
            if(sizeTable != null && address != null && tel != null && deliverydate != null) {
                List<String> contents = generateOrderMessage(
                        sourceAndNewMainOrder, materials, printerWidth, sizeTable, address, tel, deliverydate, remark,
                        (String) session.getAttribute(CC.TIMEZONE));

                //---------------------------
                try {
                    sendOutEmail(contents);
                } catch (Exception e) {
                    TaoDebug.error("Exception when sending order to email", e);
                }
                //--------------------------- send to printer cloud. first step, every thing go to one printer, also with user info, which will be easy for deliver to check and send.
                sendToCloudPrinter(contents);//TODO:the PrinterSN as a customization, will be used to print delivery bill. one person could own many kitchen printers, kitchen printer should own an account, and
                //the content should be organized base on the printer: 1/printers come out 2/find his dish during the items, 3/organized his content, 4/send to printer.
            }
            //---------------------------
            resetSelectionStatus();

            //---------------------------
            RefreshNoticeCenter.broadcast("wufangshenming", person.getId().toString());

            //----------------return-----------------------
            //		if (CC.TRUE.equals(session.getAttribute("support_note"))
            //				|| (curUser != null && CC.ROLE_EMPLOYEE.equals(curUser.getSecuritylevel()))) {
            //			TextContent textContent = new TextContent();
            //			textContent.setContent(sourceAndNewMainOrder.getId().toString());
            //			HttpHeaders headers = new HttpHeaders();
            //			headers.add("Content-Type", "application/json; charset=utf-8");
            //			return new ResponseEntity<String>(textContent.toJson(), headers, HttpStatus.OK);
            //		} else {

            return "redirect:/menu_9_9_0";
            //		}
        }
    }

    private String getMatchedKey(String fieldName){
        if(fieldName.toLowerCase().contains("name")){
            return "name";
        }else if(fieldName.toLowerCase().contains("address")){
            return "delivery address";
        }else if(fieldName.toLowerCase().contains("time") || fieldName.toLowerCase().contains("date")){
            return "delivery date";
        }else if(fieldName.toLowerCase().contains("number") || fieldName.toLowerCase().contains("tel")){
            return "tel";
        }else if(fieldName.toLowerCase().contains("email")){
            return "email";
        }else if(fieldName.toLowerCase().contains("note")){
            return "note";
        }
        return fieldName;
    }
    @GetMapping(value = "/returnFromPaypal")
    public String returnFromPaypal(Model model, HttpServletRequest request, HttpServletResponse response) {
        session = request.getSession();
        Person person = taoUtil.getCurPerson(request);
        UserAccount curUser = (UserAccount) session.getAttribute(CC.currentUser);

        String langPrf = (String) session.getAttribute("lang_printer") + "_";
        String clientID = (String)session.getAttribute(CC.client_id);//"AcEkcPFFkElC0qzAacfvBobtUwNxxb85_I7-eUddJqQM6_xzWji8V4qAftA9BbZqqhbA825ZmnLY9DVr";//"AUiU7qnNgWSt-3sX656c0-v5mHu2m5au9VEbc3PER7o47lM7a2KZm9kzLRCGs7U2IUXlnZCkTEPxKodm";
        String clientSecret = (String)session.getAttribute(CC.client_secret);

        String paymentId = request.getParameter("paymentId");
        String payerId = request.getParameter("PayerID");

        try {
            PaymentServices paymentServices = new PaymentServices();
            paymentServices.executePayment(paymentId, payerId, clientID, clientSecret, MODE);

            //-----------give the MainOrder a order number to make it live-----------------
            MainOrder sourceAndNewMainOrder = mainOrderService.findMainOrder((Long)session.getAttribute(CC.MainOrder));
            releaseOrderNumberToMainOrder(person, sourceAndNewMainOrder);

            // Send Email to Store
            int printerWidth = 48;
            try {
                printerWidth = Integer.valueOf((String)session.getAttribute(CC.PrinterWidth));
            }catch(Exception exp) {
                //do nothing
            }
            List<String> contents = (List<String>)session.getAttribute(CC.ContentToPrint);
            contents.add("***ALREADY PAID ONLINE***");
            sendOutEmail(contents);

            sendToCloudPrinter(contents);

            //---------------------------
            resetSelectionStatus();

            // notify the seesions to update
            RefreshNoticeCenter.broadcast("wufangshenming", person.getId().toString());
            // ----------------return-----------------------
            prepareConfirmMessage(model, request, person, langPrf);

        } catch (PayPalRESTException ex) {
            request.setAttribute("errorMessage", ex.getMessage());
            ex.printStackTrace();
        }
        return "/generalHTMLPage";

    }

    // reset selection status.
    private void resetSelectionStatus() {
        session.setAttribute(CC.totalPrice, 0.00f);
        session.setAttribute(CC.itemNumber, 0);
        session.setAttribute(CC.selectedItems, null);
        session.setAttribute(CC.latitude, null);
        session.setAttribute(CC.longitude, null);
        // in case user go on with ordering.... session.setAttribute(CC.tableID, null);
    }

    private void sendOutEmail(List<String> contents) {
        String mailTo = (String) session.getAttribute(CC.app_ManagerEmail);
        if (mailTo != null && mailTo.length() > 5) {
            StringBuilder sb = new StringBuilder();
            for (String content : contents) {
                sb.append(content).append("\n");
            }
            TaoEmail.sendMessage("AikaPos.STGO@gmail.com", "New Order", mailTo, sb.toString(), null);
        }
    }

    // -----------------log the visiting-------------------
    private void logVisiting(HttpServletRequest request, Person person, MainOrder sourceAndNewMainOrder) {
        TaxonomyMaterial taxonomyMaterial = new TaxonomyMaterial();
        taxonomyMaterial.setItemName(request.getRemoteHost());// computer name if not set, then will be ip address
        taxonomyMaterial.setLocation(request.getRemoteAddr());// ip address
        // do not use forein key, otherwise the main order can not be cleaned.
        taxonomyMaterial.setColor(sourceAndNewMainOrder.getId().toString());
        taxonomyMaterial.setPerson(person);
        taxonomyMaterial.setRemark(request.getRequestURI());// what ordered e.g.
        // /taostyle/thai/createAnOrder/3_2_0_3,3_2_0_1,3_1_0_1
        taxonomyMaterial.setLogtime(new Date());
        taxonomyMaterial.setSpecification((String) session.getAttribute(CC.latitude));
        taxonomyMaterial.setQuality((String) session.getAttribute(CC.longitude));
        taxonomyMaterialService.persist(taxonomyMaterial);
    }

    //this method must be cause from web page, because it get the label printer info from this person's configuration (when requestPayload
    //from JustPrint, the configuration will be get from Customize.findByKeyAndName() instead of from session.
    private void sendToCloudPrinter(List<String> contents) {
        String SN = (String) session.getAttribute(CC.PrinterSN); // *必填*：打印机编号，必须要在管理后台里添加打印机或调用API接口添加之后，才能调用API
        if(SN == null) {
            return;
        }
        Object labelPrinters = session.getAttribute(CC.LabelPrinter);
        boolean isLabelPrinter = labelPrinters != null && ((String)labelPrinters).contains(SN);

        if (SN != null && SN.length() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("<CB>" + contents.get(0) + "</CB>");
            boolean boldFlag = false;
            for (int i = 1; i < contents.size(); i++) {
                if(contents.get(i).startsWith("===========")) {
                    boldFlag = true;// don't want to turn back to small anymore  !boldFlag;
                    sb.append(contents.get(i)).append("<BR>");
                }else if(boldFlag) {
                    sb.append("<B>").append(contents.get(i)).append("</B>").append("<BR>");
                }else {
                    sb.append(contents.get(i)).append("<BR>");
                }
            }
            int time = 1;
            String printTime = (String)session.getAttribute(CC.PrintTimes);
            if(printTime != null && printTime.length() > 0) {
                try {
                    time =Integer.valueOf(printTime);
                }catch(Exception exp) {
                    TaoDebug.error("Exception when processing printTime", exp);
                }
            }
            for(int i = 0; i < time; i++) {
                TaoCloudPrint.getInstance().sendToPrinterCloud(SN, sb.toString(), isLabelPrinter);
                // should check the echo, thought it should always be right.
            }
        }
    }

    // 标签说明：
    // 单标签:
    // "<BR>"为换行,"<CUT>"为切刀指令(主动切纸,仅限切刀打印机使用才有效果)
    // "<LOGO>"为打印LOGO指令(前提是预先在机器内置LOGO图片),"<PLUGIN>"为钱箱或者外置音响指令
    // 成对标签：
    // "<CB></CB>"为居中放大一倍,"<B></B>"为放大一倍,"<C></C>"为居中,<L></L>字体变高一倍
    // <W></W>字体变宽一倍,"<QR></QR>"为二维码,"<BOLD></BOLD>"为字体加粗,"<RIGHT></RIGHT>"为右对齐
    // 拼凑订单内容时可参考如下格式
    // 根据打印纸张的宽度，自行调整内容的格式，可参考下面的样例格式
    private List<String> generateOrderMessage(MainOrder sourceAndNewMainOrder, List<Material> materials, int width,
                                              String title, String address, String tel, String time, String remark, String zone) {
        int orderID = 1;
        List<String> content = new ArrayList<>();
        content.add(sourceAndNewMainOrder.getColorProportionReceived() + "-" + title);
        content.add("=============================================");

        //if there's same dish, should be combined.....
        Map<String, Integer> map = new HashMap<>();
        for (Material material : materials) {
            String name = taoUtil.trimContentFromHTML(material.getPortionName());
            if(name.length() > width) {				//first make sure is't not too long.
                name = name.substring(0, width);
            }
            name = name + "s-t-g-o" + material.getEndSubTotal();
            if(material.getRemark() != null) {
                name = name + "s-t-g-o" + material.getRemark();
            }

            Integer number = map.get(name);
            if (number == null) {
                map.put(name, Integer.valueOf(1));
            } else {
                map.put(name, Integer.valueOf(number + 1));
            }
        }

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String name = entry.getKey();
            String[] name_price_remark = name.split("s-t-g-o");
            name = name_price_remark[0];
            String endSubTotal = name_price_remark[1];
            String note = name_price_remark.length > 2 ? name_price_remark[2] : "";
            if(entry.getValue() > 1) {
                name = name + "  x" + entry.getValue();
                float price = Float.valueOf(endSubTotal.substring(1)) * entry.getValue();
                endSubTotal = endSubTotal.substring(0, 1) + String.format("%.2f", price);
            }
            content.add(name);
            content.add(generateString(width - endSubTotal.length()," ") + endSubTotal);
            content.add(generateString(width / 2,"-"));
        }
        content.add(remark);
        content.add("=============================================");
        content.add("<BOLD>Total:" + sourceAndNewMainOrder.getPayCondition() + "</BOLD>");
        content.add("");
        content.add("");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm a");
        if(zone != null) {
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone(zone));
        }
        content.add("Placed time:" + simpleDateFormat.format(new Date()));
        content.add(generateString(width / 2, "-"));
        content.add("Address:" + address);
        content.add(generateString(width / 2, "-"));
        content.add("Tel:" + tel);
        content.add("Arrive time:" + time);
        return content;
        // ArrayList<String> contents = new ArrayList<String>();
        // //push head info
        // String tableIdx = sourceAndNewMainOrder.getSizeTable();
        // pushBillHeadInfo(contents, width, String.valueOf(billPanel.getBillID()),
        // tableIdx);
        //
        // //push bill waiter and time
        // StringBuilder startTimeStr = new
        // StringBuilder(BarFrame.instance.valStartTime.getText());
        // if(billPanel.billButton != null) {
        // startTimeStr.append("(").append(billPanel.billButton.getText()).append(")");
        // }
        // pushWaiterAndTime(contents, width, tableIdx.length() == 0 ? "N/A" : tableIdx,
        // startTimeStr.toString(), "");
        //
        // //push content and total
        // String content = formatContentForBill(saleRecords, printerIP, billPanel,
        // width).toString();
        //
        // int pEnd = content.lastIndexOf("\n");
        // int pStart = content.substring(0, pEnd).lastIndexOf("\n");//ignore the "\n"
        // at the end
        // String lastRow = content.substring(pStart, pEnd);
        //
        // contents.add(content.substring(0, pStart + 1));
        // contents.add("BigFont"); //push bigger font. while it will not work for mev.
        // contents.add(lastRow);
        // contents.add("NormalFont"); //push normal font
        //
        // //push end message
        // if(billPanel.comment.length() > 0) {
        // contents.add("\n" + billPanel.comment); //has comment means bill printed(will
        // be co-considered when saving paper flag is set) or invoice was reopened,
        // }else {
        // pushEndMessage(contents);
        // }
        //
        // //push cut
        // contents.add("\n\n\n\n\n");
        // contents.add("cut");
        // return contents.toString;
    }

    public static String generateString(int length, String character) {
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < length; i++) {
            sb.append(character);
        }
        return sb.toString();
    }

    private MainOrder doCombineAction(HttpServletRequest request, MainOrder sourceMainOrder, List<Material> materials,
                                      MainOrder mainOrder) {
        Object user = request.getSession().getAttribute(CC.currentUser);
        mainOrder.setContactPerson((UserAccount) user);

        updateMaterialMainOrderId(materials, mainOrder);
        addjustPayconditionAndLogs(mainOrder, sourceMainOrder);
        sourceMainOrder.setRecordStatus(sourceMainOrder.getRecordStatus() - 100);// TODO make any sense? didn't see it
        // persisted again.
        return mainOrder;
    }

    private void addjustPayconditionAndLogs(MainOrder mainOrder, MainOrder sourceMainOrder) {
        String price = (String) sourceMainOrder.getPayCondition();
        price = price == null ? "0.00" : price.substring(1);

        String price2 = (String) mainOrder.getPayCondition();
        price2 = price2 == null ? "0.00" : price2.substring(1);

        float newPrice = Float.valueOf(price) + Float.valueOf(price2);
        newPrice = (float) (Math.round(newPrice * 100)) / 100;
        mainOrder.setPayCondition("$" + newPrice);
        // disappear itself
        List<TaxonomyMaterial> taxonomyMaterials = taxonomyMaterialService
                .findAllTaxonomyMaterialsByMainOrder(sourceMainOrder);
        for (TaxonomyMaterial taxonomyMaterial : taxonomyMaterials) {
            taxonomyMaterial.setMainOrder(mainOrder);
            taxonomyMaterialService.persist(taxonomyMaterial);
        }
    }

    private void updateMaterialMainOrderId(List<Material> materials, MainOrder mainOrder) {
        for (Material material : materials) {
            material.setMainOrder(mainOrder);
            materialService.persist(material);
        }
    }

    private MainOrder searchSameSourceMainOrder(MainOrder sourcdAndNewMainOrder, Person person,
                                                Object limit_same_source) {
        List<MainOrder> tList = mainOrderService.finMainOrdersBySizeTableAndPerson(sourcdAndNewMainOrder.getSizeTable(),
                person);
        MainOrder mainOrder = null;
        if (tList != null && tList.size() > 1) {
            mainOrder = tList.get(1);

            long now = new Date().getTime();
            long date = mainOrder.getDelieverdate().getTime();

            long limit = 60000; // 1 minute.
            try {
                limit = Long.valueOf((String) limit_same_source);
                limit = limit * 1000 * 60;
            } catch (Exception e) {
                // do nothing, keep the default value;
            }

            if (now - date < limit) {
                return mainOrder;
            }
        }

        return null;
    }

    private void prepareConfirmMessage(Model model, HttpServletRequest request, Person person, String langPrf) {
        String confirmMsg = "<font color=\"#70db5b\" size=\"4\">Order submitted succesfully!</font>";
        String confirmform1 = (String)request.getSession().getAttribute("confirmform1");
        if(confirmform1 != null && confirmform1.toLowerCase().startsWith("menu_")) {
            confirmform1 = confirmform1.substring(5);
            if(!confirmform1.endsWith("_0") || confirmform1.length() < 5) {
                confirmform1 = confirmform1 + "_0";
            }
            TextContent textContent = textContentService.findContentsByKeyAndPerson(langPrf + "html_" + confirmform1 + "_content", person);
            if(textContent != null) {
                confirmMsg = textContent.getContent();
            }
        }else {
            confirmform1 = "9_9_0";
        }

        model.addAttribute("menuIdx", confirmform1);
        model.addAttribute("subpageContent", confirmMsg);
    }

    private UserAccount identifyTheCustomer(UserAccount curUser, String name, String tel) {
        UserAccount client = null;
        if (curUser != null) {
            client = curUser;
        } else if (12 > tel.length() && tel.length() > 6) {
            client = userAccountService.findUserAccountsByNameAndTell(name, tel);
        } else {
            // means, user has logged in, so was not asked to input name, tel..while when he
            // click send, it's already
            // time out... throw new RuntimeException("time out!, please log in again.");
            // now even logged in user should also input address and new tell, because might
            // ordered for his mother.
        }
        return client;
    }

    private void releaseOrderNumberToMainOrder(Person person, MainOrder sourceAndNewMainOrder) {
        Customize customize = customizeService.findCustomizeByKeyAndPerson(CC.OrderNumber, person);
        Integer orderNumber = 1;
        if (customize == null) {
            customize = new Customize();
            customize.setPerson(person);
            customize.setCusKey(CC.OrderNumber);
            customize.setCusValue("1");
            customizeService.persist(customize);
        }

        try{
            orderNumber = Integer.valueOf(customize.getCusValue());
        }catch(Exception e) {
            //do nothing.
        }
        sourceAndNewMainOrder.setColorProportionReceived(orderNumber);
        mainOrderService.persist(sourceAndNewMainOrder);

        customize.setCusValue(String.valueOf(orderNumber + 1));
        customizeService.persist(customize);
    }

    private OrderCalculationResult saveServicesAsOutputs(HttpServletRequest request, Person person, String langPrf,
                                                         Map<String, Integer> selectedItems, MainOrder sourceAndNewMainOrder,
                                                         List<Material> materials, String moneyLetter) {
        int total = 0;

        if (materials.size() > 0) {
            for (Material material : materials) {
                int price = material.getEndSubTotal(); // 保留小数点
                total += price;
            }
        } else {
            int i = 0;

            for (Map.Entry<String, Integer> entry : selectedItems.entrySet()) {
                String item = entry.getKey();
                int quantity = entry.getValue();

                String key = langPrf + (item.startsWith("service_") ? item : ("service_" + item)) + "_description";
                TextContent textContent = textContentService.findContentsByKeyAndPerson(key, person);
                if (textContent == null) {
                    TaoDebug.error("didn't set lang_printer version of text for key {}!", key);
                    key = "en_" + (item.startsWith("service_") ? item : ("service_" + item)) + "_description";
                    textContent = textContentService.findContentsByKeyAndPerson(key, person);
                }
                String description = textContent != null ? textContent.getContent() : "";
                String productName = taoUtil.fetchProductName(description, moneyLetter.split(","), session);

                Service service = serviceService.findServiceByCatalogAndPerson(item, person);
                int unitPrice = 0;
                if (service != null && service.getDescription() != null) {
                    unitPrice = (int)(100 * Float.parseFloat(service.getDescription()));
                }

                total += unitPrice * quantity;

                String menfu = "";
                if (service != null && service.getC3() != null) {
                    menfu = service.getC3().trim();
                }
                if (!menfu.startsWith(",")) menfu = "," + menfu;
                if (!menfu.endsWith(",")) menfu = menfu + ",";

                Material material = new Material();
                material.setLocation(item);
                material.setMainOrder(sourceAndNewMainOrder);
                material.setPortionName(productName);
                material.setEndSubTotal(unitPrice * quantity);
                material.setMenFu(menfu);
                material.setQuantity(quantity);

                // Note 备注处理
                String allNotes = (String) request.getSession().getAttribute(taoUtil.getLangPrfWithDefault(request) + CC.notes);
                StringBuilder allAvailableNotes = new StringBuilder();
                if (service != null && service.getC2() != null) {
                    if (allNotes == null) {
                        allNotes = "";
                    } else {
                        allNotes = allNotes.replace('；', ';').trim();
                        if (!allNotes.endsWith(";")) {
                            allNotes += ";";
                        }
                    }
                    String[] allNotesrAry = allNotes.split(";");
                    String[] noteIdxsInService = service.getC2().split(";");

                    List<List<Integer>> selectedNotes = (List<List<Integer>>) request.getSession().getAttribute(CC.selectedNotes);
                    if (selectedNotes == null) {
                        selectedNotes = new ArrayList<>();
                    }
                    List<Integer> selectedNotesOfCurService = taoUtil.initAndReturnSelectedNote(selectedNotes, i);

                    for (int j = 0; j < noteIdxsInService.length; j++) {
                        String idx = noteIdxsInService[j];
                        try {
                            int idxInWhole = Integer.parseInt(idx);
                            if (idxInWhole < allNotesrAry.length && "1".equals(String.valueOf(selectedNotesOfCurService.get(j)))) {
                                allAvailableNotes.append(allNotesrAry[idxInWhole]).append(";");
                            }
                        } catch (Exception e) {
                            // 忽略异常
                        }
                    }
                }

                material.setRemark(allAvailableNotes.toString());
                materialService.persist(material);
                materials.add(material);
                i++;
            }
        }

        // ========== 计算税务相关 ==========
        double taxRate = 0.0;
        try {
            taxRate = Float.parseFloat((String) request.getSession().getAttribute("tax_rate"));
        } catch (Exception ignored) {}

        // ========== 封装结果 ==========
        OrderCalculationResult result = new OrderCalculationResult();
        result.setQty(materials.size());
        result.setTotal(total);
        result.setTax((int) Math.round(total * taxRate / 100.0));
        return result;
    }


    @GetMapping(params = "/createform", produces = "text/html")
    public String addService(
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        String tPosistionStr = request.getParameter("position");
        if (tPosistionStr.startsWith("menu_"))
            tPosistionStr = "catalog" + tPosistionStr.substring(4);
        Service tService = new Service();
        tService.setPerson(person);
        tService.setC1(tPosistionStr);

        populateEditForm(uiModel, tService);
        taoUtil.initTexts(uiModel, "notice_creating_service_", 1, taoUtil.getLangPrfWithDefault(request), person);
        return "services/create";
    }

    @RequestMapping(produces = "text/html")
    public String list(
            HttpServletRequest request,
            @RequestParam(value = "page", required = false)
            Integer page,
            @RequestParam(value = "size", required = false)
            Integer size,
            Model uiModel) {
        String tPosistionStr = request.getParameter("position");
        if (tPosistionStr.startsWith("menu_"))
            tPosistionStr = "service" + tPosistionStr.substring(4);
        uiModel.addAttribute("position", tPosistionStr);
        if (page != null || size != null) {
            int sizeNo = size == null ? 10 : size.intValue();
            final int firstResult = page == null ? 0 : (page.intValue() - 1) * sizeNo;
            uiModel.addAttribute("services", serviceService.findServiceEntries(firstResult, sizeNo));
            float nrOfPages = (float) serviceService.countServices() / sizeNo;
            uiModel.addAttribute("maxPages", (int) ((nrOfPages > (int) nrOfPages || nrOfPages == 0.0) ? nrOfPages + 1
                    : nrOfPages));
        } else {
            uiModel.addAttribute("services", serviceService.findAllServices());
        }
        return "services/list";
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
        Service service = serviceService.findService(id);
        serviceService.deleteService(service);
        uiModel.asMap().clear();

        uiModel.addAttribute("position", httpServletRequest.getParameter("position"));
        if (page != null && size != null) {
            uiModel.addAttribute("page", page.toString());
            uiModel.addAttribute("size", size.toString());
        }

        String tURLStr = "menu" + httpServletRequest.getParameter("position").substring(7);
        return "redirect:/" + encodeUrlPathSegment(tURLStr, httpServletRequest);
    }

	@RequestMapping(value = "/{id}", headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> showJson(@PathVariable("id") Long id) {
        Service service = serviceService.findService(id);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        if (service == null) {
            return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<String>(service.toJson(), headers, HttpStatus.OK);
    }

	@RequestMapping(headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> listJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        List<Service> result = serviceService.findAllServices();
        return new ResponseEntity<String>(Service.toJsonArray(result), headers, HttpStatus.OK);
    }

	@RequestMapping(method = RequestMethod.POST, headers = "Accept=application/json")
    public ResponseEntity<String> createFromJson(@RequestBody String json) {
        Service service = Service.fromJsonToService(json);
        serviceService.persist(service);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        return new ResponseEntity<String>(headers, HttpStatus.CREATED);
    }

	@RequestMapping(value = "/jsonArray", method = RequestMethod.POST, headers = "Accept=application/json")
    public ResponseEntity<String> createFromJsonArray(@RequestBody String json) {
        for (Service service: Service.fromJsonArrayToServices(json)) {
            serviceService.persist(service);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        return new ResponseEntity<String>(headers, HttpStatus.CREATED);
    }

	@RequestMapping(method = RequestMethod.PUT, headers = "Accept=application/json")
    public ResponseEntity<String> updateFromJson(@RequestBody String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        Service service = Service.fromJsonToService(json);
        if (serviceService.merge(service) == null) {
            return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<String>(headers, HttpStatus.OK);
    }

	@RequestMapping(value = "/jsonArray", method = RequestMethod.PUT, headers = "Accept=application/json")
    public ResponseEntity<String> updateFromJsonArray(@RequestBody String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        for (Service service: Service.fromJsonArrayToServices(json)) {
            if (serviceService.merge(service) == null) {
                return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
            }
        }
        return new ResponseEntity<String>(headers, HttpStatus.OK);
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE, headers = "Accept=application/json")
    public ResponseEntity<String> deleteFromJson(@PathVariable("id") Long id) {
        Service service = serviceService.findService(id);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        if (service == null) {
            return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
        }
        serviceService.deleteService(service);
        return new ResponseEntity<String>(headers, HttpStatus.OK);
    }

	@RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(@Valid Service service, BindingResult bindingResult, Model uiModel, HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, service);
            return "services/create";
        }
        uiModel.asMap().clear();
        serviceService.persist(service);
        return "redirect:/services/" + encodeUrlPathSegment(service.getId().toString(), httpServletRequest);
    }

	@RequestMapping(params = "form", produces = "text/html")
    public String createForm(Model uiModel) {
        populateEditForm(uiModel, new Service());
        return "services/create";
    }

	@RequestMapping(value = "/{id}", produces = "text/html")
    public String show(@PathVariable("id") Long id, Model uiModel) {
        uiModel.addAttribute("service", serviceService.findService(id));
        uiModel.addAttribute("itemId", id);
        return "services/show";
    }

	@RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(@Valid Service service, BindingResult bindingResult, Model uiModel, HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, service);
            return "services/update";
        }
        uiModel.asMap().clear();
        serviceService.merge(service);
        return "redirect:/services/" + encodeUrlPathSegment(service.getId().toString(), httpServletRequest);
    }

	@RequestMapping(value = "/{id}", params = "form", produces = "text/html")
    public String updateForm(@PathVariable("id") Long id, Model uiModel) {
        populateEditForm(uiModel, serviceService.findService(id));
        return "services/update";
    }

	void populateEditForm(Model uiModel, Service service) {
        uiModel.addAttribute("service", service);
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

    private void saveIntoCookie(String name, String tel, String address, String delieverdate, String remark,
                                HttpServletResponse response) {
        final Cookie cookie_name = new Cookie("name", name);
        cookie_name.setMaxAge(31536000); // One year
        cookie_name.setPath("/");
        response.addCookie(cookie_name);

        final Cookie cookie_tel = new Cookie("tel", tel);
        cookie_tel.setMaxAge(31536000); // One year
        cookie_tel.setPath("/");
        response.addCookie(cookie_tel);

        final Cookie cookie_address = new Cookie("address", address);
        cookie_address.setMaxAge(31536000); // One year
        cookie_address.setPath("/");
        response.addCookie(cookie_address);

        final Cookie cookie_delieverdate = new Cookie("delieverdate", delieverdate);
        cookie_delieverdate.setMaxAge(31536000); // One year
        cookie_delieverdate.setPath("/");
        response.addCookie(cookie_delieverdate);

        final Cookie cookie_remark = new Cookie("remark", remark);
        cookie_delieverdate.setMaxAge(31536000); // One year
        cookie_delieverdate.setPath("/");
        response.addCookie(cookie_remark);
    }

    @Data
    class OrderCalculationResult {
        private int qty;
        private int total;
        private int tax;
    }
}
