package com.stgo.taostyle.service;

public class WechatPayment {

    /**
     * get wechat paymet qrcode.
     * 
     * @param userId
     *            current user id, will be used as order number, to avoid duplicated payment.
     * @return
     * 
     *         public BufferedImage getWeChatPaymentImage(Integer userId) { HashMap<String, String> paramMap = new
     *         HashMap<>(); paramMap.put("appid", "xxxxxxxxxxxx"); //appid：每个公众号都有一个appid paramMap.put("mch_id",
     *         "11111111111"); //商户号：开通微信支付后分配 //随机数 paramMap.put("nonce_str", RandomUtil.getRandomString(32,
     *         RandomUtil.LETTER_AND_NUMBER_RANGE)); paramMap.put("body", "香辣烤翅"); //商品描述 //商户订单号：用户id + “|” + 随机16位字符
     *         paramMap.put("out_trade_no", userId + "|" + RandomUtil.getRandomString(16,
     *         RandomUtil.LETTER_AND_NUMBER_RANGE)); paramMap.put("total_fee", 1000); //金额必须为整数 单位为分
     *         paramMap.put("spbill_create_ip", PaymentUtil.localIp()); //本机的Ip paramMap.put("notify_url",
     *         this.notifyUrl); //支付成功后，回调地址 paramMap.put("trade_type", "NATIVE"); //交易类型 paramMap.put("product_id",
     *         "100001"); // 商户根据自己业务传递的参数 当trade_type=NATIVE时必填 //根据微信签名规则，生成签名。随机参数可以在商户后台管理系统中进行设置。
     *         paramMap.put("sign", PaymentUtil.getSignature(paramMap, "beGPax3F1EtxxxxxxofcerMRqNvt9XJ2"));
     * 
     *         String xmlData = PaymentUtil.mapToXml(paramMap);//把参数转换成XML数据格式
     * 
     *         String codeUrl = getCodeUrl(xmlData); //获取二维码链接
     * 
     *         return PaymentUtil.encodeQrcode(codeUrl); //将二维码链接信息编码成二维码图片，用BufferedImage对象表示
     * 
     *         }
     */

    /**
     * get the URL in QRcode which is actually the payment URL from WeChat.
     * 
     * @param xmlData
     * @return
     * 
     *         private String getCodeUrl(String xmlData) { String resXml = HttpUtil.postData(WX_PAYMENT_API_URL,
     *         xmlData); String code_url = ""; Map<String, Object> map; try { map = PaymentUtil.getMapFromXML(resXml);
     *         Object returnCode = map.get("return_code"); if(PaymentUtil.SUCCESS.equals(returnCode)) { Object
     *         resultCode = map.get("result_code"); if(PaymentUtil.SUCCESS.equals(resultCode)) { code_url =
     *         map.get("code_url").toString(); } } } catch (Exception e) { return ""; }
     * 
     *         return code_url; }
     */
}
