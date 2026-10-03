package com.stgo.taostyle.web;

import javax.servlet.http.HttpServletRequest;

import com.stgo.taostyle.domain.Customize;
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.TextContent;
import com.stgo.taostyle.service.CustomizeService;
import com.stgo.taostyle.service.MediaUploadService;
import com.stgo.taostyle.service.TextContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StyleUtil {

	@Autowired
	private TaoUtil taoUtil;
	@Autowired
	private CustomizeService customizeService;
	@Autowired
	private MediaUploadService mediaUploadService;
	@Autowired
	private TextContentService textContentService;

	public void initializeToStyle1(HttpServletRequest request, Person person) {
		// ============customizes==================
		// app_name(set it with person name as default value)
		createACustomize(request, CC.app_name, person.getName(), person);
		// language
		createACustomize(request, "show_AboveMenu", CC.TRUE, person);
		createACustomize(request, "lang1", "fr", person);
		createACustomize(request, "lang2", "en", person);
		// contentTypes
		createACustomize(request, "CONTENTTYPE_1", "HTML", person);
		createACustomize(request, "CONTENTTYPE_2", "FEATURE", person);
		createACustomize(request, "CONTENTTYPE_3", "SERVICE", person);
		createACustomize(request, "CONTENTTYPE_10", "LOCATION", person);
		createACustomize(request, "default_feature_menu", "menu_2", person);
		// location
		createACustomize(request, "MAP_POS_X2", "45.497634", person);
		createACustomize(request, "MAP_POS_Y2", "-73.554010", person);
		// hide something
		createACustomize(request, "show_footArea", "", person);
		// ================textContent=================
		generateTextContent("en_menu_1", "ABOUT " + person.getName().toUpperCase(), person);
		generateTextContent("en_menu_2", "FEATURE", person);
		generateTextContent("en_menu_3", "CATALOGUE", person);
		generateTextContent("en_menu_10", "CONTACT", person);

		generateTextContent("fr_menu_1", "A PROPOSE" + person.getName().toUpperCase(), person);
		generateTextContent("fr_menu_2", "RECOMMANDE", person);
		generateTextContent("fr_menu_3", "CATALOGUE", person);
		generateTextContent("fr_menu_10", "CONTACT", person);

		generateTextContent("zh_menu_1", "关于" + person.getName().toUpperCase(), person);
		generateTextContent("zh_menu_2", "今日推荐", person);
		generateTextContent("zh_menu_3", "产品目录", person);
		generateTextContent("zh_menu_10", "联系我们", person);

		// mainpage(html)
		TextContent textContent = textContentService.findContentsByKeyAndPerson("fr_html_1_0_0_Content", taoUtil.getAdam());
		if (textContent != null) {
			generateTextContent("fr_html_1_0_0_Content", textContent.getContent(), person);
		}
		textContent = textContentService.findContentsByKeyAndPerson("en_html_1_0_0_Content", taoUtil.getAdam());
		if (textContent != null) {
			generateTextContent("en_html_1_0_0_Content", textContent.getContent(), person);
		}
		textContent = textContentService.findContentsByKeyAndPerson("zh_html_1_0_0_Content", taoUtil.getAdam());
		if (textContent != null) {
			generateTextContent("zh_html_1_0_0_Content", textContent.getContent(), person);
		}

		// footer
		String footerContent = "© 2022 " + person.getName();
		generateTextContent("en_footer_copyright", footerContent, person);
		generateTextContent("fr_footer_copyright", footerContent, person);
		generateTextContent("zh_footer_copyright", footerContent, person);
		// contact us
		generateTextContent("en_text_Contact_Titles_2", "Contact", person);
		generateTextContent("fr_text_Contact_Titles_2", "Contact", person);
		generateTextContent("zh_text_Contact_Titles_2", "联系我们", person);

		textContent = textContentService.findContentsByKeyAndPerson("en_text_Contact_Contents_3", taoUtil.getAdam());
		if (textContent != null) {
			generateTextContent("en_text_Contact_Contents_3", textContent.getContent(), person);
		}
		textContent = textContentService.findContentsByKeyAndPerson("fr_text_Contact_Contents_3", taoUtil.getAdam());
		if (textContent != null) {
			generateTextContent("fr_text_Contact_Contents_3", textContent.getContent(), person);
		}
		textContent = textContentService.findContentsByKeyAndPerson("zh_text_Contact_Contents_3", taoUtil.getAdam());
		if (textContent != null) {
			generateTextContent("zh_text_Contact_Contents_3", textContent.getContent(), person);
		}

		// ================logo=================
		MediaUpload logo = mediaUploadService.findMediaByKeyAndPerson("logo", taoUtil.getAdam());
		if (logo != null) {
			MediaUpload mediaUpload = new MediaUpload();
			mediaUpload.setContent(logo.getContent());
			mediaUpload.setContentType(logo.getContentType());
			mediaUpload.setFilesize(logo.getFilesize());
			mediaUpload.setPerson(person);
			mediaUpload.setFilepath("logo");
			mediaUploadService.persist(mediaUpload);
		}
		// ================bell================
		MediaUpload bell = mediaUploadService.findMediaByKeyAndPerson("bell", taoUtil.getAdam());
		if (bell != null) {
			MediaUpload mediaUpload = new MediaUpload();
			mediaUpload.setContent(bell.getContent());
			mediaUpload.setContentType(bell.getContentType());
			mediaUpload.setFilesize(bell.getFilesize());
			mediaUpload.setPerson(person);
			mediaUpload.setFilepath("bell");
			mediaUploadService.persist(mediaUpload);
		}
		// ================qrcode=================
		createACustomize(request, "show_QRCode", CC.TRUE, person);
	}

	public void generateTextContent(String posInPage, String content, Person person) {
		TextContent textContent = textContentService.findContentsByKeyAndPerson(posInPage, person);
		textContent = textContent == null ? new TextContent() : textContent;
		textContent.setContent(content);
		textContent.setPosInPage(posInPage);
		textContent.setPerson(person);
		textContentService.persist(textContent);
	}

	// it's good for small restaurant: bigger horizontal image and bigger space for
	// layout.
	public void initializeToStyle2(HttpServletRequest request, Person person) {
		initializeToStyle1(request, person);
		createACustomize(request, CC.app_ContentManager, CC.TRUE, person);// when someone is promoted to be a manager,
		// shall we set his name here?
		createACustomize(request, "dsp_pendingTime", CC.TRUE, person);
		createACustomize(request, "img_service_h", "720", person);
		createACustomize(request, "img_service_w", "960", person);
		createACustomize(request, "img_service_thum_h", "230", person);
		createACustomize(request, "img_service_thum_w", "270", person);
		createACustomize(request, "need_calculate_price", CC.TRUE, person);
		createACustomize(request, "service_number_lg", "4", person);
		createACustomize(request, "service_number_md", "6", person);
		createACustomize(request, "service_number_sm", "6", person);
		createACustomize(request, "service_number_xs", "12", person);

		createACustomize(request, "show_service_cBox", CC.TRUE, person);
		createACustomize(request, "show_status_area", CC.TRUE, person);
		createACustomize(request, "show_status_bell", CC.TRUE, person);
		createACustomize(request, "show_status_break", CC.TRUE, person);
		createACustomize(request, "en_show_status_message1", "My Order", person);
		createACustomize(request, "en_show_status_category1", "MENU", person);
		createACustomize(request, "show_status_total", "false", person);

		createACustomize(request, "y_status", "50", person);
		createACustomize(request, "auto_combine_print", CC.TRUE, person);

	}

	// it's good for big restaurant: having floating left bar and support multi
	// times.
	public void initializeToStyle3(HttpServletRequest request, Person person) {
		initializeToStyle2(request, person);
		createACustomize(request, "support_subMenu", CC.TRUE, person);
		createACustomize(request, "CONTENTTYPE_3_1", "FEATURE", person);
		createACustomize(request, "default_feature_menu", "menu_3_1", person);

		createACustomize(request, "support_note", "false", person);
		createACustomize(request, "leftbar_fixed", CC.TRUE, person);
		createACustomize(request, "leftbar_item_padding", "20", person);
		createACustomize(request, "background_leftbar", "78603D", person);
		createACustomize(request, "statusbar_border-radius", "10", person);

		createACustomize(request, "support_times", "false", person);
		createACustomize(request, "threshold_hiding_leftbar", "800", person);

	}

	// it's a super simple style, without menu, nor language bar.
	public void initializeToStyle4(HttpServletRequest request, Person person) {
		initializeToStyle3(request, person);
		createACustomize(request, "show_Menu", "", person);
		createACustomize(request, "dsp_status_OnTop", CC.TRUE, person);
		createACustomize(request, "y_status", "0", person);
		createACustomize(request, "show_status_category", "MENU", person);
		createACustomize(request, "statusbar_border-radius", "0", person);
	}

	public void createACustomize(HttpServletRequest request, String cusKey, String value, Person person) {
		Customize customize = customizeService.findCustomizeByKeyAndPerson(cusKey, person);
		customize = customize == null ? new Customize() : customize;
		customize.setCusKey(cusKey);
		customize.setCusValue(value);
		customize.setPerson(person);
		customizeService.persist(customize);
		request.getSession().setAttribute(cusKey, value);
	}

	public void initializeToStyleK5(HttpServletRequest request, Person person) {
		createACustomize(request, "app_ContentManager", "true", person);
		createACustomize(request, "app_name", person.getName(), person);
		createACustomize(request, "app_WebsiteAddress", "https://www.shareTheGoodOnes.com/" + person.getName(), person);
		createACustomize(request, "auto_combine_print", "true", person);
		createACustomize(request, "background_leftbar", "78603D", person);
		createACustomize(request, "contact_phone", "1234567", person);
		createACustomize(request, "CONTENTTYPE_1", "MAINPAGE", person);
		createACustomize(request, "CONTENTTYPE_10", "LOCATION", person);
        createACustomize(request, "CONTENTTYPE_2", "FEATURE", person);
        createACustomize(request, "CONTENTTYPE_3", "FEATURE", person);
        createACustomize(request, "CONTENTTYPE_4", "FEATURE", person);
        createACustomize(request, "CONTENTTYPE_6", "FEATURE", person);
        createACustomize(request, "CONTENTTYPE_7", "FEATURE", person);
        createACustomize(request, "CONTENTTYPE_8", "FEATURE", person);
        createACustomize(request, "CONTENTTYPE_9", "FEATURE", person);
        createACustomize(request, "default_feature_menu", "menu_2", person);
		createACustomize(request, "dsp_pendingTime", "true", person);
		createACustomize(request, "en_show_status_category1", "MENU", person);
		createACustomize(request, "en_show_status_message1", "My Order", person);
		createACustomize(request, "img_service_h", "720", person);
		createACustomize(request, "img_service_thum_h", "170", person);
		createACustomize(request, "img_service_thum_w", "270", person);
		createACustomize(request, "img_service_w", "960", person);
		createACustomize(request, "leftbar_fixed", "true", person);
		createACustomize(request, "leftbar_item_padding", "20", person);
		createACustomize(request, "link_log1", "menu_2", person);
		createACustomize(request, "link_log2", "menu_6", person);
		createACustomize(request, "link_log3", "menu_7", person);
		createACustomize(request, "MAP_POS_X2", "45.497634", person);
		createACustomize(request, "MAP_POS_Y2", "-73.554010", person);
		createACustomize(request, "need_calculate_price", "true", person);
		createACustomize(request, "OrderNumber", "5", person);
		createACustomize(request, "service_height", "190", person);
		createACustomize(request, "service_number_lg", "4", person);
		createACustomize(request, "service_number_md", "6", person);
		createACustomize(request, "service_number_sm", "6", person);
		createACustomize(request, "service_number_xs", "12", person);
		createACustomize(request, "show_footArea", "false", person);
		createACustomize(request, "show_QRCode", "true", person);
		createACustomize(request, "show_service_cBox", "true", person);
		createACustomize(request, "show_status_area", "true", person);
		createACustomize(request, "show_status_bell", "true", person);
		createACustomize(request, "show_status_break", "true", person);
		createACustomize(request, "show_status_submit", "false", person);
		createACustomize(request, "show_status_total", "true", person);
		createACustomize(request, "statusbar_border-radius", "10", person);
		createACustomize(request, "statusbar_border_radius", "15", person);
		createACustomize(request, "y_status", "0", person);
		createACustomize(request, "support_note", "false", person);
		createACustomize(request, "support_subMenu", "true", person);
		createACustomize(request, "support_times", "true", person);
		createACustomize(request, "threshold_hiding_leftbar", "800", person);
		//create default menu
		//the first is about.
	}
}
