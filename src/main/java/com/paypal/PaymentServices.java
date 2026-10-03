package com.paypal;

import java.util.*;

import javax.servlet.http.HttpSession;

import com.paypal.api.payments.*;
import com.paypal.base.rest.*;
import com.stgo.taostyle.web.CC;

public class PaymentServices {

	public String authorizePayment(HttpSession session, OrderDetail orderDetail, String MODE) throws PayPalRESTException {

		Payer payer = getPayerInformation(orderDetail.getProductName());
		RedirectUrls redirectUrls = getRedirectURLs(session);
		List<Transaction> listTransaction = getTransactionInformation(orderDetail);

		Payment requestPayment = new Payment();
		String profileID = (String)session.getAttribute(CC.profileID);
		if(profileID != null && profileID.length() >= 22) {
			requestPayment.setExperienceProfileId(profileID);//"XP-7TR7-J225-ZLE6-4UQG");//0-"XP-X6P4-GMT2-V2JV-E396"(orderProfile")  1-"XP-37K5-Z7BE-CFN2-M9HT"(ebayProfile)
		}
		requestPayment.setTransactions(listTransaction);
		requestPayment.setRedirectUrls(redirectUrls);
		requestPayment.setPayer(payer);
		requestPayment.setIntent("sale");
		
		String clientId = (String)session.getAttribute(CC.client_id);//"AcEkcPFFkElC0qzAacfvBobtUwNxxb85_I7-eUddJqQM6_xzWji8V4qAftA9BbZqqhbA825ZmnLY9DVr";//"AUiU7qnNgWSt-3sX656c0-v5mHu2m5au9VEbc3PER7o47lM7a2KZm9kzLRCGs7U2IUXlnZCkTEPxKodm";
		String clientSecret = (String)session.getAttribute(CC.client_secret);//"EOVmL4X9qDK9PBatXRt2k5gZoJlMpw-WNWjEwRvmpvJHo5bmfBcQYH6G1-eLoYiEiIIUYqDsRhxb1gjl";//"ELS1GB0LaMV7OHKeSAc6OKm36zTZ0Ym0yNuqOrszl5DP7k754ezjDmbxxGVhwaN_HKLRJW9Omgsy1Nol";
		APIContext apiContext = new APIContext(clientId, clientSecret, MODE);

		try {
			// 配置禁用收货地址
			InputFields inputFields = new InputFields();
			inputFields.setNoShipping(1); // 1 表示禁用收货地址

			// 创建新的 Web Experience Profile
			WebProfile webProfile = new WebProfile();
			webProfile.setName("NoShippingProfile_" + System.currentTimeMillis());
			webProfile.setInputFields(inputFields);

			WebProfile createdProfile = webProfile.create(apiContext);

			requestPayment.setExperienceProfileId(createdProfile.getId());

		} catch (PayPalRESTException e) {
			e.printStackTrace();
		}

		Payment approvedPayment = requestPayment.create(apiContext);

		return getApprovalLink(approvedPayment);

	}

	private Payer getPayerInformation(String payerName) {
		Payer payer = new Payer();
		payer.setPaymentMethod("paypal");

		PayerInfo payerInfo = new PayerInfo();
		payerInfo.setFirstName(payerName);//.setLastName("Peterson").setEmail("william.peterson@company.com");

		payer.setPayerInfo(payerInfo);

		return payer;
	}

	private RedirectUrls getRedirectURLs(HttpSession session) {
		RedirectUrls redirectUrls = new RedirectUrls();
		String website = (String) session.getAttribute(CC.app_WebsiteAddress);
		if(website == null || website.length() < 3) { //I think the shortest domain name should be longer than 3, right?
			website = "https://www.sharethegoodones.com";
		}else if(!website.toLowerCase().startsWith("http://") && !website.toLowerCase().startsWith("https://")){
			website = "https://" + website;
		}
	    redirectUrls.setCancelUrl(website + "/showSelection");
	    redirectUrls.setReturnUrl(website + "/services/returnFromPaypal");
	     
	    return redirectUrls;
    }

	private List<Transaction> getTransactionInformation(OrderDetail orderDetail) {
		Details details = new Details();
	    details.setShipping(orderDetail.getShipping());
	    details.setSubtotal(orderDetail.getSubtotal());
	    details.setTax(orderDetail.getTax());
	 
	    Amount amount = new Amount();
	    amount.setCurrency("CAD");
	    amount.setTotal(orderDetail.getTotal());
	    amount.setDetails(details);
	 
	    Transaction transaction = new Transaction();
	    transaction.setAmount(amount);
	    transaction.setDescription(orderDetail.getProductName());
	     
	    ItemList itemList = new ItemList();
	    List<Item> items = new ArrayList<>();
	     
	    Item item = new Item();
	    item.setCurrency("CAD");
	    item.setName(orderDetail.getProductName());
	    item.setPrice(orderDetail.getSubtotal());
	    item.setTax(orderDetail.getTax());
	    item.setQuantity("1");
	     
	    items.add(item);
	    itemList.setItems(items);
	    transaction.setItemList(itemList);
	 
	    List<Transaction> listTransaction = new ArrayList<>();
	    listTransaction.add(transaction);  
	     
	    return listTransaction;
    }

	private String getApprovalLink(Payment approvedPayment) {
		List<Links> links = approvedPayment.getLinks();
	    String approvalLink = null;
	     
	    for (Links link : links) {
	        if (link.getRel().equalsIgnoreCase("approval_url")) {
	            approvalLink = link.getHref();
	            break;
	        }
	    }      
	     
	    return approvalLink;
    }

	//not used for now, might be used if the customer want to show an extra confirm page.
	public Payment getPaymentDetails(HttpSession session, String paymentId, String CLIENT_ID, String CLIENT_SECRET, String MODE) throws PayPalRESTException {
	    APIContext apiContext = new APIContext(CLIENT_ID, CLIENT_SECRET, MODE);
	    return Payment.get(apiContext, paymentId);
	}
	
	public Payment executePayment(String paymentId, String payerId, String CLIENT_ID, String CLIENT_SECRET, String MODE)
	        throws PayPalRESTException {
	    PaymentExecution paymentExecution = new PaymentExecution();
	    paymentExecution.setPayerId(payerId);
	 
	    Payment payment = new Payment().setId(paymentId);
	 
	    APIContext apiContext = new APIContext(CLIENT_ID, CLIENT_SECRET, MODE);
	 
	    return payment.execute(apiContext, paymentExecution);
	}
}