package com.stgo.taostyle.web;

import com.alibaba.fastjson.JSON;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TaoCloudPrint extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(TaoCloudPrint.class);
    
	private static final int XP_Yun_Type = 1;
	
	public static final String URL_FeieYun = "http://api.feieyun.cn/Api/Open/";// 不需要修改
	public static final String USER_FeieYun = "casproteus@hotmail.com";// *必填*：账号名
	public static final String UKEY_FeieYun = "H5RGvjkq7hYE3RBb";// *必填*: 飞鹅云后台注册账号后生成的UKEY 【备注：这不是填打印机的KEY】

	//server in China
	private static final String URL_XpYun = "https://open.xpyun.net/api/openapi/xprinter/print";
	private static final String URL_XpYun_Label = "https://open.xpyun.net/api/openapi/xprinter/printLabel";
	//server in Germany
//	private static final String URL_XpYun = "https://gm.open.xpyun.net/api/openapi/xprinter/print";
//	private static final String URL_XpYun_Label = "https://gm.open.xpyun.net/api/openapi/xprinter/printLabel";


	public static final String USER_XpYun = "dmontreal@hotmail.com";// *必填*：账号名
	public static final String UKEY_XpYun = "7c8d2a3f06c74b22917103a4e70b75c9";// *必填*: 芯烨云后台注册账号后生成的UKEY 【备注：这不是填打印机的KEY】

	private static final BlockingQueue<CloudPrintTask> cloudPrintTasks = new LinkedBlockingQueue<>();

	private static TaoCloudPrint instance = null;

	public static TaoCloudPrint getInstance() {
		if (instance == null) {
			instance = new TaoCloudPrint();
			instance.start();
		}
		return instance;
	}

	private TaoCloudPrint() {
	}

	public void sendToPrinterCloud(String sn, String content, boolean isLabelPrinter) {
		logger.info("cloudprinting received(" + sn + "):" + content);
		CloudPrintTask cloudPrintTask = new CloudPrintTask();
		int p = sn.indexOf("_");
		if(p > 0) {
			cloudPrintTask.setPrinterType(Integer.parseInt(sn.substring(0, p)));
			sn = sn.substring(p + 1);
		}
		cloudPrintTask.setSn(sn);
		cloudPrintTask.setContent(content);
		cloudPrintTask.setLabelPrinter(isLabelPrinter);
		logger.info("cloudprinting about to add into tasks, size:" + cloudPrintTasks.size());
		cloudPrintTasks.offer(cloudPrintTask);
		logger.info("cloudprinting added into tasks, size:" + cloudPrintTasks.size());
	}

	private static final int MAX_RETRY = 60;
	public void run() {
		while (true) {
			try {
				CloudPrintTask task = cloudPrintTasks.take(); // 获取任务
				boolean success = false;
				int attempts = 0;

				while (!success && attempts < MAX_RETRY) {
					try {
						processPrintTask(task);
						success = true;
					} catch (Exception ex) {
						attempts++;
						logger.warn("Attempt {} failed for task {}, retrying...", attempts, task.getSn(), ex);
						Thread.sleep(10000); // 给设备一点缓冲时间
					}
				}

				if (!success) {
					logger.error("Task {} failed after {} attempts, giving up.", task.getSn(), MAX_RETRY);
				}
			} catch (Exception e) {
				logger.error("Unexpected error in printing thread", e);
			}
		}
	}

	private void processPrintTask(CloudPrintTask cloudPrintTask) {
		if (cloudPrintTask.getPrinterType() == XP_Yun_Type) {
			PrintRequest request = new PrintRequest();
			request.setUser(USER_XpYun);
			request.setTimestamp(String.valueOf(System.currentTimeMillis()));
			request.setSign(sign(USER_XpYun + UKEY_XpYun + request.getTimestamp()));
			request.setSn(cloudPrintTask.getSn());
			request.setContent(cloudPrintTask.getContent());
			request.setCopies(1);
			request.setVoice(3);

			String jsonRequest = JSON.toJSONString(request);
			logger.info("cloudprinting sending to xp:" + cloudPrintTask.getSn() + "--content:" + cloudPrintTask.getContent());

			String resp = HttpClientUtil.doPostJSON(cloudPrintTask.isLabelPrinter() ? URL_XpYun_Label : URL_XpYun, jsonRequest);
			logger.info("cloudprinting xp response: " + resp);
		} else {
			RequestConfig config = RequestConfig.custom().setSocketTimeout(30000).setConnectTimeout(30000).build();
			try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(config).build()) {
				List<NameValuePair> nvps = new ArrayList<>();
				HttpPost post = new HttpPost(URL_FeieYun);
				String STIME = String.valueOf(System.currentTimeMillis() / 1000);
				nvps.add(new BasicNameValuePair("user", USER_FeieYun));
				nvps.add(new BasicNameValuePair("stime", STIME));
				nvps.add(new BasicNameValuePair("sig", DigestUtils.shaHex(USER_FeieYun + UKEY_FeieYun + STIME))); // 生成签名字符串
				nvps.add(new BasicNameValuePair("apiname",
						cloudPrintTask.isLabelPrinter() ? "Open_printLabelMsg" : "Open_printMsg"));// 固定值,不需要修改
				nvps.add(new BasicNameValuePair("sn", cloudPrintTask.getSn()));
				nvps.add(new BasicNameValuePair("content", cloudPrintTask.getContent()));
				nvps.add(new BasicNameValuePair("times", "1"));// 打印联数

				post.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));
				logger.info("cloudprinting sending to feie:" + cloudPrintTask.getSn());

				try (CloseableHttpResponse response = httpClient.execute(post)) {
					if (response.getStatusLine().getStatusCode() == 200) {
						HttpEntity entity = response.getEntity();
						if (entity != null) {
							String result = EntityUtils.toString(entity);
							logger.info("cloudprinting feie response: " + result);
						}
					} else {
						logger.error("FeieYun printing failed. HTTP Code: " + response.getStatusLine().getStatusCode());
					}
				}
			} catch (Exception e) {
				logger.error("FeieYun printing error", e);
			}
		}
	}
	
	/**
    * The dilute signature
    * @param signSource - The source string
    * @return
    */
   private static String sign(String signSource) {
      try {
          MessageDigest crypt = MessageDigest.getInstance("SHA-1");
          crypt.reset();
          crypt.update(signSource.getBytes("UTF-8"));
          return byteToHex(crypt.digest());
      } catch (Exception e) {
      	logger.error("Sign generation error", e);
      	return "";
      }
   }

    private static String byteToHex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }
    
	private class PrintRequest {
		private String timestamp;
		private String user;
		private int copies;
		private String content;
		private String sn;
		private String sign;
		private int voice;

		public String getUser() {
			return user;
		}
		public void setUser(String user) {
			this.user = user;
		}

		public int getVoice() {
			return voice;
		}
		public void setVoice(int i) {
			this.voice = i;
		}

		public String getSign() {
			return sign;
		}
		public void setSign(String sign) {
			this.sign = sign;
		}

		public int getCopies() {
			return copies;
		}
		public void setCopies(int i) {
			this.copies = i;
		}

		public String getContent() {
			return content;
		}
		public void setContent(String content) {
			this.content = content;
		}

		public String getSn() {
			return sn;
		}
		public void setSn(String sn) {
			this.sn = sn;
		}

		public String getTimestamp() {
			return timestamp;
		}
		public void setTimestamp(String timestamp) {
			this.timestamp = timestamp;
		}
	}
	
	private class CloudPrintTask {
		private String sn;
		private String content;
		private boolean isLabelPrinter;
		private int printerType;

		public String getSn() {
			return sn;
		}

		public void setSn(String sn) {
			this.sn = sn;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public boolean isLabelPrinter() {
			return isLabelPrinter;
		}

		public void setLabelPrinter(boolean isLabelPrinter) {
			this.isLabelPrinter = isLabelPrinter;
		}

		public int getPrinterType() {
			return printerType;
		}

		public void setPrinterType(int printerType) {
			this.printerType = printerType;
		}
	}
	
}