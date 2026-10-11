package com.stgo.taostyle.web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSender;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import com.stgo.taostyle.SendGridExample;
import com.stgo.taostyle.config.spring.SpringApplicationContext;
import com.stgo.taostyle.domain.Person;

public class TaoEmail{
	
	private static final boolean useSendGrid = true;

    public static void sendJobApplyEmail(
            CommonsMultipartFile content,
            HttpServletRequest request,
            Person person) {
        String emailContent = buildEmailContent(request, person);

        // email send by applier
        Object managerEmail = request.getSession().getAttribute(CC.app_ManagerEmail);
        if (managerEmail != null) {
            try {
                sendMessage(managerEmail.toString(), "JOB APPLICATION", request.getParameter("email"), emailContent,
                        content);
            } catch (Exception emailException) {
                System.out.println(emailException);
            }
        }

        // email send by company.
        String link = "";
        Object websiteAddress = request.getSession().getAttribute(CC.app_WebsiteAddress);
        if (websiteAddress != null) {
            link = link + "<p align='center'><a href='" + websiteAddress + "/dashboard'>check uploaded resume</a>";
        }
        try {
            sendMessage(request.getParameter("email"), "JOB APPLICATION", managerEmail.toString(), emailContent + link,
                    content);
        } catch (Exception emailException) {
            sendMessage("info@sharethegoodones.com", "JOB APPLICATION", managerEmail.toString(), request
                    .getParameter("email")
                    + " wanted to send you an application, but failed(could because the email address was not acceptable by the mail server, folowing is the basic infomation:<br/>"
                    + emailContent + link, content);
        }
    }

    private static String buildEmailContent(
            HttpServletRequest request,
            Person person) {

        Object contentManager = request.getSession().getAttribute(CC.app_ContentManager);
        StringBuilder tSB = new StringBuilder("<b>Hi, " + contentManager != null ? contentManager.toString()
                : "" + "<br/> An application is delivered into system, following are the infomation:</b><br/>");
        tSB.append("firstname: ").append(request.getParameter("firstname")).append("<br/>").append("middlename: ")
                .append(request.getParameter("middlename")).append("<br/>").append("lastname: ")
                .append(request.getParameter("lastname")).append("<br/>").append("phone: ")
                .append(request.getParameter("phone")).append("<br/>").append("mobile: ")
                .append(request.getParameter("mobile")).append("<br/>").append("email: ")
                .append(request.getParameter("email")).append("<br/>").append("address: ")
                .append(request.getParameter("address")).append("<br/>").append("city: ")
                .append(request.getParameter("city")).append("<br/>").append("province: ")
                .append(request.getParameter("province")).append("<br/>").append("country: ")
                .append(request.getParameter("country")).append("<br/>").append("hearedfrom: ")
                .append(request.getParameter("hearedfrom")).append("<br/>").append("leggal: ")
                .append(request.getParameter("leggal")).append("<br/>").append("workpermit: ")
                .append(request.getParameter("workpermit")).append("<br/>").append("position: ")
                .append(request.getParameter("position")).append("<br/>").append("education: ")
                .append(request.getParameter("education")).append("<br/>").append("program: ")
                .append(request.getParameter("program")).append("<br/>").append("skills: ")
                .append(request.getParameter("skills")).append("<br/>").append("experience: ")
                .append(request.getParameter("experience")).append("<br/>").append("salary: ")
                .append(request.getParameter("salary")).append("<br/>").append("worktime: ")
                .append(request.getParameter("worktime")).append("<br/>").append("nonavailable: ")
                .append(request.getParameter("nonavailable")).append("<br/>").append("oldstaff: ")
                .append(request.getParameter("oldstaff")).append("<br/>").append("other: ")
                .append(request.getParameter("other")).append("<br/>");

        return tSB.toString();
    }

    private static HashMap<String, Long> map = new HashMap<String, Long>(); // used for preventing sending to a mail box
                                                                            // too often.
    //is used by the websites of each clients, like job apply, comments on the mainpage, will be lead to here of each website, 
    //then will be forward to the computer at home, (to the path/mailProxy, then from there will call into sendMessageWithFlag again
    //and this time, the last flag will be false
    public static void sendMessage(
            String mailFrom,
            String subject,
            String mailTo,
            String message,
            CommonsMultipartFile attach) {
    	sendMessageWithFlag(mailFrom, subject, mailTo, message, null, true);
    }

    static boolean stopSendingEmail = true;
    static void sendMessageWithFlag(
            String mailFrom,
            String subject,
            String mailTo,
            String message,
            CommonsMultipartFile attach,
            boolean redirectable) {
        if(stopSendingEmail){
            return;
        }
        Long lastsend = map.get(mailFrom + mailTo + message);
        long now = new Date().getTime();
        if (lastsend != null && (now - lastsend < 600000)) { // less than 10 minuts.
            System.out.println("*********too often, email will not send out.");
            return;
        }
        map.put(mailFrom + mailTo + message, now); // it's already more than 10 minutes

		try {
			InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
			TaoDebug.error("Exception when getting localhost ip address:", e1);
		}
		
        if(redirectable) {
        	try {
        		MultiValueMap<String, String> headers = new HttpHeaders();
                headers.set("Content-Type", MediaType.APPLICATION_JSON_UTF8_VALUE);
                
                JSONObject dto = new JSONObject();
                dto.put("subject", subject);
                dto.put("content", message);
                dto.put("email", mailTo);
                dto.put("password", "Ollwin_01");
        		
                String mailJsonStr = dto.toString();
                HttpEntity<String> request = new HttpEntity<>(mailJsonStr, headers);
                
                new RestTemplate().postForEntity("http://mailProxy.sharethegoodones.com/mailProxy", request, String.class);
                
            } catch (Exception e) {
            	e.printStackTrace();
            }
        } else if(useSendGrid) {
        	try {
				SendGridExample.send(subject, mailTo, message);
			} catch (IOException e) {
				TaoDebug.error("IO exception when sending email to Subject:" + subject +"  address: " + mailTo + "  body: " + message, e);
			}
        }else {
	        MailSender tMailSender =
	                SpringApplicationContext.getApplicationContext().getBean("mailSender", MailSender.class);
	        MimeMessage mimeMessage = ((JavaMailSender) tMailSender).createMimeMessage();
	        MimeMessageHelper helper = null;
	        try {
	            helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
	            // Multipart multipart = new MimeMultipart();
	            // //content
	            // BodyPart contentPart = new MimeBodyPart();
	            // contentPart.setContent(message, "text/html;charset=utf-8");
	            // multipart.addBodyPart(contentPart);
	            // //attachment
	            // if (attach != null) {
	            // BodyPart attachmentBodyPart = new MimeBodyPart();
	            // // FileDataSource source = new FileDataSource(new File(attach.getStorageDescription()));
	            // InputStreamDataSource source = new InputStreamDataSource(attach.getInputStream(),
	            // attach.getFileItem().getName(), attach.getContentType());
	            // attachmentBodyPart.setDataHandler(new DataHandler(source));
	            //
	            // // 网上流传的解决文件名乱码的方法，其实用MimeUtility.encodeWord就可以很方便的搞定
	            // // 这里很重要，通过下面的Base64编码的转换可以保证你的中文附件标题名在发送时不会变成乱码
	            // //sun.misc.BASE64Encoder enc = new sun.misc.BASE64Encoder();
	            // //messageBodyPart.setFileName("=?GBK?B?" + enc.encode(attachment.getName().getBytes()) + "?=");
	            //
	            // //MimeUtility.encodeWord可以避免文件名乱码
	            // attachmentBodyPart.setFileName(MimeUtility.encodeWord(attach.getFileItem().getName()));
	            // multipart.addBodyPart(attachmentBodyPart);
	            // }
	            // mimeMessage.setContent(multipart);
	            mimeMessage.setContent(message, "text/html;charset=utf-8");
	            helper.setTo(mailTo);
	            helper.setSubject(subject);
	            helper.setFrom(mailFrom);
	        } catch (Exception e) {
	            System.out.println("*********Sending email failed!" + mailTo + "|" + subject + "|" + message);
	        }
	        System.out.println("*********ready to send out email!");
	        ((JavaMailSender) tMailSender).send(mimeMessage);
	        System.out.println("*********email send! to:" + mailTo + "|" + subject + "|" + message);
        }
    }
    
    public static boolean isValidEmail(String email) {
        if (StringUtils.isBlank(email)) // not null
            return false;

        int idxOfDot = email.lastIndexOf(".");
        if (idxOfDot == email.length()) // . not at the end
            return false;

        if (idxOfDot < 3) // . not at the head
            return false;

        int idxOfAt = email.indexOf("@"); // have @
        if (idxOfAt < 1)
            return false;

        if (idxOfDot - idxOfAt < 1) // @ is before .
            return false;

        if (email.substring(idxOfAt, idxOfDot).indexOf(".") > 0) // there's only 1 . after @
            return false;

        return true;
    }

}
