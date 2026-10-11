package com.stgo.taostyle;

//using SendGrid's Java Library
//https://github.com/sendgrid/sendgrid-java
import com.sendgrid.*;
import java.io.IOException;

public class SendGridExample {
	public static void main(String[] args) throws IOException {
		SendGridExample.send("Sending with SendGrid is Fun", "tao.mtl@hotmail.com", "and easy to do anywhere, even with Java");
	}
	
	public static void send(String subject, String address, String message ) throws IOException  {
		Email from = new Email("service@ShareTheGoodOnes.com");
		Email to = new Email(address);
		Content content = new Content("text/plain", message);
		Mail mail = new Mail(from, subject, to, content);

		SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
		Request request = new Request();
		try {
			request.setMethod(Method.POST);
			request.setEndpoint("mail/send");
			request.setBody(mail.build());
			Response response = sg.api(request);
		} catch (IOException ex) {
			throw ex;
		}
	}
}