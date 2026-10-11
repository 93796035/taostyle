package com.stgo.taostyle.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.paypal.OrderDetail;
import com.paypal.PaymentServices;
import com.paypal.base.rest.PayPalRESTException;
import com.stgo.taostyle.backend.security.TaoEncrypt;
import com.stgo.taostyle.backend.security.UserContextService;
import com.stgo.taostyle.config.spring.SpringApplicationContext;
import com.stgo.taostyle.domain.*;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.Material;
import com.stgo.taostyle.domain.orders.TaxonomyMaterial;
import com.stgo.taostyle.service.*;
import com.stgo.taostyle.service.dto.ImageData;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.LinkedHashSet;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
public class MainController extends BaseController {

    private static final Pattern SPECIAL_SIGNUP_SLASH_PATTERN = Pattern.compile("^(.*?)/(\\d+)\\s*$");
    private static final Pattern PERSON_ID_SUFFIX_PATTERN = Pattern.compile("^(.*)\\((\\d+)\\)\\s*$");
    private static final int DEFAULT_LUCKY_WEIGHT = 1;

	@Autowired
	private PersonService personService;
	@Autowired
	private TaoUtil taoUtil;
	@Autowired
	private TaoImage taoImage;
	@Autowired
	private TaoDebug TaoDebug;
	@Autowired
	private TaoEncrypt TaoEncrypt;
	@Autowired
	private TaoDbUtil TaoDbUtil;
	@Autowired
	private CustomizeService customizeService;
	@Autowired
	private MainOrderService mainOrderService;
	@Autowired
	private MaterialService materialService;
	@Autowired
	private MediaUploadService mediaUploadService;
	@Autowired
	private UserAccountService userAccountService;
	@Autowired
	private TextContentService textContentService;
	@Autowired
	private ServiceService serviceService;
	@Autowired
	private ProductService productService;
	@Autowired
	private PosProductBootstrapService posProductBootstrapService;
	@Autowired
	private CartService cartService;
	@Autowired
	private FeatureService featureService;
	@Autowired
	private TaxonomyMaterialService taxonomyMaterialService;
    @Autowired
    private LuckyDrawRecordService luckyDrawRecordService;
	@Autowired
	TaoSecurity taoSecurity;

	@Autowired
	private HttpSession session;
	public static String ALARM = "ALARM_";

	@Inject
	private UserContextService userContextService;
	@Autowired
	private StyleUtil styleUtil;
	// @Inject
	// private ServerInfo serverInfo; //we don't want to save the image to
	// datafolder anymore.

	// /* in case some client wnat to display the login on main page.*/
	// @RequestMapping(value = "/admin",method = RequestMethod.GET)
	// public String admin(Model model, HttpServletRequest request) {
	// model.addAttribute("display_loginForm", CC.TRUE);
	// return index(model, request);
	// }

	// ===============view part================
	@RequestMapping(value = "/.well-known/acme-challenge/yb0cR7eUynqGbAjjppydYvWxg9Vv_P8iFr4Eg-V4ltE", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<String> ssl() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");
		return new ResponseEntity<String>(
				"yb0cR7eUynqGbAjjppydYvWxg9Vv_P8iFr4Eg-V4ltE._4TklZ8_xkq-tGHfHaZvEdH8vzUpxdbw9eEElHix4m8", headers,
				HttpStatus.OK);
	}
	
	@RequestMapping(value = "/favicon")
	public void favicon(Model model, HttpServletRequest request, HttpServletResponse response) {
		requestForImage("logo", request, response);
	}
	
	/**
	 * Entry1 normal user normal visiting goes to here.
	 */
	@RequestMapping(value = "/{identity}")
	public String clientComing(Model model, HttpServletRequest request, HttpServletResponse response,
			@PathVariable("identity") String identity) {

		HttpSession session = request.getSession();
		if(session == null) {
			TaoDebug.info(request, "!!!!found session i null when request come with identity : {}", identity);
			return "";//TODO: is it better to return index or login?
		}
		TaoDebug.info(TaoDebug.getSB(session), "Entry1, start to switching user to {}:", identity);

		String returnString = systemCommandCheck(model, request, identity);
		if (returnString != null) {
			TaoDebug.info(TaoDebug.getSB(session), "it's a system command, returning string : {}", identity);
			return returnString;
		}

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, identity);
		} else {
			TaoDebug.info(TaoDebug.getSB(session), "it's in login status, stop switching user: {}", identity);
		}

		return showFromFlashpage(model, request, response);
	}

	/**
	 * Entry2 visiting directly into a page instead of visiting from beginning.
	 * Didn't use full menu key string, because don't want other request like "/"
	 * and "/login" to go here.
	 */
	@RequestMapping(value = "/{client}/menu_{menuIdxString}")
	public String showWebPageWithClientInfo(Model model, HttpServletRequest request, HttpServletResponse response,
			@PathVariable("client") String client, @PathVariable("menuIdxString") String menuIdxString) {
		TaoDebug.info(TaoDebug.getSB(request.getSession()),
				"Entry2, start to showWebPageWithClientInfo client: {}, menuIdxString:{}", client, menuIdxString);
		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return buildPageForMenu(model, request, response, menuIdxString);
	}

	/**
	 * Entry3
	 * 
	 * @param model
	 * @param request
	 * @param client
	 * @param systemCommand
	 * @return
	 */
	@RequestMapping(value = "/{client}/{systemCommand}")
	public String systemCommandGet(Model model, HttpServletRequest request, @PathVariable("client") String client,
			@PathVariable("systemCommand") String systemCommand) {

		TaoDebug.info(TaoDebug.getSB(request.getSession()),
				"Entry3, start to responseSystemCommand client: {}, systemCommand:{}", client, systemCommand);
		if (taoUtil.hasNotLoggedIn(request))
			dirtFlagCommonText = taoUtil.switchClient(request, client);

		String returnString = systemCommandCheck(model, request, systemCommand);
		if (returnString != null)
			return returnString;

		return "login";
	}	
	
	@RequestMapping(value = "/mailProxy", method = RequestMethod.POST)
	public void systemCommandPost(Model model, HttpServletRequest request, @RequestBody String mailJSON) {
		
			JSONObject rjson = new JSONObject(mailJSON);
			// prepare filepath and configuration
			String subject = rjson.getString("subject");
			String content = rjson.getString("content");
			content += "\n\n PLEASE BE NOTICED THAT ACCORDING TO THE LIMITED SERVER SPACE, WE HAVE DELETED REPORT ON SERVER SIDE! \n if you have need to save copy of report on server, please contact \n sales@sharethegoodones.com";
			String email = rjson.getString("email");
			String password = rjson.getString("password");
			if("Ollwin_01".equals(password)) {
				TaoEmail.sendMessageWithFlag("", subject, email, content, null, false);
			}
	}

	/**
	 * default user normal visiting goes to here directly, others reach here from
	 * setClient().
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String showFromFlashpage(Model model, HttpServletRequest request, HttpServletResponse response) {
		if (taoUtil.hasNotLoggedIn(request)) {
			String app_name_inRequest = taoUtil.getAppNameInRequest(request);
			if (StringUtils.isNotBlank(app_name_inRequest)) {
				dirtFlagCommonText = taoUtil.switchClient(request, app_name_inRequest);
			}
		}
		makesureSessionInitialized(request);// if first time visit, session is till empty, then initialise it.

		Object flash_1_URL = request.getSession().getAttribute(CC.flash_1_URL);

		TaoDebug.info(TaoDebug.getSB(request.getSession()), "Entry4, showFromFlashpage, flash_1_URL is: {}",
				flash_1_URL);

		if (flash_1_URL != null && !StringUtils.isBlank(flash_1_URL.toString()))
			return flash_1_URL.toString();// should be "flashpage";
		else
			return buildPageForMenu(model, request, response, null);// if No Flash paged set, then got to main page
																	// directly.
	}

	/**
	 * Didn't use full menu key string, because don't want other request like "/"
	 * and "/login" to go here.
	 * 
	 * @param model
	 * @param request
	 * @param menuIdxString
	 * @return
	 */
	@RequestMapping(value = "/menu_{menuIdxString}")
	public String buildPageForMenu(Model model, HttpServletRequest request, HttpServletResponse response,
			@PathVariable("menuIdxString") String menuIdxString) {
		TaoDebug.info(request, "Entry5, start to buildWebPage under {}, for person: {}, path:{}", menuIdxString);
		if (taoUtil.hasNotLoggedIn(request)) {
			String app_name_inRequest = taoUtil.getAppNameInRequest(request);
			if (StringUtils.isNotBlank(app_name_inRequest)) {
				dirtFlagCommonText = taoUtil.switchClient(request, app_name_inRequest);
			}
		}
		makesureSessionInitialized(request);

		String langPrf = taoUtil.getLangPrfWithDefault(request);
		if (langPrf != null) {
			
			final Cookie cookie = new Cookie("lang_"+ taoUtil.getCurPerson(request).getName(), langPrf.substring(0, langPrf.length() - 1));
			cookie.setMaxAge(31536000); // One year
			cookie.setPath("/");
			response.addCookie(cookie);
		}

		Person person = taoUtil.getCurPerson(request);
		// put it here, because here, have response to use:)
		if (person != null) {
			final Cookie cookie = new Cookie("site", person.toString());
			cookie.setMaxAge(31536000); // One year
			cookie.setPath("/");
			response.addCookie(cookie);
		}
		makesureCommonTextInitialized(model, request, langPrf, person);

		// get every number out from the menuIndex which looks like: 2_1_1 or 2_1 or 2
		if (menuIdxString == null) {
			Object menuIdx = request.getSession().getAttribute(CC.menuIdx);
			menuIdxString = menuIdx == null ? "1" : menuIdx.toString();
		}
		int[] menuIdxes = taoUtil.splitMenuIdxToAry(menuIdxString);
		if (menuIdxes[1] != 0) {
			model.addAttribute("currentSubMenu", "accordion-element-" + menuIdxes[1]);
		}
        prepareLuckyWheelState(model, request, response, person, menuIdxes);

		TaoDebug.info(request, "start to initSubPage for: menu_{}, for person: {}, path:{}", menuIdxString);
		return taoUtil.initSubPage(request, response, model, langPrf, menuIdxes[0], menuIdxes[1], menuIdxes[2], person);
	}

	// to make sure it is initialised. first time visit or the connection suffered
	// disconnection.
	private void makesureSessionInitialized(HttpServletRequest request) {
		HttpSession session = request.getSession();
		Object app_name = session.getAttribute(CC.app_name);
		TaoDebug.info(TaoDebug.getSB(session), "makesureSessionInitialized, app_name insession is: {}", app_name);
		Person person = taoUtil.getCurPerson(request);
		if (person == null || session.getAttribute(CC.CLIENT) == null) {
			TaoDebug.info(TaoDebug.getSB(session), TaoDebug.getSB(session),
					"start to makesureSessionInitialized, cur person is: {}", person.getName());
			taoUtil.reInitSession(request.getSession(), person);
		}

		// if the tableId is set, then add into session. while added a null check, to
		// avoid it will be removed by
		// unexpected request like sharethegoodones.com/showselection,
		// sharethegoodones.com/favrateico..
		String tableID = request.getParameter("t");
		if (tableID != null) {
			session.setAttribute(CC.tableID, tableID);
			TaoDebug.info(TaoDebug.getSB(session), "tableId in request is {}.", tableID);
		}
	}

	// ==============user management==============
	@RequestMapping(value = "/signup", method = RequestMethod.GET)
	public String signupForm(Model model, HttpServletRequest request) {
		List<MainOrder> mainOrders = mainOrderService.findMainOrderByCON(request.getSession().getId());
		if (mainOrders == null || mainOrders.size() == 0 || mainOrders.size() > 1) {
			model.addAttribute("mainOrderList", mainOrders);
		} else {
			MainOrder mainOrder = mainOrders.get(0);
			List<Material> materials = materialService.findAllMaterialsByMainOrder(mainOrder);
			model.addAttribute("materials", materials);
		}
		return "signup";
	}

	@RequestMapping(value = "/signup", method = RequestMethod.POST)
	public String signup(Model model, HttpServletRequest request, HttpServletResponse response) {
		String loginname = request.getParameter("loginname");

		String recaptchaResponse = request.getParameter("g-recaptcha-response");
		if (!verifyRecaptcha(recaptchaResponse)) {
			model.addAttribute("create_error", "reCAPTCHA error");
			return "signup"; // 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佽鍨庨崘锝嗗瘱闂備胶顢婂▍鏇㈠箲閸ヮ剙鐏抽柡鍐ㄧ墕缁€鍐┿亜韫囧海顦﹀ù婊堢畺閺屻劌鈹戦崱娆忓毈缂備降鍔岄妶鎼佸蓟閻斿吋鍎岄柛婵勫劤琚﹂梻浣告惈閻绱炴笟鈧妴浣割潨閳ь剟骞冨▎鎾崇妞ゆ挾鍣ュΛ褔姊婚崒娆戠獢婵炰匠鍏炬稑鈻庨幋鐐存闂佸湱鍎ら〃鎰礊閺嶃劎绡€闂傚牊渚楅崕鎰版煛閸涱喚鍙€闁哄本绋戦埥澶愬础閻愬樊娼绘俊鐐€戦崕鏌ユ嚌妤ｅ啫鐓橀柟瀵稿仜缁犵娀姊虹粙鍖℃敾妞ゃ劌妫濋獮鍫ュΩ閳哄倸鈧鏌﹀Ο渚Ш闁挎稒鐩铏圭磼濡搫顫庨梺绋跨昂閸婃繂鐣烽幋鐘亾閿濆骸鏋熼柣鎾跺枑娣囧﹪顢涘┑鍡楁優濠电姭鍋撳ù鐘差儐閻撳啰鎲稿鍫濈婵炴垶纰嶉鑺ユ叏濮楀棗澧婚柛銈嗘礋閺屸剝寰勭€ｎ亞鍔稿┑鐐茬毞閺呯姴顕ｉ崼鏇為唶婵﹩鍘藉▓銊╂⒑闂堟稓绠為柛濠冪墵閸╂盯骞掗幊銊ョ秺閺佹劙宕奸锝囩Х闂備胶顭堥鍥ㄧ箾閳ь剟鏌＄仦鍓р姇闁诡垱妫冩慨鈧幒铏閵忋倖鍊甸悷娆忓缁€鍐磼椤旇姤宕岀€殿噮鍋婂畷姗€顢欓懖鈺嬬床婵犳鍠楅敃鈺呭储妤ｅ啫鐒垫い鎺戭槸婢у瓨鎱ㄦ繝鍐┿仢鐎规洏鍔嶇换婵嬪磼濮ｆ寧娲熷娲传閵夈儛锝嗐亜閵娿儻韬鐐插暣閺佹捇鎮╅崣鍌樺姂閺屽秹宕崟顐熷亾婵犳艾鍌ㄩ柟闂寸劍閳锋垿鏌熼懖鈺佷粶闁告梹鎸抽弻锝夊箻瀹曞洤顣洪梺宕囩帛閹瑰洤顕ｉ鈧灋闁汇垹鐏氶崵鈧銈庡幑閸旀垵鐣锋總绋款潊闁靛繆鍓濋惁鎺戔攽閻樻剚鍟忛柛鐘崇墵閺佸啴濡舵径濠勵啇濡炪倖鍔х徊鎯р枍韫囨稒鈷掑ù锝堫潐閸嬬娀鏌涙惔銏㈢煉闁糕斁鍋撳銈嗗笒鐎氼剛澹曟繝姘厵闁绘劦鍓涚粣鐐烘煛閸愩劎澧曠€瑰憡绻冮妵鍕箻鐠轰警鈧挾绱掗柆宥勬喚婵﹦绮幏鍛驳鐎ｎ偆绉烽柣搴ゎ潐濞叉﹢鏁冮姀鐘垫殾婵炲樊浜滈崘鈧悷婊冪箻瀵娊鏁冮崒娑氬幈濠电偛妫楀ù姘ｉ搹鍦＜闁抽敮鍋撻柛瀣崌濮婂宕掑顑藉亾閹间焦鍋嬪┑鐘插閻瑩鏌熼悜妯虹劸闁哥喎鎳忕换婵嬫濞戞艾鈪辩紒鐐劤閵堟悂寮诲澶嬬叆妞ゆ牗鐭竟鏇㈡⒒娴ｄ警鐒炬い鎴濇楠炴垿宕惰濞兼牜绱撴担鑲℃垶鍒婇弶鎴欌偓鎺戭潩閻撳海浠梺鍛婃⒐閹倸顫忓ú顏勪紶闁告洦鍘奸埛鍫ユ⒑閸涘﹥灏伴柣鈺婂灠閻ｅ嘲顫濈捄铏诡唺闂佺懓顕慨闈涒枔閺囥垺鈷戦柛娑橈攻绾剧敻鏌￠崒娆戠獢鐎规洘鍨块獮妯兼嫚閼碱剨绱叉繝娈垮櫙缂嶅棝宕㈤挊澶樻富濞寸姴顑冮埀顑跨窔瀵噣宕煎┑鍡欑崺婵＄偑鍊曠换鎰板箠鎼搭煈鏁傜憸鐗堝笚閳锋帡鏌涚仦鍓ф噭缂佷焦婢橀埞鎴︻敊閸濆嫧鍋撳┑瀣瀬閻庯綆浜跺〒濠氭煏閸繄绠伴柣锔界矒閺屾盯濡搁妸锔俱€愬銈嗘穿缂嶄線鐛惔銊﹀殟闁靛鍎扮花濠氭⒒娴ｄ警鐒剧紒缁橆殜瀹曟垿骞囬弶鍨亶闂備緡鍓欑粔鐢稿煕閹寸姷纾藉ù锝呭帨濡劍銇勮箛鎾搭棞妞ゃ儲纰嶉妵鍕箻鐠虹儤鐎惧┑鐐叉噽婵炩偓闁哄矉缍佹慨鈧柕蹇婃櫆濮ｅ矂姊虹粙娆惧剱闁瑰憡鎮傞崺銏ゅ箻鐠囨彃鐎銈嗘⒒閺咁偅绂嶉鍛箚闁绘劦浜滈埀顑惧€濆畷銏＄附缁嬪灝绨ラ梺鍝勮閸庢椽寮€ｎ喗鐓ユ繝闈涙－濡插摜绱掗悩鐢靛笡闁靛洤瀚板顕€宕堕…鎴烆棄濠电偛鐡ㄧ划宥囧垝閹炬剚娼栨繛宸簻缁犳氨鎲歌箛鏇犱笉闁绘垶菧娴滄粓鏌￠崶鈺佹灁濠⒀呮暩閳ь剝顫夊ú锕傚垂闁秴绠氶柡鍐ㄧ墕鎯熼梺鎸庢椤鈧俺妫勯埞鎴︽倷閼搁潧娑х紓浣瑰絻濞硷繝鍨鹃敃鍌氶唶闁靛顑呴ˇ宕囩不濞戞ǚ妲堟繛鍡樺灥楠炴姊绘担绛嬫綈闁稿骸纾竟鏇㈩敇閻斿憡鐝烽梺鎸庢閺侇噣宕戦幘鑸靛枂闁告洦鍓涢敍娑㈡⒑閸涘娈旈柛鐔锋健椤㈡岸鏁愰崱娆樻祫闁诲函缍嗛埀顒夊幐閺呮繈骞夊宀€鐤€婵ǜ鍎板Ч妤佷繆閵堝繒鍒伴柛鐕佸灦閸╂盯骞嬮敂鐣屽幈濠电偞鍨堕敃顐﹀绩鐠囧樊鐔嗛悹鍝勬惈椤忣參鏌＄仦鍓с€掗柍褜鍓ㄩ幀銉╁礃瑜嶉ˉ姘舵⒒閸屾艾鈧嘲霉閸ヮ剙违闁逞屽墴閺屾稓鈧綆鍋呯亸顓㈡婢舵劖鐓熸俊顖滃劋閳绘洟鏌涙惔銏犲闁哄矉缍侀獮妯兼崉閻戞浜梻浣告惈閻ジ宕伴弽顓炶摕闁搞儺鍓氶弲婵嬫煃瑜滈崜姘跺疾閸洦鏁婇柛鎾楀拑绱查梻浣虹帛閻燂箓鎮烽妷銉冩椽鏁冮崒娑樹簵闂佸憡鍔︽禍鐐靛閽樺褰掓晲閸繀绨甸梺鐟邦嚟婵兘鎯岄崱娑欑厓鐟滄粓宕滈悢濂夋綎濠电姵鑹鹃悙濠勬喐濠婂吘锝夊捶椤撶姷锛滅紓鍌欑劍椤洨绮婚弽顐ょ＜缂備焦顭囩粻鐐碘偓瑙勬礃鐢帡锝炲┑瀣拻閻庨潧鎽滈敍娑㈡⒒閸屾艾鈧兘鎮為敃鈧—鍐锤濡も偓閸屻劑鏌ｉ敐鍛拱闁哄棙绮撻弻锝夊箛椤掍焦鍎撻梺缁樺笒閻忔岸濡甸崟顖氱闁瑰瓨绻冮悘鎾斥攽閻愭彃鎮戦柛鏃€鐗滈幑銏犫槈閵忕姷鐓戞繝銏ｅ煐缁嬫牠鍩€椤掆偓濞硷繝寮婚埄鍐╁缂佸瀵у▓缁樼節濞堝灝鏋撻柛瀣崌濮婃椽妫冨☉姘暫闂佺懓鍟跨换鎰祫濡炪倖甯掔€氼參鍩涢幋锔解拺妞ゆ劑鍊曟禒婊堟煠濞茶鐏￠柡鍛埣椤㈡盯鎮欑€电甯鹃梻浣规偠閸庢粓宕橀崣銉х＞濠德板€楁慨鐑藉磻閻愬灚鏆滈柨鐔哄Х瀹撲線鎮楅敐搴濈按闁衡偓娴犲鐓欓梺顓ㄧ細缁ㄨ姤淇婇顐㈢仸婵﹤顭峰畷鎺戔枎閹烘垵甯俊鐐€ら崢濂告偋閹炬眹鈧線寮介鐐碉紲闂佺粯鍔樼亸娆撍囬鐐╂斀闁绘绮☉褔鏌ｅΔ鈧敃銈夋偩閻戣棄鐭楀璺虹灱閻﹀牓姊婚崒姘卞缂佸甯掗蹇撯攽閸垻锛滈梺鎶芥暜閸嬫捇鏌涚€ｎ偅宕屾慨濠勭帛閹峰懏绗熼婊冨Ъ闂備礁鎼悧婊堝礈濞戙垹绐楀┑鐘插亞閸氬顭跨捄渚剰闁逞屽墮閻栧ジ寮婚悢鍏煎€锋い鎺嶈兌娴煎洭姊虹悰鈥充壕婵炲濮撮鍡涙偂閸愵喗鐓㈡俊顖欒濡茶銇勯妷锔剧煉闁哄矉缍€缁犳盯寮崒娑樻珰闂備礁鎼張顒傜矙閹捐鐒垫い鎺戯功缁夌敻鏌涚€ｎ亝鍣归柣锝呭槻閻ｆ繈宕熼鍌氬箰闁诲骸鍘滈崑鎾绘倵閿濆骸澧伴拑閬嶆⒒娴ｇ瓔鍤冮柛銊ュ船铻為柛鎰ㄦ櫆閸欏繘鏌嶈閸撶喖寮诲鍡樺闁规鍠栨俊钘夆攽閻愬弶鍣洪柣鈺婂灠椤繒绱掑Ο璇差€撻梺鍛婄☉閿曘儵宕伴幇顓犵瘈婵炲牆鐏濋弸銈夋煛娴ｅ壊鐓煎┑鈩冩尦瀹曟﹢鍩￠崘顏嗘闂備焦鎮堕崕顕€寮插☉姘辩焾闁挎洖鍊归埛鎴︽煕濞戞﹫宸ュ┑顕嗙畵閺屾盯鎮╁畷鍥р拡婵犮垼顫夊ú鐔风暦濡ゅ懎绀傞柣鎾抽娴煎孩绻濈喊妯活潑闁搞劍濞婂畷銏ｎ槾闁轰緡鍣ｉ幊婊堟偨閻㈡鍟庨梻浣稿閸嬪懐绮欓幋鐐扮箚闁伙絽鐬肩壕鑲╃磽娴ｈ鐒介柕鍡樺浮閺屽秷顧侀柛鎾寸洴瀵偊骞栨担娴嬪亾閿曞倸閱囨繝闈涚墛閹冲啯绻濈喊澶岀？闁稿鍨垮畷鎰板冀椤撶偛鐎梺鍓插亝濞叉牜绮堟径瀣闁糕剝顨嗙粋瀣煕閵堝棙绀冮柕鍥у楠炲洭宕滄担鐑樻闂備胶绮崝鏇㈡晝閵夆晛桅闁告洦鍨扮粻宕団偓骞垮劚閻楀棝宕㈤敓鐘斥拺闁告稑鈯曢懖鈺佸灊婵炲棗娴氬鏍ㄧ箾瀹割喕绨诲ù鑲╁█閺屾盯寮撮妸銉ょ爱闂佺顑嗛幑鍥х暦閻戠瓔鏁囨繛鎴灻兼竟鏇犵磽娴ｈ娈曠紒瀣灥鐓ら柕鍫濇川娑撳秵鎱ㄥΟ鍨厫闁绘挻娲橀妵鍕箛椤斿す銏ゆ煕閺冣偓缁捇寮诲☉姘ｅ亾閿濆啫濡煎┑鈥茬矙閺屾洟宕惰椤忣厾鈧鍠曠划娆愪繆濮濆矈妲峰┑鈩冨絻閻楁挸顫忓ú顏勫窛濠电姴鍟伴崢浠嬫⒑缁嬪尅鏀荤紒璇茬墕椤曪絿鎷犲顔兼倯闂佹悶鍎崝宀勫几閸岀偞鈷戦柛娑橈攻婢跺嫰鏌涚€ｎ亝顥為悗鐢靛帶閻ｆ繈宕熼鑺ュ闂佽崵濮村ú鐘诲焵椤掑啯鐝柣蹇婂亾闂傚倷绀侀幖顐﹀箠閹邦厽鍙忛柟缁㈠枟閸嬧晠鏌ｉ妶搴＄仜濞存粌缍婇弻鐔兼倻濡偐鐣洪梺鍝勬噺缁诲牆顫忓ú顏勭閹艰揪绲块悾闈涒攽閳藉棗浜濈紒瀣灥瀹撳嫰姊虹涵鍛涧闂傚嫬瀚伴崺娑㈠箳濡や胶鍘遍柣蹇曞仜婢т粙骞婇崱娑欑厱闊洦鎸诲﹢浼存煏閸パ冾伃妞ゃ垺锕㈤幃娆撴濞戞帒寰嶅┑锛勫亼閸婃垿宕瑰ú顏呭亱闁绘灏欓弳锕傛煕濡ゅ啫浜规俊鎻掔墛缁绘盯宕卞Δ鍐唶婵犮垻鎳撳Λ娆愮┍婵犲洦鍊锋い蹇撳閸嬫捇寮介鐔蜂罕濠电姴锕ら悧鍡欏缂佹绠鹃柟瀵稿仦閻ㄦ垵鈽夐幘宕囆ч柡灞糕偓鎰佸悑闁告劑鍔岄‖鍡欑磽娴ｇ瓔鍤欐俊顐ｇ懇婵＄敻宕熼姘敤闂侀潧臎閸涱垳鐛ラ梻鍌欑窔閳ь剛鍋涢懟顖炲储閹绢喗鐓曢悗锝庡亝瀹曞本鎱ㄦ繝鍛仩闁诡喕鍗抽崹楣冨礂閸涱収妫滃┑鐘垫暩閸嬫盯顢氶鐔稿弿闁圭虎鍣弫鍕煕閳╁啰鈽夌痪鎯х秺閺岋綁濮€閻樺啿鏆堥梺缁樻尪閸庤尙鎹㈠┑瀣棃婵炴垶鐟Λ銈夋⒑閸涘﹤鐏ユ繝鈧潏鈺傤潟闁圭儤顨呮儫闂侀潧顭堥崕娑㈠箻缂佹鍘梺鎼炲劀閸愬彞绱旈梻浣芥〃缁€渚€宕愭繝姘闁绘顕ч惌妤€顭跨捄鍝勵殭闂侇収鍨跺濠氬磼濞嗘埈妲梺姹囧€曞ú顓㈡晲閻愭潙绶為柟閭﹀墮閻庮參姊虹粔鍡楀娴犵娀鏌嶈閸忔稓绮堟笟鈧崺銏℃償閵娿儳顓洪梺缁橆焽閸庣敻寮ㄥú顏呪拺閻犲洦褰冮崵杈╃磽瀹ュ懏顥㈢€规洘绮岄埢搴ㄥ箛椤撶偛浼庡┑鐘绘涧閸婃悂骞夐敓鐘茬厱闁圭儤鍤氳ぐ鎺撴櫜闁割偆鍣ユ禒鈺呮⒑闂堟稒顥滈柨鏇ㄤ邯瀵鏁撻悩鎻掔獩濡炪倖鏌ㄦ晶浠嬫偂婢舵劖鈷戦悹鍥ｂ偓铏闂佹悶鍎崝灞剧瑜版帗鐓熼柣妯哄级缁屽潡鎮樿箛鏃傛噰闁诡喗鍎崇叅妞ゅ繐鎳夐幏娲⒑缂佹ê濮屾俊顐㈠瀹曟繂顭ㄩ崼鐔哄幐闁诲函缍嗛崜娆戞暜閸洖纭€闂侇剙绉甸悡鏇熴亜閹邦喖孝闁告梹绮撻弻锝夊箻瀹曞洨顔掗梺缁樻惄閸嬪﹤鐣烽崼鏇炍ㄩ柕澶堝劚琚樼紓鍌欒兌閸嬫捇宕曢崘宸劷闁跨喓濮撮拑鐔哥箾閹寸儑渚涙繛绗哄姂閺屽秷顧侀柛鎾存皑缁顓兼径濠囧敹闂佸搫娲ㄩ崑鐘诲箺閺囥垺鈷戦柟绋挎捣缁犳捇鏌ｅΔ鈧换姗€骞冮崸妤€鐒垫い鎺戝閳锋垿鏌涘┑鍡楊仾鐎瑰憡绻堥弻娑氣偓锝庡亾閼版寧顨ラ悙宸剶闁诡喗绮撻幊鐐哄Ψ閵忊€愁伖闂傚倷娴囬鏍垂鎼淬劌宸濇い鏍电到閺佽绻濆閿嬫緲閳ь剚顨嗛幈銊╂偨缁嬭法鐣洪梺绋跨灱閸嬫盯寮伴妷鈺傜厪闊洤顑呴埀顒佹礈缁粯绻濆顓炰化闂佹悶鍎滈崘顏堢崜婵＄偑鍊戦崕鑼崲閸岀偛鐓橀柟杈剧畱閻擄繝鏌涢埄鍐︿沪缂併劋绮欏鐑樻姜閹殿喖濡介梺璇″枛閸婂潡鎮伴鐣岀瘈闁搞儜鍜佸晪闂備胶绮崹鐔煎棘娓氣偓瀹曟垿骞樼紒妯绘珖闂佺鏈閬嶅煛閸涱喚鍘撻柡澶屽仦婢瑰棝藝閿斿墽纾奸柟閭﹀幘閳藉銇勯鍕殻濠碘€崇埣瀹曞崬螣濞差亞鈧櫣绱撻崒娆愮グ妞ゆ泦鍥х９闁兼剚鍨遍敍鍌涚節濞堝灝鏋熸い顓炵墛缁傚秹顢楅崒姘兼婵炲濮撮鍡涙偂閺囥垺鍊堕柣鎰絻閳锋棃鏌熼崘鍙夘棦闁哄本绋栫粻娑㈠箻閸撲椒绱樼紓鍌欐祰妞村摜鏁敓鐘偓浣割潩鐠哄搫绐涘銈嗘尵婵娊宕犻弽顓熲拻濞达絿鍎ら崵鈧紓浣哄Т缁夌懓鐣疯ぐ鎺撳癄濠㈣泛鏈▓楣冩⒑缂佹ɑ鈷掗柍宄扮墦瀵偄顓奸崱妯哄伎濠碘槅鍨伴妵姗€宕ラ銈傚亾鐟欏嫭绀夋繛澶嬬洴閸╃偤骞嬮敂钘変汗闂佸壊鐓堥崑鍛掗崟顒傜瘈婵炲牆鐏濋弸鐔兼煙缁嬪灝鏆遍柣锝囧厴椤㈡盯鎮滈崱妯绘珖闂備線娼ч悧鍡椢涢銏犵；闁靛ň鏅滈埛鎴犵磽娴ｅ顏呮叏婢跺浜滈柨婵嗗閻瑧鈧娲橀崹鍧楃嵁濡吋瀚氶柤纰卞墻濡差垰鈹戦悩顔肩伇婵炲鐩弫鍐晲閸℃瑧褰鹃梺鍝勬储閸ㄦ椽鍩涢幒鎴欌偓鎺戭潩閿濆懍澹曢梻渚€鈧偛鑻晶浼存煕鐎ｎ偆娲撮柟宕囧枛椤㈡稑鈽夊▎鎰娇闂備礁婀遍搹搴ㄥ窗濡ゅ懏鍋傛繛鎴炩棨瑜版帗鏅查柛娑卞枟閸犳劙鏌℃担鍝ュⅵ婵﹨娅ｉ幏鐘诲灳閾忣偆浜栭梻浣规偠閸斿繐螞閸曨垱鍋╃€瑰嫭瀚堥弮鍫濈鐟滃繘宕滈妸鈺傗拺闁告繂瀚ⅹ闂佸憡鏌ㄩ惌鍌炪€侀弮鍫濇嵍妞ゆ挾濮烽敍婵嬫⒑缁嬫寧婀伴柤褰掔畺閸┾偓妞ゆ帒瀚峰Λ鎴犵磼椤旇偐澧涚紒妤冨枛閸┾偓妞ゆ帒瀚ㄩ埀顑跨窔瀵粙顢橀悙鑼垛偓鍨攽閻愭潙鐏卞瀛樻倐瀵煡鎮欏ù瀣杸闂佺粯鍔曞鍫曀夐悙鐑樼厵缂佸瀵ч幉绋款熆鐟欏嫭绀嬬€规洜顭堣灃濞达絽鎼獮宥夋⒒娴ｇ瓔娼愭い鏃€鐗犲畷浼村冀椤撴壕鍋撻崒娑氶檮闁告稑锕﹂崢浠嬫⒑鐟欏嫬鍔ら柣掳鍔庣划鍫⑩偓锝庡枟閻撴盯鎮橀悙闈涗壕缂佲偓閸愵亞纾肩紓浣诡焽濞插鈧娲栫紞濠囥€佸▎鎾村亗妤犵儐鍠楅崹鐢稿煘閹达附鍊风€瑰壊鍠栧▓鑸电節濞堝灝鏋欓柛妤€鍟块悾椋庢喆閸曨収娴勯柣搴秵娴滐綁骞楅弴銏♀拺闁圭娴风粻鎾淬亜閿斿灝宓嗙€殿喗鐓″濠氬Ψ閿旀儳骞堥梻渚€娼чˇ浼村垂閻㈢鐤い鎰╁€楁稉宥夋煥濠靛棙顥欑紒璇叉閺屾洟宕煎┑鍥ㄦ倷闁哥喐鎮傚铏圭矙濞嗘儳鍓辩紓浣割儐閸ㄥ綊宕ｉ崨顓ф富闁靛牆妫欓幆鍕箾閻撳孩鍋ラ柛鈹惧亾濡炪倖鍨煎▔鏇犵玻閺冨倵鍋撳▓鍨灆缂侇喗鐟╅妴浣割潨閳ь剟骞冨▎鎾搭棃婵炴垶顭囬鍥⒒閸屾艾鈧兘鎳楅崼鏇椻偓锕傚醇閵夈儱鐝樺銈嗗笂閼冲爼鎮风憴鍕箚闁靛牆鍊告禍楣冩煣閼姐倕浠遍柡灞剧洴楠炲洭妫冨☉娆戜憾闂備胶顭堥敃锕傚极婵犳艾绠栫€瑰嫭澹嬮弸搴ㄧ叓閸ャ劍鎯勫ù鐘插⒔缁辨帡宕滆娴滈箖鏌熼鐓庘偓鍨嚕鐠囨祴妲堥柕蹇曞Х閻嫰姊洪柅鐐茶嫰婢у鈧娲栫紞濠傜暦缁嬭鏃堝焵椤掑啰绠芥繝鐢靛仩閹活亞寰婇懞銉х彾濠电姴鍋嗛悞浠嬫煏婵炵偓娅嗛柣鎾冲暟閹茬顭ㄩ崼婵堫槶闂佺粯妫侀崑鎰暤娓氣偓閺屾盯骞囬棃娑欑亪濡炪値鍋勯幊姗€寮婚弴锛勭杸濠电偞鍎虫禍楣冩煕韫囨挻鍣介梺鐣屾暬濮婂宕掑▎鎺戝帯闂佺顑冮崝鎴濈暦閺囥垺鐒肩€广儱鎳庨崑宥夋⒑閸濆嫮袪闁告柨閰ｉ幃鐢稿醇閺囩喓鍘搁梺鎼炲劗閺呮盯宕滄潏銊х闁圭偓鍓氶悡濂告煛鐏炵偓绀嬬€规洜鍘ч埞鎴﹀炊閼告妫х紓鍌氬€烽悞锕傘€冮崱娆愭殰闁跨喓濮寸粻鏍ㄤ繆閵堝倸浜惧銈庡亝缁诲牓骞冮埄鍐╁劅闁挎稑瀚澶愭⒒閸屾瑦绁版い鏇熺墵瀹曟娊顢涘鍕簥濠电娀娼ч鍛不閺嶎厽鐓忛煫鍥ь儏閳ь剚娲滅划鍫ュ磼閻愬鍘惧┑鐐存綑椤戝棗鈻嶉崨顕呮闁绘劖鎯屽▓鏇犵磼缂佹绠橀柛鐘诧工铻ｆ繛鑼帛缂嶅姊绘担瑙勫仩闁告柨顑夊畷锟犲箮閻ｅ苯绁﹂梺鍝勭▉閸欏酣寮崘顔界厪闁割偅绻冩径鍕煥濞戞绠婚柟顔煎槻椤劑宕熼澶嬵棃闁诲孩顔栭崰鏍€﹂悜钘夌畺闁靛繈鍊栭崑鍌炲箹鏉堝墽绉垫俊宸灦濮婄粯鎷呴搹鐟扮闂佸湱顭堥幉锟犮€冮妷鈺佺濞撴艾锕︾粙蹇涙倵楠炲灝鍔氭俊顐ｇ〒缁粯銈ｉ崘鈺冨弳濠电娀娼уΛ娆撳闯濞差亝鐓欓柛蹇曞帶婵秹鏌＄仦璇插鐎殿喗鎸虫俊鎼佸Χ婢跺﹣绮ｉ梻鍌欑窔濞佳兾涢弮鍌滅焼濞撴埃鍋撻柛鈹垮劜瀵板嫭绻濇惔銏犵紦闂備胶纭堕崜婵嬫晪闂佺绻愰敃顏勵潖缂佹ɑ濯村〒姘煎灡閺侇垰顪冮妶搴′簻妞わ箓娼ч悾宄扳攽鐎ｎ亜宓嗛梺闈涚箳婵兘鎮炬ィ鍐┾拺闁告挻褰冩禍鏍煕鎼淬垻鍙€闁?
		}

		if (CC.ADMIN.equalsIgnoreCase(loginname)) {
			Person person;
			String rawPassword = request.getParameter("password");
			boolean shouldBootstrapPosProducts = false;
            boolean shouldInitializeStyleTemplate = true;
            boolean shouldUseCompactServiceThumb = false;
            boolean shouldMarkDepanneurSite = false;

			if (personService.findPersonByName(CC.ADMIN) == null) { // if not exist, then create the super user.
				person = new Person();
				person.setName(CC.ADMIN);
				person.setPassword(TaoEncrypt.encryptPassword(rawPassword));
				personService.persist(person);
			} else { // user the password as name to create a new client.
				SpecialSignupSiteInfo siteInfo = parseSpecialSignupSiteInfo(rawPassword);
				if (StringUtils.isBlank(siteInfo.personName)) {
					model.addAttribute("SignUp_ErrorMessage", "Website name is required, please try an other one!");
					return "signup";
				}
				if (siteInfo.personName.contains(CC.SAVED_NAME_STR)) {
					model.addAttribute("SignUp_ErrorMessage", "'*' is not allowed, please try an other one!");
					return "signup";
				}

				if (siteInfo.activationCode != null) {
					Person personByName = personService.findPersonByName(siteInfo.personName);
					if (personByName == null) {
						model.addAttribute("SignUp_ErrorMessage", "This activation code can only rebuild an existing site, please check the site name!");
						return "signup";
					}
					if (!activationCodeMatchesSite(siteInfo.activationCode, siteInfo.personName)) {
						model.addAttribute("SignUp_ErrorMessage", "This activation code does not match the website name, please try again!");
						return "signup";
					}
					person = personByName;
                    shouldBootstrapPosProducts = true;
                    shouldInitializeStyleTemplate = false;
                    shouldUseCompactServiceThumb = true;
                    shouldMarkDepanneurSite = true;
				} else {
					if (personService.findPersonByName(siteInfo.personName) != null) {
						model.addAttribute("SignUp_ErrorMessage", "This name is occupied, please try an other one!");
						return "signup";
					}
					person = new Person();
					person.setName(siteInfo.personName);
					person.setPassword(TaoEncrypt.encryptPassword(siteInfo.personName));
					personService.persist(person);
				}
			}
			if (shouldInitializeStyleTemplate) {
				styleUtil.initializeToStyleK5(request, person);
			}
			if (shouldMarkDepanneurSite) {
                styleUtil.createACustomize(request, CC.is_depanneur_site, CC.TRUE, person);
            }
            if (shouldUseCompactServiceThumb) {
				styleUtil.createACustomize(request, "img_service_thum_h", "145", person);
				styleUtil.createACustomize(request, "img_service_thum_w", "145", person);
				styleUtil.createACustomize(request, "service_number_xs", "6", person);
			}
			if (shouldBootstrapPosProducts) {
				posProductBootstrapService.importProductsForPerson(person);
			}

			String tel = request.getParameter("tel");
			if (!StringUtils.isBlank(tel)) {
				styleUtil.createACustomize(request, "contact_phone", tel.trim(), person);
			}

			String email = request.getParameter("");
			if (!StringUtils.isBlank(email)) {
				styleUtil.createACustomize(request, "email", email.trim(), person);
			}

			styleUtil.createACustomize(request, "app_WebsiteAddress", "https://www.shareTheGoodOnes.com/" + person.getName(),
					person);
			if (taoUtil.hasNotLoggedIn(request)) {
				dirtFlagCommonText = taoUtil.switchClient(request, person.getName());
			}

		} else {
			if (loginname.contains(CC.SAVED_NAME_STR)) {
				model.addAttribute("SignUp_ErrorMessage", "'*' is not allowed in name, please choose an other one.");
				return "signup";
			}
			Person person = taoUtil.getCurPerson(request);
			if (person == null) {
				model.addAttribute("SignUp_ErrorMessage", "session closed, please refresh page and try again!");
				return "signup";
			}
			// same with client is not allowed, otherwise when login, don't know which one.
			if (person.getName().equalsIgnoreCase(loginname)) {
				model.addAttribute("SignUp_ErrorMessage", "This name is occupied, please try an other one!");
				return "signup";
			}

			String enrichedName = TaoEncrypt.enrichName(loginname, person);
			// make sure no existing
			if (userAccountService.findUserAccountByName(enrichedName) != null) {
				model.addAttribute("SignUp_ErrorMessage", "This name is occupied, please try an other one!");
				return "signup";
			}

			UserAccount tUserAccount = new UserAccount();
			tUserAccount.setPerson(person);
			tUserAccount.setLoginname(enrichedName);
			String password = request.getParameter("password");
			tUserAccount.setPassword(TaoEncrypt.encryptPassword(password));
			tUserAccount.setTel(request.getParameter("tel"));
			tUserAccount.setEmail(request.getParameter("email"));
			tUserAccount.setCompanyname(request.getParameter("companyname"));
			tUserAccount.setSecuritylevel(CC.ROLE_CLIENT);
			userAccountService.persist(tUserAccount);
            attachPendingLuckyPrizeToUser(request, response, person, tUserAccount, false);
		}
		return buildPageForMenu(model, request, response, null);
	}

	public boolean verifyRecaptcha(String recaptchaResponse) {
		String secret = "6Le8xgArAAAAAOK3i85yV157MgcYG_VbIEJDmQFC";
		String verifyUrl = "https://www.google.com/recaptcha/api/siteverify";

		try {
			URL url = new URL(verifyUrl);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);

			String postData = "secret=" + URLEncoder.encode(secret, "UTF-8")
					+ "&response=" + URLEncoder.encode(recaptchaResponse, "UTF-8");

			try (OutputStream out = conn.getOutputStream()) {
				out.write(postData.getBytes(StandardCharsets.UTF_8));
			}

			String json = new BufferedReader(new InputStreamReader(conn.getInputStream()))
					.lines().collect(Collectors.joining());

			return json.contains("\"success\": true");

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}




	private SpecialSignupSiteInfo parseSpecialSignupSiteInfo(String rawPassword) {
		String personName = rawPassword == null ? "" : rawPassword.trim();
		String activationCode = null;

		Matcher slashMatcher = SPECIAL_SIGNUP_SLASH_PATTERN.matcher(personName);
		if (slashMatcher.matches()) {
			personName = slashMatcher.group(1).trim();
			activationCode = slashMatcher.group(2).trim();
		} else {
			Matcher matcher = PERSON_ID_SUFFIX_PATTERN.matcher(personName);
			if (matcher.matches()) {
				personName = matcher.group(1).trim();
				activationCode = matcher.group(2).trim();
			}
		}

		return new SpecialSignupSiteInfo(personName, activationCode);
	}

	private boolean activationCodeMatchesSite(String activationCode, String siteName) {
		if (StringUtils.isBlank(activationCode) || StringUtils.isBlank(siteName)) {
			return false;
		}
		String headInfo = resolveHeadInfoByActivationCode(activationCode);
		return StringUtils.isNotBlank(headInfo) && headInfo.contains(siteName);
	}

	private String resolveHeadInfoByActivationCode(String activationCode) {
		Person aikaPos = makeSurePersonExist("AikaPos", "asdf");
		Customize customize = customizeService.findCustomizeByKeyAndPerson(activationCode, aikaPos);
		return customize == null ? null : customize.getCusValue();
	}

	private static final class SpecialSignupSiteInfo {
		private final String personName;
		private final String activationCode;

		private SpecialSignupSiteInfo(String personName, String activationCode) {
			this.personName = personName;
			this.activationCode = activationCode;
		}
	}

	@RequestMapping(value = "/register", method = RequestMethod.POST)
	public String register(Model model, HttpServletRequest request, HttpServletResponse response) {
		Person person = taoUtil.getCurPerson(request);
		String loginname = request.getParameter("loginname");
		if (person.getName().equalsIgnoreCase(loginname)) {
			model.addAttribute("SignUp_ErrorMessage", "This name is occupied, please try an other one!");
			return "signup";
		}

		String email = request.getParameter("email");
		if (StringUtils.isBlank(loginname)) {
			loginname = email;
		}

		String password = request.getParameter("password");
		password = StringUtils.isEmpty(password) ? person.getName() : password;
		String enrichedLoginName = TaoEncrypt.enrichName(request, loginname);
		String tel = request.getParameter("tel");
		String address = request.getParameter("address");
		String comments = request.getParameter("commenets");

		if (userAccountService.findUserAccountByName(enrichedLoginName) != null) {
			model.addAttribute("SignUp_ErrorMessage", "This name is occupied, please try an other one!");
			return "signup";
		} else {
			UserAccount tUserAccount = new UserAccount();
			tUserAccount.setPerson(person);
			tUserAccount.setLoginname(enrichedLoginName);
			tUserAccount.setPassword(TaoEncrypt.encryptPassword(password));
			tUserAccount.setTel(tel);
			tUserAccount.setEmail(email);
			tUserAccount.setCompanyname(request.getParameter("companyname"));
			tUserAccount.setSecuritylevel(CC.ROLE_CLIENT);
			tUserAccount.setDescription(comments);
			tUserAccount.setAddress(address);
			userAccountService.persist(tUserAccount);
            attachPendingLuckyPrizeToUser(request, response, person, tUserAccount, false);
			model.addAttribute("email", tUserAccount.getEmail());
			model.addAttribute("loginname", loginname);
		}
		// send out an email.
		Object managerEmail = request.getSession().getAttribute(CC.app_ManagerEmail);
		if (managerEmail != null) {
			TaoEmail.sendMessage(managerEmail.toString(), "Register Conformation!", email, comments, null);
		}
		// notice the user go and check their email box.
		return buildPageForMenu(model, request, response, (String)request.getSession().getAttribute(taoUtil.INFO_COLLECT_FORM_CUST_PAGE_PATH));
	}

    @RequestMapping(value = "/spinLuckyWheel", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> spinLuckyWheel(HttpServletRequest request, HttpServletResponse response) {
        JSONObject json = new JSONObject();
        Person person = taoUtil.getCurPerson(request);
        if (person == null) {
            json.put("ok", false);
            json.put("message", "site not ready");
            return new ResponseEntity<>(json.toString(), HttpStatus.BAD_REQUEST);
        }

        UserAccount currentUser = (UserAccount) request.getSession().getAttribute(CC.currentUser);
        List<LuckyPrizeOption> prizeOptions = buildLuckyPrizeOptions(person);
        if (currentUser != null) {
            luckyDrawRecordService.expireOldPendingPrizes(person, currentUser);
        }
        LuckyDrawRecord pendingRecord = luckyDrawRecordService.findPendingPrize(person, currentUser);
        if (pendingRecord != null) {
            json.put("ok", true);
            json.put("prizeCode", pendingRecord.getPrizeCode());
            json.put("prizeName", pendingRecord.getPrizeName());
            json.put("prizeImageUrl", resolveLuckyPrizeImageUrl(prizeOptions, pendingRecord.getPrizeCode()));
            json.put("requiresAuth", false);
            json.put("hasPendingPrize", true);
            return new ResponseEntity<>(json.toString(), HttpStatus.OK);
        }

        if (currentUser != null && luckyDrawRecordService.hasSpinToday(person, currentUser)) {
            json.put("ok", false);
            json.put("hasPendingPrize", false);
            json.put("message", "You already used today's spin.");
            return new ResponseEntity<>(json.toString(), HttpStatus.OK);
        }

        String pendingPrizeCode = resolvePendingLuckyPrizeCode(request, response);
        if (StringUtils.isBlank(pendingPrizeCode)) {
            pendingPrizeCode = chooseLuckyPrizeCode(request.getSession(), prizeOptions);
            if (StringUtils.isBlank(pendingPrizeCode)) {
                json.put("ok", false);
                json.put("message", "no prizes configured");
                return new ResponseEntity<>(json.toString(), HttpStatus.BAD_REQUEST);
            }
            storePendingLuckyPrize(request, response, pendingPrizeCode);
        }

        if (currentUser != null && isLuckyWheelEligibleRole(request.getSession())) {
            if (!luckyDrawRecordService.hasSpinToday(person, currentUser)) {
                luckyDrawRecordService.createWinningRecord(person, currentUser, pendingPrizeCode,
                        resolveLuckyPrizeLabel(prizeOptions, pendingPrizeCode));
            }
            clearPendingLuckyPrize(request, response);
            json.put("requiresAuth", false);
        } else {
            json.put("requiresAuth", true);
        }

        json.put("ok", true);
        json.put("hasPendingPrize", true);
        json.put("prizeCode", pendingPrizeCode);
        json.put("prizeName", resolveLuckyPrizeLabel(prizeOptions, pendingPrizeCode));
        json.put("prizeImageUrl", resolveLuckyPrizeImageUrl(prizeOptions, pendingPrizeCode));
        return new ResponseEntity<>(json.toString(), HttpStatus.OK);
    }

	@RequestMapping(value = "/comment", method = RequestMethod.POST)
	public String comment(Model model, HttpServletRequest request, HttpServletResponse response) {
		Person person = taoUtil.getCurPerson(request);
		String loginname = request.getParameter("loginname");
		if (person.getName().equalsIgnoreCase(loginname)) {
			model.addAttribute("SignUp_ErrorMessage", "This name is occupied, please try an other one!");
			return "signup";
		}

		String email = request.getParameter("email");
		if (StringUtils.isBlank(loginname)) {
			loginname = email;
		}
		String password = request.getParameter("password");
		String enrichedLoginName = TaoEncrypt.enrichName(request, loginname);
		String tel = request.getParameter("tel");
		String comments = request.getParameter("commenets");

		// send out an email.
		Object managerEmail = request.getSession().getAttribute(CC.app_ManagerEmail);
		if (managerEmail != null) {
			TaoEmail.sendMessage(managerEmail.toString(), "Customer Requirement", email, comments, null);
		}
		// notice the user go and check their email box.
		return buildPageForMenu(model, request, response, null);
	}

	@RequestMapping(value = "/updateUserInfo", method = RequestMethod.POST)
	public String updateUserInfo(Model model, HttpServletRequest request, HttpServletResponse response) {

		Long id = Long.valueOf(request.getParameter("id"));
		UserAccount tUserAccount = userAccountService.findUserAccount(id);
		String loginname = request.getParameter("loginname");
		if (loginname.contains(CC.SAVED_NAME_STR)) {
			model.addAttribute("Update_ErrorMessage", "'*' is not allowed in name, please choose an other one!");
			return SpringApplicationContext.getApplicationContext()
					.getBean("userAccountController", UserAccountController.class)
					.updateForm(tUserAccount.getId(), model);// with error message.
		}

		String enrichedName = TaoEncrypt.enrichName(request, loginname);
		if (!enrichedName.equalsIgnoreCase(tUserAccount.getLoginname())
				&& userAccountService.findUserAccountByName(enrichedName) != null) {
			model.addAttribute("Update_ErrorMessage", "The name is already used, please choose an other one!");
			return SpringApplicationContext.getApplicationContext()
					.getBean("userAccountController", UserAccountController.class)
					.updateForm(tUserAccount.getId(), model);// with error message.
		}
		tUserAccount.setLoginname(enrichedName);

		String password = request.getParameter("password");
		if (!tUserAccount.getPassword().equals(password)) {
			password = TaoEncrypt.encryptPassword(password);
		}
		tUserAccount.setPassword(password);

		tUserAccount.setTel(request.getParameter("tel"));
		tUserAccount.setFax(request.getParameter("fax"));
		tUserAccount.setEmail(request.getParameter("email"));
		tUserAccount.setCompanyname(request.getParameter("companyname"));
		if (request.getParameter("address") != null) {
			tUserAccount.setCel(request.getParameter("cel"));
			tUserAccount.setAddress(request.getParameter("address"));
			tUserAccount.setCity(request.getParameter("city"));
			tUserAccount.setPostcode(request.getParameter("postcode"));
			tUserAccount.setDescription(request.getParameter("description"));
		}
		if (request.getParameter("securitylevel") != null)
			tUserAccount.setSecuritylevel(request.getParameter("securitylevel"));

		try {
			tUserAccount.setCredit(
					request.getParameter("credit") == null ? 0 : Integer.valueOf(request.getParameter("credit")));
			tUserAccount.setBalance(
					request.getParameter("balance") == null ? 0 : Integer.valueOf(request.getParameter("balance")));
		} catch (Exception e) {
			model.addAttribute("Update_ErrorMessage",
					"Error Occured when trying to update the infomation into database, Please check if the NUMBERs are Valid!");
			return SpringApplicationContext.getApplicationContext()
					.getBean("userAccountController", UserAccountController.class)
					.updateForm(tUserAccount.getId(), model);// with error message.
		}

		userAccountService.persist(tUserAccount);
		return buildPageForMenu(model, request, response, null);
	}

	@RequestMapping(value = "/dashboard", method = RequestMethod.GET)
	public String dashboard(Model model, HttpServletRequest request) {
		String tUserName = userContextService.getCurrentUserName();
		Person person = taoUtil.getCurPerson(request);
		UserAccount userAccount = userAccountService.findUserAccountByName(TaoEncrypt.enrichName(tUserName, person));
		// @NOTE: if beautify the name here, then when the following mainOrder.persist()
		// excuted,
		// useraccount's loginName will be updated into db. DKW!
		// userAccount.setLoginname(TaoEncrypt.stripUserName(tUserName));
		model.addAttribute("useraccount", userAccount);
		model.addAttribute("itemId", userAccount.getId());
		model.addAttribute("todoList", mediaUploadService.findMediaByAudientAndPerson(userAccount, person));
		model.addAttribute("infoList", mediaUploadService.findMediaByAuthor(userAccount));
		List<MainOrder> mainOrders = null;
		String securityLevel = userAccount.getSecuritylevel();
		if (CC.ROLE_EMPLOYEE.equalsIgnoreCase(securityLevel)) {
			mainOrders = mainOrderService.findMainOrdersByStatusLimitationAndPerson(CC.LEVEL_FULL, person, "desc");
			String langPrf = taoUtil.getLangPrfWithDefault(request);
			List<String> quick_notes = textContentService.findAllMatchedContent(langPrf + "quick_note_%", "content", person);
			model.addAttribute("quick_note", quick_notes);
		}else if ( CC.ROLE_MANAGER.equalsIgnoreCase(securityLevel)){
			mainOrders = mainOrderService.findMainOrdersByPerson(person, "desc");
			String langPrf = taoUtil.getLangPrfWithDefault(request);
			List<String> quick_notes = textContentService.findAllMatchedContent(langPrf + "quick_note_%", "content", person);
			model.addAttribute("quick_note", quick_notes);
		}else if (CC.ROLE_PRINTER.equalsIgnoreCase(securityLevel)) { // not used for now, beuase it ask an extra computer on site.
			request.setAttribute("show_footArea", "");
			request.setAttribute("show_foot", "false");
			request.setAttribute("show_AboveMenu", "");
			request.setAttribute("show_Menu", "");

			mainOrders = mainOrderService.findMainOrdersByStatusAndPerson(CC.STATUS_TO_PRINT, person, "asc");
			if (mainOrders != null) {
				for (MainOrder mainOrder : mainOrders) {
					String returnPath = prepareDetailOrder(mainOrder, model, request, null, null, null);
					if (CC.STATUS_FULL.equals(returnPath)) {
						mainOrder.setRecordStatus(CC.STATUS_PRINTED);
						mainOrderService.persist(mainOrder); // DKW! when this excuted, useraccount will be persist into db.
						continue;
					} else if (CC.STATUS_MINE_ARE_FULL.equals(returnPath)) {
						continue;
					} else {
						return returnPath;
					}
				}
			}
			// if not return yet, means nothing to print for now.
			model.addAttribute("materials", null);
			return "printpreview";
		} else if (CC.ROLE_PARTNER.equalsIgnoreCase(securityLevel) || CC.ROLE_CLIENT.equalsIgnoreCase(securityLevel)) {
			mainOrders = mainOrderService.findMainOrdersByClientAndPerson(userAccount, person, "desc");
		} else {
			mainOrders = mainOrderService.findUnCompletedMainOrdersByPerson(person, "desc");
		}
		model.addAttribute("mainOrderList", mainOrders);

		// only the waiter who's fax is set as auto_print can execute auto print task.
		// it's not allowed that two device use same account.
		if (CC.TRUE.equals(request.getSession().getAttribute(CC.auto_print))) {
			UserAccount currentUser = (UserAccount) request.getSession().getAttribute(CC.currentUser);
			if (currentUser == null || !CC.ROLE_EMPLOYEE.equals(currentUser.getSecuritylevel())
					|| !CC.auto_print.equals(currentUser.getFax())) {
				request.getSession().setAttribute(CC.auto_print, "false");
			}
		}

		return "dashboard";
	}

	// shouldn't use "$" in endSubTotal, should use an specific column for "$".
	// we have to user material, instead of service, because it's a record, will be
	// there even if the product deleted.
	@RequestMapping(value = "/showDetailOrder/{mainOrderID}", method = RequestMethod.GET)
	public String showDetailOrder(@PathVariable("mainOrderID") Long mainOrderId, Model model,
			HttpServletRequest request) {
		if (mainOrderId > -1) {
			MainOrder mainOrder = mainOrderService.findMainOrder(mainOrderId);
			// prepare the data for websocket printers
			List<String> printers = new ArrayList<>();
			Map<String, String> printersMap = new HashMap<String, String>();
			List<List<Material>> materialsForPrinters = new ArrayList<>();
			String returnPath = prepareDetailOrder(mainOrder, model, request, printers, printersMap,
					materialsForPrinters);
			model.addAttribute("printers", printers);
			model.addAttribute("printersMap", printersMap);
			model.addAttribute("materialsForPrinters", materialsForPrinters);
			// end preparing data for websocket printers.

			// if the status is not 0, then auto_print should not work when open showing
			// detail.
			model.addAttribute("recordStatus", mainOrder.getRecordStatus());
			return returnPath;
		} else {
			model.addAttribute("materials", null);
			return "printpreview";
		}
	}

	// TODO: when the printers array is not null(it's an initialized empty list;
	// used as a flag to indicate that calling
	// from showDetailOrder method), means it's employee user need the data for
	// websocket printers!
	private String prepareDetailOrder(MainOrder mainOrder, Model model, HttpServletRequest request,
			List<String> printers, Map<String, String> printersMap, // inside, will install the printerIP/printStyle
			List<List<Material>> materialsForPrinters) {
		if (mainOrder != null) {
			UserAccount currentUser = (UserAccount) request.getSession().getAttribute(CC.currentUser);

			List<Material> materials = materialService.findAllMaterialsByMainOrder(mainOrder);
			// the printer as a user asking for material case is not used, because it ask
			// for an extra computer on site.
			if (currentUser != null && CC.ROLE_PRINTER.equals(currentUser.getSecuritylevel())) {
				String curPrinterName = "," + TaoEncrypt.stripUserName(currentUser.getLoginname()) + ",";
				String processedPrinters = mainOrder.getColorCard();
				boolean processed = processedPrinters != null && processedPrinters.contains(curPrinterName);
				// TODO: shall we order it or combine it
				boolean isAllFull = true;
				for (int i = materials.size() - 1; i >= 0; i--) {
					Material material = materials.get(i);
					int status = material.getRecordStatus();
					if (status < (CC.LEVEL_FULL - 5)) {
						isAllFull = false;
					}
					if (!processed) {
						String printersStr = material.getMenFu();
						if (printersStr == null || !printersStr.contains(curPrinterName)) {
							materials.remove(material);
						}
					}
				}
				if (isAllFull) {
					return CC.STATUS_FULL;
				} else if (processed) {
					return CC.STATUS_MINE_ARE_FULL;
				}
			} else if (currentUser != null) { // employee ask for material case.
				if (CC.TRUE.equals(request.getSession().getAttribute(CC.auto_combine_print))) {
					combineMaterials(materials); // combine the list.
				}
				// if the parameter is already initialized, means it's asking for datas needed
				// by websocket printer.
				if (printers != null) {
					// find out all printer users.
					List<UserAccount> users = userAccountService.findAllUserAccountsByPerson(taoUtil.getCurPerson(request));
					for (UserAccount user : users) {
						if (CC.ROLE_PRINTER.equals(user.getSecuritylevel())) {
							// addin the printer.
							String printerName = TaoEncrypt.stripUserName(user.getLoginname());
							printers.add(printerName);
							if (printersMap != null) {
								printersMap.put(printerName, user.getFax()); // in fax, print style is stored.
							}
							// add in materials
							List<Material> materialsForPrinter = new ArrayList<>();
							for (Material material : materials) {
								String printersStr = material.getMenFu(); // menfu field can contains many printer
																			// names.
								if (printersStr != null && printersStr.contains(printerName)) {
									materialsForPrinter.add(material);
								}
							}
							materialsForPrinters.add(materialsForPrinter);
						}
					}
				}
			} else {
				// check authentication
				if (!request.getSession().getId().equals(mainOrder.getClientSideOrderNumber())) {
					return "printpreview";
				}
			}

			addNameOfSpecificLanguage(materials, request.getLocale().getLanguage(), request);
			model.addAttribute("materials", materials);

			model.addAttribute("sizeTable",
					StringUtils.isBlank(mainOrder.getSizeTable()) ? mainOrder.getModel() : mainOrder.getSizeTable());
			model.addAttribute("mainOrderID", mainOrder.getId());
			model.addAttribute("targetTime", taoUtil.formateDate(request, mainOrder.getDelieverdate()));
			model.addAttribute("name", mainOrder.getModel());
			model.addAttribute("contactPhone", mainOrder.getSampleRequirement());
			model.addAttribute("address", mainOrder.getClientSideModelNumber());
			String total = mainOrder.getPayCondition();
			model.addAttribute("total", total == null ? "$0" : total);
			float totalPrice = Float.valueOf(total == null ? "0" : total.substring(1)).floatValue();
			String taxRate = (String) request.getSession().getAttribute("tax_rate");
			float taxValue = 0.00f;
			try {
				taxValue = totalPrice * Float.valueOf(taxRate) / 100;
				taxValue = (float) Math.round(taxValue * 100) / 100;
			} catch (Exception e) {
				TaoDebug.error("tax not set yet!", request);
			}
			model.addAttribute("tax", taxValue);
			model.addAttribute(CC.endPrice, (float) (Math.round((totalPrice + taxValue) * 100)) / 100);
			if (currentUser != null) {
				model.addAttribute("printStyle", currentUser.getFax());
			}
		} else {
			model.addAttribute("materials", null);
		}
		return "printpreview";
	}

	private List<Material> combineMaterials(List<Material> materials) {
		// if need to combine material
		HashMap<String, Material> materialMap = new HashMap<String, Material>();
		for (int i = materials.size() - 1; i >= 0; i--) {
			Material material = materials.get(i);
			String location = material.getLocation();
			if (materialMap.containsKey(location)) {
				Material m = materialMap.get(location);
				int currentNumber = m.getRecordStatus();
				m.setRecordStatus(currentNumber + 1);

				materials.remove(i);
			} else {
				material.setRecordStatus(1); // note: don't save to db, as it's also used in print server mode to
												// save printe status.
				materialMap.put(location, material);
			}
		}
		return materials;
	}

	private void addNameOfSpecificLanguage(List<Material> materials, String lang, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		for (Material material : materials) {
			String location = material.getLocation();
			// location is the key of the service. like 2_0_0_1, what we want is like
			// en_service_2_0_0_1_description
			if (location == null) {
				material.setColor(material.getPortionName());
				TaoDebug.error("location not set for material {}.", material.getId());
				continue;
			}
			location = lang + "_service_" + location + "_description";

			String description = textContentService.findTextByKeyAndPerson(location, person);
			if (description == null) {
				material.setColor(material.getPortionName());
				if (CC.TRUE.equals(request.getSession().getAttribute("check_lang_text"))) {
					TaoDebug.error("no description found for key {}. in store: {}", location, person.getName());
				}
				continue;
			}

			String moneyLetter = (String) request.getSession().getAttribute("moneyLetter");
			if (moneyLetter == null)
				moneyLetter = "$";
			String productName = taoUtil.fetchProductName(description, moneyLetter.split(","), request.getSession());
			if (productName == null) {
				material.setColor(material.getPortionName());
				TaoDebug.error("no productName found in TextContent with key {}.", location);
				continue;
			}

			// get the name out.
			material.setColor(productName);
		}
	}

	@RequestMapping(value = "/careerApply", method = RequestMethod.GET)
	public String careerApplyForm(Model model, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		taoUtil.initTexts(model, "text_on_career_", 1, taoUtil.getLangPrfWithDefault(request), person);
		return "careerApply";
	}

	// this is for normal careerApply, user regest first then past/upload resume.
	// while it's not used now because the
	// client need a specific way.
	@RequestMapping(value = "/careerApply", method = RequestMethod.POST)
	public String careerApply(Model model, HttpServletRequest request) {
		String tUserName = userContextService.getCurrentUserName();
		// if not throgh the main page, then the menu may not initialized yet.
		if (request.getSession().getAttribute("menuContent") == null) {
			return "redirect:/menu_1";
		}

		UserAccount userAccount = userAccountService.findUserAccountByName(TaoEncrypt.enrichName(request, tUserName));

		String content = "<p align='center'><a href='https://www.sharethegoodones.com/"
				+ request.getSession().getAttribute(CC.CLIENT) + "/dashboard'>" + "check uploaded resume" + "</a>";

		TaoEmail.sendMessage("info@sharethegoodones.com", "Career Application", userAccount.getEmail(),
				request.getParameter("content") + content, null);

		model.addAttribute("notice",
				"Application is submitted successfully! Please feel free go on uploading more documents, or input in the editor directly.");
		return "careerApply";
	}

	@RequestMapping(value = "/sendEmail", method = RequestMethod.GET)
	public String sendEmailForm(Model model, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		taoUtil.initTexts(model, "text_on_email_", 1, taoUtil.getLangPrfWithDefault(request), person);
		return "sendEmail";
	}

	@RequestMapping(value = "/sendEmail", method = RequestMethod.POST)
	public String sendEmail(Model model, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		Object managerEmail = request.getSession().getAttribute(CC.app_ManagerEmail);
		if (managerEmail != null) {
			List<UserAccount> userAccounts = userAccountService.findAllUserAccountsByPerson(person);
			for (UserAccount userAccount : userAccounts) {
				String email = userAccount.getEmail();
				if (!StringUtils.isBlank(email)) {
					try {
						TaoEmail.sendMessage(managerEmail.toString(), request.getParameter("subject"), email,
								request.getParameter("content"), null);
					} catch (Exception e) {
						System.out.println("Exception when sending email to :" + email);
					}
				}
			}
		}
		return "redirect:/menu_1";
	}
	
	// =============changing the text on page==============
	@RequestMapping(value = "/changeContentForm", method = RequestMethod.GET)
	public String changeContentForm(Model model, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);

		String key = request.getParameter("position");
		String langPrf = taoUtil.getLangPrfWithDefault(request);

		model.addAttribute("isRichText", CC.TRUE);
		model.addAttribute("position", key);
		
		String content = "";
		if(key.contains("html_service_")) {
	        key = key.substring(key.indexOf("html_service_") + 5);

	        StringBuilder sb = new StringBuilder("<div ccp_infra_version=\"3\" ccp_infra_timestamp=\"1597804130458\" ccp_infra_user_hash=\"3445629718\" ccp_infra_copy_id=\"a4cd1a70-6f98-4997-bb45-d5df1687da84\" data-ccp-timestamp=\"1597804130458\">");
	        long serviceAmount = serviceService.countServicesByKeyAndPerson(key, person);
	        model.addAttribute("serviceAmount", serviceAmount);

	        List<String> contents = taoUtil.prepareDescriptions(langPrf, key, serviceAmount, person);
	        model.addAttribute("descriptions", contents);
	        for (String string : contents) {
	        	if(string != null) {
		        	//if user modified which the pencil of specific service, the <div> could be removed....then the display might be wrong.....and the
		        	if(!string.startsWith("<div")) {
		        		string = "<div>" + string + "</div>";
		        	}
		        	sb.append(string);
	        	}
			}

			content = sb.append("</div>").toString();
		}else {
			if(!key.startsWith(langPrf)){
				key = langPrf + key;
			}
			TextContent textContent = textContentService.findContentsByKeyAndPerson(key, person);
			content = textContent == null ? "" : textContent.getContent();
		}
		model.addAttribute("content", content);
		return "richEditor";
	}

	@RequestMapping(value = "/changeTextForm", method = RequestMethod.POST)
	public String changeTextForm(Model model, HttpServletRequest request, HttpServletResponse response) {
		model.addAttribute("position", request.getParameter("position"));
		model.addAttribute("replacingContent", request.getParameter("content"));
		return buildPageForMenu(model, request, response, null);
	}

	@RequestMapping(value = "/changeText", method = RequestMethod.POST)
	public String changeText(Model model, HttpServletRequest request, HttpServletResponse response) {
		// Note: to make it stronger, if the language sign already in the
		// positionString, remove it.
		String posStr = request.getParameter("position");
		if (posStr.indexOf("_") == 2) // @notice: this mean we can not use any word with length of 2 except for
			posStr = posStr.substring(3);							// language, otherwise will cause error.

		Person person = taoUtil.getCurPerson(request);
		String key = taoUtil.getLangPrfWithDefault(request) + (posStr.startsWith("html_service") ? posStr.substring(5) : posStr);
		String content = request.getParameter("content");
		
		if(posStr.contains(CC.HTML_SERVICE.toLowerCase())) {
			//parser the content and update the services.
			if(content.startsWith("<div ccp_infra_version=") && content.lastIndexOf("<div ccp_infra_version=") == 0) {	//contains the content generated when added formatting.
				content = content.substring(content.indexOf('>') + 1);
				content = content.substring(0, content.lastIndexOf('<'));//remove the </div> at the end also.
			}else if(content.indexOf("<div ccp_infra_version=") != -1){
				int j = content.indexOf("<div ccp_infra_version=");
				int k = content.indexOf(">", j);
				if(k + 1 == content.indexOf("</div>")) { //if there's no content between the <div ccp...></div>, it happens now (2021 christmas).
					 content = content.substring(0,j) + content.substring(k + "</div>".length() );
					 //for the case like name*<div ccp_in....></div>
					 if(!content.startsWith("<div>")) {
						 int l = content.indexOf("<div>");
						 content = "<div>" + content.substring(0, l) + "</div>" +  content.substring(l);
					 }
				}
			}
			//go through each "<div>"
			int i = 1;
			int p = content.indexOf("<div");
			
			if(p > 0) { //means there's content before the first "<div>"--the content is like "dish1 $1.00<div>dish2 $2.00</div>
				String description = content.substring(0, p);
				//construct the key for textContent table
				TextContent tContent = textContentService.findContentsByKeyAndPerson(key, person);
				if (tContent == null) {
					tContent = new TextContent();
					tContent.setPerson(person);
					tContent.setPosInPage(key + "_" + i + "_description");
				}

				if (isContentValidAndProductCheck(request, posStr + "_" + i + "_description", description, person)) {
					tContent.setContent(description);
					textContentService.persist(tContent);
				}
				
				//find the price and update it into the service.
				content = content.substring(p);
				i++;
			}
			
			while(content.indexOf("</div>") > 0) {//might be start with plan text, then wrapped with <div> (if manually edit), or wrapped with <div blah blah....> if copied from notepad, or double wrapped with <div blah....
				p = content.indexOf("</div>") + 6;
				String description = content.substring(0, p);	//get the first div block.
				if(description.trim().length() == 6) {
					content = content.substring(p);
					continue;	//in case there are an etra </div>
				}
				TextContent tContent = textContentService.findContentsByKeyAndPerson(key + "_" + i + "_description", person);	//if it's not update, then create a new TextContent.
				if (tContent == null) {
					tContent = new TextContent();
					tContent.setPerson(person);
					tContent.setPosInPage(key + "_" + i + "_description");
				}

				//update relevant service and product then save the discription
				if (isContentValidAndProductCheck(request, posStr + "_" + i + "_description", description, person)) {
					if(description.startsWith("<div ccp_infra_version=")) {
						description = description.substring(description.indexOf('>') + 1);
						if(description.startsWith("<div ccp_infra_version=")) {
							description = description.substring(description.indexOf('>') + 1);
						}
						if(description.endsWith("</div>")) {
							description = description.substring(0, description.length() - 6);
						}
					}
					tContent.setContent(description);
					textContentService.persist(tContent);
					i++;
				}
				
				content = content.substring(p);
			}

			//clean other existing services.
			String serviceKey = posStr.substring(CC.HTML_SERVICE.length() + 1) + "_";
			while(true){
				Service service = serviceService.findServiceByCatalogAndPerson(serviceKey + i, person);
				if (service != null) {
					serviceService.deleteService(service);
					List<TextContent> textContents = textContentService.findAllMatchedTextContents("%_service_" + serviceKey + i + "_description", null, person);
					for (TextContent textContent : textContents) {
						textContentService.remove(textContent);
					}
					i++;
				}else {
					break;
				}
			}
		}else {
			TextContent tContent = textContentService.findContentsByKeyAndPerson(key, person);
			if (tContent == null) {
				tContent = new TextContent();
				tContent.setPerson(person);
				tContent.setPosInPage(key);
			}
			
			StructuredServiceEdit structuredEdit = parseStructuredServiceEdit(content);
			if (structuredEdit != null && posStr.startsWith("service_") && posStr.endsWith("_description")) {
				content = applyStructuredServiceEditIfNeeded(posStr, content, person);
				tContent.setContent(automaticFormat(content));
				if (tContent.getId() == null) {
					textContentService.persist(tContent);
				} else {
					textContentService.merge(tContent);
				}
			} else if (isContentValidAndProductCheck(request, posStr, content, person)) {
				tContent.setContent(automaticFormat(content));
				if (tContent.getId() == null) {
					textContentService.persist(tContent);
				} else {
					textContentService.merge(tContent);
				}
			}
			// in case the text in common area modified, and language not modified, so it
			// will not update.
			checkIfDirtFlagNeeded(posStr);
		}
		return buildPageForMenu(model, request, response, null);
	}

	public static String automaticFormat(String input) {
		if (input == null || !input.contains("<div") || !input.contains("</div>")) {
			return input;
		}

		// 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑顑藉亾缁嬫５娲Χ婢癸箑娲幃鐣岀矙閼愁垱鎲伴梻渚€娼ч…鍫ュ磿椤曗偓瀵劍绂掔€ｎ偆鍘遍梺鎶芥暜閸嬫捇鏌￠崨顔剧煁闁哄懌鍎茬换婵嬫偨闂堟稐绮堕梺瀹︽澘濮傜€规洖鐏氶幆鏃堝Ω閵壯嶇串婵犵數濞€濞佳囶敄閸涘瓨瀚呴柣鏂垮悑閻撳繘鏌涢锝囩婵℃彃鎽滅槐鎺楁偑濞嗗繑澶勯柣鎾寸懅缁辨帞鈧綆浜濋崑銉︺亜鎼淬埄娈旈棁澶愭煟濡寧鐝悘蹇ョ畵閺岀喖顢欑粵瀣暥闂佸磭绮幑鍥х暦瑜版帩鏁婇柣锝呭闁垶姊婚崒娆掑厡缂侇噮鍨堕幃褍顭ㄩ崼婵嗗亶閻熸粍妫冮獮鍐┿偅閸愨晛鈧鏌ら幁鎺戝姎濞存粍顨婇弻鐔煎礂閸忕厧鍓归梺鍝ュУ椤ㄥ﹤鐣烽幘缁橆棃婵炴垼椴哥€靛矂姊洪棃娑氬婵☆偅鐟ф禍鎼佹偨閸涘﹦鍘遍梺缁橆焾濞呮洖鐣烽崟顖涚厪闁搞儯鍔屾慨宥団偓瑙勬磸閸斿酣鍩€椤掍胶鈯曢懣銈夋煙妞嬪海甯涚紒缁樼⊕濞煎繘宕滆琚ｆ俊鐐€ら崑鍕囬棃娑氭殾闁硅揪闄勯崑鎰磽娴ｈ鐒介柛姗€浜跺娲传閸曨偀鍋撻挊澶嗘灃闁哄洠鈧枼鍋撻幘顔解拻濞达絿鍎ら崵鈧紓浣哄Т缁夌懓鐣疯ぐ鎺撳癄濠㈣泛鏈▓鎯р攽椤斿浠滈柛瀣尰閵囧嫰濮€閿涘嫭鍣伴悗瑙勬礀瀵泛顕ラ崟顐熸婵椴稿▓濂告⒒娴ｅ憡鎯堥柡鍫墴閹嫰顢涘鐓庢闂佸湱铏庨崰鏍ㄥ劔闂備線娼ч敍蹇涘磼濮橆厽鍟熼梻鍌氬€搁崐椋庣矆娓氣偓楠炲鏁撻悩鑼槷闂婎偄娲︾粙鎴犲婵犳碍鐓欓柛鎾楀懐鐦堢紓浣筋嚙濡繈寮婚敐澶婄疀闁稿繐鎽滈惄搴ㄦ⒑闁偛鑻晶顖炴煟濡ゅ啫孝闁伙絽鍢查埞鎴犫偓锝庡亝濞呭洭姊洪柅鐐茶嫰婢ф挳鏌嶉妷顖滅暤鐎规洜顭堣灃闁逞屽墴閹偤宕归鐘辩盎闂佸湱鍎ら崹鐢稿焵椤掑啫鍚瑰瑙勬礋瀹曟绮潪鎵泿闂備礁鎼崯顐﹀磹閸濄儲鏆滈柤鍝ュ仯娴滄粓鏌ㄥ┑鍡樺櫧闁挎稑绉剁槐鎺撴綇閵娿儳鐟插┑鐐靛帶缁绘ɑ淇婇幖浣规櫇闁逞屽墴濡嫬顓兼径瀣ф嫼闂佽崵鍠愬妯何ｆ繝姘厵闁惧浚鍋撻懓鍧楁煟濞戝崬娅嶆鐐搭焽閹风娀鎳犻澶婃暯闂佽楠哥粻宥夊磿闁秴绠悗锝庘偓顓熺☉椤劑宕橀敐鍡樻澑闂備胶绮敋闁诲繑宀稿鎶藉煛娴ｅ弶鏂€濡炪倖妫侀褍鐣甸崱娆屽亾鐟欏嫭绀夋い顐㈩槺缂傛捇鎮惧畝鈧惌娆撴煙缁嬪灝顒㈢悮锝夋⒒閸屾瑧顦﹂柟纰卞亞缁瑦绗熼埀顒€鐣烽姀銈嗗殐闁冲搫鍟伴悞鍏肩節閵忥絽鐓愰柛鏃€鐗犻幃鈥斥槈濮橈絽浜炬鐐茬仢閸旀艾螖閻樿櫕鍊愰柣娑卞枛铻栭柛娑卞枤閸橀亶鏌ｆ惔顖滅У濞存粍绮撻崺鈧い鎺嗗亾婵犫偓闁秴绠查柕蹇曞Л濡插牓鏌曡箛鏇炐ユい鎾虫惈閳规垿鎮欓崣澶樻缂備胶绮敮妤冪矉閹烘挶鍋呴柛鎰ㄦ杹閹疯櫣绱撻崒娆戝妽閽冮亶鎮樿箛鏇烆暭缂佺粯鐩幊鐘活敆閳ь剟寮稿☉姘ｅ亾濞堝灝鏋涙い顓炲槻椤曪綁骞橀纰辨綂闂佺粯顭堥褔寮憴鍕瘈闁汇垽娼ч埢鍫熺箾娴ｅ啿鍘惧ú顏勎ч柛娑变簼閻庢椽鎮峰鍕叆妞ゎ偄绻橀幖褰掓偡閺夋鍤欓梻浣告惈濞层劑宕伴幇鐗堝仧闁靛ě鍛紳婵炶揪绲藉﹢閬嶅矗閳ь剛绱撴担鍝勑ｉ柣妤侇殕缁岃鲸绻濋崶銊ヤ缓缂備礁顑堝▔鏇⑺囬弶娆炬富闁靛牆妫涙晶顒傜磼鐎ｎ偄绗掗柍璇茬Т椤劑宕奸悤浣诡棅婵＄偑鍊栭崝鎴﹀垂閻戞ê绶為柛鏇ㄥ墰缁犻箖鏌涘▎蹇ｆШ闁活厽甯為埀顒侇問閸犳绻涙繝鍥х畺闁冲搫鍟扮壕鍏间繆椤栨粌甯舵鐐村姇閳规垿鎮欓懜闈涙锭缂備浇寮撶划娆撶嵁濡も偓椤劑宕熼浣哄娇婵犵數鍋為崹鍫曞Υ閹烘挻鍎熼柕濞垮劤閸婄偤鎮峰鍕疄闁绘搩鍓熼、妤呭焵椤掑嫬鐓橀柟杈剧畱閻忓磭鈧娲栧ú銈夋偂閻斿吋鍊甸悷娆忓绾炬悂鏌涢弮鈧崹鍧楀Υ娴ｇ硶鏋庨柟鎯х－椤ρ呯磼閻愵剚绶茬憸鏉款樀閹姤绻濆顓涙嫽闂佺鏈懝楣冨焵椤掑倸鍘撮柟顔惧仱閺佸倿鎮惧畝鈧鏇熺節閻㈤潧孝婵炲眰鍊楃槐鎺楀煛閸涱喒鎷哄銈嗗坊閸嬫挾绱掓径瀣唉鐎规洖缍婂畷鎺楁倷鐎电骞堥柣鐔哥矊闁帮綁濡撮崘顔煎窛閻庢稒锚閻濇棃姊虹紒妯荤叆闁告艾顑夐幃锟犳偄閸忚偐鍘搁梺绋挎湰濮樸劍绂掗姀銈嗙厵妞ゆ棁妫勯悘鎾煛鐏炲墽娲村┑鈥崇埣瀹曘劑顢橀悢鍓插晪闂傚倷绀侀幖顐︽嚐椤栨粎鐭撻梻鍫熶緱閻掕姤銇勮箛鎾跺闁稿顑夐弻娑㈩敃椤愵澀绨奸梺瀹狀嚙閿曪妇妲愰幘璇茬＜婵炲棙鍨肩粣妤呮⒑閸濄儱校闁绘濞€楠炲啴濡烽埡鍌氫簵闁瑰吋鎯岄崰妤冪礊鎼粹檧鏀介柣鎰级閳绘洖霉濠婂嫮鐭婃い鏂跨箰閳规垿宕辫箛鏃€鏉搁梻浣虹帛钃辩憸鏉垮暣瀵櫕绻濋崶銊у幍閻庣懓瀚竟鍡欑矓閻戞ɑ鍙忓┑鐘插暞閵囨繈鏌熼鎯у幋闁诡啫鍥ч唶婵犻潧娲﹂悾鍏肩節閻㈤潧啸闁轰焦鎮傚畷鎴濃槈閵忊剝娅囬梺闈涚墕椤︿粙寮€ｎ喗鐓ユ繝闈涙婢跺嫰鎮楀顓狀暡缂佺粯鐩鍫曞箣椤撶偛澹夋俊鐐€ら崑鍕矓閻熼偊娼栨繛宸簼椤ュ牊绻涢幋鐐寸殤妞ゆ柨锕ョ换婵嗏枔閸喗鐏撻梺绋跨箲閿曘垽濡存担绯曟瀻闁规儳纾濠傗攽鎺抽崐鎾绘嚄閸洖鍌ㄥù鐘差儐閻撴瑩鏌涜箛姘汗闁哄棙锕㈤弻娑㈠棘鐠恒剱銏ゆ煃閽樺妲兼い锕€缍婇弻鈥崇暆閳ь剟宕伴幘璇茬闁绘ü璀﹀Σ鍓х磽娴ｅ弶顎嗛柛瀣崌閺岋絾鎯旈妶搴㈢秷濠电偛寮惰摫缂佺粯绋掔换婵嬪炊閵夈垹浜惧ù锝囩《閺嬪酣鏌熼幆褏锛嶆い锔诲弮閹嘲顭ㄩ崘顏嗩啋闂佽桨绀佺粔鎾煘閹达箑鐐婇柍鍝勫枤閸熷酣姊绘担鐑樺殌濠⒀傜矙钘熼柟閭﹀幘閻濊埖淇婇婵嗗惞缂佺娀绠栭幃姗€鎮欓幓鎺嗗亾閹间礁鐒垫い鎺嶈兌缁犳挻銇勯弴顏嗘偧缂侇喗鐟﹂幆鏃堝箻閹碱厽缍岄梻鍌欑窔濞佳勵殽韫囨洘顫曢柡鍥ュ灩閸屻劑鏌﹀Ο渚Т闁衡偓娴犲绠抽柟鎯版绾惧綊鏌熼崜褏甯涢柛濠傛健閺屻劑寮崹顔剧◤闂佺粯甯掗敃銉╁Φ閸曨垰绠婚柛妤冨仧娴犻箖姊虹紒妯诲暗闁哥姵鐗犲濠氬Ω閵夈垺鏂€闂佺硶妾ч弲婊勬櫏闂傚倷娴囧銊ッ归崶銊ь洸妞ゆ帒鍊归～鏇㈡煙閻戞﹩娈曢柟顖滃仱閹嘲鈻庤箛鎿冧痪闂佺粯绻嶆禍鐐垫閹惧鐟归柛銉戝嫮浜堕梻浣藉吹閸熸瑩宕惰椤斿姊洪崨濠冨闁搞劎澧楅崕顐︽⒒娓氣偓濞佳囁囬銏犵？闁规儼濮ら崑鈺呮⒑椤掆偓缁夌敻鍩涢幒妤佺厱閻忕偞宕樻竟姗€鏌嶈閸撶喖藟閹捐泛鍨濋柛顐ゅ枔閻熷綊鏌嶈閸撶喖鐛崘顔奸敜婵°倐鍋撻柣鎰功閹茬顭ㄩ崗鐘茬秺瀵粙顢橀悢鍝勫妇濠电姷鏁搁崑娑㈡倶濠靛绀夐悗娑欘焽缁犻箖鏌熼悙顒€澧柣鎾炽偢閺屸€崇暆閳ь剟宕伴弽顓炵畺婵犲﹤鍚橀悢鐑樺珰闁告瑥顦伴鍌炴⒒閸屾瑨鍏岀紒顕呭灣閹广垽宕掗悙鏉戜函婵犻潧鍊婚…鍫ユ嫅閻斿吋鐓ラ柣鏂挎惈瀛濋梺缁樺姇閿曨亪寮诲鍡樺闁瑰嘲鐭堥崬褰掓⒑缁嬫鍎愰柟鐟版喘瀵偊骞囬鐐电獮婵犵數濮寸€氼喖顭囨径鎰拻濞达絿顭堥ˉ蹇涙煕鐎ｎ亝顥㈢€规洜澧楅幆鏃堝閵忊晜閿ら柣鐔哥矌婢ф鏁Δ鍛厱闁圭儤顨嗛悡鏇㈡倶閻愰潧浜鹃柣銊﹀灥椤儻顦崇紒瀣尵濡叉劙骞掗弬鍝勪壕闁挎繂楠告禍婊冣攽椤旂⒈妯€闁哄本鐩獮鎺楀箻閾忣偉鐧侀柣搴ゎ潐濞叉牠濡剁粙娆惧殨闁圭虎鍠楅崑鍕煕濞嗗浚妲告い蹇撶秺濮婄粯鎷呴挊澶婃優婵犳鍠楀娆戝弲闂佸啿鎼崯鈺呮倿婵犲啰绠鹃柛鈩兠悘鈺佲槈閹惧磭效闁哄被鍊楅崰濠囧础閻愬樊娼剧紓浣哄亾閸庢娊濡堕幖浣哥畺鐟滅増甯掗悙濠冦亜韫囨挸顏慨锝冨灪缁绘繈鍩涢埀顒勫礋椤掑偆妲归梻浣告惈閻绱炴笟鈧顐﹀箛閺夊灝绐涘銈嗘尵閸熸﹢鍩￠崨顔规嫼闁诲骸婀辨刊顓烆焽閹扮増鐓曢柕濞垮劤娴犮垽鏌ｉ敐鍥у幋闁哄苯妫楅濂稿川椤栨艾绠伴梻鍌欒兌椤牓寮甸鍕殞濡わ絽鍟崒銊╂煟閹邦厾銈撮柡鈧禒瀣叆婵炴垶锚椤忊晛霉濠婂啨鍋㈤柡灞剧⊕缁绘繈宕橀鍕ㄦ嫲婵°倗濮烽崑鐐哄礉濞嗘挸绠栨繛鍡樻惄閺佸棗霉閿濆浂鐒惧ù鐓庨叄濮婄粯鎷呴搹鐟扮闂佹悶鍔岀紞濠囧箖濞差亜绫嶉柛顐ｇ箖閻庮剚绻濋悽闈浶㈡繛璇х畵閹€斥槈閵忥紕鍘遍梺闈涱檧缁蹭粙宕濆鑸电厽闊浄绲奸柇顖炴煛瀹€瀣М妤犵偞顭囬幑鍕倻濡棿閭梻鍌欒兌缁垶骞栭埡鍛；濠电姴鍊婚弳锕傛煕椤愶絾绀€閹喖姊洪幐搴⑩拻闁惧繐楠稿嵄妞ゆ巻鍋撻柍瑙勫灴閹瑥顔忛鍏碱啀濠电姵顔栭崰姘跺极閹间礁绀嗛柟鐑橆殔缁犲鎮归崶顏勮敿闁硅姤娲栭埞鎴︽倷閺夋垹浠搁梺鑽ゅ暀閸涱厼袣闂侀€炲苯澧存慨濠呮缁瑩骞愭惔銏″缂傚倷绀侀鍡涘垂閸噮鍤曢柟鎯版闁卞洭鏌ｉ弮鍥仩妞ゆ梹娲熷娲箰鎼达絿鐣甸梺鐟板暱缁绘﹢鐛弽顓炵疀闁绘鐗忛崣鍡涙⒑閸濆嫭鍌ㄩ柛鏂款儑閼鸿鲸绻濆顓犲幗闂佹寧绻傚ú锕傛倿閸ф鐓忛柛銉ｅ妼婵秵顨ラ悙鏉戠瑨闁宠鍨垮畷鍫曞Ω閿旀儳鍓戦梻鍌氬€搁崐椋庣矆娓氣偓楠炲鍩勯崘顏嗘嚌濠德板€曢幊搴ㄥ垂閸屾褰掑礂閸忚偐绋囬梺鍝勵儎缁舵岸寮婚悢鍏尖拻閻庣數顭堟俊浠嬫⒑閹惰姤鏁遍柛銊ユ健瀵濡堕崱妯哄伎闂佸綊鍋婇崗姗€宕戦幘璇插嵆闁靛繒濮撮崵鎴︽⒑绾懏褰ч梻鍕閹繝鎮㈤崗鑲╁幗闂佸搫鍟犻崑鎾寸箾閸忚偐鎳囩€规洘绻堟俊鑸靛緞鐎ｎ剙甯楅柣鐔哥矋缁挸鐣峰鍐炬僵閻犻缚娅ｉˇ鈺呮偡濠婂懎顣奸悽顖涘浮瀹曟帡濡搁埡鍌滃幈闂佸湱鍋撻〃鍛村疮椤愶附鍊跺┑鐘叉处閳锋垿鏌涢幘鏉戠祷濞存粍鐗犻弻娑欐償閵堝嫬鎯堢紓渚囧枛閻楁挸鐣峰鈧崺鐐村緞閸濄儳娉块梻鍌氼煬閸嬪嫬煤閿曞倸绠板┑鐘愁嚟婢舵劖鍋愮紓浣诡焽閸欏棗鈹戦悙鏉戠仸闁荤啙鍥ㄥ剹鐎光偓閳ь剛妲愰幒鎾寸秶闁靛绠戠壕鎶芥倵鐟欏嫭绀冮悽顖涘浮閸┿垺鎯旈妸銉ь吅濠电娀娼уΛ顓㈡倵閺夋垟鏀介柨娑樺娴滃ジ鏌涙繛鍨偓婵嗙暦閵忋倖鍋傛鐐靛枑濡炶姤淇婂宀婃Щ闂佹悶鍔嶇换鍌炲煘閹达附鍋愰悗鍦Т椤ユ繄绱撴担鍝勑ョ紒顕呭灣閹广垹鈽夐姀鈩冩珳闁硅偐琛ラ埀顒€鍟跨粻鎵磽閸屾瑩妾烽柛鏂跨箻椤㈡俺顦归柍銉︽瀹曟﹢顢欓崲澹洦鐓曢柍鈺佸枤濞堟梻鎮伴懖鈺冪＝闁稿本鐟︾粊浼存倶韫囷絼绨婚柍缁樻瀵挳鎮╅幓鎺斾喊濠电姷鏁告慨鐢靛枈瀹ュ纾归梺鍨儑缁犻箖鏌ㄥ┑鍡涱€楅柛妯烘憸缁辨帡濡搁妷顔惧悑濠殿喖锕ュ钘壩涢崘銊㈡婵浜弳鐗堢節绾版ê澧茬憸鏉垮暟閳ь剚鐭崡鎶界嵁婵犲洤绠婚悹鍥ㄧ叀閺佹粌鈹戞幊閸婃捇鎳楅崼鏇炲偍闂傚牊渚楀〒濠氭煏閸繃顥炴い銉ユ閵囧嫰顢曢姀鈺傂﹂梺瀹狀嚙缁夌數鎹㈠┑瀣闁绘劦鍓﹂崯宥夋⒒娴ｈ櫣甯涢柛鏃€娲熼幃娲Ω閵壯勬闁诲繒鍋熼搹搴♀枔娴犲鐓熼柟閭﹀幗缂嶆垶绻涢幖顓炴灍妞ゃ劊鍎甸幃娆愭綇閹规劏鍋撶捄銊㈠亾鐟欏嫭绀堥柛蹇旓耿閵嗕礁顫滈埀顒勫箖濞嗘挻鍤戞い鎺嗗亾闁活厽绻冪换婵嬫偨闂堟刀銉╂煛娴ｅ憡鍟為柟渚垮姂閹粓鎸婃径瀣偓顒勬⒑閸涘﹦鈽夐柨鏇樺劦瀹曪綀绠涘☉娆戝幗闂佺鎻徊楣兯夋径鎰厸閻庯絺鏅濈粣鏃堟煛鐏炲墽鈽夐柍钘夘槸椤粓宕奸悢鍛婃瘞闂傚倷绀侀幉锟犳偡閵壯勫床婵せ鍋撶€殿喖顭烽弫鎰緞婵犲嫷鍚呴梻浣瑰缁诲倸螞椤撶倣娑㈠礋椤栨稈鎷洪梺鍛婄箓鐎氱兘宕曟惔锝囩＜闁兼悂娼ч崫铏光偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇娴滄粍銇勯幘璺盒㈤柛妯侯嚟閳ь剚顔栭崰鏍€﹂悜钘夋瀬闁归偊鍘肩欢鐐测攽閻樻彃顏柡澶婃啞娣囧﹪鎮欓鍕ㄥ亾閺嶎偅鏆滃┑鐘叉处閸婂潡鏌ㄩ弴鐐测偓鍝ュ閸ф鐓欓柛鎾楀懎绗￠梺鎶芥敱閸ㄥ潡寮婚悢鍏煎殐闁宠桨妞掔划鑸电節绾版ǚ鍋撻悙钘変划闂佸搫鏈粙鎺旀崲濠靛纾兼繛鎴灻煎ǎ顕€鏌ｆ惔銏╁晱闁哥姵鐩、姘愁樄闁糕斂鍎插鍕箛椤掑缍傞梻浣稿暱閹碱偊宕愰悷鎵虫瀺闁搞儺鍓氶埛鎺懨归敐鍥ㄥ殌妞ゆ洘绮庣槐鎺旀嫚閹绘巻鍋撳宀€浜辨繝鐢靛仦閸垶宕瑰ú顏勭柧妞ゆ帒瀚悡娑㈡煕閵夛絽鍔氶柣蹇ｄ邯閺屽秹鏌ㄧ€ｎ亞浼屽┑顔硷攻濡炰粙銆侀弴銏犖ч柛娑卞墰閹规洟姊绘担绋挎倯婵＄偛娼″畷褰掓焼瀹ュ懐鏌ч梺鍓插亝濞叉牜绮堥崼銏″枑闊洦娲栭ˉ姘攽閸屾碍鍟為柣鎾跺枑娣囧﹪顢涘☉姗嗗殝闂佺粯绻嶆禍顏堝箖濡法鐤€闁挎繂鎳庣猾宥夋⒑閸濆嫮鐒跨紓宥勭窔瀵偊骞囬弶璺ㄥ€為悷婊冪Ч閻涱喚鈧綆鍠楅崐鐢告偡濞嗗繐顏紒鈧埀顒勬⒑闂堟稓澧㈡い顓炵墦椤㈡岸鏁愭径濠勵啋濡炪倖妫佸Λ鍕焵椤掑嫷妫戦柍褜鍓欑粻宥夊磿娴煎瓨鍊块柨鏇楀亾闁宠绉电换婵嬪炊閵娧冨箰闂佽绻掗崑鐘活敋瑜庨幈銊╁磼濠ф儳浜炬繛鍫濈仢濞呮﹢鏌涢敐蹇曞埌闁伙絿鍏橀獮鎺楀箣閺冣偓椤秴鈹戦悙鍙夘棡闁搞劑娼ч埥澶庮樄婵﹥妞介幊锟犲Χ閸涱喚鈧墽绱撴笟鍥ф灈缂佸鎸抽垾锕傚垂椤斻儳鍠愮粭鐔碱敍濠婂啫楔闂傚倷鑳堕崑銊╁磿閺屻儱绀堟慨姗嗗墻閻掍粙鏌″搴′簽缁炬崘妫勯湁闁挎繂鐗滈崵鍐煟閹哄秶鐭欓柡宀嬬秮閹垺顦版惔锛般劑姊洪崫鍕缂佸鍏樼瘬濞撴埃鍋撻柡灞剧洴楠炴鈧潧鎲￠崳浼存⒑閻愯棄鍔电紒鐘虫尭閻ｇ兘宕奸弴銊︽櫆閻庡箍鍎遍弻濠囶敆娴ｈ櫣鐦堥梺姹囧灲濞佳勭濠婂嫪绻嗘い鎰╁灲閸欏嫮鈧娲栫紞濠囧箰婵犲啫绶為悘鐐靛亾椤旀洟鏌ｉ悢鍝ョ煂濠⒀勵殘閺侇噣鍩￠崨顓熺€梺鍛婂姀閺傚倹绂嶅鍫熺厵闁绘垶锚閻忋儵鏌嶈閸撴瑩鈥﹂悜钘夌畺闁跨喓濮寸粻锝嗙節闂堟稑鏆為柡鍌楀亾闂傚倷鑳剁划顖炲礉濮椻偓瀹曟椽寮介鍙ユ睏閻熸粌绻掑Σ鎰板箻閹颁礁鎮戞繛杈剧秮閺呭弶绔熼弴鐐╂斀闁绘ê鐏氶弳鈺呮煕鐎ｎ偆娲存鐐茬箻楠炲鎮╅懠顒傜摌濠电偛顕慨鎾敄閸涘瓨鍋傛繝闈涱儐閻撴盯鏌涢幇鈺佸濠⒀勬礋濮婅櫣鎲楅妶鍛亾閺嶃劎鈹嶅┑鐘叉搐鍥撮梺鍛婃处閸ｎ噣宕ぐ鎺撯拺闁荤喐婢樺Σ缁樸亜椤撶偛妲绘い顐㈢箻閹煎綊宕烽鐘靛幆闂備礁婀遍…鍫⑩偓娑掓櫊楠炲繘鏌嗗鍡忔嫼闂佺厧顫曢崐鏇㈠几閸岀偞鐓曢柡鍐ｅ亾闁搞劎鏁婚幃鎯х暋閹锋梹妫冮崺鈧い鎺戝閸嬧晜绻濋棃娑卞剰缂佲偓鐎ｎ偁浜滈柟鐑樺灥閳ь剚鎮傞悰顔碱吋婢跺鎷绘繛杈剧悼閹虫捇宕濋妶鍡欑閻庢稒锚閸濊櫣鈧娲橀崹鍨暦閵娾晩鏁囬柣妯挎珪椤旀洟鏌ｆ惔鈥冲辅闁稿鎹囬弻娑㈠即閵娿儱顫梺鍛婏耿濞佳団€旈崘顔嘉ч柛鈩冿供濮婃寧绻濆▓鍨灁闁稿﹥绻冩穱濠囨濞戞帗鏂€闂佺硶鈧磭绠查柡鍌楀亾闂傚倷鐒﹂弸濂稿疾濞戙垹鐤ù鍏兼綑缁犳椽鏌涢幇銊︽澓濞存粍绮撻弻銊╁即濡も偓娴滃墽绱掗崜褑妾搁柛娆忓暣瀹曟椽鍩€椤掍降浜滈柟鐑樺灥閺嬨倖绻涢崗鐓庡缂佺粯鐩畷锝嗗緞濞戞壕鍋撻崸妤佺厵妞ゆ洖妫涚弧鈧梺杞扮劍閹瑰洭骞冮埡鍛殤闁肩鐏氶崯鍝勨攽閻樺灚鏆╅柛瀣仱瀹曞綊顢涢悙鎻掔€梺闈╁瘜閸樼偓绋夊鍡欑闁瑰瓨鐟ラ悘顏堟煟閹惧磭绠伴柍瑙勫灴閹晠鎳犻鍌ゅ晪婵犵绱曢搹搴ㄥ闯閿濆钃熼柨婵嗘噳閺€浠嬫煕閵夛絽濡烽柟鐤吹缁辨挻鎷呮禒瀣懙闂佸湱顭堥…鐑界嵁韫囨稑宸濇い鏍ㄧ矌閻撴挸鈹戦悙鍙夘棤闁稿鎸搁埢鎾趁洪鍛嫽婵炶揪绲肩拃锕傛倿閻愵兙浜滈柟瀛樼箓閺嗭絿鈧娲橀崝鏇㈠煘閹达箑骞㈤柍杞扮劍椤撳潡姊洪懡銈呅㈡繛璇у缁﹪寮堕幊绛圭秮瀹曞ジ濡烽敂鎯у妇闂傚鍋勫ú锕€煤閺嵮呮懃闂佽姘﹂～澶愬箰缁嬭娑樷攽閸♀晜缍庡┑鐐叉▕娴滄繈藟閸喓绠鹃柟杈剧导閸氼偊鏌涢弮鈧幃鍌氼潖閾忓湱鐭欓柟绋块閺€顓熺箾鐎涙鐭ら柛鎾寸洴瀹曟岸骞掗幘鍓佺槇濠殿喗锕╅崢鐓庘枔閵夆晜鈷戦梻鍫熺〒婢ф洘绻涚仦鍌氬闁轰礁绉撮…銊╁醇閻斿弶瀚肩紓鍌氬€烽悞锕傛晪婵犳鍣粻鎾诲蓟濞戙垹绠婚悗闈涙啞閸ｄ即鎮楃憴鍕缂佽鐗婃穱濠囨嚋闂堟稓绐為柣搴秵閸撴瑧鏁ィ鍐┾拻濞达絿顭堥ˉ蹇涙煕鐎ｎ亝顥㈢€规洑鍗抽獮鍥级鐠侯煉绱梻浣哥秺閸嬪﹪宕ｆ惔鈾€鏋斿ù鐘差儐閻撶喖鏌熼柇锕€骞栫紒鎻掝煼閺岋繝宕卞Ο鍏煎櫚闂佺妫勯悥鐓庮嚕娴犲鏁囬柣鏂挎惈楠炲牊绻濋悽闈涗沪闁搞劑娼ч悾鐑筋敆閸曨偄鍤戦梺缁樻煥閹芥粎绮绘ィ鍐╃厱婵炴垵宕弸銈夋煟椤撶喎娴柡灞糕偓宕囨殕闁逞屽墴瀹曚即寮介鐐电暫闂佹枼鏅涢崯顖炲磿閻斿吋鐓ユ繝闈涙椤ョ偤鏌涢悩宕囧⒈缂佽鲸鎹囧畷鎺戔枎閹邦喓鍋樻俊鐐€栧▔锕傚炊瑜忛弻鍫ユ⒑閸濆嫷妲归柛銊ョ秺瀵娊鏁冮崒娑氬幐婵犮垼娉涢鎰板磿閵夛妇绠鹃悘蹇旂墬濞呭棝鏌曢崶褍顏鐐差儔閺佹劙宕ㄩ崨顔藉磳闁哄矉绻濆畷鎺懳旈埀顒佺妤ｅ啯鈷掗柛灞捐壘閳ь剟顥撶划鍫熺瑹閳ь剟骞冮崸妤€鐐婃い顒夊墯閻╊垰鐣烽悢纰辨晝閻庯綆鍋嗛埥澶愭懚閺嶎厽鐓曟繛鎴濆船閺嬫盯鏌嶈閸撴繈宕洪弽顐ｅ床婵炴垯鍨归獮銏′繆椤栨繍鍤欓梻澶婄Ч濮婅櫣绱掑Ο璇查瀺濠电偠灏欓崰鏍嵁閸愩剮鏃堝川椤旇姤鐝抽梺纭呭亹鐞涖儵鍩€椤掑啫鐨洪柛鎴濈秺閺岋絾鎯旈妶搴㈢秷濠电偛寮堕悧鐘差嚕閻㈠壊鏁嗛柛鏇ㄥ亞閻ゅ嫰姊洪棃娑辩叚濠碘€虫川瀵囧焵椤掑嫭鈷戦柛娑橈攻婢跺嫰鏌涘Ο鍨汗缂侇噮鍙冮弫鎾绘偐閺傘儲瀚奸梻浣藉吹閸犳劕顭垮Ο浣曪絾绻濆顓犲幍闂佹儳娴氶崑鎾绘嚀閹稿寒娈介柣鎰嚟婢ф娊鏌曢崶銊ュ妤犵偞甯″顒傛崉閵婏絽浜伴梻鍌欑濠€閬嶅储瑜旈幃娲Ω閳轰胶顔囬梺鍛婄☉閿曪箓銆呴弻銉︾厵妞ゆ牕妫岄崑鎾绘煛閳ь剚绂掔€ｎ偆鍘藉┑鈽嗗灠閹碱偆鐥椤法鎹勯崫鍕典純闂佸搫鐭夌紞渚€鐛Ο灏栧亾闂堟稒鍟為柛鎺撶洴濮婃椽宕崟顒佹嫳缂備礁顑嗛幐鑽ょ矉閹烘鏅濋柛灞炬皑椤︻參鎮峰鍐弰鐎规洘濞婇、娆撳礈瑜忛敍婵囩箾鏉堝墽鎮奸柟铏尰閹便劑宕奸悢绋垮伎婵犵數濮撮崑鍡涙偂婵傚憡鐓曢柍瑙勫劤娴滅偓淇婇悙顏勨偓鏍暜閹烘绐楁慨姗嗗墻閻掍粙鏌熼柇锕€骞樼紒鐘荤畺閺屾稑鈻庤箛锝嗩€嗛梺鍏煎濞夋洟鍩€椤掑喚娼愭繛鍙夌墱缁辩偞绻濋崒婊勬缂備礁顑堥鎶藉籍閸繄顦ㄩ梺闈浤涙担闀愬枈濠电姷鏁告慨鐢割敊閺嶎厼绐楁俊銈呮噹妗呭┑鐐村灟閸ㄥ湱澹曟繝姘厵闁绘劦鍓氶崕妤佺箾閸粎鐭欓柡宀嬬秮楠炲洭顢楅埀顒傜棯瑜嶉…璺ㄦ崉閸濆嫷浼冮梺鍝勭灱閸犲酣鎮鹃敓鐘冲亱闁割偅绻冮悾濠氭⒒娴ｇ瓔鍤冮柛鐘冲哺瀹曟顫滈埀顒勬偘椤旂⒈鍚嬪鑸瞪戦弲鈺呮⒑閸涘﹦鎳愰柛瀣噽閺侇噣鍨鹃幇浣圭稁婵犵數濮甸懝鍓у閸忓吋鍙忔慨妤€妫楅崢鎾煕鐎ｎ偅灏版い锔惧閹棃濮€閳轰椒缂撳┑鐘愁問閸犳鏁冮埡鍛闁挎洖鍊归崐鍧楁煟閿濆懏婀伴柛鐘冲姍閺岋繝宕橀妸褍顣虹紓浣哄У閻楁粓骞夐幖浣告閻犳亽鍔嶅▓楣冩⒑閸涘娈橀柛搴㈠姇閵嗘帗绻濆顓犲帾闂婎偄娲㈤崕宕囧閸ф鐓冮柦妯侯樈濡偓闂佺妫勯悥鐓庮嚕娴犲鏁囬柣鏂挎惈楠炲牓姊绘担绛嬫綈婵犮垺锕㈠畷銉╁焵椤掑倻纾奸柕濞垮€楅惌娆撴煛瀹€瀣М闁轰焦鍔欏畷銊╊敍濮樺崬骞楀┑鐘愁問閸犳牠鏁冮妸銉㈡瀺闁挎繂娲ら崹婵囩箾閸℃ê鐏︾€规洖顦甸弻鏇熺箾瑜嶉崐鑽ょ矆閸愵喗鈷掗柛灞剧懆閸忓本銇勯鐐靛ⅵ妞ゃ垺鐗犲畷鍫曨敆閳ь剛澹曟繝姘厪闁割偅绻嶅Σ鍝ョ磼閻樺磭澧甸柡灞诲姂閹垽宕崟鎴秮閺屾稓鈧綆鍋勬慨澶岀磼鏉堛劍灏伴柟宄版噽閹风娀鏁嶉崟顐⑿ㄩ梻鍌欑閹诧繝鏁冮姀锛勵洸閻犺桨璀﹀鏍旈敃渚婇獜濞存粌缍婇弻鐔兼倻濡櫣浠梺鍝ュТ閿曨亜顫忓ú顏勭闁绘劖褰冮‖鍡涙⒑閸涘娈曞┑鐐诧躬閻涱噣宕卞☉妯碱槰濡炪値鍘奸鍡楊渻娴犲鈧線寮崼婵嗙獩濡炪倖鐗楅懝鍓х不閻愮儤鈷掗柛灞剧懅椤︼箓鏌涢悢閿嬪仴妤犵偞鐗犻、鏇㈡晜缂佹ɑ娅堥梻浣规偠閸庮噣寮插┑瀣剹闁圭偓鐣禍婊堟煛閸ヮ煈娈斿ù婊堢畺濮?div>濠电姷鏁告慨鐑藉极閸涘﹥鍙忛柣鎴ｆ閺嬩線鏌涘☉姗堟敾闁告瑥绻橀弻锝夊箣閿濆棭妫勯梺鍝勵儎缁舵岸寮诲☉妯锋婵鐗婇弫楣冩⒑閸涘﹦鎳冪紒缁橈耿瀵鏁愭径濠勵吅闂佹寧绻傚Λ顓炍涢崟顖涒拺闁告繂瀚烽崕搴ｇ磼閼搁潧鍝虹€殿喛顕ч埥澶娢熼柨瀣垫綌婵犳鍠楅〃鍛存偋婵犲洤鏋佸Δ锝呭暞閳锋垿鏌涘☉姗堝姛闁瑰啿鍟扮槐鎺旂磼濮楀牐鈧法鈧鍠栭…鐑藉极閹邦厼绶炲┑鐘插閸氬懘姊绘担鐟邦嚋缂佽鍊歌灋妞ゆ挾鍊ｅ☉銏犵妞ゆ牗绋堥幏娲⒑閸涘﹦绠撻悗姘卞厴瀹曟洘鎯旈敐鍥╋紲闂佸吋鎮傚褔宕搹鍏夊亾濞堝灝鏋涙い顓犲厴楠炲啴濮€閵堝懐顦ч柣蹇撶箲閻楁鈧矮绮欏铏规嫚閺屻儱寮板┑鐐板尃閸曨厾褰炬繝鐢靛Т娴硷綁鏁愭径妯绘櫓闂佸憡鎸嗛崪鍐簥闂傚倷鑳剁划顖炲礉閿曞倸绀堟繛鍡樻尭缁€澶愭煏閸繃顥撳ù婊勭矋閵囧嫰骞樼捄鐩掋垽鏌涘Ο渚殶闁逞屽墯椤旀牠宕伴弽顓涒偓锕傛倻閽樺鐎梺褰掑亰閸樿偐娆㈤悙娴嬫斀闁绘ɑ褰冮顐︽煛婢跺﹥鍟炲ǎ鍥э躬閹瑩顢旈崟銊ヤ壕闁哄稁鍋呴弳婊冣攽閻樺弶澶勯柛濠傛健閺屻劑寮撮悙娴嬪亾閸濄儱顥氶柛褎顨嗛悡娆撴煕閹炬鎳庣粭锟犳⒑閹惰姤鏁遍柛銊ユ健瀵鎮㈤崗鐓庢異闂佸疇妗ㄥ鎺斿垝閼哥數绡€缁剧増菤閸嬫捇鎮欓挊澶夊垝闂備礁鎼張顒傜矙閹烘梹宕叉繝闈涱儏绾惧吋绻涢幋鐏荤厧菐椤曗偓閺岋絾鎯旈姀鈺佹櫛闂佸摜濮甸惄顖炪€佸鎰佹▌闂佺硶鏂傞崕鎻掝嚗閸曨垰绠涙い鎺戭槹缂嶅倿姊绘担绋挎毐闁圭⒈鍋婂畷鎰版偡閹佃櫕鐎洪梺鎼炲労閸撴岸鍩涢幒鎳ㄥ綊鏁愰崶銊ユ畬濡炪倖娲樼划搴ｆ閹烘梹瀚氶柟缁樺坊閸嬫挸顫㈠畝鈧禍杈ㄧ節閻㈤潧浠﹂柛銊у枛楠炲﹪鎮欓崫鍕€炲銈嗗坊閸嬫捇宕鐐粹拺闁告劕寮堕幆鍫ユ煕婵犲嫭鏆鐐茬箰鐓ゆい蹇撴噳閹锋椽姊洪崗闂磋埅闁稿骸绉归、鏃堝春鐠虹儤鍠樻い銏＄洴閹瑩宕ｆ径搴ｅ彂闂傚倷鑳剁划顖炲蓟閵娾晛瑙﹂悗锝庡枟閸嬪倿鏌￠崶銉ョ仾闁绘挾鍠愭穱濠囶敍濠靛浂浠╅梺鎸庣☉缁夋挳鈥﹂懗顖ｆЬ闂佽桨鐒﹂幃鍌炲春閳ь剚銇勯幒宥囪窗闁哥喎绻橀弻娑㈡偐瀹曞洤鈷岄梺鐐藉劵缁犳垿鎮鹃敓鐘崇劷闁挎洍鍋撴繛鍫涘姂濮婃椽宕烽鈩冾€楅梺鍝ュУ椤ㄥ﹤鐣烽幋锕€绀嬫い鏍ㄧ〒閸欏嫰妫呴銏″闁告垵缍婂畷鐢稿礃椤旂晫鍘遍梺缁樻⒒閸忔﹢宕ｉ崟顒佸弿濠电姴瀚敮娑㈡煙瀹勭増鍤囬柟鐓庣秺閹兘骞嶉鍛还闂傚倸鍊风粈渚€骞夐垾婢勬盯骞嬮悩鍏哥瑝闂佽鍎崇壕顓㈠汲閿旂晫绡€濠电姴鍊搁弸銈嗙箾鐏忔牗娅婇柡灞诲€濋獮鏍ㄦ媴鐟欏嫰鏁┑鐐差嚟婵秹宕堕妸銏″闂備礁婀遍…鍫⑩偓娑掓櫇缁牓鍩€椤掑嫭鈷戠紒瀣皡瀹搞儳绱撳鍜冭含鐎规洖鎼埥澶愬閻樻鍚呴梻浣瑰濞插秹宕戦幘鍓佺＜闁绘瑦鐟ュú锕傛偂閻斿吋鐓欓梺顓ㄧ畱楠炴绱撳鍡楃仸闁靛洤瀚版俊鐑藉Ψ椤旂厧澹夐梻渚€娼уú銈団偓姘嵆瀵偊宕掗悙鏉戞異闂佸啿鎼崯顖氣枔婵傚憡鈷掑ù锝囩摂閸ゅ啴鏌涢悩鎰佹疁闁靛棗鍟村畷銊︽綇閸撗呪棨婵犵數濞€濞佳囶敄閹版澘鏋侀柛鎰靛枟閻撳繘鐓崶銊︾閸熸悂姊洪棃娑氬⒈闁革綇缍佸濠氭晲閸℃ê鍔呴梺闈涚箚閺呮盯寮查柆宥嗏拺闁告縿鍎辨牎闂佺儵鏅╅崹鍫曞Υ娴ｅ壊娼╅柟棰佺劍閳诲矂姊绘担铏广€婇柡鍌氬€垮畷鏇㈠箵閹烘繂娈梺鍛婃处閸撴稓绮绘ィ鍐╃厪闊洤顑呴悘鈺呮煛鐎ｎ偆娲撮柡宀嬬磿娴狅妇鎷犻幓鎺濈€烽梻渚€鈧偛鑻晶鍓х磼闊厾鐭欓柟顔矫～婵囨綇閳哄喚妲搁柣搴″帨閸嬫捇鏌涢弴銊ュ幋闁瑰嘲顭峰铏圭矙閹稿孩鎷遍梺鑽ゅ暀閸ヤ礁娲弫鍐磼濞戞艾骞嶉梺鑽ゅТ濞壯囧川椤栨粍顫岄梻鍌欑窔濞佳兾涘Δ鍛柈闁哄鍨归弳锕€鈹戦崒婊庣劸閸烆垶姊洪棃娑崇础鐎广儱娲ら懓鍧楁⒒閸屾艾鈧兘鎳楅崜浣稿灊妞ゆ牜鍋涚粈澶嬩繆閵堝懏鍣圭痪鎯ф健閺屾洟宕煎┑鎰︾紒鐐劤缂嶅﹪寮诲☉銏犵闁煎ジ顤傞崬褰掓⒑缁嬫鍎愰柟鐟版喘閹即顢氶埀顒€鐣疯ぐ鎺濇晩闁绘挸瀵掑娑㈡⒒閸屾瑨鍏岄柟铏崌瀹曨垶宕稿婵嗘喘濡啫鈽夐崡鐐搭吙闂備線娼ч…顓熶繆閸ヮ兙鈧懘鎮滈懞銉モ偓鐢告煥濠靛棝顎楀ù婊呭仱閺屾稑螣閸︻厾鐓撳┑顔硷躬缂傛岸濡甸幇鏉跨闁瑰瓨绮岄弸鍫ユ⒒娴ｅ憡鍟為柟绋款煼閹柉顦归柟顖楀亾濡炪倕绻愬ù鍌毼熼崟顖涒拺闁告繂瀚﹢浼存煟閳哄﹤鐏︾€殿噮鍋婇獮鍥级閸ф鏁归梻浣虹帛濞叉垿鎮為敃鍋瑰寰勯幇顓涙嫽婵炶揪绲介幗婊堝几閸愨斂浜滄い鎰╁焺濡偓閻庤娲栫紞濠傜暦缁嬭鏃堝焵椤掑倻涓嶉柡宥冨妿缁犻箖鏌涢埄鍏╂垹浜搁銏＄厽闁规儳顕埥澶愭婢舵劖鐓ユ繝闈涙閸ｈ淇婇懠顒傚笡闁靛洤瀚版俊鐑芥晝閳ь剟宕悜妯诲弿濠电姴鍋嗛悡鑲┾偓瑙勬礃鐢帡锝炲┑瀣垫晞闁芥ê顦竟鏇熶繆閻愬樊鍎忔繛瀵稿厴閹矂宕卞灏栧亾閹烘埈娼╅柨婵嗘噸婢规洘淇婇妶鍥ラ柛瀣仱閹囨偐濮瑰洠鍋撴笟鈧鎾閳╁啯鐝曢梻浣藉Г閿氭い锔诲灡缁旂喖骞掗幋鏃€鏂€闂佺粯锕╅崰鏍倶鏉堛劎绠惧璺侯儑濞叉挳鏌熷畷鍥ф灈妞ゃ垺绋戦…銊╁礋闂堟稐缂撻梻鍌欑窔濞佳囨偋閸℃蛋鍥ㄥ鐎涙ê浜楅梺鍝勬川閸嬫劙寮ㄦ禒瀣厽婵☆垵顕х徊缁樸亜韫囧﹥娅婇柡宀嬬磿閳ь剨缍嗛崑鍛存偂閵夆晜顥嗗璺侯儑缁♀偓婵犵數鍋為崕铏閸撗呯＝濞撴艾娲ら弸鐔兼煟閻旀潙鍔﹂柨婵堝仩缁犳盯寮撮悩纰夌床婵犵妲呴崹鐢割敋瑜旈、鎾诲箻閸撲胶锛濇繛杈剧到婢瑰﹪宕曢幇鐗堝€电紒妤佺☉濞层倗澹曠憴鍕╀簻闁哄秲鍔庨惌宀€鐥幑鎰棄闂囧鏌ㄥ┑鍡╂▓婵☆偅鍨块弻娑㈠棘鐠恒剱褔鏌″畝鈧崰鎾跺垝濞嗘挸鍨傛い鏃囧Г閻︼絾淇婇悙顏勨偓鏍洪埡鍐濞撴埃鍋撻柣娑卞櫍楠炴鎷犻懠顒夊敽闂備胶纭堕崜婵嬫晝閳轰緡鐒介柡鍐ｅ亾缂佺粯绻傞埢鎾诲垂椤旂晫浜舵俊鐐€ら崢濂告偋婵犲嫮鐭夌€广儱顦介弫鍡涙煕閺囥劌澧ù鐘层偢濮婃椽宕滈幓鎺嶇凹濠电偛寮堕敃銏ゅ春濞戙垹绠ｉ柨鏃囨娴狀垶姊虹拠鈥冲箺閻㈩垱甯楁穱濠冪附閸涘﹦鍘搁柣搴秵閸嬪懘鎮￠妷鈺傜厓閻熸瑥瀚悘瀛樸亜閵忥紕鎳囬柟顔界懇椤㈡鎷呴崷顓燁潨闂傚倸鍊烽懗鍓佹兜閸洖鐤炬繝濠傜墛閸庡孩銇勯弽顐户鐎规挷绶氶幃妤呮晲鎼粹剝鐏嶉梺绋款儐閸旀瑩寮婚敐澶婎潊闁靛繆鏅滃▓鎵磽娴ｄ粙鍝洪柣鎾偓鎰佹綎闁惧繗顫夌€氭岸鏌熺紒妯虹瑨婵絾鍔楃槐鎾诲磼濮樻瘷銏′繆椤愶絿鎳呯紒顔碱儏椤撳ジ宕ㄩ鍛澑闂佽鍑界紞鍡樼閻愮儤鏅繛鎴欏灪閳锋垿姊婚崼鐔剁繁婵＄嫏鍥ㄥ仯闁归偊鍠栭弸鎴犵磼閸屾氨孝妞ゎ厹鍔戝畷鐓庘攽閸偄鏅ｅ┑鐘垫暩閸庢垹绱為崱娑樼婵炲棙鎸婚崐鍧楁煟閹寸儐鐒界紒鐘荤畺閺屾盯顢曢妶鍛彙闂佺粯甯為弲顐ゆ閹烘挻缍囬柕濞垮劤閻熸彃鈹戦悙闈涘付缂佺粯蓱娣囧﹪骞栨担鑲濄劎鎲歌箛娑樼疇闁哄稁鍘介悡鐔煎箹鏉堝墽纾垮ù鐘崇⊕缁绘繈濮€閳藉棛鍔烽梺閫炲苯澧柛鎴濈秺瀹曘垺銈ｉ崘銊ь唹闂侀潧绻掓慨顓㈠绩娴犲鍊甸柨婵嗙凹缁ㄨ姤銇勯敂鑺ョ凡妞ゎ亜鍟存俊鍫曞幢濡櫣顣叉俊鐐€х€靛矂宕ｉ崘銊ф殾婵犻潧顑呯粻锝夋煥閺囨浜剧紓浣哄У閻楁绌辨繝鍥ч柛灞剧煯婢规洟姊绘担鍝ワ紞缂侇噮鍨堕獮鎴﹀炊瑜忛弳锔炬喐閻楀牆淇柡浣革躬閺岋綁鏁愰崨顓ф缂備緡鍠楅懝楣冨煘閹达附鏅柛鏇ㄥ亗閺夘參姊虹粙鍖℃敾闁绘娲熼崺銏ゅ籍閸喐娅囬梺绋挎湰濮樸劑宕愰悙鐑樺仭婵犲﹤鍟扮粻鑽も偓瑙勬穿缂嶄線宕洪埀顒併亜閹哄棗浜鹃梺瀹狀潐閸ㄥ潡骞冮埡鍜佹晩闁兼祴鏅欑槐姗€姊绘担铏广€婇柡鍛矒閹囨偐閼碱剚娈惧┑鐘绘涧椤戝懘宕￠搹顐犱簻妞ゆ劦鍋勯弸銈囩磼鐎ｎ亞绠绘慨濠冩そ瀹曘劍绻濋崟銊︻潔闂備礁顓介弶鍨潎闂佽鍣换婵嬪箖閵忋倖鈷愰柟閭﹀枤閻ｇ偓淇婇悙顏勨偓鏍偋濡ゅ啯宕茬€广儱顦崥瑙勭箾閸℃ê鐏╃痪鎯с偢閺屻倝鎳濋悧鍫€愰梺鍛婅壘濠€杈╂閹烘挻缍囬柕濞垮劤閻熻尙绱撴担璇℃畼闁哥姵鐗曢悾鐑藉箚闁附歇闂備胶绮换鍐崲閸岀偛鐓橀柟杈惧瘜閺佸﹦鐥鐐叉Щ濠殿喗绋撶槐鎾寸瑹閸パ勭彯闂佹悶鍔岀壕顓㈠礆閹烘鏁囬柕蹇娾偓鍏呯盎闂備胶绮幐绋棵归悜钘夌闁挎洍鍋撴い顏勫暣婵″爼宕卞Δ鈧鎴︽⒑缁嬫鍎愰柟绋款煼婵＄敻宕熼锝嗘櫍闂佺粯鍔曢顓㈠煘濞戞﹩娓婚柕鍫濈箳閻ｈ京绱撳鍜冭含鐎殿喛顕ч鍏煎緞婵烆潿鍔嶉妵鍕冀閵娧呯厒闂佸搫妫庨崐婵嬪箖濡ゅ啯鍠嗛柛鏇ㄥ墰椤︺儵姊洪棃娑氬闁规祴鍓濈粚杈ㄧ節閸ャ劌鈧鏌﹀Ο鐚寸礆闁靛ě鍕瀾濠电娀娼ч悧濠囷綖閺囥垺鐓熼柕蹇曞У閸熺偤鏌嶉柨瀣诞闁哄本绋撴禒锕傚礈瑜庨崳顕€鏌ｉ悩鍐插闁告濞婂璇测槈閵忕姷顔掗柣搴ㄦ涧閹诧繝宕抽鍓х＝濞撴艾娲ら弸鐔兼煟閻斿弶娅呴柣锝囧厴閹垻鍠婃潏銊︽珝闂備胶绮摫妞ゆ梹鐗曞嵄闁归棿鐒﹂埛鎺懨归敐鍛暈闁诡垰鐗忕槐鎺撳緞鐎ｎ剙寮ㄩ悗瑙勬礃缁诲啰鎹㈠┑瀣妞ゅ繐绉甸柨銈嗙節閻㈤潧孝闁挎洏鍊濋幃褑绠涘☉妯肩厬闂佺懓澧界划顖炴偂閺囥垺鐓忓鑸电〒閸掓澘鈹戦垾鑼煓闁诡喗顭堢粻娑樷槈濞嗘劙鏁俊鐐€ら崣鈧柛搴☆煼钘濋柟鎯板Г閻撶喖鏌嶉崫鍕跺伐闁诲骏濡囬埀顒侇問閸犳盯顢氳椤㈡﹢宕楅悡搴ｇ獮闁诲函缍嗛崜娆忣嚕閹惰姤鈷戦悹鍥ㄥ絻閸よ京绱撳鍛棡缂佸倸绉归幃娆撳级閹搭厼娈奸柣搴″帨閸嬫捇鏌涢弴銊ュ幋闁归攱妞介弻锝夋偐閸欏顦遍梺閫炲苯澧叉繛鍛礋閹﹢宕奸姀銏紳闂佺鏈銊ョ摥闂備焦瀵уú蹇涘磹濠靛绠栧Δ锝呭暞閸婂鏌﹀Ο渚█闁哥偛鐖煎娲传閸曨剙绐涢梺绋款煭缂嶄線宕洪敓鐘插窛妞ゆ梹鍎崇敮鎯р攽閻橆喖鐏辨い鏇熺墵瀹曡瀵奸弶鎴犵枃閻庡厜鍋撻柛鏇ㄥ亞閿涙盯姊虹捄銊ユ灁濠殿喚鏁婚崺娑㈠箳濡や胶鍘遍柣蹇曞仜婢т粙骞婇崟顑句簻闁挎洖鍋嗗Λ鎴犵磼鏉堛劌绗氭い顐ｅ灴瀵€燁槼闁诲繑鎹囧娲偡閺夋寧些缂傚倸绉撮敃顏堢嵁閸愵喖顫呴柕鍫濇娴滄鏌熼懝鐗堝涧缂傚秴妫楀嵄闁绘垼濮ら埛鎺懨归敐鍕劅闁衡偓娴煎瓨鐓欐繛鑼额唺缁ㄧ晫绱掗纰辩吋妤犵偛顑夐弫鍌炴寠婢跺苯绲介梻鍌欒兌缁垶寮婚妸鈺佺婵犲﹤鎳愭稉宥呂旈敐鍛殲闁绘挻鐟╅幃妤€鈽夊▎妯煎姺闂佺懓鍟块崯鎾蓟閿濆牏鐤€闁挎繂瀚伴崑妤呮⒑閸濆嫮鐒跨紒缁樼箓閻ｇ兘濡搁埡濠冩櫓闂佽鍨庨崘顏勬憢闂傚倸鍊烽懗鍓佸垝椤栫偛绠归柍鍝勬噹绾捐鈹戦悩鎻掆偓濠氬汲閿曞倹鐓曟い鎰剁稻缁€鈧柟顖滃枛濮婃椽骞愭惔鈶╂嫽闂佹儳绻愭绋垮祫闂佸壊鍋呭ú姗€鎮￠悢鍏肩厽闁归偊鍓﹂崵鐔兼煕閻旇鐨虹紒杈ㄥ浮閹晠鎼归銏紦闁诲孩顔栭崰鏍€﹂悜钘夋瀬闁圭増婢橀獮銏′繆椤栨碍鎯堝┑鈩冨▕濮婄粯绗熼埀顒€顭囪閹广垽宕卞☉妯兼煣濠电偞鍨崹褰掓偪妤ｅ啯鈷戞い鎺嗗亾缂佸鏁婚崺娑㈠箣閿旂晫鍘卞┑鐘绘涧鐎氼剟宕濆璺哄唨闁跨喓濮甸埛鎴犵磽娴ｇ櫢渚涙繛鍫熋埞鎴︽倷閳轰椒澹曢梻鍌欑劍閹爼宕濋幋鐘典笉闁哄稁鍘奸弸渚€鏌涢幇闈涙灈缁炬儳鍚嬬换娑㈠箣閻愬啯宀稿畷鎴﹀箻鐎靛摜鐦堝┑顔斤供閸橀箖宕ｉ崘銊㈡斀闁宠棄妫楅悘鐔兼偣閳ь剟鏁冮埀顒€宓勯悷婊呭鐢鎮￠悢鐓庣闁圭⒈鍘奸弸娑橆熆瑜嶉悺銊╁箞閵婏妇绡€闁告劏鏂傛禒銏ゆ⒑鐠団€虫灓闁稿繑锚椤曪綁鎳滈崹顐㈠妳闂佸湱鍋撳娆撴儊鏉堚晝纾介柛灞捐壘閳ь剟顥撳▎銏ゆ晸閻樻煡妫峰銈嗘磵閸嬫挾鈧娲栧﹢閬嶅箲閸曨垱鎯為悷娆忓椤旀洟姊绘担渚敯闁规椿浜炵划濠氬箣閻樺吀绗夊┑鐐村灟閸ㄦ椽鎮￠悢闀愮箚闁靛牆鍊告禍楣冩⒑缂佹﹩娈旂紒缁樏悾鐑藉閵堝懐顔愭繛杈剧到濠€閬嶅储娴犲顥婃い鎰╁灪婢跺嫭绻涢崣澶屽缂佸倹甯￠崺锟犲磼濡湱鐩庨梻浣告惈濞层倕螞濡も偓鐓ら柕鍫濇川閻棗顭跨捄渚剱闁告瑥绻戞穱濠囶敍濮樺彉铏庨梺缁樻尰閸旀瑩寮婚敐澶婄閻庣數纭跺Σ鍫ユ⒒閸パ屾█闁哄被鍔岄埞鎴﹀幢濡櫣鐛╅梻浣侯攰濡嫰宕愰崷顓熷床婵炴垯鍨圭粈鍌炴煟閹炬娊顎楁い顐㈢焸閺屻倝骞愭惔鈥愁潚闂佸搫鏈惄顖涗繆閻戣棄绠ｆ繝闈涚唵閵夆晜鐓曟慨妯煎帶娴滅増鎱ㄦ繝鍛仩闁归濞€閸ㄩ箖鎼归銈勬喚闂傚倷娴囧畷鐢稿疮閸ф鐤鹃柣妯款嚙杩濇繛鎾村焹閸嬫挾鈧鍣崳锝呯暦閻撳簶鏀介柛顐亝鏁堥梻鍌氬€风欢姘焽瑜旈垾锕傚醇閵夈儳锛熼梺缁橆殔閻楀繘鎮甸崼鏇熺厸闁搞儯鍎遍悘顏堟煃闁垮鐏撮柡灞剧☉閳规垿宕卞Δ濠佺磻闂備礁鐤囬褔藝閻㈢钃熸繛鎴烆焸閻旂厧鍨傛い鏃傜帛閻繒绱撻崒娆戣窗闁哥姵鐗犻幃褎绻濋崒銈呮闂佸憡娲﹂崹鎵不濞戞瑣浜滈柟鎹愭硾瀛濆Δ鐘靛仜缁夌懓顫忓ú顏勪紶闁告洖鐏氱瑧闂備胶绮〃鍛涘▎鎾崇柧闁煎摜鏁搁々鐑芥倵閿濆骸浜滃ù鐘层偢濮婄粯绗熼崶褍顫╃紓浣割槺閺佸骞冮敓鐘插嵆闁靛骏绱曢崢顏呯節閻㈤潧浠滈柣蹇旂箞瀹曟繂顫濋懜鐢靛帗閻熸粍绮撳畷婊冣枎閹绢垪鍋撻敃鍌涘€婚柣锝呰嫰缁侊附绻濋姀锝呯厫闁告梹娲滅划濠氭偡閹冲﹤缍婇弫鎰板炊閵娿儲鐣┑鐐茬摠缁挻绂嶇捄渚綎婵炲樊浜滅粈鍫ユ煙缂佹ê绗傜紒銊ㄥ亹缁辨挻鎷呯粵瀣闁诲孩鐭崡鎶界嵁閸愵喗鍊婚柦妯猴級閳哄懏鐓冮柛婵嗗閳ь剛鏁婚獮蹇涙惞閸︻厾锛濋梺绋挎湰閻熝囧礉瀹ュ瀚呴梺顒€绉甸崐鍫曠叓閸ャ劍绀€妞わ綀鍋愮槐鎺旂磼濡吋鍒涢梺鐟扮－婵炩偓妞ゃ垺顨婇崺鈧い鎺戝閸戠姵绻涢幋鐐茬劰闁稿鎸搁埢鎾诲垂椤旂晫浜俊鐐€ら崢楣冨礂濡警鍤曟い鎰剁畱缁犺崵绱撴担濮戭亝绂嶈ぐ鎺撯拺闁兼祴鏂侀幏锟犳煕閹惧鎳囬柛鈹惧亾濡炪倖甯掗崐鍛婄鏉堛劍鍙忓┑鐘叉噺椤忕娀鏌嶈閸撴瑥锕㈡潏銊﹀弿闁汇垻顭堣繚闂佸憡鍔︽禍鐐靛婵傚憡鐓欑紓浣姑粭姘舵煕鐎ｃ劌鍔氶柍瑙勫灴閺佸秹宕熼鍌氬Ы婵＄偑鍊х粻鎴︽偤閵婏妇鈹?
		int firstDivIndex = input.indexOf("<div");
		String prefix = input.substring(0, firstDivIndex).trim();
		String rest = input.substring(firstDivIndex);

		// 闂?<div> 闂?</div> 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢弻銉﹀殞濡わ絽鍟悡姗€鏌熺€电浠滅紒鐘靛█濮婅櫣绮欓崠鈩冩暰濡炪們鍔屽Λ婵嬬嵁閸儱惟闁冲搫鍊搁埀顒€顭烽弻锕€螣娓氼垱楔闂佹寧绋掔粙鎴﹀煘閹达附鍊烽柡澶嬪灩娴滃爼姊洪悷鎵紞闁稿鍊曢悾鐑藉醇閺囥劍鏅㈡繛杈剧秮閺呰尙绱撻幘鍓佺＝闁稿本鐟чˇ锔姐亜閹存繃顥犻柍褜鍓涢悷鎶藉炊閵娿儮鍋撻崹顐犱簻闁圭儤鍨甸顏堟煕婵犲倻浠涙い銊ｅ劦閹瑩鎳犻鑳闂備礁鎲″鍦枈瀹ュ桅闁告洦鍨遍弲婊堟偣閸ヮ亜鐨哄ù鐙€鍨崇槐鎾寸瑹閸パ勭亪闂佺粯顨呯换姗€宕洪埀顒併亜閹烘埊鍔熺紒澶屾暬閺屾稓鈧絺鏅濋崝宥囩磼閸屾氨孝妞ゎ厹鍔戝畷濂告偄閸濆嫬绠ラ梻鍌欑閹诧紕鎹㈤崒婧惧亾濮樼厧鏋﹂柛濠冩尦濮婄粯鎷呴崨濠傛殘闂佸搫琚崝搴ｅ垝閺冨牊鍋ㄧ紒瀣嚦閿曞倹鐓曢柡鍥ュ妼閻忕姵淇婇锝忚€块柡宀€鍠撶划娆撳锤濡ゅň鍋撳Δ鍛厸閻庯綆鍓欏瓭闂佸疇顫夐崹鍧椼€佸▎鎴炲厹鐎瑰嫭婢橀～鐘电磽閸屾瑧璐伴柛锝庡櫍瀹曞綊宕奸弴鐘茬ウ闂婎偄娲︾粙鎴濐啅濠靛鐓涘璺侯儏閻忋儳绱掗幇顓犫姇缂佺粯绻堥幃浠嬫濞磋翰鍨介弻鐔兼惞椤愩垹顫掗梺闈涙缁舵艾鐣烽妸褉鍋撳☉娅辨岸骞忕紒妯肩閺夊牆澧界€靛ジ鎮归埀顒勬晝閸屾稑鈧潡鏌涢幘妤€鎳愰敍婊堟⒑闂堟侗鐓梻鍕婵￠潧鈹戦崼姘壕閻熸瑥瀚粈鍐煕閺冣偓濞茬喖宕洪悙鍝勭闁挎洍鍋撴鐐灪缁绘盯宕卞Ο鍝勵潔濡炪倖姊瑰ú鐔奉潖缂佹ɑ濯撮柛锔诲幗閼垮洭姊洪幖鐐插缂傚秴锕悰顔藉緞閹邦厽娅㈤梺缁橆焾鐏忔瑩寮昏閳规垿鎮╃紒妯婚敪濡炪倖鍨靛Λ婵嬪箖閿熺姴绀冩い蹇撴噹閺嬫垵鈹戦悩璇у伐闁绘妫涚划鍫ュ醇閵夛妇鍘介梺鍝勫暙閸婂摜鏁崼鏇熺厸闁告洍鏅涢崝锕傛煛鐏炵偓绀冪€垫澘瀚埥澶婎潩鏉堛劍顔忕紓鍌氬€风粈浣规櫠鎼淬劌纾绘繛鎴欏灩閻撴﹢鏌熸潏楣冩闁稿﹪鏀遍妵鍕疀閹炬剚浼€婵炲鍘х€氭澘顫忓ú顏勪紶闁靛鍎涢敐澶嬬厽婵°倕鍟瓭濡炪倖娲╃徊鍓ф崲濠靛棭娼╂い鎴旀杹閺呮繈骞夐崨濠傜窞闁归偊鍓涢敍娑㈡⒑閻熸澘鈷旂紒顕呭灦瀵煡寮婚妷銉ь啇缂備緡鍠栭崢婊堝磻閹捐绀冮柤纰卞墻閸氬懘姊婚崒姘偓鐑芥嚄閸洍鈧箓宕奸妷顔芥櫈闂佺硶鍓濈粙鎴犵不娴煎瓨鐓犲┑顔藉姇閳ь剚鐗曢悾鐑藉矗婢跺瞼鐦堥梻鍌氱墛缁嬫帡鏁嶅鍡欑閻忓繑鐗楀▍鍡涙煏閸パ冾伃妤犵偞顭囬埀顒傛暩绾埖鎱ㄩ崶顒佺厽閹兼番鍨兼竟姗€鏌曢崼銏╃劸妞ゎ偄绻掔槐鎺懳熺拠宸偓鎾绘⒑閼恒儍顏埶囬鐐茬畾闁绘劗鍎ら埛鎴︽煕濠靛嫬鍔氶弽锟犳⒑閸涘﹥鈷愰柣妤佺矌閸掓帡鍩￠崨顔间簻闂佺粯鎸告鎼佹嚀閸喒鏀介柍钘夋閻忥綁鏌嶅畡鎵闂囧鏌涢弴銊ョ仭闁抽攱鍨垮濠氬醇閻旀亽鈧帞绱掗悩闈涗槐闁哄本绋掔换婵嬪礃椤忓嫧鎷伴梻浣筋嚃閸ｎ垳鎹㈠┑鍡╁殨闁圭虎鍠栭～鍛存煥濞戞ê顏╂鐐搭殜濮婄粯鎷呴崨濠冨創濠电偠顕滅粻鎴︼綖濠靛惟闁冲鍐╁枠闁轰礁鍟村畷鎺戔槈濮橆剙绠洪梻鍌氼煬閸嬪嫬煤閵忋倕纾块梺顒€绉查埀顒€鎳橀、妤呭礋椤掑倸甯楅柣鐔哥矋缁挸鐣峰鍐炬僵濡増绻冮弲鈺呮⒑閸濆嫮鈻夐柛妯垮亹缁絽螖娴ｉ绠氶梺闈涚墕閹冲繘宕冲ú顏呯厱閻庯綆浜滈埀顒€娼″濠氬Ω閳哄倸浜滈梺鍛婄箓鐎氬懘濮€閵堝棛鍘搁梺鍛婃磵閺呮盯宕濋敃鍌涚厸閻忕偠顕ф慨鍌炴煙椤斿搫鐏茬€规洦鍋婂畷鐔煎箣濞嗗繒浼勯梺閫炲苯澧伴柟铏崌楠炲鏁嶉崟顒€搴婇梺鍛婂姦閸犳牗顢婇梻浣告啞濞诧箓宕归弶璇炬稑顫滈埀顒勫箖瑜版帒绠掗柟鐑樺灥椤姊虹拫搴″€荤粣鏃€鎱ㄦ繝鍛仩缂侇喗鐟ラ埢搴ㄥ箚瑜嶆竟澶愭⒒娴ｅ憡璐￠柟铏尭鐓ゆい鎾卞灮瀹撲線鏌涢幇銊︽珖妞も晝鍏橀幃妤呮晲鎼存繄鐩庣紓浣瑰姈缁嬫帞鎹㈠┑瀣仺闂傚牊鍒€閵忋倖鍊垫慨妯煎帶婢ф挳鏌ｅ☉鍗炴珝鐎规洖銈告俊鐑芥晜鐟欏嫬顏烘繝鐢靛Х閺佸憡鎱ㄧ€涙﹩娈介柛娑橈功椤╁弶绻濇繝鍌氼伀缂佺娀绠栭幃褰掑炊閸パ勫櫧闁哄棎鍊曡灃闁绘﹢娼ф禒锕傛煟濡ゅ啫鈻堢€规洘妞介崺鍕礃閳轰緡鈧捇姊洪崨濠冪濠⒀冮叄楠炲啴鎳栭埡鍐紲濡炪倖姊婚弲顐﹀箠閸愵喗鎳氶柣鎰劋閻撶喖鏌曡箛鏇炐ュù鐘崇洴閺屾盯濡堕崒姘婵犵绱曢崑鎴﹀磹閵堝鍌ㄧ憸鐗堝笒缁€澶愭煙鐎涙绠橀柡鍡畱閳规垿宕掑搴ｅ姼濡炪値鍋勯幊姗€寮婚弴锛勭杸濠电姴鍊搁埛澶愭⒑娴兼瑩妾悽顖涘浮閸╃偤骞嬮敂钘変汗闂佸綊顣︾粈渚€寮查柆宥嗏拺闁告縿鍎辨牎闂佺粯顨堟繛鈧€殿噮鍋婂畷姗€顢欓崲澶堝姂閺屾洘寰勫Ο鐑樼亶闁诲酣娼ч惌鍌氼潖濞差亝鍤嶉柕澶婂枤娴滎亣妫熼梻渚囧墮缁夋潙效閺屻儲鐓ラ柡鍥╁仜閳ь剙缍婇幃陇绠涢幘顖涙杸闂佺粯顭堢亸娆戠不閻楀牄浜滈柍鍝勶工婢ф壆绱掓潏銊ユ诞闁糕晪绠撻崺鈧い鎺戝绾惧鏌熼崜褏甯涢柍閿嬪笒闇夐柨婵嗘噺閸熺偤鏌涢悢鍝勪槐闁哄本鐩顕€骞橀崜浣规闂備浇顕栭崳顖滄崲濠靛棛鏆﹂柣鎾冲瘨閻斿棙銇勮箛鎾存崳缂佸鍣ｉ弻锛勪沪閼恒儺妫炲銈嗘尭閵堢鐣锋潏顐ょ杸闁哄倹顑欐导婊冣攽閻樺灚鏆╅柛瀣█楠炴捇顢旈崱妤冪瓘闂佸吋绁撮弲婊勫垔娴煎瓨鐓ラ柡鍥╁仜閳ь剙缍婂畷鎰板锤濡や胶鍘介梺閫涘嵆濞佳囧储闂堟耽鐟邦煥閸曨厾鐓夐梺鍝勭灱閸犳捇鍩€椤掍胶鈯曟い顓炴川缁寮介鐔哄幈闂佹寧绻傞幊鎰矓濞差亝鐓欓柦妯侯槺閸╋綁鏌熼鐣屾噮闁逞屽墴濞佳囧箺濠婂牊鍎婇柛顐犲劜閳锋帡鏌涚仦鐐殤濠⒀勭洴閺屾盯骞囬澶屽悑閻庢鍠撻崝搴ｆ閹烘嚦鐔兼⒐閹邦喚娉块梻鍌欑窔濞佳嚶ㄩ埀顒傜磼闊厾鐭欓柛鈹惧亾濡炪倖甯婇懗鑸垫櫠椤掑倻纾肩€光偓閸愵喖鎽电紓浣虹帛缁诲牆鐣烽崼鏇炍╅柕澶堝灩娴滈箖姊洪崹顕呭剭濞存粍绮撻悡顐﹀炊閵婏箑纰嶅銈呯箞閸婃繈寮诲☉姘ｅ亾閿濆骸浜濈€规洖鐬奸埀顒侇問閸犳洜鍒掑▎鎾扁偓浣割潨閳ь剟骞冨▎蹇婃灁闁圭宸╅妷鈺傗拺闁煎鍊曢弸鎴犵磼椤旇偐效鐎殿喖顭锋俊鎼佸煛鐎ｎ剛鈼ら梻濠庡亜濞诧箑顫忔繝姘モ偓鍛存倻閼恒儱鈧敻鏌ㄥ┑鍡欏嚬缂併劏濮ら妵鍕晜閼恒儳鍑″銈庝簻閸熷瓨淇婇崼鏇炲耿婵炲棗娴氬閿嬬節濞堝灝鏋熼懣褔鏌涢弮鈧崹鐢告偩閻戣棄顫呴柕鍫濇閸斿爼鎮楅獮鍨姎婵☆偄鐭傞獮蹇涙惞閸︻厾锛滅紓鍌欓檷閸ㄥ綊鐛弽顓熺厵闁告劘灏欑粻娲煏閸ャ劌濮屾い锕侇潐閹便劍绻濋崒銈囧悑閻庤娲滈崰鏍€侀弴銏狀潊闁宠　鍋撶紒杈ㄦ濮婄粯鎷呯粵瀣異闂佸摜濮电敮锟犲春閳ь剚銇勯幒宥囧妽闁诲繘浜堕弻锛勨偓锝庝邯閸欏嫰鏌″畝鈧崰鏍嵁閸℃凹妾ㄩ梺鎼炲€楅崰鏍蓟濞戞鐔烘嫚閹绘帒袘闂備礁鐤囬～澶愬垂閸ф绠栨繛鍡樺灍閸嬫挸鈽夊▎妯煎姺缂備礁顑呭ú銈夆€旈崘顔嘉ч柛鈩冾殘娴犳潙鈹戦埥鍡椾簼妞ゃ劌锕畷娲閻欌偓閸氬顭跨捄鐚村伐缂佹劖绋戦—鍐Χ閸℃顑傞梺鍛婎焼閸涱垳顦繝鐢靛Т濞层倗绮婚弽顓熺厓閺夌偞濯介崗宀勬煛閸☆厾鐣甸柡灞剧洴婵＄兘濮€閳╁啰褰囬梻浣告啞钃辩紒顔芥尭椤繒绱掑Ο璇差€撻梺鍛婄☉閿曘儵宕曢幘缁樷拺闁告繂瀚晶閬嶆煕閹捐泛鏋涙鐐叉瀹曠喖顢涢敐鍡樻珫婵犳鍠楅…鍥储瑜嶉埢鎾诲Ω閵夘喗瀵岄梺闈涚墕濡绮幒妤佸€垫慨妯煎帶婢ь垶鎮楅棃娑栧仮闁诡喒鏅濈槐鎺懳熼悡搴＄疄闂傚倷绀侀幖顐⒚洪姀銈呯閻庯綆鍓濇慨宕囩磽娴ｉ婊勭濠婂牊鐓忓鑸电〒閻ｉ亶鏌涘┑鍫濆妺闁逛究鍔嶇换婵嬪磼閵堝洤鎮戦柣搴ゎ潐濞叉鏁幒妤嬬稏婵犻潧顑愰弫鍕煢濡警妲峰瑙勬礋濮婇缚銇愰幒鎿勭吹缂備讲鍋撳ù锝呮惈椤ユ岸鏌ｉ悢鐓庝喊婵炴挸顭烽幃妤呮晲鎼存繄鍑归梺鍦缂嶄線寮婚敐澶樻晣闁绘劖鎯屽Λ鐐烘⒑闂堟稒顥為悽顖涘浮閿濈偛鈹戠€ｎ偅娅滈梺鍛婁緱閸撴瑩藟濮橆兘鏀介幒鎶藉磹濡や焦鍙忛柣鎴ｆ绾剧粯绻涢幋鏃€鍤嶉柛銉墯閸嬨劎绱掔€ｎ偄顕滈柣搴墴濮婃椽鎮烽弶搴撴寖缂備緡鍣崹璺虹暦閹达箑绠涢柣妤€鐗忛崢鍓х磼閻愵剚绶茬€规洦鍓氶弲鍓佺磼濡湱绠氶梺鍝勮閸庢煡寮潏鈺冪＜缂備焦顭囩粻鎾淬亜閺囨ê鍔﹂柡灞诲妿閳ь剨缍嗛崑鍡涘礄閿熺姵鈷戦梺顐ゅ仜閼活垱鏅堕娑氱闁肩⒈鍓欓弸娑㈡煟濞戝崬鏋熺紒缁樼箞瀹曟﹢鍩℃担绋课ら梻鍌欑劍鐎笛呮崲閸岀偛绠悗锝庘偓顓熺☉椤劑宕熼鐘垫闂備焦鐪归崹钘夘焽瑜庨悧搴ｇ磽娴ｅ搫浜炬繝銏☆焽缁棁銇愰幒鎴犵暫闂佺偨鍎查弸濂稿几鎼淬劍鐓欓柟顖嗗苯娈堕梺鍛婏耿濞佳団€旈崘顔嘉ч柛鈩冪懃閳懓鈹戦埥鍡椾簼妞ゃ劌锕ら悾鐑筋敍閻戝棙鏅梺缁樺灥濡瑩寮?
		List<String> divContents = new ArrayList<>();
		int cursor = 0;
		while (true) {
			int start = rest.indexOf("<div>", cursor);
			int end = rest.indexOf("</div>", start);
			if (start == -1 || end == -1) break;

			String content = rest.substring(start + 5, end).trim();
			divContents.add(content);
			cursor = end + 6;
		}

		// 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢弻銉﹀殞濡わ絽鍟悡姗€鏌熺€电浠滅紒鐘靛█濮婅櫣绮欓崠鈩冩暰濡炪們鍔屽Λ婵嬬嵁閸儱惟闁冲搫鍊搁埀顒€顭烽弻锕€螣娓氼垱楔闂佹寧绋掔粙鎴﹀煘閹达附鍊烽柡澶嬪灩娴滃爼姊洪悷鎵紞闁稿鍊曢悾鐑藉醇閺囥劍鏅㈡繛杈剧秮閺呰尙绱撻幘鍓佺＝闁稿本鐟чˇ锔姐亜閹存繃顥犻柍褜鍓涢悷鎶藉炊閵娿儮鍋撻崹顐犱簻闁圭儤鍨甸顏堟煕婵犲倻浠涙い銊ｅ劦閹瑩鎳犻鑳闂備礁鎲″鍦枈瀹ュ桅闁告洦鍨遍弲婊堟偣閸ヮ亜鐨哄ù鐙€鍨崇槐鎾寸瑹閸パ勭亪闂佺粯顨呯换姗€宕洪埀顒併亜閹烘埊鍔熺紒澶屾暬閺屾稓鈧絺鏅濋崝宥囩磼閸屾氨孝妞ゎ厹鍔戝畷濂告偄閸濆嫬绠ラ梻鍌欑閹诧紕鎹㈤崒婧惧亾濮樼厧鏋﹂柛濠冩尦濮婄粯鎷呴崨濠傛殘闂佸搫琚崝搴ｅ垝閺冨牊鍋ㄧ紒瀣嚦閿曞倹鐓曢柡鍥ュ妼閻忕姵淇婇锝忚€块柡宀€鍠撶划娆撳锤濡ゅň鍋撳Δ鍛厸閻庯綆鍓欏瓭闂佸疇顫夐崹鍧椼€佸▎鎴炲厹鐎瑰嫭婢橀～鐘电磽閸屾瑧璐伴柛锝庡櫍瀹曞綊宕奸弴鐘茬ウ闂婎偄娲︾粙鎴濐啅濠靛洢浜滈柡宥冨妿閳藉鏌＄€ｎ亜鈧灝顫忓ú顏勫窛濠电姴鍟ˇ鈺呮⒑缁嬫鍎忛柛濠傜仢閻ｇ兘顢曢埗鑺ユ瀹曘劑顢欑憴鍕伜婵犵數鍋犻幓顏嗗緤閼测晜濯伴柨鏇炲€归崐鍨旈敐鍛殲闁稿﹤鐏氱换娑㈠箣閻愯尙鐟插┑鐐叉噹閹冲繘濡甸崟顖ｆ晜闁告洦鍋呭▓濠氭⒑閸︻収鐒鹃柨鏇樺劚椤曘儵宕熼婵囧媰闂佺粯鍔﹂崜锕傚疮鎼粹檧鏀介柣妯虹仛閺嗏晛鈹戦鑺ュ唉闁轰礁鍟存俊鑸靛緞婵犲嫭鐒鹃梻浣虹帛椤牓顢氳瀹曟瑩鎮╃拠鑼啇濠电儑缍嗛崜娆愪繆娴犲鐓熼柟鎯ь嚟閹冲洭鏌熼绛嬫當闁宠棄顦埢搴∥熼悡搴⌒ㄧ紓鍌氬€烽懗鑸垫叏娴兼潙纾块柛娆忣槺閳瑰秴鈹戦悩鍙夌ォ闁轰礁鍟撮弻銊モ攽閸℃ê绐涢梺缁樻尭椤﹂潧顫忛搹瑙勫珰闁炽儴娅曢悘鍫ユ⒑閹肩偛鐏柣鎿勭節閸ㄩ箖鏁冮崒姣尖晠鏌嶆潪鎵槮缂佹劖绋戦—鍐Χ閸℃ê鐦烽梺鍛婃处閸撴稑危濮椻偓濮婂宕掑▎鎴濆闂佹椿浜滅紞濠傜暦濞差亜绀嬫い鏍ㄨ壘閻庮參姊绘笟鍥у缂佸鐓￠崺鈧い鎺嗗亾闁哥噥鍨抽幑銏犫攽鐎ｎ亞鍊為悷婊冪箻閹儳煤椤忓應鎷洪梺缁樺姌濡嫰宕濆鍫熺厸閻庯綆鍓欑敮鍫曞础闁秵鐓曟い鎰╁€曢弸鎴︽煕濞嗗繒绠查柟渚垮妼铻栭柍褜鍓熼幃褑绠涘☉妯碱槴闂佸搫顦伴敍鏇㈡偡闁妇鍙嗛梺鍛婁緱閸樼儤绔熼弴鐑嗘富闁靛牆鍟俊鎼佹煕鎼达絾鏆鐐插暙閳诲酣骞橀弶鎴烆吇婵＄偑鍊栭崹濂稿磿鐠囧樊鐎堕柟闂寸劍閳锋帒霉閿濆懏鍟為柛鐔哄仦閹便劍绻濋崒婊呅ㄩ梺纭呮珪缁诲牓鐛幋锕€绠涙い鏃囧吹閼虫椽姊婚崒姘偓鐑芥嚄閸撲礁鍨濇い鏍亹閳ь剨绠撳畷鐓庘攽閸愨晝鈧椽姊洪崫鍕偍闁搞劌婀辨竟鏇°亹閹烘挾鍘甸梺瑙勵問閸犳牠銆傛總鍛婄厽闁规崘娉涢弸鎴犵磼缂佹娲存鐐差儏閳规垿宕卞顒傚幋濠电姷鏁搁崑娑㈠箠鎼达絿鐭撻柛顐ｆ礀閻掑灚銇勯幒宥囪窗闁哥喎绻橀弻娑㈡偐閾忣偆顦梺鍛婂笚鐢帡鍩㈡惔銊ョ闁瑰吀绀佹禍鎯归敐鍛棌婵炵鍔戦弻宥堫檨闁告挾鍠栭悰顕€宕橀纰辨綂闂侀潧鐗嗛幊宥囨閸洘鈷戦柛娑橈攻婢跺嫰鏌涚€ｎ亝顥㈤柟顖氱墦瀹曠喖顢涘☉鎺撳闂備胶顭堥張顒勬嚌妤ｅ啫鐒垫い鎺戝濡垹绱掗鑲╁缂佹鍠栭崺鈧い鎺戝瀹撲礁顭块懜闈涘闁哄懏鎮傞弻锝呂熼崹顔炬濡炪倧瀵岄崳锝咁潖濞差亜绠伴幖绮规噰閹峰姊洪崫鍕闁稿锕俊鎾川閺夋垹鐓戞繝銏ｆ硾閻ジ鎯侀崼鐔虹瘈闁汇垽娼у瓭闂佺锕︾划顖炲疾閸洖鍗抽柕蹇ョ磿閸橀亶姊洪悷閭﹀殶濠殿喖鐡ㄩ悧搴ｇ磽閸屾瑨鍏岀紒顕呭灦閹嫰顢涢悙闈涚ウ闁诲函缍嗘禍鏍几鎼淬劎鍙撻柛銉ｅ妽绾惧鏌ｉ妷顔婚偗婵﹦绮幏鍛存惞閻熸壆顐奸梻浣告贡椤牓鈥﹂悜钘夌畾闁告洦鍨奸弫宥夋煟閹邦垰鐨洪柣锝呮惈閳规垿顢欐慨鎰捕闂佺顑嗛幑鍥蓟閻旂⒈鏁婇柣锝呮湰閸ｆ澘螖閻橀潧浠﹂柟鐟版喘閻涱噣骞掑Δ鈧獮銏′繆椤栨繃顏犳俊鑼劋缁绘繈鎮介棃娴躲儵鏌℃担瑙勫€愮€规洘鍨甸埥澶婎潨閸℃顓块梺鑽ゅЬ濡椼劎鎷冮敃鍌涘€块柤鎭掑劤缁犻箖鏌涢埄鍏╂垹浜搁銏＄厽闁规崘鎻懓鍧楁煛瀹€鈧崰鎰焽韫囨柣鍋呴柛鎰ㄦ櫓閳ь剙绉瑰娲传閸曨剙顎涢梺鍛婃尵閸犳牕顕ｇ拠娴嬫闁靛繒濮村畵鍡涙⒑闂堟侗鐒鹃柛搴㈠閼鸿京鎷犻崣鍌涙閹晠妫冨☉妤冩崟婵＄偑鍊栭崹鐢稿箠濮椻偓閸ㄩ箖寮崼婵堫槰濡炪倖鏌ㄥΣ鍫ｎ樄闁哄本鐩獮鍥煛娴ｅ壊鐎峰┑鐐差嚟婵炩偓缂佺姵鐗曢～蹇撁洪鍛闂侀潧鐗嗛幊蹇撔掗幇鐗堚拺闁告繂瀚崳钘夆攽閻愨晛浜鹃梻浣虹帛娓氭宕抽敐鍛殾婵せ鍋撴い銏＄懇閹虫牠鍩℃担鎰熴倝姊婚崒娆戭槮闁硅绻濆畷婵婄疀濞戞鏌у┑鐐村灟閸ㄥ湱绮堟径鎰厱婵炴垵宕鐐箾閹炬剚鐓奸柡灞炬礋瀹曠厧鈹戦崶鑸碉紒闂備焦濞婇弨閬嶅礉濞嗘挸钃熸繛鎴欏灩鎯熼梺鎸庢煥婢т粙鎮鹃棃娑辨富闁靛牆楠告禍婵堢磼鐠囪尙澧︾€殿噮鍋婂畷姗€顢欓懖鈺嬬床婵犵數鍋為崹鍓佹暜濡ゅ啠鍋撳顐ょ煓婵﹦绮幏鍛瑹椤栨粌濮奸梻浣告惈閻楁粓宕滈悢鐓庣畺闁绘垼濮ら弲婊堟煕閹炬鎳愰崝鎼佹⒒娴ｈ櫣甯涙い銊ユ嚇閹囧礋椤掑偆娲稿┑鐘诧工閻楀﹪鎮″☉銏＄厱闁靛鍨电€氬嘲螞閻愮儤鈷戦柣鐔告緲閺嗚鲸銇勯敃鍌涙锭闁伙絿鍏橀獮瀣晝閳ь剛绮绘繝姘厸闁稿本锚閸旀粍銇勯銏⑿ф慨濠冩そ瀹曠兘顢橀悙鎻掝瀱闂備礁婀遍…鍫モ€﹂悜钘夌畺闂傚牊渚楀鈺呮煠閸濄儺鏆柟鐤缁辨帞绱掗姀鐘茬闂佺懓鍟跨换姗€骞冮悽鍓叉晜闁糕剝鐟ч敍婊堟煟閻樺弶澶勭憸鏉垮暣瀵娊鏁愰崨顏呮杸闂佺偨鍎辩壕顓㈠春閿濆鐓涘ù锝囨嚀婵牏鈧灚婢樼€氼厾鎹㈠┑瀣闁割煈鍊ｅ┑鍡忔斀闁挎稑瀚禍濂告煕婵犲啰澧甸柟顔ㄥ吘鏃堝礃閵婏妇鈧娊鎮楅獮鍨姎妞わ富鍨崇划锝呂旀担鐟板伎濠碉紕鍋犻褎绂嶆ィ鍐┾拺闁告繂瀚悘閬嶆煕閻樺磭澧甸柕鍡曠閳诲酣骞橀弶鎴斿亾闁垮浜滈柟鍝勭Х閸忓瞼鎲搁幎濠傛噽绾句粙鏌涚仦鍓ф噮閻犳劒鍗抽弻娑㈡偐閹颁焦鐤侀梺璇″枤閸忔﹢宕洪敓鐘插窛妞ゆ挾濮烽弸鍐╃節瀵伴攱婢橀埀顒佹礋楠炲繒鈧綆鍠栭弸浣衡偓骞垮劚濡稓绮绘ィ鍐╃厵閻庣數顭堟禒褔鏌熼崘鎻掓殻闁哄矉绻濆畷閬嶅煛娴ｅ嘲鏋堟繝娈垮枛閿曪妇鍒掗鐐茬闁告侗鍨虫す鎶芥倵閿濆簼绨兼い鏂垮閳规垶骞婇柛濠冾殕閹便劑骞橀鑲╊啈濠电姴锕ら幊搴ㄥ垂濠靛鍋ｉ柛銉戝嫧鏋欓梺鍛婅壘椤戝懘鈥︾捄銊﹀磯闁绘碍娼欐慨娑㈡⒑閹稿海銆掗柛鐘崇墪椤繒绱掑Ο鑲╂嚌闂佹悶鍎滈崘鐐氦缂傚倸鍊风粈渚€藝闁秴鏋侀悹鍥皺閺嗭箓鏌熺€电袥闁稿鎹囬弫鎰償閳ヨ尙鏁栫紓鍌欒濡狙囧磻閹炬枼鏀介柣妯虹仛閺嗏晛鈹戦鑺ュ唉鐎规洘鍔欓獮鏍ㄦ媴缁嬪灝娈ら梻渚€娼ч悧鍡浰囨导瀛樺亗闁绘梻鍘х粻褰掑级閸繂鈷旂痪鎯ф健閺屾盯鍩＄€ｎ剛袦闂佸搫鐭夌槐鏇熺閿曞倹鍊锋い鎺戝亞濡叉壆绱撻崒娆掑厡缂侇噮鍨堕幆鍕敍閻愯尙鐣哄┑鐐叉閹尖晠寮崱妞曞綊鏁愰崨顔藉創缂備焦鐓＄粻鏍ь潖婵犳艾纾兼繛鍡樺灥婵¤姤绻濋悽闈涗粶闁挎洦浜滈锝夊Ω閳哄倻鍔堕悗骞垮劚閹冲酣鎮楅銏♀拺缂佸妫楃€氬嘲鈻撻弮鍫熺參闁告劦浜滈弸鎴犵磼缂佹娲存鐐差儔閹瑩宕橀埡鍕棟闁告浜堕弻娑㈩敃閵堝懏鐎鹃梺鍛婄懃缁绘垶绌辨繝鍥ч柛灞剧煯婢规洟姊绘担铏瑰笡妞ゃ劌鎳橀弫鍐敂閸曢潧娈ㄥ銈嗗姧缁插宕戦崨瀛樼厱闁硅埇鍔嶅▍鍥煕濡湱鐭欐俊顐㈡嚇椤㈡洟濮€閳ユ剚妲遍梻浣告贡椤牊鏅堕悾灞藉灊濠电姵鑹剧粻顕€鏌ら幁鎺戝姢闁告ê鎲＄换娑欐綇閸撗冨煂婵犮垻鎳撻敃顏堝箖閿熺姵鍤冮柍瑙勫劤娴滅偓绻涢崼婵堜虎闁哄闄勯妵鍕即閸℃鎼愰柣鎾偓鎰佺唵闁兼悂娼ф慨鍥ㄣ亜椤愩垺鍤囬柡灞界Ч婵＄兘濡烽妷銉ь儓婵犳鍠涢～澶愩€冩繝鍌ゆ綎缂備焦蓱婵挳鏌涘☉姗堟敾闁稿孩鎹囬弻锝夋倷閺夋垵姣堥梺绋款儏閿曘倝鎮鹃悜钘夊嵆闁靛繒濮烽、鍛存⒑缂佹ê鐏ユ俊顐ｎ殔閳绘捇濡搁敂鍓ь啎闁诲海鏁搁…鍫㈡暜閸洘鐓熼幒鎶藉礉閹达箑鏄ユ繛鎴欏灩缁狅綁鏌ㄩ弮鍥棄濞寸媭鍨跺娲箹閻愭彃濮风紓浣稿级濡炶棄鐣烽幋锕€绠荤紓浣股戦崕顏勵渻閵堝棗濮傞柛濠冩礈缁辩偤宕卞▎鎴狅紳闂佺鏈懝楣冨焵椤掑倸鍘撮柟铏殜閹虫粓宕归銏犱紟闂備焦鐪归崹褰掑箟閿熺姴纾婚悗锝庡枟閻撶喐淇婇姘变虎闁告艾婀遍埀顒冾潐閹搁娆㈠璺鸿摕婵炴垯鍨圭粻娑㈡煟閵忋倖娑ч悽顖樺劦濮婅櫣绱掑Ο璇茶敿闂佺娴烽弫璇差嚕鐠囨祴妲堥柕蹇婃櫆閺呮繈姊洪棃娑氬闁瑰啿绻橀幃妤佺節濮橆厸鎷洪柣鐘差儏妤犳悂宕锔绘晜妞ゅ繐妫涚壕鑲╃磽娴ｈ鐒界紒鐘靛仜閳规垿顢欓悷棰佸闂傚倷鑳剁划顖濇懌閻熸粍婢橀崯顖氼焽韫囨稑宸濋悗娑櫱氶幏娲⒑閸涘﹦绠撻悗姘煎墴閸┾偓妞ゆ帊鐒﹂崐鎰偓瑙勬礃閸旀瑥鐣疯ぐ鎺濇晩闁绘劦鍓氶鏇熺節閻㈤潧鈻堟繛浣冲浂鏁勯柛娑卞灣娑撳秹鏌ㄥ┑鍡╂Ч闁绘挶鍎甸弻锟犲炊椤垶鐣舵繛瀛樼矒缁犳牕顫忓ú顏呭癄濠㈣泛锕ュ▓鍫曟⒑閸濄儱校闁绘濮撮锝嗙節濮橆剙宓嗛梺缁樻濞咃綁顢欓弴銏♀拺缂備焦锚婵箓鏌涢幘鏉戝摵闁诡喚鏁诲顕€宕掑鍜冪闯濠电偞鎸婚懝鎯洪妶鍡欘洸婵犲﹤鎳愮壕鐓庮熆鐠虹尨鍔熺紒澶庢閳ь剚顔栭崰娑㈩敋瑜旈崺銉﹀緞閹邦剦娼婇梺缁樕戦悧鎾诲礉瀹ュ桅闁告洦鍨扮猾宥夋煃瑜滈崜鐔风暦閺囩偟鏆﹂柛銉ｅ妽濞堜即姊洪棃娑辨Ф闁稿寒鍣ｅ銊︾鐎ｎ偆鍘撻梺鑺ッˇ浼此夊鍏犵懓顭ㄩ崟顓犵厜闂佽鍠楅〃鍛达綖濠靛鏁囬柣鏂挎惈閸ゆ帗淇婇悙顏勨偓銈夊磻閸曨厽宕查柟閭﹀枛瀵弶淇婇悙顏勨偓鏇犳崲閹邦喒鍋撳鐓庡缂侇噯绲介埥澶娾枎閹寸姷妲囬梻浣圭湽閸ㄨ棄顭囪閻☆參姊绘担鍛婃喐闁哥姴绉瑰畷锟犲礃閼碱剚娈鹃梺鎸庣箓椤︻垶鐛姀锛勭闁瑰鍋熼幉鍧楁煙闁垮銇濇慨濠冩そ閹筹繝濡堕崨顔锯偓鎯р攽閻橆喖鐏柨鏇樺灩椤曪綁骞庨懞銉у姸閻庡箍鍎卞Λ宀勫箯濞差亝鐓熼柕蹇婃櫅閻忛亶鏌涢悩铏殤缂侇喗妫冮、娆撴煥椤栨矮澹曢柣鐔哥懃鐎氼厾绮堥崘鈺€绻嗛柣鎰閻瑦顨ラ悙鎻掓殭闁宠閰ｉ獮姗€宕楅崨顖滃€為梻鍌欑閹测€趁洪敃鍌氱煑閹兼番鍔岀憴锕傛煕濞戞鎽犻柍閿嬪笒闇夐柨婵嗘处閸も偓濡炪倖娲樼划宥嗙┍婵犲浂鏁冮柨娑樺濡差噣鏌ч懡銈呬槐闁哄本鐩鎾Ω閵夈儺娼炬俊鐐€愰弲婊堟偂閿熺姴钃熸繛鎴欏灪閺呮粓鎮归崶銊ョ祷缂佺姾宕电槐鎺楀箚瑜嶇紞鏍磼椤旂晫鎳囩€殿喛顕ч埥澶婎潩椤愶絽濯伴梻浣告啞閹稿棝宕熼锝囧蒋闂傚倸鍊风粈渚€骞夐敓鐘茶摕闁靛ě鍛厠閻熸粎澧楃敮鎺旂不椤栫偞鐓欓弶鍫ョ畺濡绢噣鏌ｉ幒鏇炰汗闁逞屽墮缁犲秹宕曢崡鐐嶆稑鐣濋崟顐ゎ唵闂佸憡绻傜€氀囧绩娴犲鐓熼柟閭﹀墮缁狙勩亜閵壯冧槐闁哄本绋掗幆鏃堝灳閾忣偅鐦ｆ俊鐐€栧ú鈺冪礊娓氣偓閵嗕礁鈻庨幘鏉戞疅闂侀潧顦崕杈ㄥ鎼粹檧鏀介柣妯虹仛閺嗏晛鈹戦鑺ュ唉妤犵偛锕ㄧ粻娑樷槈鏉堛劍娅嗛梻浣虹帛椤洭寮崫銉ヮ棜濠靛倻顭堢痪褔鏌涢锝団槈濠德ゅ亹缁辨帒螖娴ｅ摜鐟查梺闈涙搐鐎氫即銆侀弴銏℃櫜闁搞儮鏅濋崢婊堟煟鎼淬値娼愭繛鍙夌墪閻ｇ兘顢楅崘顏冭埅闂傚倷鑳堕…鍫㈡崲閹版澘瑙﹂悗锝庡櫘閺佸﹪鏌￠崶鈺佇ョ痪鍙ョ矙閺屾稓浠﹂幑鎰棟闂侀€炲苯澧柟顔煎€搁悾鐑藉箛椤撗勑ч柟鍏肩暘閸╁嫰宕戦妸鈺傗拺閻犳亽鍔岄弸鏂库攽椤旂⒈鍤熺紒顕嗙到铻栭柛娑卞枤閸樻悂鎮楅獮鍨姎濡ょ姴鎲＄粋宥夋倷閻戞鍘藉銈嗘尵閸犳劕鐣峰畝鍕厸閻忕偛澧藉ú瀛樸亜閵忊剝绀嬮柡浣瑰姍瀹曞崬鈻庡Ο鎭嶆岸姊婚崒娆掑厡缂侇噮鍨抽弫顕€骞掑Δ浣糕偓鑸垫叏濡寧纭惧鍛存⒑閸涘﹥澶勯柛銊﹀缁骞庨懞銉у幍闁诲孩绋掗…鍥╃不閵夈儍褰掓偐閸愭彃濮㈤梺瀹狀潐閸ㄥ潡骞冨▎鎾崇骇闁瑰鍋熷Σ鏇㈡⒒娴ｈ鍋犻柛搴灦瀹曟繃鎯旈妸銉ュ亶濡炪倕绻愰悧濠囧煕閹达附鐓曢柟鐐綑缁茶霉濠婂嫮澧甸柡灞剧洴閸┾剝鎷呴崜韫磾闂備浇顕栭崰姘跺磻閻愬搫围闁挎繂顦粈鍐煃閸濆嫬鏆欐鐐搭殜濮婄粯鎷呴悷閭﹀殝缂備浇顕ч崐鍝ュ弲闂侀潧鐗嗛ˇ顖炲磼閵娾晜鍊甸柣銏㈡暩閵嗗﹪鏌涚€ｎ偅灏甸柟鍙夋尦瀹曠喖顢楅崒銈喰氶梻鍌欑窔濞佳囨偋韫囨稈鈧箓宕奸妷顔芥櫔闂佹寧绻傞ˇ顖滅不缂佹ǜ浜滈柡鍐ㄦ处椤ュ鏌嶈閸撶喖藟閹捐鐒垫い鎺嗗亾缂佺姴绉瑰畷鏇熷鐎涙ê鈧潡鏌ㄩ弴妤€浜鹃梺瀹狀嚙缁夌懓鐣烽崼鏇ㄦ晢濞达絽鎼獮宥夋⒒娴ｅ憡鍟為柣鐔村劦钘濋梺顒€绉寸粻娲煕閹板吀绨界痪鎯с偢閺岋絽螣閾忕櫢绱為梺瀹犳椤︾敻寮诲澶嬪癄濠㈣埖蓱鏁堟繝鐢靛仧閸樠呮崲濡绻嗛柟闂寸劍閺呮粓鏌ｉ敐鍛板缂傚秵顨婂濠氬磼濮橆兘鍋撻幖浣哥９濡炲娴烽惌鍡椼€掑锝呬壕濡ょ姷鍋為悧鐘汇€侀弴銏犵厱婵﹩鍘介妵婵嗏攽椤旂懓浜鹃梻渚€娼ч悧鍡涘箠鎼淬劍鍊垫い鎾跺亹閺€浠嬫煟閹邦剙绾ч柍缁樻礀闇夋繝濠傚閻帡鏌熼鍡欑瘈鐎殿喗鎸虫慨鈧柨娑樺楠炲秹姊洪崫鍕垫Ц闁绘鎸剧划濠氬冀瑜滈悗鑸点亜閺囨浜鹃梺鍝勭焿缂嶄線鐛Ο鍏煎磯闁绘垶顭囨禍顏堟⒒娴ｅ憡鎯堥柣顓烆槺缁辩偞鎷呴崜鎻掓濡炪倖娲嶉崑鎾垛偓瑙勬礃鐢帡锝炲┑瀣垫晞閻犳亽鍔岀粭鈥斥攽閻樺灚鏆╅柛瀣洴閹椽濡歌閸ㄦ繈鏌涢鐘插姎缁绢厸鍋撻梻浣筋潐閸庣厧螞閸曨垱瀚呴柣鏂挎憸缁犻箖鏌熺€涙鎳冮柣蹇婃櫊瀹曞爼骞橀瑙ｆ嫼闂佺鍋愰崑娑㈠礉閳ь剟姊洪崨濠冨闁告ɑ妞介弻鍥敍閻愮补鎷婚梺绋挎湰閻熝囁囬敃鍌涚厵缁炬澘宕禍鎵偓娈垮枤閺佸銆佸Δ鍛＜婵犲﹤鎳愰崢顖炴⒒娴ｇ瓔娼愮€规洘锕㈤、姘愁樄闁诡喚鍋ゅ畷褰掝敃閻樿京鐩庨梻浣告贡閸庛倝宕烘繝鍥х闁绘棁娅ｇ壕鐓庮熆鐠虹儤婀伴柡鍡╁墴閺岋紕浠﹂崜褎鍒涘Δ鐘靛仦椤洭鍩€椤掑嫭娑ч柟璇х節瀹曟娊顢橀悩鐢碉紳婵炶揪绲块…鍫ュ焵椤掆偓濠€閬嶅极椤斿槈鏃堝焵椤掑嫧鈧箓宕堕鍡欐澑濠电偞鍨跺濠氬磻閹剧粯鏅濋柛灞炬皑椤撴椽姊虹紒妯虹仴婵☆偅顨婂鏌ヮ敆閸曨兘鎷婚梺绋挎湰閻熴劑宕楀畝鈧槐鎺楊敋閸涱厾浠搁悗瑙勬礃缁诲牆顕ｉ幘顔藉€婚柛鈩冾殕椤撳潡姊绘担绋款棌闁稿鎳庨埢鏂库枎閹邦剛绐為梺褰掑亰閸擄箑顭囨繝鍥ㄢ拺闁告劘灏欐禍顏堟煛閸涱喚鈯曠紒鍌氱У閵堬綁宕橀埡浣风敾婵犵數鍋涘Λ妤冩崲閹伴偊鏁傛い蹇撴绾惧ジ鏌嶈閸撶喎鐣锋總绋课ㄩ柨鏃€鍎抽獮鍫ユ⒒娴ｈ櫣甯涢柨姘辩棯缂併垹骞楅柡鍛埣閹粙宕ㄦ繝鍕妇闂備胶纭堕崜婵喢哄鍛幓闁哄啫鍊甸崑鎾斥枔閸喗鐏嶉梺瑙勭摃濞呮洟骞堥妸鈺佺劦妞ゆ帒瀚悡鐔告叏濡炶浜惧┑鐐板尃閸曨剙寮块梺鎸庢⒐閸ㄦ繄鎹㈤崱娑欑厽闁规澘鍚€缁ㄥ鏌嶈閸撴岸鎮у鍫濇瀬妞ゆ洍鍋撴鐐村浮瀵剟宕崟顏勵棜婵犳鍠楅…鍥储瑜嶉锝夘敊闁款垰浜鹃悷娆忓缁€鍐煕閵娿儲鍋ョ€殿噮鍋婇獮妯肩磼濡桨姹楅梻浣告惈閸婂爼宕曢懠顑藉亾濮橆剙鎮戝ǎ鍥э躬閹瑩顢旈崟銊ヤ壕闁哄稁鍘肩粻浼存煕椤愶絾绀€缂佲偓婢跺备鍋撻崗澶婁壕闂佸憡娲嶉弬渚€宕戦幘璇插窛闁圭⒈鍘介弲銏ゆ⒑閸涘﹥澶勯柛鎾寸洴瀹曘垽宕￠悙鈺傛杸闂佺粯鍔曞鍫曀夊鍛＜缂備焦锚閻忔挳鎸婇悢鍏肩厪闊洤锕ゆ晶鏌ユ煕濞嗗繒绠伴柍瑙勫灴閹晠骞撻幒鎾搭唹闂備線娼уú锔炬崲閸曨垰鐒垫い鎺戝枤濞兼劖绻涢幓鎺旂鐎规洘绻傞濂稿炊閵娿儱绨ユ繝鐢靛█濞佳囶敄閸涱垳鐭嗛柛鎰ㄦ杺娴滄粍銇勯幘璺烘瀻闁诲骏濡囬幃顕€鍩￠崨顔规嫽闂佺鏈懝楣冨焵椤掑嫷妫戠紒顔肩墛缁楃喖鍩€椤掑嫮宓佸鑸靛姈閺呮悂鏌ｅΟ鎸庣彧婵炲懎娲娲濞戞氨鐤勯梺鎼炲妿缁垳绮欐径濠庡悑濠㈣泛顑囬崢鎼佹倵楠炲灝鍔氬Δ鐘虫倐閻涱噣寮介銈囷紲闂佺粯锚绾绢厽鏅堕鍫熸嚉闁哄稁鍘介悡銉︾節闂堟稒顥為柛锝呯秺閺岋繝宕卞▎蹇庢闂佸搫鏈粙鎾诲焵椤掑﹦绁烽柛鏂挎湰閹便劑宕掗悙瀵稿幐闁诲繒鍋涙晶浠嬪煡婢舵劖鐓冮柦妯侯樈濡插憡銇勯锝囩煉鐎规洜鍘ч埞鎴﹀幢閳哄倹绁梻鍌氬€搁崐椋庣矆娓氣偓楠炲鏁撻悩铏珨闂佽姘﹂～澶娒洪敃鍌氱闁绘梻鍘ч弰銉╂煟閹邦剚鎯堢紒鐙呯秮閺岋綁骞囬姘辨闂佹眹鍊曞ú顓烆潖缂佹ɑ濯村〒姘煎灣閸旀悂姊洪幐搴㈢８闁稿氦浜崣鍛存⒑缂佹ɑ顥嗛柛瀣姇閻ｅ灚鎷呯化鏇熸杸闂佺粯蓱瑜板啯绂嶉悙鐑樼厱婵﹩鍓﹂崕蹇斻亜椤撯剝纭堕柟鐟板閹噣寮堕幋婵愭浆闂傚倷鐒﹂惇褰掑磹閺囥垹绠犻煫鍥ㄦ⒒閳瑰秴鈹戦悩鍙夋悙閸ユ挳妫呴銏″婵炲弶鐗犲畷鍝勭暆閸曨兘鎷洪梺鍛婄缚閸庡崬鈻嶈箛娑欑厱闁靛鍎抽崺锝嗩殽閻愭彃鏆ｉ柛銊╃畺瀵剟濡烽敂缁樼秾濠电姵顔栭崰妤呮晝椤愩倕绶ら柤鎭掑劤椤╂彃螖閿濆懎鏆為柣鎾存礋閻擃偊宕堕妸锔藉剮濠德ゅ皺椤牓婀佸┑鐘诧工閻楀繘鎮鹃悜妯诲弿濠电姴鍟妵婵嬫煛鐏炶姤鍤囬柟顔界懇閹崇姷鎹勬笟顖欑磾婵犵數濮幏鍐礃閹勵啀闂備線鈧偛鑻晶鍙夈亜椤愩埄妲搁悡銈夋煙鏉堝墽鐣辩痪顓涘亾闂備礁鎲￠崝鏇炍熸繝鍥у惞闁哄洢鍨洪悡鏇炩攽閻樻彃顏╂繛鍛箲娣囧﹥绌遍崡鐐差瀳闂佸疇顫夐崹鍧楀箖濞嗘挸绠甸柟鐑樻尭閳ь剦鍨跺铏圭磼濮楀棙鐣兼繝鐢靛亹閸嬫捇鎮楀▓鍨灈妞ゎ參鏀辨穱濠囧醇閺囩偛鑰垮┑鈽嗗灠閹碱偅瀵兼惔銊︹拻濞达絽鎽滅粔娲煕鐎ｎ亷韬€规洘顨呴～婊堝焵椤掆偓閻ｇ兘骞嬮敃鈧粻濠氭偣閸ヮ亜鐨洪柣銈傚亾闂傚倷绀侀幖顐λ囬崘娴嬫灃闁哄洢鍨洪弲顒佺箾閹存瑥鐏柍閿嬪灴閺岋綁鎮㈤崨濠勫嚒闂佹娊鏀卞鑽ゆ閹烘鏁嬮柛娑卞幘娴犳悂鏌﹀Ο鐓庢灈闁哄本鐩、鏇㈠Χ閸涱喚浜栫紓鍌欒兌婵炩偓缂佺姵鐗犲濠氭偄鐞涒€充壕闁汇垺顔栭悞楣冩煕閹惧啿鏆遍柕鍡樺笒椤繈顢橀悩顐㈩潓濠电儑绲藉ú銈夋晝椤忓牄鈧線寮撮姀鐙€娼婇梺鎸庣箓閹峰瀵奸崘顔解拻濞达絽鎽滈弸鍐┿亜椤愩埄妯€鐎规洖缍婂畷绋课旈埀顒勫垂閸屾鏃堟晲閸涱厽娈查梺璇″灣閸嬬偤濡甸崟顖氱疀妞ゆ柨鍚嬮悗楣冩⒑鐠囪尙绠版俊顐ｇ箓椤繒绱掑Ο鑲╂嚌闂侀€炲苯澧い顓炴喘閹筹繝濡堕崶鈺婃Ц闂備焦鎮堕崕顕€寮插┑鍫㈢焼濠电姴浼呰ぐ鎺撴櫜闁告侗鍙庡Λ宀勬⒑閹肩偛鈧牕煤閺嶎厼鐓橀柟杈鹃檮閸嬫劗鈧娲栧ù鍌炲汲閿熺姵鈷戦柛婵嗗閸ｈ櫣鎲搁弶鍨殻闁炽儻濡囬幑鍕Ω閿曗偓绾绢垱绻涢幘鏉戝毈闁搞劋鍗冲畷婊堟偋閸垻顔曢柣搴㈢⊕椤洭鎯岀€ｎ剛纾兼い鏃囧Г鐏忣厽銇勯銏㈢閻撱倖銇勮箛鎾愁仼缂佹劖绋掔换婵嬫偨闂堟刀銏ゆ倵濮樼厧鏋ゆ俊鍙夊姈鐎佃偐鈧稒菤閹疯櫣绱撴担鍓插剰闁诲繑绻堥、妤呭閵堝棛鍘介梺鍦檸閸亪宕戦幘宕囨殾闁搞儯鍔嶉鏇㈡⒒娴ｅ湱婀介柛銊ヮ煼閳ワ箓宕奸姀鐙€妫滈梺绋跨箺閸嬫劗寮ч埀顒佺節閻㈤潧孝闁稿﹤缍婂畷铏鐎ｎ偆鍙嗗┑鐐村灦閻熝呯不閺嶎偆纾肩紓浣诡焽閳洘绻涢悡搴ｇ鐎规洘绮忛ˇ鏌ユ煕閳轰胶鐒告慨濠冩そ瀹曠兘顢樺☉娆忕彵闂備胶顭堥鍛村箠濮椻偓楠炲啳顦抽柟顖涙婵℃悂鏁傜憴鍕伜婵犵數鍋犻幓顏嗗緤娴犲绠熼柨娑樺鐎氬鏌ㄩ悢鍝勑ｉ柣鎾寸懇閺屟嗙疀閿濆懍绨奸柣蹇撴禋閸欏啴骞冮敓鐘参у璺侯儑閸橆亪姊洪崜鎻掍簼缂佽绉磋灒闁割偁鍎查悡娑樏归敐鍛棌闁稿孩妫冮弻锛勪沪鐠囨彃顬堥梺瀹狀潐閸ㄥ灝鐣烽崡鐑嗘建闁糕剝锕蹇曠磽閸屾艾鈧鎷嬮幓鎺濈€剁憸鏂跨暦閹达箑绠婚悗闈涙憸閻﹀牆鈹戦悙鑼闁诲繑绻堝鎼佹偄閸忚偐鍙嗛梺鍝勬处椤ㄥ棗鈻嶆繝鍕ㄥ亾鐟欏嫭绀冩繛鑼枛楠炲啴鎮惧畝鈧惌娆撴偣閹帒濡兼鐐村姍濮婄粯鎷呴崨濠傛殘婵炴挻纰嶉〃濠傜暦閺夋娼╅悹鍝勬惈閸嬪秹姊洪悷鏉库挃缂侇噮鍨堕幃鈥斥槈濮楀棛鍞甸柣鐘虫そ缂傛氨鈧碍婢橀湁闁绘瑥鎳愰悾閬嶆煃鐟欏嫬鐏存い銏＄☉閳诲酣骞嬮悩闈涘箚闂佽姘﹂～澶娒哄鈧妴鍐川鐎涙ê浠煎┑鐘绘涧椤戝懐绮堢€ｎ偁浜滈柟鐑樺灥閳ь剚鎮傞悰顔嘉旀担椋庣畾闂佺粯鍔︽禍婊堝焵椤掍胶澧电€规洘绻堥獮瀣晝閳ь剟宕归崒婊勫枑闁绘鐗嗘穱顖炴煛娴ｇ鈧綊濡甸崟顖氱閻犺櫣鍎ら悗楣冩⒑閸涘﹦鎳冪紒缁樺姍閸╃偤骞嬮敂钘変汗闂佸湱绮敮妤€鈻撻弻銉︹拺闂傚牊绋掓径鍕磼鐠囨彃鏆ｉ柕鍡楀暣婵＄兘鍩￠崒姘ｅ亾閻戣姤鍊甸柣銏☆問閻掗箖鏌嶅畡鎵劯婵﹨娅ｉ幏鐘诲灳閾忣偆浜堕梻浣藉吹閸熷潡寮查悩缁樺仒妞ゆ洍鍋撴鐐搭焽閹风娀鎳犻澶婃暯闂傚倷鑳堕幊鎾绘偪閸モ晝绀勫ù鍏兼綑绾捐绻濋棃娑欙紞婵炲吋鐗滅槐鎾存媴閼测剝鍨堕崚濠勨偓闈涙憸绾惧吋绻涢幋鐑囦緵闁稿骸绻戠换娑㈠醇椤愩垹绠荤紓浣介哺閹稿骞忛崨瀛樻優闁荤喐澹嗛濂告⒒娴ｈ鍋犻柛鏂跨灱缁辩偞绻濋崶鈺佺ウ闂佺鎻粻鎴︼綖閸涘瓨鐓忛柛顐ｇ箖閸ゅ洭鏌涢悙鑼煟婵﹥妞藉Λ鍐ㄢ槈瀹曞洨顢呴梻浣告啞閸旓附绂嶅Δ鍛；濠电姴鍊甸弨浠嬫煟濡澧柛鐔风箻閺屾盯鎮╅幇浣圭杹婵犵绻濆褔鍩ユ径鎰潊闁绘顣槐閬嶆⒒娴ｇ瓔娼愰柛搴㈠▕閹﹢宕奸妷銉э紱闂佽宕橀崺鏍窗閸℃稒鐓曢柡鍥ュ妼婢х増銇勯敂鍝勫缂佽鲸鎸荤粭鐔煎炊瑜庨悘鍫㈢磽娴ｅ壊妲哥紓宥咃躬閹即顢欑捄銊ф澑濠电偞鍨堕敋缂佹劖鐩弻锝嗘償閵忊懇濮囬柦鍐憾閺屾稒鎯旈濠氬仐闂佸搫鐭夌紞渚€骞冮姀銈呬紶闁靛绠戝▍鎴︽⒑鐠囨彃顒㈤柛鎴濈秺瀹曪綁宕橀琛″亾?$+闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇缁♀偓闂侀€炲苯澧撮柡灞芥椤撳ジ宕ㄩ姘曞┑锛勫亼閸婃牜鏁幒妤€纾圭憸鐗堝笒閸氬綊鏌嶈閸撶喖寮婚敐鍡樺劅闁靛繒濮村В鍫ユ⒑閸涘﹦鎳冮柛鐕佸亰閹儳鐣￠幍顔芥闂佹悶鍎滅仦缁㈡％闂備浇顕ч崙鐣屽緤婵犳艾绀夐悗锝庘偓顖嗗吘鏃堝川椤旇瀚奸梻渚€娼荤€靛矂宕㈡總绋跨閻庯綆鍠楅悡鏇㈡煏婵炲灝鍔ょ紒澶庢閳ь剝顫夊ú姗€宕濆▎鎾崇畺婵炲棙鎸婚崐缁樹繆椤栨繃銆冮柣銏㈢帛缁绘繈鎮介棃娴躲垽鏌ㄩ弴妯衡偓妤呭焵椤掍礁鍤柛锝忕秮楠炲棛浠﹂崜褏鐦堥梺鎼炲労閻撳牆顫忛幐搴ｇ瘈闁靛骏绲介悡鎰版煕閺冣偓閻楃娀骞冮垾鏂ユ婵﹫绲芥禍鐐殽閻愯尙浠㈤柛鏃€鑹捐灃闁绘娅曢崐鎰版煟濞戝崬鏋涢摶鏍煕濞戝崬鏋ら柛妯圭矙濮婅櫣鎲撮崟顐㈠Б缂佸墽铏庨崢鍓у垝缂佹ê顕遍悗娑欘焽閸樻捇鏌ｉ悢鍝ユ噧閻庢凹鍓熼幃姗€骞橀鐣屽幍濡炪倖姊婚弲顐﹀箠閸ャ劋绻嗘い鎰╁灩閺嗘瑩鏌嶉挊澶樻Ц閾绘牠鏌嶈閸撶喕妫㈠┑顔斤供閸樺墽寮ч埀顒勬⒑濮瑰洤鐏叉繛浣冲嫮顩锋繝濠傜墛閻撴瑩鏌涢幇顓犲弨妞ゅ孩顨婇幗鍫曟倷鐎靛摜鐦堥梻鍌氱墛娓氭宕曡箛娑欑參闁告劦浜滈弸鎴犵磼缂佹娲存鐐差儔閹瑩宕归銏＄彯缂傚倸鍊风拋鎻掝瀶瑜斿畷鎴﹀箻缂佹ǚ鎷绘繛杈剧导鐠€锕傛倿妤ｅ啯鐓涢柛顐亜婢ф壆绱掗鐣屾噰妤犵偛顑夐幃娆忊枔閹稿海銈梻鍌欑濠€閬嶆惞鎼淬劌绐楁俊銈呮噹閻撴洟鏌熸潏楣冩闁绘挻娲樼换婵嬫濞戞瑯妫炲銈呮禋閸撴岸濡甸崟顖ｆ晝妞ゆ帒鍊婚弳銈夋⒑閸濆嫭婀伴柣鈺婂灠椤曪綁顢氶埀顒勫春閳ь剚銇勯幒鎴濐仼闁搞劌鍊块弻锝夊閻樺樊妫岄梺杞扮椤戝寮婚妸銉㈡斀闁糕檧鏅滅瑧缂傚倷鑳剁€氬繘宕堕妸褍骞嶉梺璇查濠€杈ㄦ叏閻㈠憡鍋熸い蹇撴噸缁诲棙銇勯幇鈺佺仼闁哄棴绲块埀顒冾潐濞叉ê顪冩禒瀣槬闁逞屽墯閵囧嫰骞掑澶嬵€栨繛瀛樼矋缁捇寮婚悢鐓庝紶闁告洦鍘滈妷鈺傜厱闊洦鎸诲﹢浼存煏閸パ冾伃闁糕晪绻濆畷鎺戭煥閸愩劎鍝楅梻鍌欑閹碱偊鎯夋總绋跨獥闁哄稁鍘归埀顑跨閳藉濮€閻樿尪鈧灝鈹戞幊閸婃捇鎮為敃鍌氱闂侇剙绉甸悡鐔煎箹濞ｎ剙濡芥繛鎳峰洦鐓熸い鎾跺仜閳ь剚鎮傞敐鐐剁疀閹句焦妞介、鏃堝椽娴ｈ妫冨┑鐘垫暩閸嬬偤宕归崼鏇熷殞濡わ絽鍟ㄩ埀顒€鍊块幃婊兾熼崷顓犵暰闂備線娼ч悧鍡椕洪妶鍡欏ⅰ闂傚倷娴囬鏍窗閺嶎厼纾归柛褎顨呯粻鏍归崗鍏煎剹闁轰礁顑夐弻宥堫檨闁告挻鐟╅、姘舵晲閸℃瑯娴勯柣搴到閻忔岸寮查敐澶嬧拺缂備焦蓱閻撱儵鏌熺喊鍗炰喊闁绘侗鍠栬灃闁告侗鍠掗幏缁樼節閻㈤潧孝濡ょ姴鎲＄粋宥咁煥閸曨厾顔曢柣鐘叉厂閸涱垱娈告俊鐐€ら崑鍕崲濮椻偓楠炲﹪鏁撻悩鍙傃囨煕閵夘垰顩柣鐔哥箞濮婄粯鎷呴搹鐟扮闂佸憡姊归〃濠傜暦閺囥垹围濠㈣泛顑呮禍妤呮⒑闂堟侗妾у┑鈥虫川瀵囧焵椤掑嫭鈷掗柛灞炬皑婢ф稓绱掔€ｎ偅灏伴柡鍛埣瀵挳鎮樼拠鑼Ш闁诡喒鏅涢悾鐑藉炊閳哄啫甯掗梻鍌欑窔濞佳兠洪妶鍥ｅ亾濮橆偄宓嗛柕鍡曠铻ｅ〒姘煎灣閸欏棝姊洪崨濠傚Е闁绘挸鐗嗛…鍥ㄦ償閵婏妇鍘介柟鍏肩暘閸娿倕顭囬幇顓犵闁告瑥顦遍惌鎺斺偓瑙勬磸閸ㄥジ藝瑜版帗鐓曢柍鍝勫暙娴犺鲸顨ラ悙宸剶闁轰礁鍟撮崺鈧い鎺戝閻撲線鏌涢妷锝呭缂佲檧鍋撻梻鍌氬€搁悧濠勭矙閹烘鍋傞柍褜鍓熷娲传閸曨剙绐涢梺绋款儐閸旀瑥顕ｉ妸锔绢浄閻庯綆鍋嗛崣鍡椻攽閻愭潙鐏﹂柨姘亜韫囧﹥娅呴棁澶嬬節婵犲倸鏆旈柛瀣ㄥ灲閺岋紕浠﹂崜褉濮囩紓浣虹帛缁诲牆鐣峰鈧、鏃堝礋閵婏箑鐝遍梻鍌氬€峰ù鍥ь浖閵娧勵偨闁跨喓濮撮梻顖炴煥濠靛棭妲虹€规挷绶氶弻鐔兼倻濮楀棙鐣跺┑鈽嗗亝閿曘垽寮诲☉妯锋斀闁糕剝顨忔禒瑙勭節閵忥綆娼愭繛鍙夌墵閸╃偤骞嬮敂缁樻櫓缂備焦绋戦鍥礉閻戣姤鈷戦柛娑橆煬閻掑ジ鏌涢弴銊ュ闁绘繄鍏樺铏规喆閸曢潧鏅遍梺鍝ュУ濮樸劍绔?
		if (divContents.isEmpty()) return input;
		String last = divContents.get(divContents.size() - 1);
		if (!last.matches("\\$\\d+(\\.\\d{2})?")) {
			return input; // 濠电姷鏁告慨鐑藉极閸涘﹥鍙忛柣鎴ｆ閺嬩線鏌涘☉姗堟敾闁告瑥绻橀弻锝夊箣閿濆棭妫勯梺鍝勵儎缁舵岸寮诲☉妯锋婵鐗婇弫楣冩⒑閸涘﹦鎳冪紒缁橈耿瀵鏁愭径濠勵吅闂佹寧绻傚Λ顓炍涢崟顖涒拺闁告繂瀚烽崕搴ｇ磼閼搁潧鍝虹€殿喛顕ч埥澶娢熼柨瀣垫綌婵犳鍠楅〃鍛存偋婵犲洤鏋佸Δ锝呭暞閳锋垿鏌涘☉姗堝姛闁瑰啿鍟扮槐鎺旂磼濮楀牐鈧法鈧鍠栭…鐑藉极閹邦厼绶炲┑鐘插閸氬懘姊绘担鐟邦嚋缂佽鍊歌灋妞ゆ挾鍊ｅ☉銏犵妞ゆ牗绋堥幏娲⒑閸涘﹦绠撻悗姘卞厴瀹曟洘鎯旈敐鍥╋紲闂佸吋鎮傚褔宕搹鍏夊亾濞堝灝鏋涙い顓犲厴楠炲啴濮€閵堝懐顦ч柣蹇撶箲閻楁鈧矮绮欏铏规嫚閺屻儱寮板┑鐐板尃閸曨厾褰炬繝鐢靛Т娴硷綁鏁愭径妯绘櫓闂佸憡鎸嗛崪鍐簥闂傚倷鑳剁划顖炲礉閿曞倸绀堟繛鍡樻尭缁€澶愭煏閸繃顥撳ù婊勭矋閵囧嫰骞樼捄鐩掋垽鏌涘Ο渚殶闁逞屽墯椤旀牠宕伴弽顓涒偓锕傛倻閽樺鐎梺褰掑亰閸樿偐娆㈤悙娴嬫斀闁绘ɑ褰冮顐︽煛婢跺﹥鍟炲ǎ鍥э躬閹瑩顢旈崟銊ヤ壕闁哄稁鍋呴弳婊冣攽閻樺弶澶勯柛濠傛健閺屻劑寮撮悙娴嬪亾閸濄儱顥氶柛褎顨嗛悡娆撴煕閹炬鎳庣粭锟犳⒑閹惰姤鏁遍柛銊ユ健瀵鎮㈤崗鐓庢異闂佸疇妗ㄥ鎺斿垝閼哥數绡€缁剧増菤閸嬫捇鎮欓挊澶夊垝闂備礁鎼張顒傜矙閹烘梹宕叉繝闈涱儏绾惧吋绻涢幋鐏荤厧菐椤曗偓閺岋絾鎯旈姀鈺佹櫛闂佸摜濮甸惄顖炪€佸鎰佹▌闂佺硶鏂傞崕鎻掝嚗閸曨垰绠涙い鎺戭槹缂嶅倿姊绘担绋挎毐闁圭⒈鍋婂畷鎰版偡閹佃櫕鐎洪梺鎼炲労閸撴岸鍩涢幒鎳ㄥ綊鏁愰崶銊ユ畬濡炪倖娲樼划搴ｆ閹烘梹瀚氶柟缁樺坊閸嬫挸顫㈠畝鈧禍杈ㄧ節閻㈤潧浠﹂柛銊у枛楠炲﹪鎮欓崫鍕€炲銈嗗坊閸嬫捇宕鐐村仭婵犲﹤鍟扮粻鍐差熆鐟欏嫭绀嬮柟绋匡攻缁旂喎鈹戦崱娆懶ㄩ梺杞扮劍閸旀瑥鐣烽妸鈺婃晩闁芥ê顦竟宥夋⒑鐠囪尙绠扮紒缁樺姉閹广垹螣娓氼垱缍庡┑鐐叉▕娴滄粌顔忓┑鍡忔斀闁绘ɑ褰冮銏ゆ煟閿濆鏁辩紒缁樼箞閹粙妫冨☉妤冩崟闂備焦瀵уú蹇涘磹濠靛鈧礁顫濋懜鍨珳闂佹悶鍎崝宀勬偟閺夊簱鏀介柣鎰摠缂嶆垶銇勯幋婵囶棦闁挎繄鍋涢濂稿川椤忓懏鏉搁梻浣哥枃濡嫬螞濡ゅ懏鍊堕柨鏇炲€归悡娑㈡煃瑜滈崜鐔煎蓟閸℃鍚嬮柛娑卞櫘濡喖姊绘笟鈧埀顒傚仜閼活垱鏅剁€涙ɑ鍙忓┑鐘插鐢盯鏌熷畡鐗堝殗鐎规洏鍔嶇换婵嬪磼濞戞瑧鏆繝鐢靛Х閺佹悂宕戦悩娲绘晪婵犲﹤鎳愭稉宥夋煙閹咃紞闁哄棴绠撻弻鐔告綇閸撗呮殸缂備讲鍋撻悗锝庡亖娴滄粓鏌熼崫鍕ゆい锔煎閳ь剚顔栭崰鏍ь焽閳ユ剚娼栨繛宸簼椤ュ牊绻涢幋鐐寸殤闁汇倕瀚板铏圭矙閸栤剝鏁鹃梺缁橆殘婵炩偓鐎规洖鎼埥澶愬閻樻鍚呴梻浣虹帛閸旀寮幖渚囨晩闁哄洢鍨洪埛鎴︽煟閹惧磭鏄傞柛瀣ㄥ灪閵囧嫰濡堕崨顓涘亾閸喚鏆﹂梻鍫熺▓閺嬪酣鏌熼弶璺ㄤ粵妞ゆ梹妫冨铏圭磼濡搫顫嶉梺璇″灠閼活垶鍩㈤幘鎰佺叆闁割偆鍠撻崢鎼佹倵閸忓浜鹃梺閫炲苯澧寸€规洑鍗冲浠嬵敇閻愮數鏆梻浣虹帛閸ㄥ吋鎱ㄩ妶澶婄；閻庯綆鍠楅悡鏇熴亜閹板墎鎮肩紒鐘崇墵閺岋綀绠涢幘璇插及闂佸搫鐭夌徊楣冨箚閺冨牆顫呴柍杞扮劍濞呮盯姊绘担鍛婃喐濠殿喚鏁婚幃褔鎮╃拠鑼紜闂佹寧娲栭崐褰掑磹婵犳碍鐓㈡俊顖欒濡叉椽鏌ｉ敐鍡樸仢婵﹥妞藉Λ鍐ㄢ槈濮樿京鏉介梻浣侯焾閿曘儵鎮ч悩鑽ゅ祦闁糕剝绋掗崐鐑芥煟閹寸儐鐒介柛姗€浜跺娲箰鎼淬埄姊垮銈嗘肠閸愭儳娈ㄩ柣鐘充航閸斿海澹曟總绋跨骇闁割偅绋戞俊鍧楁煕濞嗗苯浜惧┑鐘愁問閸犳牠鏁冮敂鎯у灊妞ゆ牗绮庣粻鏃堟煟閺冨倸甯堕柣鎺戠仛閵囧嫰骞掗崱妞惧婵＄偑鍊ら崢鐓幟洪埡鍚藉洭骞橀鐣屽幍濡ょ姷鍋涢悘婵嬫倶閸欏娈介柣鎰綑濞搭喗顨ラ悙杈捐€跨€殿喖鐖奸獮瀣攽閸涱垳顦伴梻鍌氬€搁崐鎼佸磹閹间礁纾归柟闂寸劍閸嬪鈹戦悩鎻掝伀闁活厽鐟╅弻娑㈩敃閻樻彃濮庨梺鍛婂灩婵炩偓闁哄矉绻濆畷姗€鏁愰崨顒€顥氱紓鍌氬€烽懗鍓佸垝椤栫偞鍎庢い鏍ㄦ皑閺嗭箓鏌涘Δ鍐ㄢ偓锝夋偄閻戞ê鐝伴梺鍝勮閸庢娊鍩€椤掍焦銇濇慨濠勭帛閹峰懘鎮烽幍铏亞闂備浇銆€閸嬫捇姊洪鈧粔鎾垂閸岀偞鐓曠憸搴ㄣ€冮崨瀛樺珔闁绘柨鍚嬮悡鐔兼煛閸屾氨浠㈤柟顔藉灴閺屾盯濡堕崨顓熸闂佸搫鑻粔闈涱焽椤忓牊鍋嬮柛顐亝閳诲﹦绱撻崒娆戣窗闁哥姵鐗犻幃銉╂偂鎼搭喗缍庡┑鐐叉▕娴滄粌顔忓┑鍡忔斀闁绘ɑ褰冮弳濠囨煙瀹勬壆绉烘慨濠冩そ閹兘鎮ч崼婵囨畼濠电姷鏁搁崑娑㈡晝閵堝鏁嬮柨婵嗘处鐎氭碍绻涢弶鎴剱妞ゎ偄绉瑰娲濞戞氨顔婃繝娈垮枤閸忔﹢銆佸▎鎾崇婵°倓鑳堕崢浠嬫煙閸忚偐鏆橀柛鏂跨Т閳藉顦归柡宀€鍠撻幏鐘侯槾缂佲檧鍋撻柣搴ゎ潐濞叉繂鈻嶉弴鐘亾娴ｅ啫浜归柍褜鍓氱粙鎺曞綔闂佸綊鏀卞浠嬪蓟閿濆鍋愰柛娆忣槺椤﹂亶姊洪柅鐐茶嫰婢у弶绻涢崨顔界闁诡喗锕㈤崺鈧い鎺戝閳锋帒銆掑锝呬壕闂侀€炲苯澧伴柛瀣洴閹崇喖顢涘☉娆愮彿濡炪倖鐗滈崑鐐烘偂濞戙垺鍊甸柨婵嗛婢ь噣鎮介姘卞煟鐎规洩绻濋弻鍡楊吋閸℃瑥骞堟俊鐐€栭崝妤佹叏閹绢喖绀夋繝濠傜墛閻撶喖鏌熼幆褏锛嶇紒鐘差煼閺岋綁骞欓崘銊ゅ枈閻庤娲栭悥鍏间繆閻戣棄唯闁靛鍎涢幋鐘电＝濞达絽鎼禍楣冩煕鎼粹€虫毐妞ゎ厼娲弫鎾绘偐閼艰埖鎲版繝鐢靛仦閸ㄥ爼鈥﹂崒鐐村亜闁绘挸绨肩槐鍫曟⒑閸涘﹥澶勯柛鐘崇墵閹€愁潨閳ь剙顫忕紒妯诲闁告縿鍎查悗顔碱渻閵堝骸浜滄い锕傛涧閻ｇ柉銇愰幒鎴︽暅濠德板€愰崑鎾剁磼閻樺磭澧紒缁樼洴瀹曞崬螣鐠囪尙顣茬紓鍌欒兌鐎氬繘宕堕妸褍骞嶉梺璇叉捣閺佹悂鈥﹂崼婵堟瘓濠电姷鏁搁崕鎴犵礊閸℃稑闂柨婵嗩槸閽冪喖鏌ㄥ┑鍡橆棡闁稿海鍠栭弻鏇㈠醇濠靛棭鍔夌紓鍌氱Т閻楀棝鍩為幋锔藉€峰Λ鐗堢箓濞堟繄绱撴担鍝勑ｉ柟鍛婃倐閹儳鐣￠柇锔惧弳闂佸壊鍋嗛崰搴㈢濠婂嫮绡€闂傚牊绋戦埀顒佹倐钘濇い鏍ㄥ焹閺嬫棃鏌曟繛褍鎳愰敍婵囩箾鏉堝墽鍒板鐟帮躬瀹曟洝绠涢弴鐘碉紲闂侀潧顭堥崕閬嶆嚋瑜版帒纭€闂侇剙绉甸悡鏇熴亜閹扳晛鈧洟寮稿鍥╃＜?
		}

		// 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢弻銉﹀殞濡わ絽鍟悡姗€鏌熺€电浠滅紒鐘靛█濮婅櫣绮欓崠鈩冩暰濡炪們鍔屽Λ婵嬬嵁閸儱惟闁冲搫鍊搁埀顒€顭烽弻锕€螣娓氼垱楔闂佹寧绋掗惄顖氼潖閾忓湱纾兼俊顖氭惈椤酣姊虹粙璺ㄦ槀闁稿﹥绻傞悾鐑藉箣閻橆偄浜鹃柨婵嗛閺嬬喖鏌ｉ幘璺烘瀾濞ｅ洤锕、娑樷攽閸℃鍎繝鐢靛Л閸嬫挸霉閻樺樊鍎愰柣鎾冲暟閹茬顭ㄩ崼婵堫槯濠电偞鍨剁喊宥夘敃閼恒儲鍙忔慨妤€妫楁晶濠氭煕閵堝棙绀嬮柡宀€鍠撶槐鎺楀閻樺磭浜俊鐐€ら崑鍕箠濮椻偓瀵顓兼径濠勫幐婵炶揪绲介幉鈥斥枔閺屻儲鈷戠紓浣诡焽缁犳牗绻涢崣澶岀煂婵″弶鍔欓獮妯兼嫚閼碱剨绱叉繝娈垮枟閿曗晠宕楀鈧畷鎴﹀箻缂佹ê鈧鏌ら幁鎺戝姎闁逞屽墮濞硷繝寮婚妸鈺佸嵆婵ê宕俊浠嬫⒑閻熸澘鏆遍柡鍫墰閹广垹鈹戠€ｎ偄浠洪梻鍌氱墛缁嬫劗鍒掗崼鏇熲拺闁荤喐婢橀弳閬嶆⒑鐢喚绉鐐插暢椤﹁顨ラ悙杈捐€挎鐐叉处閹峰懘宕烽鐘瀱缂傚倸鍊搁崐鎼佸磹閻戣姤鍊块柨鏇氱劍閹冲苯鈹戦悩鎰佸晱闁搞劑浜堕獮鎰板箮閽樺鎽曞┑鐐村灦鑿ゆ俊鎻掔墛閹便劌螖閳ь剟鎮ч崱妯侯嚤闁规壆澧楅埛鎴︽煕濠靛棗顏い銉︾矒閺岋絽螖閳ь剟鏁冮敃鍌氱闁靛繒濮Σ鍫ユ煏韫囨洖啸妞ゆ梹甯￠弻锝嗘償閵婏附閿繝銏㈡嚀閿曨亜顕ｉ幎钘夌伋闁归鐒︾€靛矂姊洪棃娑氬闁哥噥鍋呮穱濠冪附閸涘﹦鍙嗛梺鍝勬处閿氶柍褜鍓氱换鍫ョ嵁閸愨斂鍋呴柛鎰ㄦ櫅娴犳儳顪冮妶鍡欏閻鎱ㄧ憴鍕垫疁婵﹥妞藉畷鐑筋敇閻愭彃顬嗙紓鍌欐祰椤曆囧疮閺夋埈鍤曢柟缁樺俯閻撱儵鏌涢銈呮毐闁归攱妞藉娲川婵犲啫闉嶉悷婊勬緲閸燁垳绮嬪鍛斀闁割偁鍨婚敍婊堟煟閻樺弶澶勭憸鏉垮暣椤㈡棃顢橀姀锛勫幐婵炶揪绲介幗婊冪摥闂備礁鐤囧Λ鍕囬崹顐ｅ弿闁逞屽墴閺屽秹鏌ㄩ姘闂備礁缍婇弨鍗烆渻閽樺娼栨繛宸簼椤ュ牓鏌嶆潪鎷屽厡婵¤缍佸娲川婵炴帩浜畷銉р偓锝庡墰閻鈧箍鍎卞Λ搴ㄥ磻閸涘瓨鐓曢柟鑸妽濞呭洭鏌涘鈧禍璺侯潖濞差亝鍋￠柡澶婄氨閹稿啫顪冮妶鍐ㄥ姕闁告梹鐗滅划鈺呮偄閼茶櫣鍠庢晥闁搞儻濡囬惄搴繆閻愵亜鈧牠骞愭ィ鍐ㄧ；闁绘劕鎼粣妤佹叏濡炶浜鹃梺鍝勬湰閻╊垶宕洪敍鍕ㄥ亾閿濆骸澧伴柣锕€鐗撻幃妤冩喆閸曨剛顦ㄩ柣銏╁灡鐢喖鎮橀幒妤佲拺闂傚牃鏅涢惁婊堟煕濡鍔ら柍缁樻瀹曠螖娴ｅ弶瀚介梻浣侯焾閺堫剟鎮烽敂鍓х焾闁绘鐗勬禍婊勩亜閹扳晛鐒烘俊鎻掓贡缁辨帡寮崒姘亖闂侀€炲苯澧剧紓宥呮瀹曚即寮介鐔蜂痪闂佺粯鍔﹂崜娑氱不妤ｅ啯鐓欓悗娑欘焽閹冲啴鏌ｈ箛锝勯偗闁哄本绋掔换婵嬪磼濠婂啰鍘介梻浣告惈閼活垳绮旈悷鎵殾妞ゆ劧绠戠粈瀣亜閹板墎绋绘い鏂跨Ф缁辨挻鎷呴悷鏉垮Б婵犫拃鍌滅煓鐎规洏鍨奸ˇ鐟扳攽閳╁啯灏︽鐐村浮楠炲顢橀悢宄板帪濠碉紕鍋戦崐鏍暜婵犲洦鍊块柨鏇炲€哥粻鏍ㄧ箾閸℃绠氶柡鈧禒瀣厽闁归偊鍓欑痪褔鏌涢敐鍛础缂佽鲸甯楀鍕沪閻愵剚鐦撴俊銈囧Х閸嬫盯鎮ч幘缈犵箚闁归棿鐒﹂弲婊堟煕閹炬鎳忛～宀勬⒒閸屾瑧顦﹂柟娴嬪墲缁楃喎螖閸涱參妫峰銈嗘⒒閺咁偊銆呴悜鑺ョ厓闁告繂瀚崳娲煃闁垮鐏撮柡灞剧☉閳藉顫滈崼婵嗩潬濠电姭鎷冪仦钘夆拫闂佸搫鐭夌徊鍊熺亙闂佸憡鍔戦崜閬嶅鎺虫禍婊堟煛閸ユ湹绨界紒澶樺枟椤ㄣ儵鎮欐潏鎹愨偓鎸庛亜閵忥紕鎳囬柟绋匡攻瀵板嫭寰勫畝鈧▔鍧楁⒒閸屾艾鈧绮堟笟鈧獮澶愭晬閸曨厾鐓撻梺纭呮彧闂勫嫰寮插┑瀣叆闁哄洨鍋涢埀顒€缍婂畷鎴﹀煛閸屾ü绨婚棅顐㈡处閹哥偓鏅跺☉銏＄厓闂佸灝顑呭ù顕€鏌＄仦鍓с€掑ù鐙呯畵瀹曟粏顦抽柛锝庡灦濮婅櫣绮欑捄銊ь唶闂佸搫鐗滈崜鐔镐繆閻㈢绀嬫い鏍电稻閺咃綁姊虹紒妯哄婵炰匠鍥х劵闂傚牊渚楀〒濠氭煏閸繂鏆欓柣蹇ｄ邯閺岋絽螖閸愩剱銏ゆ煟閿濆洤鍘寸€规洩绻濋幃娆撳煛閸屻倖缍屽┑鐘殿暯濡插懘宕归幎钘夌厱闁割偁鍎遍懜褰掓煕椤愶絾绀冮柍閿嬪笒闇夐柨婵嗘噺閹插摜绱掓径灞藉妺濞ｅ洤锕、鏇㈠Ψ閿斾勘鈧﹪姊洪悷鏉挎Щ妞ゆ垵顦锝嗙節濮橆儵銊╂煏婢诡垰鍟婵犵绱曢崑鎴﹀磹閺嶎偅鏆滃┑鐘叉处閸婂潡鏌ㄩ弴鐐扮椽濠㈣埖鍔曢柋鍥煃閸ㄦ稒娅呭ù婊堢畺閹嘲鈻庤箛鎿冧痪缂備讲鍋撻柛鎰靛枟閻撱儵鏌￠崶顭嬵亪鎮橀弻銉︾厪闁糕剝娲滈ˇ锕傛煃鐠囨煡鍙勬鐐达耿瀹曟﹢鎳犻鈧幖鍛婄節閻㈤潧袨闁搞劌銈搁、姘跺冀椤撶偛鐎梺鍝勵槹閿涙洖煤椤忓懏娅囬梺绋挎湰缁嬫垶绂嶅Δ鍛拺闁告繂瀚崒銊╂煕閵婏附銇濋柟顕嗙節瀹曟﹢顢欓悾灞藉笚闂佸搫顦遍崑鐐寸珶閸℃蛋鍥晝閸屾稓鍘搁柣搴祷閸斿矂鍩€椤掍胶澧遍柟骞垮灩閳藉濮€閻樿鏁归梻浣虹帛濞叉牠宕愭禒瀣劦妞ゆ巻鍋撴繛纭风節瀵鍨惧畷鍥ㄦ畷闁诲函缍嗛崜娑溾叺闂傚倷娴囬鏍窗閹捐鍨傞柦妯侯樈閸ゆ洟鏌涢锝嗙缂佺媴绲剧换婵嬪垂椤愩垹顫嶇紓鍌氱Т濡鍩為幋锔藉亹闁归绀侀弲閬嶆⒑閸濄儱校闁绘濞€瀵偊宕橀鍢夆晠鏌曟径鍫濈仾闁诲孩妞藉娲箰鎼达絿鐣靛┑鐐茬湴閸婃繂鐣烽悷鎵虫斀闁搞儮鏅濋鏇㈡⒑缁洖澧查拑閬嶆倶韫囷絽骞樼紒杈ㄥ浮閸┾偓妞ゆ巻鍋撻柣锝嗙箞瀹曠喖顢楅崒姘瑲闂傚倷绀侀悿鍥ь浖閵娧呯焼濞撴埃鍋撶€殿喗濞婇幃娆撴倻濡厧骞愰梻浣虹《閸撴繂煤閺嵮呮殼闁糕剝蓱閸欏繐鈹戦悩鎻掝伀閻㈩垱鐩弻鐔风暋閻楀牆娅х紓渚囧枤閺佹悂鍩€椤掑﹦绉靛ù婊勭箞閹偓瀵肩€涙ǚ鎷绘繛杈剧到閹诧繝骞夌粙搴撴斀妞ゆ梻鍋撻弳顒勬煙椤斻劌娲﹂崑鎰版煠绾板崬澧繛鍫ョ畺濮婃椽宕烽鐑嗘毉濠电姰鍨洪敃銏ゅ蓟閵娾晛围闁糕剝娲滅粻姘舵⒑闂堟稓澧曢柛濠傛啞缁傚秹宕滆绾捐棄霉閿濆懏鎯堥弽锟犳⒑缂佹﹩娈曟い銊ユ缁碍娼忛妸褏鐦堥梺鎼炲労閻忔稖顦归柡灞剧洴瀵挳濡搁妷銉у摋缂傚倷鐒﹂崝鏍€冮崨鏉戠厴闁硅揪绠戦悞娲煕閹扳晛濡跨紒渚€顥撶槐鎾诲磼濮樻瘷銏ゆ煥閺囥劋閭柟顔诲嵆椤㈡岸鍩€椤掆偓椤曪絾绂掔€ｎ€晠鏌ㄩ弬鍨挃閼叉牠姊婚崒娆戭槮闁硅绻濋獮鎰板幢濞戞顦板銈嗗厞閵堝洨顔庨梻鍌氬€搁崐鎼佸磹閻戣姤鍊块柨鏃堟暜閸嬫挾绮☉妯诲櫧闁活厽鐟╅弻鐔兼倻濡儵鎷归悗瑙勬礃閻擄繝寮诲☉銏╂晝闁挎繂娲ㄩ鐓庘攽閻愭彃鎮戦柣妤冨█瀵槒顦剁紒鐘崇洴楠炴澹曠€ｎ剦鏀ㄩ梻鍌欑劍濡炲潡宕㈡禒瀣仭闁冲搫鎳庨拑鐔兼煟閺冨洦顏犻柣顓熺懇閺屾盯鈥﹂幋婵囩彯闂佸憡鏌ㄩˇ闈涱潖濞差亜鎹舵い鎾跺仜楠炲绻涚€涙鐭庢繛澶嬬洴閿濈偠绠涢幘浣规そ椤㈡棃宕ㄩ鐕佸悑闂傚倷绶氬褔鎮ч崱娑樼疇闊洦鎸撮弸鏃傗偓鍏夊亾闁告洦鍏橀幏娲⒑閸涘﹦鈽夐柨鏇悼濞嗐垽鏌嗗鍡欏幈闂侀潧顭粻鎴﹀煝閺囥垺鐓熸繛鎴濆船濞呭秵顨ラ悙鎼劷缂佽鲸甯掕灒闂佸灝顑呴褰掓⒒閸屾瑧顦﹂柟璇х磿閹广垽宕掑┃鎯т壕婵鍘ф晶瀵糕偓娈垮枦濞呮洜鎹㈠┑瀣倞闁靛ě鍐ㄧ疄闂備浇顕ч崙鐣岀礊閸℃稑纾婚柛娑卞幘閺嗭妇鈧厜鍋撻柛鏇ㄥ墰閸樻悂鏌ｈ箛鏇炰户闁稿鎸剧划鍫⑩偓锝庡厴閸嬫挾鎲撮崟顒傤槰闂佺楠搁妶姝屾闂佸憡鐟ラˇ顓熺濠婂牏鍙撻柛銉╊棑閸掔増顨ラ悙瀵稿⒌闁哄苯绉堕幉鎾礋椤愩倓绱濋梻浣规偠閸斿秹宕曢幎鑺ュ仼闁绘垼妫勯悙濠勬喐韫囨稑鍌ㄥù鐘差儐閻撶喖骞栧ǎ顒€鈧倕顭囬幇顓犵闁圭粯甯炵粻鑽も偓瑙勬礃缁诲牓鐛€ｎ喗鏅滈悶娑掆偓鍏呭婵炶揪缍€閸婁粙鎮㈤悜妯轰粧闂佹枼鏅涢崰姘焽閺冣偓缁绘繄鍠婂Ο娲绘綉闂佹悶鍔嶆繛濠囩嵁婵犲洤绠婚柛鎴炴緲椤︻垶锝炲┑瀣殝缁剧増蓱鐎氳棄鈹戦悙鑸靛涧缂佹彃娼￠垾锕傚醇閵夊顦崇粻娑樷槈濞嗘垵骞楅梻浣筋潐閸庡磭澹曢鐔告珷闁挎棃鏁崑鎾舵喆閸曨剛顦ㄥ┑鐘灪閿曘垽鏁愰悙娴嬫斀闁割偆鍠庣壕顖炴⒑缂佹ê鐏︽い顓炵墢濞嗐垽濮€閵堝棌鎷绘繛杈剧悼閹虫捇顢氬鍕闁告瑥顦辨晶鐢碘偓瑙勬礃閸旀﹢濡甸幇鏉跨闁规儳鍘栫花鐢告⒒娓氣偓濞佳嚶ㄩ埀顒€鈹戦垾铏窛鐎殿啫鍥х劦妞ゆ帒瀚埛鎺楁煕鐏炵偓鐨戝褎绋撶槐鎺斺偓锝庡亜濞搭噣鏌℃担鍝バ㈡い鎾炽偢瀹曢亶寮撮悩鐢垫毎闂傚倷绀佸﹢杈╁垝椤栨粍鏆滄俊銈呮噹缁犵喖鏌涘┑鍕姢缁炬儳銈稿鍫曞醇濞戞ê顬堝┑鐐存儗閸ｏ綁寮诲☉銏犵厴闁诡垎鍌氼棜婵犵绱曢崑鎴﹀磹閺嶎偅鏆滈柟鐑樻煛閸嬫挸顫濋悡搴☆潾缂備緡鍠栭悧鎾崇暦濮椻偓閸╃偞寰勯崫銉ф晨闂傚倷绀侀幖顐﹀疮椤愶箑纾归柣鐔稿閺嬪秹鏌￠崶銉ョ仾闁绘挻娲橀幈銊ノ旈埀顒€螞閺冨倽濮抽柤娴嬫櫇绾捐偐绱撴担璇＄劷缂佺姷鍋ら弻鐔碱敊閼姐倗鐓撳┑鈽嗗亜閸燁偊鍩ユ径鎰闁瑰瓨绮岄ˉ姘繆閻愵亜鈧牠鎮у鍫濈；婵炴垶姘ㄩ惌鍡涙煕閹般劍鏉哄ù婊勭矋閵囧嫰骞樼捄鐑樼€婚梺璇查獜缂嶄線寮婚敐澶娢ㄦい鏃囧吹閸戣绻?
		StringBuilder result = new StringBuilder();
		result.append("<div style=\"text-align:center;font-size:medium; font-weight:bold;\">").append(prefix).append("</div>");

		for (int i = 0; i < divContents.size() - 1; i++) {
			result.append("<div style=\"text-align:center;\">").append(divContents.get(i)).append("</div>");
		}

		return result.toString();
	}

	private boolean isContentValidAndProductCheck(HttpServletRequest request, String posStr, String description, Person person) {
		Object need_calculate_price = request.getSession().getAttribute("need_calculate_price");
		if ((posStr.startsWith("html_service_") || posStr.startsWith("service_") || posStr.startsWith("product_")) && posStr.endsWith("_description")) {
			String moneyLetter = (String) session.getAttribute("moneyLetter");
			if (moneyLetter == null)
				moneyLetter = "$";
			String[] moneyLetters = moneyLetter.split(",");
			String productName = taoUtil.fetchProductName(description, moneyLetters, request.getSession());
			if (StringUtils.isBlank(productName)) {
				return false;
			}
			//price check
			String payCondition = "";
			String form1Value = (String)request.getSession().getAttribute(taoUtil.INFO_COLLECT_FORM_CUST_PAGE_PATH);
			if(CC.TRUE.equals(need_calculate_price)) {
				payCondition = taoUtil.fetchProductPrice(description, moneyLetters);
				//if it not html_service, for sure need to check. 
				//if it's html_service but didn't set form1, also need check.
				//if form1 is set, while current html_service is not under that location
				//then need check.
				if(!posStr.startsWith("html_service_") || form1Value == null || form1Value.length() == 0
						|| !("menu" + posStr.substring(CC.HTML_SERVICE.length())).startsWith(form1Value)){
					
					try {
						Float.valueOf(payCondition);
					} catch (Exception e) {
						return false;
					}
				}
			}

			// if it's valid, then update service and product record.
			int p = posStr.indexOf("service_");
			if (p > -1) {
				posStr = posStr.substring(p + 8);
				if (posStr.endsWith("_description")) {
					posStr = posStr.substring(0, posStr.length() - 12);
				}
				Service service = serviceService.findServiceByCatalogAndPerson(posStr, person);
				if (service == null) {
					service = new Service();
					service.setC1(posStr);
					service.setRecordStatus(Integer.valueOf(posStr.substring(posStr.lastIndexOf("_") + 1)));
					service.setPerson(person);
				}
				service.setName(productName);

				if (containsMoneyInfo(description, moneyLetters)) { // if description not containning the money letter,
																	// then don't change the price, to make it possible
																	// that we set the price with money letter, then
																	// modify the
					service.setDescription(payCondition); // description with something without money letter.
				}else {
					if(form1Value != null && form1Value.length() > 0 && ("menu_" + posStr).startsWith(form1Value)){
						if(!"0".equals(payCondition)) {
							service.setDescription(payCondition); // description with something without money letter.
						}
					}
				}
				serviceService.persist(service);
			} else if ((p = posStr.indexOf("product_")) > -1) {
				posStr = posStr.substring(p + 8);
				if (posStr.endsWith("_description")) {
					posStr = posStr.substring(0, posStr.length() - 12);
				}
				Product product = productService.findProductByCatalogAndPerson(posStr.substring(8), person);
				if (product == null) {
					product = new Product();
					product.setC1(posStr.substring(8));
					product.setPerson(person);
				}
				product.setName(productName);
				product.setDescription(payCondition);
				productService.persist(product);
			}
		}
		return true;
	}

    private StructuredServiceEdit parseStructuredServiceEdit(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        String[] parts = content.split("/");
        if (parts.length != 5) {
            return null;
        }

        StructuredServiceEdit edit = new StructuredServiceEdit();
        String namePart = parts[0].trim();
        if (StringUtils.isNotBlank(namePart) && !"-".equals(namePart)) {
            edit.name = namePart;
        }
        edit.price = parseEditableCentValue(parts[1]);
        edit.cost = parseEditableCentValue(parts[2]);
        edit.stock = parseEditableIntegerValue(parts[3]);
        edit.weeklySalesQty = parseEditableIntegerValue(parts[4]);
        edit.structured = true;
        return edit;
    }

    private Integer parseEditableCentValue(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }
        try {
            double value = Double.parseDouble(normalized);
            if (value < 0) {
                return null;
            }
            return (int) Math.round(value * 100.0d);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseEditableIntegerValue(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }
        try {
            int value = Integer.parseInt(normalized);
            return value < 0 ? null : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String applyStructuredServiceEditIfNeeded(String posStr, String content, Person person) {
        if (person == null || StringUtils.isBlank(content)) {
            return content;
        }
        if (!posStr.startsWith("service_") || !posStr.endsWith("_description")) {
            return content;
        }

        StructuredServiceEdit edit = parseStructuredServiceEdit(content);
        if (edit == null || !edit.structured) {
            return content;
        }

        String catalog = posStr.substring(8, posStr.length() - 12);
        Service service = serviceService.findServiceByCatalogAndPerson(catalog, person);
        if (service == null) {
            return content;
        }

        if (StringUtils.isNotBlank(edit.name)) {
            service.setName(edit.name.trim());
        }
        if (edit.price != null) {
            service.setPrice(edit.price);
        }
        if (edit.cost != null) {
            service.setCost(edit.cost);
        }
        if (edit.stock != null) {
            service.setStock(edit.stock);
        }
        if (edit.weeklySalesQty != null) {
            service.setWeeklySalesQty(edit.weeklySalesQty);
        }
        serviceService.saveService(service);
        serviceService.updateRemotePosProduct(service.getC2(), person, edit.name, edit.price, edit.cost, edit.stock, edit.weeklySalesQty);

        if (StringUtils.isNotBlank(edit.name)) {
            return edit.name.trim();
        }
        return StringUtils.defaultIfBlank(service.getName(), content);
    }

    private static class StructuredServiceEdit {
        private boolean structured;
        private String name;
        private Integer price;
        private Integer cost;
        private Integer stock;
        private Integer weeklySalesQty;
    }

    private boolean containsMoneyInfo(String description, String[] moneyLetters) {
		for (String string : moneyLetters) {
			int p = description.indexOf(string);
			if (p > -1) {
				string = description.substring(p + 1);
				string = taoUtil.trimContentFromHTML(string);
				try {
					Float.valueOf(string);
					return true;
				} catch (Exception e) {
					continue;
				}
			}
		}

		return false;
	}

	/*
	 * currently only menu, and footer need to update common text.
	 */
	private void checkIfDirtFlagNeeded(String key) {
		if (key.startsWith("menu_") || key.startsWith("footer_") || key.startsWith("text_footContent")) {
			dirtFlagCommonText = true;
		}
	}

	// ==============changing images on page===================
	@RequestMapping(value = "/{client}/getImage/{id}")
	public void getImage(@PathVariable("client") String client, @PathVariable("id") String id,
			HttpServletRequest request, HttpServletResponse response) {
		TaoDebug.info(request, "start to getImage, client : {}, id:{}, person:{}, path:{}", client, id);
		// specially for some explore like safari on iphone........
		// TODO:may be can remove now, we use taoutil.getCurPerson now.
		if (request.getSession().getAttribute(CC.CLIENT) == null) {
			Person person = personService.findPersonByName(client);
			request.getSession().setAttribute(CC.CLIENT, person);
		}
		requestForImage(id, request, response);
	}

	@RequestMapping(value = "/getImage")
	public void getImage(HttpServletRequest request, HttpServletResponse response) {
		Person person = taoUtil.getCurPerson(request);
		if (person == null) {
			TaoDebug.info(request, "***Error***caught a 'getImage' request, person is null, person is:{}, path:{}");
		} else {
			TaoDebug.info(request, "***Error***caught a 'getImage' request, person is {}, path:{}");
		}
		requestForImage("logo", request, response);
	}

	@RequestMapping(value = "/uncaughtException")
	public void uncaughtException(HttpServletRequest request) {
		TaoDebug.info(request, "***Error***caught a 'uncaughtException', person : {}, path{}");
	}

	@RequestMapping(value = "/getImage/{filePath}")
	public void requestForImage(@PathVariable("filePath") String filePath, HttpServletRequest request,
			HttpServletResponse response) {

		Person person = taoUtil.getCurPerson(request);
		if (person == null) {
			TaoDebug.info(request, "***Error***unexpected non client in getImage! person:{} ,path:{}");
			return;
		}
		String originalFilePath = filePath;
		filePath = taoUtil.isLangPrfNeededForMedia(request, filePath)
				? taoUtil.getLangPrfWithDefault(request) + filePath
				: filePath;
		TaoDebug.info(TaoDebug.getSB(request.getSession()),
				"start to findMediaByKeyAndPerson, person : {}, filePath:{}, path:{}", person, filePath,
				request.getContextPath());
		MediaUpload mediaUpload = findMediaByDisplayKey(originalFilePath, filePath, person);
		byte[] imageBytes = null;
		try {
			imageBytes = readImageBytes(person, mediaUpload);

			if (imageBytes != null) {
				response.getOutputStream().write(imageBytes);
				response.getOutputStream().flush();

			} else {
				if(filePath.startsWith("html_")) {
					//for html, need to use empty line, because if use point, it will be enlarged according to the page width.
					imageBytes = taoUtil.EMPTY_LINE;
					response.getOutputStream().write(imageBytes);
					response.getOutputStream().flush();
				}else if(filePath.startsWith("service_")) {
					imageBytes = resolveSharedBarcodeImageBytes(originalFilePath, person, request);
					if (imageBytes != null) {
						response.getOutputStream().write(imageBytes);
						response.getOutputStream().flush();
						return;
					}

					//for the case that html_service was included into the feature, all the image key will be same and pointing to html_menukey
					filePath = filePath.substring(8);
					if(filePath.endsWith("_thum")) {
						filePath = filePath.substring(0, filePath.length() - 5);
					}
					int p = filePath.lastIndexOf("_");
					mediaUpload = mediaUploadService.findMediaByKeyAndPerson("html_" + filePath.substring(0, p), person);
					imageBytes = readImageBytes(person, mediaUpload);
					if(imageBytes != null) {
						response.getOutputStream().write(imageBytes);
						response.getOutputStream().flush();
					}else {
						//for service, need to find a empty image with given size. if there's one under this person, select it first, 
						//otherwise use for_demo's, otherwise use default nomatter size good or not.
						mediaUpload = mediaUploadService.findMediaByKeyAndPerson("defaultservice", person);
						imageBytes = readImageBytes(person, mediaUpload);
						if (imageBytes != null) {
							response.getOutputStream().write(imageBytes);
							response.getOutputStream().flush();
						}else {
							mediaUpload = mediaUploadService.findMediaByKeyAndPerson("defaultservice", personService.getAdam());
							imageBytes = readImageBytes(personService.getAdam(), mediaUpload);
							if (imageBytes != null) {
								response.getOutputStream().write(imageBytes);
								response.getOutputStream().flush();
							}
						}
					}
				}else {
					mediaUpload = mediaUploadService.findMediaByKeyAndPerson(filePath, personService.getEve());
					imageBytes = readImageBytes(personService.getEve(), mediaUpload);
					if (imageBytes == null) {
						imageBytes = taoUtil.EMPTY_POINT;
					}
					response.getOutputStream().write(imageBytes);
					response.getOutputStream().flush();
				}
			}
		} catch (Exception e) {
			System.out.println("[MainController]Exception occurred when fetching img of ID:" + filePath + "! (pid:" + person.getId() + " ) "  + e);
		}
	}
	private MediaUpload findMediaByDisplayKey(String originalFilePath, String localizedFilePath, Person person) {
		MediaUpload mediaUpload = mediaUploadService.findMediaByKeyAndPerson(localizedFilePath, person);
		if (mediaUpload != null) {
			return mediaUpload;
		}

		if (!StringUtils.equals(localizedFilePath, originalFilePath)) {
			mediaUpload = mediaUploadService.findMediaByKeyAndPerson(originalFilePath, person);
			if (mediaUpload != null) {
				return mediaUpload;
			}
		}

		return null;
	}

	private byte[] resolveSharedBarcodeImageBytes(String filePath, Person person, HttpServletRequest request) throws IOException {
		if (StringUtils.isBlank(filePath) || !filePath.startsWith("service_")) {
			return null;
		}

		boolean thumbRequested = filePath.endsWith("_thum");
		String catalog = thumbRequested ? filePath.substring(0, filePath.length() - 5) : filePath;
		Service service = serviceService.findServiceByCatalogAndPerson(catalog, person);
		if (service == null || StringUtils.isBlank(service.getC2())) {
			return null;
		}

		String barcodeKey = service.getC2().trim();
		File sharedFile = mediaUploadService.findSharedProductImageFile(barcodeKey, thumbRequested);
		if (sharedFile != null && sharedFile.exists()) {
			return Files.readAllBytes(sharedFile.toPath());
		}
		if (!thumbRequested) {
			return null;
		}

		File originalFile = mediaUploadService.findSharedProductImageFile(barcodeKey, false);
		if (originalFile == null || !originalFile.exists()) {
			return null;
		}

		return buildSharedServiceThumbnailBytes(originalFile, request);
	}

	private byte[] buildSharedServiceThumbnailBytes(File originalFile, HttpServletRequest request) throws IOException {
		BufferedImage originalImage = ImageIO.read(originalFile);
		if (originalImage == null) {
			return null;
		}

		int targetWidth = resolveIntSessionValue(request, "img_service_thum_w", 144);
		int targetHeight = resolveIntSessionValue(request, "img_service_thum_h", 192);
		double scale = Math.min((double) targetWidth / Math.max(1, originalImage.getWidth()),
				(double) targetHeight / Math.max(1, originalImage.getHeight()));
		int drawWidth = Math.max(1, (int) Math.round(originalImage.getWidth() * scale));
		int drawHeight = Math.max(1, (int) Math.round(originalImage.getHeight() * scale));
		int offsetX = Math.max(0, (targetWidth - drawWidth) / 2);
		int offsetY = Math.max(0, (targetHeight - drawHeight) / 2);

		BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D graphics = canvas.createGraphics();
		try {
			graphics.setColor(java.awt.Color.WHITE);
			graphics.fillRect(0, 0, targetWidth, targetHeight);
			graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
					java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
					java.awt.RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
					java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(originalImage, offsetX, offsetY, drawWidth, drawHeight, null);
		} finally {
			graphics.dispose();
		}

		return TaoImage.getByteFormateImage(canvas, ".png");
	}

	private int resolveIntSessionValue(HttpServletRequest request, String key, int defaultValue) {
		if (request == null || request.getSession(false) == null) {
			return defaultValue;
		}
		Object value = request.getSession(false).getAttribute(key);
		if (value == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(String.valueOf(value).trim());
		} catch (Exception ignore) {
			return defaultValue;
		}
	}

	private byte[] readImageBytes(Person person, MediaUpload mediaUpload) throws IOException {
		if (mediaUpload == null) {
			return null;
		}

		if (mediaUpload.getContent() != null) {
			return mediaUpload.getContent();
		}

		String diskFilePath = mediaUploadService.generateFilePathForPerson(person, mediaUpload.getFilepath(),
				mediaUpload.getContentType());
		File diskFile = new File(diskFilePath);
		if (diskFile.exists()) {
			return Files.readAllBytes(diskFile.toPath());
		}

		return null;
	}

	@RequestMapping(value = "/changeImgForm", method = RequestMethod.POST)
	public String changeImgForm(Model model, HttpServletRequest request, HttpServletResponse response) {
		model.addAttribute("position", request.getParameter("position"));
		model.addAttribute("imageIndex", request.getParameter("imageIndex"));
		model.addAttribute("operation", request.getParameter("operation"));

		if (StringUtils.isBlank(request.getParameter("operation"))) {
			model.addAttribute("form_url_img", CC.singleUploadUri);
		} else {
			model.addAttribute("form_url_img", CC.multiUploadUri);
		}

		model.addAttribute("mediaUpload", new MediaUpload());
		// return index(model, request);
		return buildPageForMenu(model, request, response, null);
	}

	// for under security frame work apps, including simonstyle
	/**
	 * 
	 * @param request
	 * @param response
	 * @param version
	 * @param personName
	 *            when client side is running as server, it will use person name as
	 *            label when communicating with test server.
	 * @param message
	 * @param time
	 * @return
	 */
	@RequestMapping(value = "/security")
	@ResponseBody
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML, "application/zip",
			MediaType.APPLICATION_OCTET_STREAM })
	public ResponseEntity<String> security(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("version") final String version, @RequestParam("hostId") final String personName,
			@RequestParam("label") final String label, @RequestParam("message") final String message,
			@RequestParam("time") String time) {

		// prepare the header for return;
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json;charset=UTF-8");

		// make sure Security user exist.
		String remoteIp = request.getRemoteAddr().replace(".", "_");
		String name = personName.equals(CC.ADAM) ? personName // remoteIp +
				: personName.substring(personName.indexOf("_") + 1);
		Person person = makeSurePersonExist(name, remoteIp);

		// get the submitDate ready for use
		Date date = null;

		MediaUpload mediaUpload = null;
		if ("exportImage".equals(version)) {
			mediaUpload = mediaUploadService.findMediaByKeyAndPerson(label, person);
		} else {
			if (version == null || version.equals("-1")) {
				date = new Date(new Long(1));// if date not set yet, means not changed any thing, then make it super
												// early.
			} else {
				date = new Date(Long.valueOf(version));
				if (needToSendEmail(label)) {
					try {
						String password = person.getPassword();
						if (password.split("_").length != 4) { // currently we use ip as password, so do not send out
																// alarm email for now.
							// we use the email as the account's password.
							String managerEmail = TaoEncrypt.decrypt(password, "dmfsJiaJdwz=", 1);
							if (managerEmail != null && managerEmail.contains("@") && managerEmail.contains(".")) {
								TaoEmail.sendMessage("info@ShareTheGoodOnes.com", "Security Alarm!", managerEmail,
										message, null);
							}
						}
					} catch (Exception eee) {
						System.out.println("Exception when sending email for client :" + personName);
					}
				}
			}

			mediaUpload = mediaUploadService.findMediaByKeyAndPerson("system_security_monitor", person);
		}

		// check if the mediaUpload exists
		if (mediaUpload != null && mediaUpload.getContent() != null) {
			// if it's backing up images, there's no submit date. only submitted document
			// has submit date.
			if (mediaUpload.getSubmitDate() == null || (date != null && mediaUpload.getSubmitDate().after(date))) { // downloading
				try {
					InputStream inputStream = new ByteArrayInputStream(mediaUpload.getContent());
					InputStreamResource inputStreamResource = new InputStreamResource(inputStream);
					headers.setContentLength(inputStream.available());
					return new ResponseEntity(inputStreamResource, headers, HttpStatus.OK);
				} catch (Exception e) {
					TaoDebug.error("error happened when reading content into a String", e);
				}
			}
		}

		return new ResponseEntity<String>("", headers, HttpStatus.OK);
	}

	private boolean needToSendEmail(String label) {
		return label.contains(ALARM);
	}

	// ===================for JustPrint Client================
	@RequestMapping(value = "/activeJustPrintAccount", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> activeJustPrintAccount(@RequestBody String content) {

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");

		Person person = makeSurePersonExist("JustPrint", "asdf");
		Customize customize = makeSureSNexist(person);// Customize.findCustomizeByKeyAndPerson(SN, person);
		String snsValue = customize.getCusValue();
		String[] SNs = StringUtils.split(snsValue, ',');

		String contentFR = "0";
		// add the new remark base on content in param: {"username":"asdfas,StoreName"}
		if (content != null && content.length() > 0) {
			int p = content.indexOf("\"username\"");
			if (p > -1) {
				int startP = p + 12;
				p = content.indexOf("}");
				if (p > -1) {
					int endP = p - 1;
					content = content.substring(startP, endP); // get out "asdfas,StoreName-username"
					p = content.indexOf(',');
					if (p > -1) {
						String snStr = content.substring(0, p); // get out sn
						String storeName = content.substring(p + 1); // get out storname-username

						UserAccount userAccount = getAnUserAnyway(person, storeName); // get out the userAccout of
																						// person JustPrint
						String snInAccount = userAccount.getCel();// SN
						if (snInAccount == null) {
							for (String sn : SNs) {
								if (sn.equals(snStr)) {
									contentFR = userAccount.getTel(); // left time
									userAccount.setCel(sn); // activate code
									userAccount.setFax(String.valueOf(new Date().getTime())); // activated time
									userAccountService.persist(userAccount);
									break;
								}
							}
						} else if (snInAccount.equals(snStr)) {
							contentFR = userAccount.getTel(); // left time
							userAccount.setFax(String.valueOf(new Date().getTime())); // activate time
							userAccountService.persist(userAccount);
						}
					}
				}

			}
		}

		if (contentFR == null || contentFR.length() == 0) {
			contentFR = "34560000000";// 400days=400*1000*3600*24=34,560,000,000
		}
		return new ResponseEntity<String>(contentFR, headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/syncJustPrintDb", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> syncJustPrintDb(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("filepath") String filepath, @RequestParam("submitDate") String submitDate,
			@RequestBody String strContent) {

		// prepare the header for return;
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json;charset=UTF-8");

		// get content uploaded for use.
		byte[] content;
		try {
			request.getInputStream();
			content = strContent.getBytes("UTF-8");
		} catch (Exception e) {
			return new ResponseEntity<String>("", headers, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		// get the submitDate ready for use
		Date date = null;
		if (submitDate != null && submitDate.length() > 1) {
			try {
				date = new Date(Long.valueOf(submitDate));
			} catch (Exception e) {
				// TaoDebug.error("the submitDate from justprint when synchronizing db is not
				// correct:{}", submitDate);
			}
		}

		// make sure JustPrint user exist.
		Person person = makeSurePersonExist("JustPrint", "asdf");

		// check if the mediaUpload exists
		MediaUpload mediaUpload = null;
		mediaUpload = mediaUploadService.findMediaByKeyAndPerson(filepath, person);
		if (mediaUpload == null) {
			mediaUpload = new MediaUpload();
			mediaUpload.setFilepath(filepath);
			mediaUpload.setPerson(person);
			mediaUpload.setContent(content);
			mediaUpload.setFilesize(content.length);
			mediaUpload.setSubmitDate(new Date());
			UserAccount userAccount = userAccountService.findUserAccountByName("system*" + person.getId());
			if (userAccount != null)
				mediaUpload.setAudient(userAccount);
			mediaUploadService.persist(mediaUpload);
		} else {
			if (date != null) {// is an uploading
				// add a check, incase it's replaced by mistake: if existing backup is very
				// small or the new committed content must be
				// big enough.
				if (content.length < 100000 && mediaUpload.getFilesize() > 120000) {
					TaoEmail.sendMessage("info@ShareTheGoodOnes.com", "JustPrint Sync Alarm!", "tao.mtl@hotmail.com",
							mediaUploadService.toJson(mediaUpload), null);

					mediaUpload = new MediaUpload();
					mediaUpload.setFilepath(filepath);
					mediaUpload.setPerson(person);
					mediaUpload.setContent(content);
					mediaUpload.setFilesize(content.length);
					mediaUpload.setSubmitDate(new Date());
					UserAccount userAccount = userAccountService.findUserAccountByName("system*for_demo");
					if (userAccount != null)
						mediaUpload.setAudient(userAccount);
					mediaUploadService.persist(mediaUpload);

					return new ResponseEntity<String>("", headers, HttpStatus.LENGTH_REQUIRED);
				}
				mediaUpload.setContent(content);
				mediaUpload.setSubmitDate(date);
				mediaUpload.setFilesize(content.length);
				mediaUploadService.persist(mediaUpload);
			} else { // downloading
				String contentFR = null;
				try {
					contentFR = new String(mediaUpload.getContent(), "UTF-8");
				} catch (Exception e) {
					TaoDebug.error("error happened when reading content into a String", e);
				}
				return new ResponseEntity<String>(contentFR, headers, HttpStatus.OK);
			}
		}

		return new ResponseEntity<String>("", headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/uploadDb", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> uploadDb(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("filepath") String filepath, @RequestParam("submitDate") String submitDate,
			@RequestBody String strContent) {

		// prepare the header for return;
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json;charset=UTF-8");

		// get content uploaded for use.
		byte[] content;
		try {
			request.getInputStream();
			content = strContent.getBytes("UTF-8");
		} catch (Exception e) {
			return new ResponseEntity<String>("", headers, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		// get the submitDate ready for use
		Date date = null;
		if (submitDate != null && submitDate.length() > 1) {
			try {
				date = new Date(Long.valueOf(submitDate));
			} catch (Exception e) {
				TaoDebug.error("the submitDate from justprint when synchronizing db is not correct:{}", submitDate);
				date = new Date();
			}
		}else {
			date = new Date();
		}

		// make sure JustPrint user exist.
		Person person = makeSurePersonExist("JustPrint", "asdf");

		// check if the mediaUpload exists
		MediaUpload mediaUpload = null;
		mediaUpload = mediaUploadService.findMediaByKeyAndPerson(filepath, person);
		if (mediaUpload == null) {
			mediaUpload = new MediaUpload();
			mediaUpload.setFilepath(filepath);
			mediaUpload.setPerson(person);
			mediaUpload.setContent(content);
			mediaUpload.setFilesize(content.length);
			mediaUpload.setSubmitDate(date);
			UserAccount userAccount = userAccountService.findUserAccountByName("system*" + person.getId());
			if (userAccount != null)
				mediaUpload.setAudient(userAccount);
			mediaUploadService.persist(mediaUpload);
		} else {
			mediaUpload.setContent(content);
			mediaUpload.setSubmitDate(date != null ? date : new Date());
			mediaUpload.setFilesize(content.length);
			mediaUploadService.persist(mediaUpload);
		}

		return new ResponseEntity<String>("", headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/downloadDb", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> downloadDb(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("filepath") String filepath, @RequestParam("submitDate") String submitDate,
			@RequestBody String strContent) {

		// prepare the header for return;
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json;charset=UTF-8");

		// get content uploaded for use.
		byte[] content;
		try {
			request.getInputStream();
			content = strContent.getBytes("UTF-8");
		} catch (Exception e) {
			return new ResponseEntity<String>("", headers, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		// make sure JustPrint user exist.
		Person person = makeSurePersonExist("JustPrint", "asdf");

		// check if the mediaUpload exists
		MediaUpload mediaUpload = null;
		mediaUpload = mediaUploadService.findMediaByKeyAndPerson(filepath, person);
		if (mediaUpload == null) {
			mediaUpload = new MediaUpload();
			mediaUpload.setFilepath(filepath);
			mediaUpload.setPerson(person);
			mediaUpload.setContent(content);
			mediaUpload.setFilesize(content.length);
			mediaUpload.setSubmitDate(new Date());
			UserAccount userAccount = userAccountService.findUserAccountByName("system*" + person.getId());
			if (userAccount != null)
				mediaUpload.setAudient(userAccount);
			mediaUploadService.persist(mediaUpload);
		}

		String contentFR = "";
		try {
			contentFR = new String(mediaUpload.getContent(), "UTF-8");
		} catch (Exception e) {
			TaoDebug.error("error happened when reading content into a String", e);
		}
		return new ResponseEntity<String>(contentFR, headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/uploadConfig", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> uploadConfig(@RequestBody String configString) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");

		JSONObject rjson = new JSONObject(configString);
		// prepare filepath and configuration
		String filepath = rjson.getString("filepath");
		String configuration = rjson.getString("configuration");
		try {
			configuration = URLDecoder.decode(configuration, "UTF-8");
		} catch (Exception e) {
			TaoDebug.error("CreateNewOrderAction", "exception when convert the orderContent into UTF-8", e);
		}

		// make sure JustPrint user exist.
		Person person = makeSurePersonExist("JustPrint", "asdf");

		// check if the mediaUpload exists
		MediaUpload mediaUpload = null;
		mediaUpload = mediaUploadService.findMediaByKeyAndPerson(filepath, person);
		if (mediaUpload == null) {
			mediaUpload = new MediaUpload();
			mediaUpload.setFilepath(filepath);
			mediaUpload.setPerson(person);
			mediaUpload.setContentType(configuration);
			mediaUpload.setSubmitDate(new Date());
			UserAccount userAccount = userAccountService.findUserAccountByName("system*" + person.getId());
			if (userAccount != null)
				mediaUpload.setAudient(userAccount);
			mediaUploadService.persist(mediaUpload);
		} else {
			mediaUpload.setContentType(configuration);
			mediaUploadService.persist(mediaUpload);
		}

		return new ResponseEntity<String>("OK", headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/downloadCofig", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> downloadCofig(@RequestBody String configString) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");

		JSONObject rjson = new JSONObject(configString);
		// prepare filepath and configuration
		String filepath = rjson.getString("filepath");
		String configuration = rjson.getString("configuration");
		try {
			configuration = URLDecoder.decode(configuration, "UTF-8");
		} catch (Exception e) {
			TaoDebug.error("CreateNewOrderAction", "exception when convert the orderContent into UTF-8", e);
		}

		// make sure JustPrint user exist.
		Person person = makeSurePersonExist("JustPrint", "asdf");

		// check if the mediaUpload exists
		MediaUpload mediaUpload = null;
		mediaUpload = mediaUploadService.findMediaByKeyAndPerson(filepath, person);
		if (mediaUpload == null) {
			mediaUpload = new MediaUpload();
			mediaUpload.setFilepath(filepath);
			mediaUpload.setPerson(person);
			mediaUpload.setContentType(configuration);
			mediaUpload.setSubmitDate(new Date());
			UserAccount userAccount = userAccountService.findUserAccountByName("system*" + person.getId());
			if (userAccount != null)
				mediaUpload.setAudient(userAccount);
			mediaUploadService.persist(mediaUpload);
		}

		String contentFR = "";
		try {
			contentFR = mediaUpload.getContentType();
		} catch (Exception e) {
			TaoDebug.error("error happened when reading content into a String", e);
		}
		return new ResponseEntity<String>(contentFR, headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/sendReport", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> sendEmail(@RequestBody String content) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");

		JSONObject rjson = new JSONObject(content);
		// prepare email and content
		String idx = rjson.getString("idx");
		String email = rjson.getString("email");
		String message = rjson.getString("content");
		try {
			message = URLDecoder.decode(message, "UTF-8");
		} catch (Exception e) {
			TaoDebug.error("sendEmail", "exception when convert the emailContent into UTF-8", e);
		}

		if (TaoEmail.isValidEmail(email)) {
			SimpleDateFormat df = new SimpleDateFormat(" MMM.dd HH:mm");
			StringBuilder subject = new StringBuilder(idx).append(df.format(new Date()));
			TaoEmail.sendMessageWithFlag("AikaPos.STGO@gmail.com", subject.toString(), email, message, null, false);
		}

		return new ResponseEntity<String>("", headers, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/cloudPrint", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> cloudPrint(@RequestBody String content, HttpServletRequest request) {

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");
		
		JSONObject rjson = new JSONObject(content);
		// prepare email and content
		String sn = rjson.getString("filepath");
		String message = rjson.getString("content");
		String width = rjson.getString("width");
		
		try {
			message = URLDecoder.decode(message, "UTF-8");
		} catch (Exception e) {
			TaoDebug.error("sendEmail", "exception when convert the emailContent into UTF-8", e);
		}

		boolean isLabelPrinter = isLabelPrinter(sn);	//temporally it's using a string to indicate which sns are label printer.
		if(isLabelPrinter) {							//should change to use a db table to save it for better performance.
			StringBuilder sb = new StringBuilder();
			String[] lines = message.split("\n");
			
			boolean isSettingSizeCommand = false;
			if(lines.length == 1) {
				int p = lines[0].indexOf(',');
				String w = lines[0].substring(0, p);
				String h = lines[0].substring(p + 1);
				try {
					Integer.valueOf(w);
					Integer.valueOf(h);
					isSettingSizeCommand = true;
				}catch(Exception e) {
					TaoDebug.error("changing label printer size", message + "," + sn );
					return new ResponseEntity<String>("", headers, HttpStatus.BAD_REQUEST);
				}
			}
			
			if(isSettingSizeCommand) {
				message = "<SIZE>" + message + "</SIZE>";//30,20
			}else {
				for(int i = 0; i < lines.length; i++) {
					sb.append("<TEXT x=\"5\" y=\"" + (15 + 30 * i)  + "\" font=\"12\" w=\"2\" h=\"2\" r=\"0\">" + taoUtil.addingEnterIntoContent(lines[i], width) + "</TEXT>");
				}
				message = sb.toString();
			}
		} else if(message.indexOf("REPORT") < 0){	//if it's report, don't make the font big.
			message = "<B>" + message + "</B>";
		}

//		TaoCloudPrint.sendToPrinterCloud(sn, "<SIZE>40,30</SIZE>", isLabelPrinter);
//		<DIRECTION>n</DIRECTION>
		TaoCloudPrint.getInstance().sendToPrinterCloud(sn, message, isLabelPrinter);

		return new ResponseEntity<String>("", headers, HttpStatus.OK);
	}

	private boolean isLabelPrinter(String sn) {
		Customize customize = customizeService.findCustomizeByKeyAndPerson(CC.LabelPrinter, personService.getAdam());
		if(customize == null) {
			return false;
		}
		String labelPrinters = customize.getCusValue();
		return labelPrinters != null && ((String)labelPrinters).contains(sn);
	}

	//if I use GET as request method, the request from Android can not reach, don't know why yet, might because there's @RequestBody String content, or the headers = "Accept=application/json"
	//TODO: the main orders should contains the person info, so the public sms phone can know if sending sms for who, and later can update the order status with it.
	@RequestMapping(value = "/requestNewOrders/{storeName}/{startTime}", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> requestNewOrders(
			@PathVariable("storeName") String storeName,
			@PathVariable("startTime") String lastProcessedID,
			@RequestBody String content) {

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");
		
		Person person = personService.findPersonByName(storeName);
		//TODO verify the password of the person.
		
		//check out the customization of this guy, to find out a configuration which indicates which person accounts this guy cares about.
		Customize customize = customizeService.findCustomizeByKeyAndPerson(CC.SMSList, person);
		if(customize != null) {
			Object value = customize.getCusValue();
			if(value != null) {
				String personsStr = value.toString();
				if(personsStr.length() > 0) {
					//by now, we know this guy do have a valid configuration about the account list, so, convert the name list into id list.
					String[] names = personsStr.split(",");
					Set<Person> persons = new HashSet<Person>();
					for (String string : names) {
						Person p = personService.findPersonByName(string.trim());
						if(p != null) {
							persons.add(p);
						}
					}
					
					String orders = prepareNewOrderJsonString(
							mainOrderService.findNewMainOrdersOfToday(lastProcessedID, persons));
					return new ResponseEntity<String>(orders, headers, HttpStatus.OK);
				}
			}
		}
		
		return new ResponseEntity<String>(null, headers, HttpStatus.OK);
	}
	


	// ===================for SanjiPos Client================
	@RequestMapping(value = "/activeAccount", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> activeAccount(@RequestBody String content) {

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");
		
		// in AikaPos's account, the Customized are full of the password and the Head info of the each JP user. 
		//the key is the password and generally the date of sign.
		Person person = makeSurePersonExist("AikaPos", "asdf");

		String contentFR = "0_";
		contentFR = getHeadInfoString(content, person, contentFR);

		if (contentFR == null) {
			contentFR = "400";// 400days=400*1000*3600*24=34,560,000,000
		}
		return new ResponseEntity<String>(contentFR, headers, HttpStatus.OK);
	}

	private String getHeadInfoString(String content, Person person, String contentFR) {
		// add the new remark base on content in param: {"username":"asdfas,StoreName"}
		if (content != null && content.length() > 0) {
			int p = content.indexOf("\"licence\"");
			if (p > -1) {
				int startP = p + 11;
				p = content.indexOf("}");
				if (p > -1) {
					int endP = p - 1;
					content = content.substring(startP, endP); // get out "licence=asdfasdfasd"
					try {
						Customize customize = customizeService.findCustomizeByKeyAndPerson(content, person);
						contentFR = customize.getCusValue();
						int positionOfActivatedTimes = contentFR.lastIndexOf("_") + 1;
						String activedTimes = contentFR.substring(positionOfActivatedTimes);
						if(activedTimes.length() <= 3) {
							int i = Integer.getInteger(activedTimes);
							customize.setCusValue(contentFR.substring(0, positionOfActivatedTimes) + String.valueOf(i++));
						}else {		
							//when we created headerinfo for user, we don't put a number, until the first time activated, it will be updated to 1 (indicates that sanjiPos installed)
							customize.setCusValue(contentFR + "_1");
						}
						customizeService.persist(customize);
						contentFR = customize.getCusValue();
					} catch (Exception e) {
						TaoDebug.warn(new StringBuilder(content),
								"licence number do not have a matched information in db yet.", "activeAccount");
					}
				}
			}
		}
		return contentFR;
	}
	

	//for AikaPos to get latest orders to send to printers and mev
	//content in accountInfo must be: <username>:<asdfas>
	//must remember to send request to update the status, other wise next request will get the request again.
	@RequestMapping(value = "/{storeName}/requestNewOrders", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> requestNewOrders(@PathVariable("storeName") String storeName,
			@RequestBody String accountInfo) {

		Person person = makeSurePersonExist("AikaPos", "asdf");
		String headInfo = "";
		headInfo = getHeadInfoString(accountInfo, person, headInfo);
		if (!headInfo.contains(storeName)) {
			return null;
		}

		// header
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");

		// mainOrder
		List<MainOrder> mainOrders = mainOrderService.findMainOrdersByStatusLimitationAndPerson(CC.STATUS_CHANGED_PLACE,
				personService.findPersonByName(storeName), "DESC");
		
		if (mainOrders == null)
			mainOrders = new ArrayList<MainOrder>();
		String jsonStr = prepareNewOrderJsonString(mainOrders);
		
		// last step: modify the satatus of these mainOrders, so they will not be
		// request by pos in a period of time. Just
		// in case client side send an other request before the first return has been
		// processed successfully.
		for (MainOrder mainOrder : mainOrders) {
			mainOrder.setRecordStatus(CC.STATUS_TO_PRINT);
			mainOrderService.persist(mainOrder);
		}

		return new ResponseEntity<String>(jsonStr, headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/synchronizeFromServer", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> synchronizeFromServer(@RequestBody String accountInfo) {

		Person person = TaoDbUtil.verifyAccountInfo(accountInfo);
		if (person == null) {
			return null;
		}

		// header
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");

		// prepare the json String.
		Collection<String> collection = new ArrayList<String>();

		// useraccounts
		List<UserAccount> userAccounts = userAccountService.findAllUserAccountsByPerson(person);
		if (userAccounts == null)
			userAccounts = new ArrayList<UserAccount>();
		String tUserAccountJsonAryStr = UserAccount.toJsonArray(userAccounts);
		collection.add(tUserAccountJsonAryStr);

		// service
		List<Service> services = serviceService.findServiceByPerson(person);
		if (services == null)
			services = new ArrayList<Service>();
		String tServicesJsonAryStr = Service.toJsonArray(services);
		collection.add(tServicesJsonAryStr);

		// textContent
		List<TextContent> textContents = textContentService.findAllMatchedTextContents("%", null, person);
		if (textContents == null)
			textContents = new ArrayList<TextContent>();
		String tTextContentJsonAryStr = TextContent.toJsonArray(textContents);
		collection.add(tTextContentJsonAryStr);

		// return
		String jsonStr = new JSONSerializer().exclude("*.class").serialize(collection);
		return new ResponseEntity<String>(jsonStr, headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/{storeName}/updateMainOrderStatus/{mainOrdeID}/{status}", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> updateMainOrderStatus(@PathVariable("storeName") String storeName,
			@PathVariable("mainOrdeID") Long mainOrdeID, @PathVariable("status") int status, // could be 20--printed,
																								// 50--served, -1--paid
			@RequestBody String accountInfo) {

		Person person = makeSurePersonExist("AikaPos", "asdf");
		String headInfo = "";
		headInfo = getHeadInfoString(accountInfo, person, headInfo);
		if (!headInfo.contains(storeName)) {
			return null;
		}
		// verify pass!---------------------

		// header
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");

		MainOrder mainOrder = mainOrderService.findMainOrder(mainOrdeID);
		mainOrder.setRecordStatus(status);
		mainOrderService.persist(mainOrder);

		// return
		return new ResponseEntity<String>(headers, HttpStatus.OK);
	}

	private UserAccount getAnUserAnyway(Person person, String name) {
		name = name + "*" + person.getId();
		UserAccount userAccount = userAccountService.findUserAccountByName(name);
		if (userAccount == null) {
			userAccount = new UserAccount();
			userAccount.setPerson(person);
			userAccount.setLoginname(name);
			userAccount.setPassword("Byiz2GrdTDE=");
			userAccount.setTel(null); // Time left
			userAccount.setFax(null); // registered time.
			userAccount.setCel(null); // SN
		}
		return userAccount;
	}

	private Person makeSurePersonExist(String name, String password) {

		Person person = personService.findPersonByName(name);
		if (person == null) {
			person = new Person();
			person.setName(name);
			person.setPassword(password);
			personService.persist(person);

			UserAccount userAccount = new UserAccount();
			userAccount.setPerson(person);
			userAccount.setLoginname("system*" + person.getId());
			userAccount.setPassword(TaoEncrypt.encryptPassword("asdf"));
			userAccount.setSecuritylevel("ROLE_EMPLOYEE");
			userAccountService.persist(userAccount);

			Customize customize = new Customize();
			customize.setPerson(person);
			customize.setCusKey("app_ContentManager");
			customize.setCusValue("system");
			customizeService.persist(customize);

			styleUtil.generateTextContent("en_menu_1", "ABOUT " + person.getName().toUpperCase(), person);
		}
		return person;
	}
	// SanjiPos end---------------
	
	private String prepareNewOrderJsonString(List<MainOrder> mainOrders) {
		// prepare the json String.
		Collection<String> collection = new ArrayList<String>();
	
		String tMainOrdersJsonAryStr = new JSONSerializer().exclude("*.class").serialize(mainOrders);
		collection.add(tMainOrdersJsonAryStr);
	
		// Material
		List<Material> materials = new ArrayList<Material>();
		for (MainOrder mainOrder : mainOrders) {
			List<Material> tMaterials = materialService.findAllMaterialsByMainOrder(mainOrder);
			if (tMaterials != null) {
				for (Material material : tMaterials) {
					materials.add(material);
				}
			}
		}
		String tMaterialsJsonAryStr = new JSONSerializer().exclude("*.class").serialize(materials);
		collection.add(tMaterialsJsonAryStr);
	
		// TextContent in case in the web, the service has changed name, then pos should
		// also change name.
		List<TextContent> menuTextContents = new ArrayList<TextContent>();
		List<TextContent> serviceTextContents = new ArrayList<TextContent>();
		for (Material material : materials) {
			String location = material.getLocation();
			String[] nums = location.split("_");
			String menyKey = "%_menu_" + nums[0] + "_" + nums[1];
			String serviceKey = "%_service_" + location + "_description";// @NOTE: do no use"_%", it will cause
																			// _1_description and _10_description both
																			// search out.
			List<TextContent> tMenuTextContents = textContentService.findAllMatchedTextContents(menyKey,
					material.getMainOrder().getPerson());
			if (tMenuTextContents != null) {
				for (TextContent textContent : tMenuTextContents) {
					menuTextContents.add(textContent);
				}
			}
			List<TextContent> tServiceTextContents = textContentService.findAllMatchedTextContents(serviceKey,
					material.getMainOrder().getPerson());
			if (tServiceTextContents != null) {
				for (TextContent textContent : tServiceTextContents) {
					serviceTextContents.add(textContent);
				}
			}
		}
		collection.add(new JSONSerializer().exclude("*.class").serialize(menuTextContents));
		collection.add(new JSONSerializer().exclude("*.class").serialize(serviceTextContents));
	
		// get the string for return
		String jsonStr = new JSONSerializer().exclude("*.class").serialize(collection);
	
		return jsonStr;
	}
	
	private Customize makeSureSNexist(Person person) {
		Customize customize = customizeService.findCustomizeByKeyAndPerson("SNs", person);
		if (customize == null) {
			customize = new Customize();
			customize.setPerson(person);
			customize.setCusKey("SNs");
			String SNs = "AFJAIU,RQWEIU,ZXVZXV,IASYDU,WLEJKR,WMENRT,QWKJER,UADSYF,YUSDJF,HJHKQE,asdfas";
			customize.setCusValue(SNs);
			customizeService.persist(customize);
		}
		return customize;
	}

	// hard code here, because As we know only the 2.3,4,5 are disigned to display
	// galarry.
	@RequestMapping(value = CC.singleUploadUri, method = RequestMethod.POST, produces = "text/html")
	public String createSingleItem(@Valid MediaUpload mediaUpload, BindingResult bindingResult, Model model,
			@RequestParam("content") CommonsMultipartFile[] contents, HttpServletRequest request,
			HttpServletResponse response) {
		CommonsMultipartFile content = contents[0];
		String keyString = request.getParameter("position"); // can be category, big-slid, product's pratNo...
		FileItem tFileItem = content.getFileItem();
		TaoImage.getBitmapDegree(tFileItem);
		Person person = taoUtil.getCurPerson(request);

		// check size, no big file allowed except for
		// administrator.----------------------
		String returnPageOnError = checkSize(model, keyString, tFileItem, person);
		if (returnPageOnError != null) {
			return returnPageOnError;
		}

		// Initialise the meiaUpload.
		String tKeyStr = taoUtil.isLangPrfNeededForMedia(request, keyString)
				? taoUtil.getLangPrfWithDefault(request) + keyString
				: keyString;
		String fileName = tFileItem.getName();
		MediaUpload media = getOrInitializeMediaUpload(fileName, tKeyStr, person);

		// check if it's delete
		// command.---------------------------------------------------
		if (checkIfIsDeleteCommand(fileName, media)) { // delete a image not allowed in service :(
			if (!keyString.startsWith("service_"))
				mediaUploadService.remove(media);
			return buildPageForMenu(model, request, response, null);
		}

		processFileType(fileName, media);

		// save file or image to db----------------------------------------------------------------
		String pageOnError = null;
		if ("personalDocument".equals(keyString) || "emailBackGround".equals(keyString)) { // save document to db
			mediaUploadService.saveDocumentToDB(model, content, fileName, media, keyString, person);
		} else if ("careerApply".equals(keyString)) { // save document to db
			TaoEmail.sendJobApplyEmail(content, request, person);
			mediaUploadService.saveJobApplicationToDB(model, content, fileName, media, request, response, person);
		} else if (keyString.contains("service_")) {
			String keyForTempralStopThum = CC.THUMNEEDED_ + tKeyStr;
			request.getSession().setAttribute(keyForTempralStopThum, "false");
			try {
				mediaUploadService.saveImageToDisk(model, content, fileName, media, tKeyStr, request, response);
			} finally {
				request.getSession().removeAttribute(keyForTempralStopThum);
			}
		} else if(tKeyStr.contains("slide_superBig_")) {
			int last_pos = tKeyStr.lastIndexOf("_");
			long idx = Integer.valueOf(tKeyStr.substring(last_pos + 1));
			String part1 = tKeyStr.substring(0, last_pos);
			long qt = mediaUploadService.countMediaUploadsByKeyAndPerson(part1, person);
			if(idx > qt + 1) {
				idx = qt + 1;
			}
			tKeyStr = part1 + "_" + idx;
			media.setFilepath(tKeyStr);
			mediaUploadService.saveImageToDisk(model, content, fileName, media, tKeyStr, request, response);
		} else {// save image to db
			mediaUploadService.saveImageToDisk(model, content, fileName, media, tKeyStr, request, response);
		}

		if ("logo".equals(keyString)) {
			List<MediaUpload> mediaUploads = mediaUploadService.listAllMediaUploadsByKeyAndPerson("QRCode_", person);
			if (mediaUploads != null) {
				Object obj = request.getSession().getAttribute(CC.app_WebsiteAddress);
				if (obj != null) {
					String url = obj.toString();

					url = url.endsWith("/") ? url + "menu" : url + "/menu";
					for (MediaUpload oldQRCode : mediaUploads) {
						String fildPath = oldQRCode.getFilepath();
						generateQRCode(null, fildPath, url + fildPath.substring(6), person);
					}
				}
			}
		}
		return buildPageForMenu(model, request, response, null);
	}

	// hard code here, because As we know only the 2.3,4,5 are disigned to display
	// galarry.
	// hard code here, because As we know only the 2.3,4,5 are disigned to display
	// galarry.
	@RequestMapping(value = CC.multiUploadUri, method = RequestMethod.POST, produces = "text/html")
	public String createMultiItem(@Valid MediaUpload mediaUpload, BindingResult bindingResult, Model model,
								  @RequestParam("content") CommonsMultipartFile[] contents, HttpServletRequest request,
								  HttpServletResponse response) {

		Person person = taoUtil.getCurPerson(request);
		// if idle too long time, the person will be null;
		if (person == null) {
			return buildPageForMenu(model, request, response, null);
		}

		String langPrf = taoUtil.getLangPrfWithDefault(request);
		String keyString = request.getParameter("position");
		keyString = checkValidation(keyString, langPrf, person);
		String operation = request.getParameter("operation");

		String tKeyStr = taoUtil.isLangPrfNeededForMedia(request, keyString) ? langPrf + keyString : keyString;
		boolean isGallary = keyString.toUpperCase().startsWith(CC.GALLERY);

		List mediaUploads = isGallary ? mediaUploadService.listAllMediaUploadsByKeyAndPerson(tKeyStr, person) : serviceService.listAllServiceKeyByKeyAndPerson(tKeyStr, person);
		long countOfExistingItem = mediaUploads != null ? mediaUploads.size() : 0;
		countOfExistingItem = isGallary ? TaoImage.validateThumNeededImageCount(tKeyStr, mediaUploads)
				: countOfExistingItem;

		if (countOfExistingItem < 0) {
			return buildPageForMenu(model, request, response, null);
		}

		// !!!to be same with the number, index of programming is also start from 1.
		float imageIndex;
		String imageIndexStr = request.getParameter("imageIndex");
		int appendSameDisplayIndex = 0;
		boolean isAppendSameTarget = false;
		try {
			if (imageIndexStr != null && imageIndexStr.contains(".")) {
				String[] imageIndexArr = imageIndexStr.split("\\.");
				imageIndex = Float.valueOf(imageIndexArr[0]);
				appendSameDisplayIndex = imageIndexArr.length > 1 && imageIndexArr[1].length() > 0 ? Integer.valueOf(imageIndexArr[1]) : 0;
				isAppendSameTarget = appendSameDisplayIndex > 1;
			} else {
				imageIndex = Float.valueOf(imageIndexStr);
			}
		} catch (Exception e) {
			imageIndex = countOfExistingItem;
		}
		if (isGallary && imageIndex > countOfExistingItem + 1) {
			imageIndex = countOfExistingItem + 1;	//index should be too big.
		}

		boolean isSubImageOperation = isAppendSameTarget;
		boolean isThumNeeded = taoUtil.isTHUMNeeded(request, tKeyStr, person);
		long mainIndex = (int)imageIndex;
		String apdBaseKey = CC.APD + tKeyStr + "_" + mainIndex;
		List apdMediaUploads = isSubImageOperation ? mediaUploadService.listAllMediaUploadsByKeyAndPerson(apdBaseKey, person) : null;
		long countOfExistingAptItem = apdMediaUploads != null ? apdMediaUploads.size()/2 : 0;
		long appendSameIndex = appendSameDisplayIndex > 1 ? appendSameDisplayIndex - 2 : 0;
		if (appendSameIndex < 0) {
			appendSameIndex = 0;
		}
		if (appendSameIndex > countOfExistingAptItem) {
			appendSameIndex = countOfExistingAptItem;
		}

		if (CC.APPEND.equals(operation) && !isSubImageOperation) {
			imageIndex++;
			taoImage.makeRoomForNewImages(tKeyStr, (int)imageIndex, countOfExistingItem, contents.length, isThumNeeded,
					person);
		} else if (CC.APPEND.equals(operation) && isSubImageOperation) {
			for (long j = countOfExistingAptItem - 1; j >= appendSameIndex; j--) {
				String oldApdKey = apdBaseKey + "_(" + j + ")";
				String newApdKey = apdBaseKey + "_(" + (j + contents.length) + ")";
				MediaUpload oldApdMedia = mediaUploadService.findMediaByKeyAndPerson(oldApdKey, person);
				if (oldApdMedia != null) {
					oldApdMedia.setFilepath(newApdKey);
					mediaUploadService.persist(oldApdMedia);
				}
				if (isThumNeeded) {
					MediaUpload oldApdThumb = mediaUploadService.findMediaByKeyAndPerson(oldApdKey + "_thum", person);
					if (oldApdThumb != null) {
						oldApdThumb.setFilepath(newApdKey + "_thum");
						mediaUploadService.persist(oldApdThumb);
					}
				}
			}
		} else if (CC.APPEND_SAME.equals(operation) && isSubImageOperation) {
			appendSameIndex = countOfExistingAptItem;
		}

		boolean needCleanUp = false;
		long i = (int)imageIndex;
		for (CommonsMultipartFile content : contents) {

			FileItem tFileItem = content.getFileItem();
			// check size, no big file allowed except for
			// administrator.----------------------
			// while front end should already checked the size.....
			// String returnPageOnError = checkSize(uiModel, tKeyStr, tFileItem, person);
			// if (returnPageOnError != null) {
			// return returnPageOnError;
			// }

			String fileName = tFileItem.getName();
			//if it's for setting default service image for the items that do not have image uploaded. then use "defaultService" as tkeyStr (filepath)
			String key = fileName.contains(CC.defaultservice) ? CC.defaultservice : tKeyStr + "_" + i;
			if (isSubImageOperation) {
				key = apdBaseKey + "_(" + appendSameIndex + ")";
			} else if(CC.APPEND_SAME.equals(operation)){	//add process for append_same.
				//闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇川绾剧晫鈧箍鍎遍幏鎴︾叕椤掑倵鍋撳▓鍨灈妞ゎ厾鍏樺顐﹀箛椤撶偟绐炴繝鐢靛Т鐎氱兘宕ラ崨瀛樷拻濞达絿鎳撻婊呯磼鐠囨彃鈧潡鐛径濞炬闁靛繒濮烽鎺旂磽閸屾瑧鍔嶆い顓炴喘瀹曘垺娼忛妸褏鐦堥梺姹囧灲濞佳冪摥闂備胶顭堥敃銈囩礊婵犲倻鏆﹂柟杈剧畱鍞梺鍐叉惈閸婂宕㈤幖浣圭厽闊洦娲栨禒鈺傘亜閺囧棗瀚崣蹇涙煕鐏炴儳顥氶柛瀣尭閳绘捇宕归鐣屼憾婵犵數鍋涢崥瀣偡閵娾晜鍊堕柛鎰靛枟閳锋垿姊洪銈呬粶闁兼椿鍨遍弲鍫曨敍濠婂懐锛滈梺绋挎湰濮樸劍鏅ユ俊銈囧Х閸嬫稑螞濠婂煪銊︽媴閸︻厾顔曢梺鍦亾閸撴岸鎮℃總鍛婄厸閻忕偟鍋撶粈瀣偓瑙勬礈閸樠囧煘閹达箑閱囨繝闈涙閻︽挾绱撻崒姘偓鐑芥⒔瀹ュ绀夌€光偓閸曘劉鍋撻敃鍌涘€婚柦妯侯槼閹芥洖鈹戦悙鏉戠仧闁搞劍妞藉畷鎴﹀礋椤栨稓鍘卞┑鐐村灦閿曨偄顔忛妷鈺傜厱闁绘劕妯婂Ο鈧┑顔硷龚濞咃絿妲愰幒鎳崇喖宕烽鐘电暢闂傚倷绶氬鑽ょ礊閸モ晝绀婂〒姘ｅ亾闁诡喕鍗抽、姘跺焵椤掆偓閻ｇ兘宕奸弴銊︽櫌婵犮垼娉涢鍡椻枍瀹ュ鈷掑ù锝堟鐢盯鏌＄仦璇插鐎规洘鐓″畷锝嗗緞婢跺瞼鐣鹃梻浣筋潐閸庡吋鎱ㄩ妶澶婄厱闁圭儤鍤氳ぐ鎺撴櫜闁搞儯鍔屽▓灞筋渻閵堝棙绀冪紒顔兼捣濡叉劙骞掗弮鈧€氭岸鏌熺紒妯哄潑闁稿鎸荤换婵嗩潩椤掑倸濮︽俊鐐€栫敮濠囨嚄閸洖鐓濋柟鍓х帛閻撴盯鏌涘☉鍗炵仩闁宠鐗忛埀顒侇問閸犳牠鈥﹂柨瀣╃箚闁归棿绀侀悡娑樏归敐鍛彇闁搞儯鍔庨崢閬嶆⒑閺傘儲娅呴柛鐔跺嵆楠炲﹪宕ㄧ€涙鍘介梺鎸庢磵閸嬫捇鏌ｅΔ浣瑰碍妞ゎ偄绻樺畷顐﹀礋閹冲嘲娲ら悙濠勬喐韫囨柨顕遍柛顐ｇ妇閺€浠嬫煟閹邦厽绶查悘蹇撳暣閺屾盯骞囬崗鍝ュ嚬闂佺懓鍟块崯鏉戭潖婵犳艾纾兼慨姗嗗厴閸嬫捇鎮滈懞銉モ偓鍧楁煥閺冨牊鏆滈柛瀣尵缁厼鈽夊Ο鍝勭婵°倧绠掑▔鏇㈡偪閳ь剙鈹戦悙鏉戠仸妞ゎ厼娲弫宥呪枎韫囧﹥鏂€闂佺粯顭囩划顖氣槈瑜庨妵鍕箣濠靛洤娅х紓渚囧枛椤嘲顕ｆ禒瀣垫晝闁挎繂鎳忛悗楣冩煟鎼达紕鐣柛搴ㄤ憾钘濇い鎰剁畱閻掑灚銇勯幒鎴姛闁伙綁娼ч埞鎴﹀焺閸愨晛鈧劙鏌涢埞鎯у⒉缂佹鍠栭崺鈧い鎺戝閼稿綊鎮规ウ瑁も偓鈧柡鈧禒瀣厽婵☆垵鍋愮敮娑欑箾閹冲嘲瀚粻楣冩倶閻愭彃鈧憡鎱ㄩ崒鐐寸厱闁宠鍎虫禍鐐繆閻愵亜鈧牜鏁幒妞濆洭顢涘☉娆愮彿闁诲孩绋掕摫闁告瑥绻愰埞鎴︽偐閹绘帗娈查梺闈涙处缁诲嫰鍩€椤掑喚娼愭繛鎻掔箻瀹曟繂顓奸崶銊ュ簥濠电娀娼ч鍛矆閸愨斂浜滄い鎾跺枎閻忥妇鎮埡鍛拻闁稿本鑹鹃埀顒傚厴閹偤鏁冮崒姘辩暫闂佸壊鍋呭ú鏍矆婢舵劖鐓欓悗鐢殿焾娴犙勩亜閳哄倻鍙€闁哄本鐩、鏇㈡晲閸℃瑯妲紓鍌欒兌婵敻鎮ч悩璇茶摕闁靛ň鏅滈崑鍕煕濠靛棗鐝旈柕蹇嬪€栭悡鏇熶繆閵堝嫮顦﹂柍缁樻礋閺岋紕浠﹂悙顒傤槷缂備胶濮甸惄顖涗繆閻ゎ垼妲鹃梺鎸庣〒閸犳牕顫忓ú顏勫窛濠电姴绻嬬欢鍨箾鐎涙鐭嬬紒璇插楠炴垿濮€閻橆偅鏂€闁诲函缍嗘禍璺侯焽婵犲洦鈷戦柟绋挎捣缁犳挻淇婇锝囨创濠碘剝鎸抽獮鍥敆婢跺鐦滈梻渚€娼ч悧鍡椢涘Δ鍛€堕柕澶嗘櫆閻撴瑩鏌ц箛锝呬壕闁肩増瀵ч〃銉╂倷閹绘帗娈銈庡亝缁捇宕洪埀顒併亜閹哄秵顦风紒璇叉閺岋綁濡舵惔锛勪紘濠碘剝褰冩晶鐣屾閹烘绠氱憸宥夊煕閺冨牊鐓曟俊顖滃Т濞茬娀鍩€椤掑﹤顩柟鐟板婵℃悂鏁冮埀顒傚椤栨稐绻嗛柕鍫濈箳閻ｉ亶鏌ｈ箛鏃傜疄妞ゃ垺妫冮、姗€濮€閳锯偓閹锋椽姊洪崨濠勨槈妞ゎ厸鍋撻梺纭呮彧缁犳垹绮绘导瀛樼厵闁诡垳澧楅ˉ澶愭煢閸愵亜鏋戠紒缁樼洴楠炲鈻庤箛鏇氭偅闂備胶绮幐濠氭晝閵忋倕钃熼柣鏃傗拡閺佸秹鏌涢埄鍐炬當闁逞屽墯閹倽妫㈠┑顔斤供閸樺墽寮ч埀顒傜磼閻愵剚绶叉い锕佷含婢规洟宕稿Δ浣哄幈闁诲函缍嗛崑鍕倶鐎电硶鍋撶憴鍕闁告梹鐟ラ锝夊磹閻曚焦顎囬梻浣告憸閸犲酣骞婃惔銊ョ厴闁硅揪闄勯崑鎰版倵閸︻厼孝妞ゃ儲绻勭槐鎺楁倷椤掆偓閸斻倖銇勯鐘插幋鐎殿喖顭烽幃銏ゆ偂鎼达絿鏆伴柣鐔哥矋缁挸鐣烽悽鍛婂亜闁惧繐婀遍敍婊堟⒑缂佹〞鎴ｃ亹閸愵喖鐓曢柡鍐ㄧ墛閻撶喖鏌曟径妯烘灆闂侇収鍨遍妵鍕閳藉懓鈧灝鈹戦鐟颁壕闂備礁鍟块幖顐﹀磻閸℃あ娑㈠冀椤愩倗锛濋梺绋挎湰閻熴劑顢欐径瀣╃箚闁绘瑦鐟ュú銈囧婵犳碍鐓欓柛鎾楀懎绗￠梺缁樻尵閸犳牠鐛弽顬ュ酣顢楅埀顒勬倿濞差亝鐓曢柣鏃堫棑缁犲鏌＄仦鍓ф创闁诡喒鏅涢悾鐑藉炊閵娿儱鐏″┑锛勫亼閸婃垿宕归悜妯尖攳婵炲樊鈧亜娲╅妵鎰板箳閹捐泛骞堥梻浣哥－閹虫捇鎮樺┑瀣婵犲﹤鎳夐崑鎾诲垂椤愶絿鍑￠柣搴㈠嚬閸橀箖骞戦姀鐘闁靛繒濯濠囨⒑闂堟稓绠冲┑顔炬暬閹﹢宕奸妷锔规嫼闂佸憡绻傜€氼垶锝為敃鍌涚厱闁哄倸娼￠崣鍕煕閳规儳浜炬俊鐐€栫敮鎺楁晝閿曞倸绀嗗ù鐓庣摠閻撶喖鐓崶銊︹拹闁汇劍鍨圭槐鎺撴綇閳轰椒鎴锋繛瀵稿婵″洭骞夐幘顔芥櫜闁糕剝蓱濠㈡垿姊婚崒娆掑厡缂侇噮鍨甸幗顐㈩渻閵堝棙鈷愰柣妤冨Т椤曪綁骞庣粵瀣櫖濠电偞鍨堕〃鍫㈢不濮樿埖鈷戦柛锔诲幘鐢盯鎮介娑辨疁鐎规洜鏁诲畷鍫曞煘閹傚闁荤喐鐟ョ€氼厾绮堟径瀣ㄤ簻妞ゆ劑鍩勫Σ娲煃瑜滈崜鐔奉焽瑜旀俊鍓佺矙濞嗘儳娈ㄩ梺鍓插亝濞叉牜绮婚敐澶嬬叆婵炴垶鑹鹃。鍏肩箾閼碱剙鏋涚€殿喖顭烽弫鎰板幢濡搫濡虫俊鐐€栭悧妤冨垝鎼淬垻澧￠梻鍌氬€风粈渚€骞楀鍕弿闁汇垹鎲￠崑瀣節婵犲倹鍣归柛銊︾箓閳规垿鎮╁畷鍥舵殹闂佺顑呯€氫即骞冨Ο璺ㄧ杸閹艰揪绲芥俊浠嬫⒑閸濆嫬鈧敻宕戦幘缁樷拻闁稿本鐟ㄩ崗灞俱亜椤撶偟澧︾€殿喚鏁婚、妤呭磼濮橆厾鈧娊鎮楅獮鍨姎妞わ富鍨堕幃鍧楊敋閳ь剟寮婚敐澶婄疀妞ゆ梻鍘ф俊娲⒑缂佹ɑ灏柛鐔告綑椤繐煤椤忓嫬鐎銈嗘礀閹冲酣宕滄导瀛樷拺閻犲洩灏欑粻姘舵煛閸涱喚绠樻俊鍙夊姍楠炴帡骞樼€靛摜肖闂備線娼ч…鍫ュ磿閹剁瓔鏁勯柛宀€鍋為埛鎴︽⒑椤愩倕浠滈柤娲诲灡閺呭爼宕滆绾剧晫鈧箍鍎遍幊蹇浰夐悙鐢电＜闁稿本姘ㄥ瓭闂佸疇顫夐崹褰掑焵椤掑﹦绉靛ù婊呭仱閺佸秴鈽夐姀鈾€鎷洪梻鍌氱墛娓氭顬婅閹綊骞囬鍏肩€剧紓浣稿€哥粔鎾€﹂妸鈺侀唶婵犻潧鐗滈崯鍥ㄧ節閻㈤潧鍓崇紓宥呮瀹曟粌鈻庨幘鏉戔偓鍧楁煟閹寸儐鐒界紒鐘荤畺閺屾盯顢曢妶鍛亖闂佹椿鍘界敮锟犲蓟閳ュ磭鏆嗛柍褜鍓熷畷浼村冀椤撴壕鍋撴担绯曟瀻闁瑰濞€閳瑰繘鏌ｆ惔顖滅У闁稿妫濆鎶藉Ψ閳哄倵鎷绘繛杈剧到閹芥粓寮搁崘鈺€绻嗘い鎰╁灩椤忣參鎸婂┑瀣厽闁靛繒濮甸幉鍝モ偓瑙勬尫缁舵岸寮婚悢鍏煎€绘慨妤€妫欓悾鍓佺磽娴ｅ搫孝妞ゎ厾鍏橀獮鍐ㄎ旈崨顔间缓濠电偛鐗愬▔鏇㈠礉閸濄儳纾介柛灞炬皑瀛濋梺鍝ュ櫏閸ㄦ娊骞戦姀鐘斀閻庯綆鍋勬禒娲⒒閸屾氨澧涢柛鎺嗗亾闂侀潧顧€鐠愮喐绂嶅鍫熺叆婵犻潧妫濋妤呮煛鐎ｃ劌鈧牗绌辨繝鍥х煑濠㈣泛锕ら～鎺楁倵濞堝灝鏋熼柟姝屾珪閹便劑鍩€椤掑嫭鐓熸俊顖濇閿涘秹鏌嶈閸擄箓宕滈悢鐓庤摕闁挎繂顦伴崑鎰版煕濞嗗浚妲洪柍瑙勭洴閺岋綀绠涢幘鍓侇唹闂佽崵鍟块弲娑㈩敋閿濆绠柤鎭掑劜濞呭洭姊虹粙鎸庢拱缂佸鎹囧畷銏＄鐎ｎ偀鎷洪梺鍛婄箓鐎氼剟寮冲▎鎾寸厱濠电姴瀚弳顒侇殽閻愯尙绠婚柡浣规崌閹晛鐣烽崶褍绠版繝鐢靛仩閹活亞寰婃禒瀣婵犲﹤瀚晶锟犳⒒閸屾瑧绐旈柍褜鍓氱粙鎾诲煘閹烘鐓曢柡鍌濇硶閻掔兘鏌ｈ箛鏂跨瑲缂佺粯绻堟慨鈧柣妯烘▕閺嗐垹顪冮妶搴″箹闁绘鎹囬悰顕€宕卞☉姗€鍞跺┑鐘绘涧閸燁垶寮埀顒勬⒒娴ｈ櫣甯涢柛鏃€鐟ラ…鍥槾闁轰緡鍣ｉ幊婊堟倷闁稓鐩庨梻浣瑰濞叉牠宕愰悷鎵虫鐟滃酣濡甸崟顖ｆ晝闁靛繈鍨婚濠勭磽娴ｄ粙鍝洪悽顖涘笩閻忔帡鏌ｉ悩鍙夋悙婵☆垰锕崺鈧い鎺嶇劍鐏忔壆绱掔紒妯兼创妤犵偞锕㈠鍫曞箣濠靛洨妲楅梻鍌欑劍閹爼宕濆鍡欑濠电姴鍊婚弳锔界節闂堟侗鍎忕紒鐘垫嚀闇夐柨婵嗘噹鐢劑鏌熼柨瀣仢婵﹥妞藉畷銊︾節閸曨厾鏆ら梺璇插閸戝綊宕板Δ鍛柧闁割偅娲橀悞鑲┾偓骞垮劚濡盯宕㈤柆宥嗏拺閻熸瑥瀚粈鍐╃箾婢跺娲寸€殿喚顭堥埥澶娾枎濡椿鍟嶉梻浣虹帛閸旀﹢顢氬鍐惧晠闁靛鏅滈悡鍐偡濞嗗繐顏╅柣蹇婃櫊閺屽秷顧侀柛鎾卞妿缁辩偤宕卞☉妯硷紱闂佸憡娲﹂崹浼存倿閸偁浜滈柟鐑樺灥椤忣亪鏌℃笟鈧禍鍫曞蓟閺囥垹鐐婄憸宥夘敂椤撶噥娈介柣鎰綑濞搭喗顨ラ悙宸剰闁宠鍨垮畷銊╊敍濞戞瑦顏ｅ┑鐘垫暩婵兘寮幖浣哥；闁绘劕妯婇悞鑺ョ箾閸℃ê鐏嶉柛瀣尭椤繈鎮℃惔鈾€鎷梻浣虹《閺呮繈宕戦妶澶屽祦閻庯綆鍠楅弲婵嬫煃瑜滈崜鐔煎箖閿熺姵鏅搁柣妯虹－閸橀亶姊洪弬銉︽珔闁哥姵鑹鹃埢鎾澄熸總钘夌秺閹晛鈻庤箛鏂款槱闂佺粯鎸鹃崰鏍蓟閿濆拋娼ㄩ柛鈩冦仦缁ㄧ粯绻涚€涙鐭掔紒鐘崇墪椤繐煤椤忓嫮顔囬柟鍏肩暘閸ㄥ鏁嶉弴銏♀拺缂佸顑欓崕鎰版煙閻熺増鍠樼€殿喖顭峰鎾閻樿尪鈧灝鈹戦埥鍡楃仴婵炲拑缍侀幃鐑芥嚑椤掑倻锛濇繛杈剧秬濞咃絿鏁☉姘辩＜閻犲洦褰冮埀顒€娼￠弫鎰版倷濞村鏂€闁诲函绲介悘姘跺疾閿濆鈷戠紓浣广€為幋锝冧汗闁告劏鏅欑换鍡涙煕閵夘喖澧柍閿嬪浮閺屾稓浠﹂崜褎鍣銈忚缁犳垿鈥﹂崸妤佸仭閻㈩垼鍠涢崥顐︽倵鐟欏嫭绀冮柛銊ョ仢閻ｇ兘濡搁埡濠冩櫖濠电偞鍨堕敋缂併劊鍎靛缁樻媴娓氼垳鍔搁梺娲诲墮閵堢鐣烽幋婵冩婵☆垳鍊崶褏鐓戞繝銏ｆ硾閻ジ顢欓弴銏♀拺缂侇垱娲栨晶鏌ユ嫅闁秵鍊堕煫鍥ч瀹撳棝鏌″畝瀣М妤犵偞锕㈠畷姗€宕ｆ径瀣靛晙闂傚倷鑳堕、濠勪焊濞嗘挴鈧箓宕奸妷鈶╁亾閿曞倸惟闁宠桨绶氶崬璺衡攽閻樼粯娑ч柣妤佺矊鍗遍柤鍝ユ暩缁♀偓濠电偛鐗嗛悘婵嬪几閻斿吋鐓ラ柡鍥殕濞呭﹦鈧娲樺ú鐔笺€侀弮鍫濈闁兼亽鍎查妵婵囥亜閵忊剝顥堢€规洏鍔戦、妯衡攽閸垺鏅ㄩ梻鍌氬€搁崐椋庣矆娓氣偓瀹曘儳鈧綆鍓涢惌鍫ユ煙缂併垹鏋涢柣銈囧亾缁绘盯骞嬮悙瀵告闂佹椿鍘奸悧鎾诲蓟閵娾晛绫嶉柛灞剧⊕閻濐亞绱撴担鍙夘€嗛柛瀣崌濮婄粯鎷呴崷顓熻弴闂佹悶鍔忓Λ鍕€﹂崶顏嶆Щ闂佺儵妲呴崢濂稿煘閹达附鍊风€瑰壊鍠栧▓鍫曟⒑闂堟稒顥滄い鎴濇川濡叉劙鎮欑€靛摜鎳濋梺閫炲苯澧撮柣娑卞櫍瀹曟﹢顢欑喊杈ㄧ秱闂備線娼ч悧鍡涘箠瀹ュ洦顫曢柛顐ｆ礃閳锋垿姊婚崼鐔峰枙缂佹唻绻濋弻娑㈠即閵娿儰绨奸梺姹囧€曠€氼厾鎹㈠┑鍫濇瀳濠㈣泛鐬奸敍鐔兼⒑缁嬫鍎愰柛銊ョ仢閻ｇ兘骞囬弶鍨敤濡炪倖鎸鹃崰搴㈢閸楃偐鏀介幒鎶藉磹閺囥垹鐤い鎰剁畱閸戠娀鏌涢鐔稿櫚闁稿鎹囬悰顕€宕归鐓庮潛婵＄偑鍊х紓姘卞垝濞嗗浚鍤曟い鎰剁畱閻愬﹥銇勯幒宥堝厡闁告﹢浜堕幃宄邦煥閸愵喖寮伴梺闈涙閸熸潙鐣烽崡鐐╂婵☆垳鍘ч獮鍫ユ⒒娴ｅ憡璐￠柛搴涘€濆畷闈涱潩閻愭垝姹楅梺鍦劋閹歌鈻嶅鍫熲拺闁硅偐鍋涢崝姘熆瑜嶉柊锝呯暦濠靛牏鐭欐繛鍡欏亾鐎靛矂姊洪棃娑氬闁哥噥鍋婂畷婵嗩潩鏉堚晝锛滅紓鍌欑劍閿曨偊鎳撶捄銊㈠亾濞堝灝鏋︽い鏇嗗洤鐓″璺侯煬濞尖晠鏌ㄥ┑鍡樺櫢濠㈣娲熷娲箰鎼达絿鐣电紓浣靛姀閸嬫劙鎳炴潏銊ч檮闁告稑锕﹂崢閬嶆⒑閹稿海绠撻柟铏姉缁柨煤椤忓懐鍘靛銈嗘礀濡稓寮ч埀顒佺節瀵版灚鍊曢悡鎰偓鍨緲鐎氼噣鍩€椤掑﹦绉甸柛鎾寸懇閻涱噣骞囬悧鍫㈠幗闁硅壈鎻槐鏇㈡偩椤撱垺鐓曢幖娣€濋崫鐑樸亜閵婏絽鍔︽鐐寸墬閹峰懘宕崟顓滃亰濠电姴鐥夐弶搴撳亾閺囥垹绠犻煫鍥ㄧ⊕閸嬪倿鏌￠崶銉ョ仾闁稿﹦鏁婚弻銊モ攽閸℃瑥鍤紓浣靛妼椤兘寮诲鍫闂佸憡鎸婚悷銉╂晝閵忋倖鐒肩€广儱鎳愰澶愭⒑闂堟稓绠冲┑顕呭幖鍗遍柛顐犲劜閻撳繘鐓崶銊︾閸熺顪冮妶鍡樺碍闁圭⒈鍋婇崺鐐哄箣閿旇棄鈧兘鏌涘▎蹇ｆ▓婵☆偆鍋熺槐鎾存媴閾忕懓绗￠柣銏╁灡椤ㄥ牏鍒掔€ｎ喖绠抽柟鎯у船娴滄繈姊洪崨濠傚闁哄倷绶氶獮蹇涙惞閸︻厾锛濋梺绋挎湰閻熝囧礉瀹ュ瀚呴梺顒€绉甸崐鍫曠叓閸ャ劍绀€妞わ綀鍋愮槐鎺旂磼濡吋鍒涢梺鐟扮－婵炩偓妞ゃ垺顨婇崺鈧い鎺戝閸戠姵绻涢幋鐐垫噽婵炲牅绮欓弻锝夊箛椤撶偟绁烽梺鍝勬閻燂箓濡甸崟顖ｆ晝闁挎繂娲ㄩ悡澶愭⒑缁洘鏉归柛瀣尭椤啴濡堕崱妤冪懆闁诲孩鍑归崜娑氬垝婵犳艾鍐€闁靛ě鍜佸晭闂備胶纭堕崜婵婃懌闁诲繐娴氶崢鍓ф閹烘鍋愰柛顭戝亝濮ｅ牓姊洪崫鍕伇闁哥姵鐗曢悾鐑筋敃閿曗偓缁€瀣亜閹烘埈妲归柨娑楃窔濮婅櫣鎷犻弻銉偓妤呮煟韫囨柨鍝洪柟顔ㄥ洦鍋愮€瑰壊鍠楃紞搴ｇ磽閸屾瑧鍔嶉拑鍗炩攽椤栨稒灏﹂柟顔煎槻閳诲氦绠涢幙鍐ф樊濠电偛顕慨宥夊炊閵娿垺瀚奸梻浣告啞缁嬫垿鍩婇弴銏╂晜闁割偅鎯婅閺岀喖姊婚幘顔筋€栭梺閫炲苯鍘哥紒鑸佃壘椤曪絾绂掔€ｎ€晠鏌曟竟顖氭噽瀹撲線姊婚崒娆戭槮缂傚秴锕畷鎴炵節閸パ咁槶濠电娀娼ч鍛不濞差亝鐓涢柛銉㈡櫅娴滃綊鏌￠埀顒佺鐎ｎ偆鍘介梺褰掑亰閸樼晫绱為幋锔界厽闊洦娲栭弸娑㈡煛鐏炲墽娲村┑鈩冩倐閺佸倹鎱ㄩ幇顏囨闂佽瀛╅鏍窗濡ゅ懏鍋傞柍銉﹀墯閸ゆ洖鈹戦悩瀹犲閸烆垶姊洪幐搴⑩拹闁稿孩濞婇悰顕€宕奸妷锔规嫽婵炶揪绲块悺鏂款焽閹邦剦鐔嗛柣鐔煎亰閸庢梻鈧鍠楄ぐ鍐煡婢跺ň鏋庨幖杈剧悼閻ｉ箖姊绘担铏瑰笡闁圭鎽滈懞閬嶅醇閺囩偟鍝楅梺鍏间航閸庮垱绂嶅鍫熺厸闁搞儮鏅涙禒锔姐亜閵壯冣枅闁哄矉缍侀弫鎰緞婢跺鏋ら梻浣告惈閼活垳绮旈崼鏇炵疅闁圭虎鍠栫粈瀣亜閹扳晛鈧挾妲愬┑鍥︾箚闁绘劦浜滈埀顒佸灴瀹曠懓煤椤忓懎浠悷婊冪Ч閸┿儲寰勯幇顓ф濠电偞鍨剁喊宥呪枔濠靛鈷戦柛婵嗗濡茶銇勯幋鐐垫噧妞ゎ厼娲、妤呭磼濠婂拑绱抽梻浣侯焾閺堫剟鎳濇ィ鍐ㄧ劦妞ゆ帒瀚峰Λ鎴炵箾閸℃劕鐏查柡浣规崌閹晠寮婚妷锔肩船濠碉紕鍋戦崐鏍哄澶婄；闁规儳鐏堥崑鎾舵喆閸曨剛顦ョ紓鍌氱Т閿曘倝鎮鹃柨瀣檮缂佸鐏濆畵鍡涙⒑缂佹﹩鐒鹃悘蹇旂懇婵″爼鏁冮崒娑掓嫽婵犵數濮存鍛婄濠婂嫮绠鹃悹鍥囧喚娲紓渚囧枟閻熲晠鐛€ｎ亖鏀介柟閭﹀墯濞呭﹥绻濋悽闈涒枅婵炰匠鍏犳椽濮€閿涘嫷娲稿┑鐘诧工閻楀﹪鎮″☉姘ｅ亾楠炲灝鍔氶柛鐕佸亝娣囧﹪宕稿Δ浣哄幗濡炪倖鎸鹃崑鐔兼倿閹间焦鐓涚€光偓鐎ｎ剛袦濡ょ姷鍋為…鍥焵椤掍胶鈯曢柨姘辩棯閻愵剙鈻曟慨濠冩そ瀹曨偊宕熼鐘插Ы缂傚倸鍊哥粔宕囨濮樺墎宓侀柛鎰靛枛閻撴盯鏌涘☉鍗炲箻妞は佸洦鈷戦柛娑橈功閳洘绻濋姀鈽呰€块柟顔惧仦缁绘繂顫濋娑欏濠电偠鎻徊浠嬪箟閿熺姴鐤柣鎰摠閸欏繐鈹戦悩鎻掆偓鑽ょ矓閾忓厜鍋撶憴鍕缂傚秴锕ら悾椋庣矙鐠囩偓妫冮崺鈧い鎺戝€绘稉宥夋煛瀹ュ海浜圭憸鐗堝笒缁€鍌炴煕韫囨艾浜圭紒瀣川缁辨挻鎷呴崫鍕戙垺銇勯鐘插幋鐎殿喛顕ч埥澶愬閻樻鍟嬮梻浣藉婢瑰牆鈻斿☉銏犲偍闁跨喓濮甸埛鎴︽煙缁嬫寧鎹ｉ柍顖涙礈缁辨帞鎷犻懠顒€顣哄銈庡亜缁绘﹢骞栬ぐ鎺戞嵍妞ゆ挾濮烽崢顖炴⒒娴ｅ憡璐℃い顓炵墢閳ь剚绋堥弲鐘荤嵁韫囨稒鐒肩€广儱妫岄幏娲⒑閻撳寒娼熼柛濠冩礋瀵娊鍩€椤掑嫭鈷戦柛娑橈梗缁堕亶鏌涢悩宕囧⒌妞ゃ垺妫冨畷銊︾節閸曨収鍟庨梺璇叉捣閺佹悂鈥﹂崼銏℃殰闁割偅娲橀悡鐔煎箳閹惰棄绀夐柟杈剧畱缁犳牠鏌ㄩ悢鍝勑㈤柛妤佸▕閺屾洝绠涢妷褏锛熼梺鍛婎焾濡嫰鍩為幋锕€鐓￠柛鈩冾殘娴犳潙鈹戦悙鍙夊珔缂傚秳绶氬顐㈩吋閸滀礁鎮戦梺鍛婄矆閼冲爼宕悽鍛婂仭婵犲﹤瀚悘锔剧磼閸屾氨效妞ゃ垺顨婂畷鎺戔槈鏉堛劎绋愰梻鍌欑濠€閬嶅磿閵堝鈧啳绠涘☉妯荤€繝鐢靛Т濞诧箓鎮￠悩缁樼厵妞ゆ挾鍠庣粭鎺楁煕閺傝法鎽犻柟渚垮妽缁绘繈宕掑☉妯规樊婵犳鍠栭敃銉ヮ渻閽樺）娑㈠礃閵娿垺顫嶅┑鐐叉濞存艾危闁秵鈷掑ù锝呮啞閸熺偞鎱ㄦ繝鍌滅Ш闁诡喚鍏橀崺锟犲川椤旈棿绮ф俊鐐€栧ú宥夊磻閹炬惌娈介柣鎰綑缁楁帡鎽堕弽顓熺厓鐟滄粓宕滃▎鎺斾罕婵犳鍠楅妵娑㈠磻閹剧粯鐓欐い鏃囧亹缁夌儤顨ラ悙鎵獢妤犵偞甯￠獮瀣熆椤忎焦娅婃慨濠呮缁瑥鈻庨幆褍澹堥梻浣瑰濞诧附绂嶅鍫稏闊洦姊荤弧鈧┑顔斤供閸撴盯鏁嶅鍐ｆ斀妞ゆ梻鐡旈悞楣冩煕鐎ｎ偄濮嶇€规洩缍佸鎾閳锯偓閹疯櫣绱撴担鍓插剱妞ゆ垶鐟╁畷鏇＄疀濞戞瑥鈧灚鎱ㄥΟ鐓庡付濠⒀傚嵆閺屾稑鈻庤箛鏂挎畻濠殿喖锕ュ浠嬬嵁閹邦厽鍎熼柕蹇嬪灩瀵啿鈹戦悩顐ｅ闁稿本绮庨悡鎾绘⒑鐠団€崇仩闁兼椿鍨堕崺銏℃償閵娿儳顔掗柣搴ｆ暩椤牓鐛澶嬧拺閻犲洦褰冮銏㈢磼鐎ｎ偄娴柍銉畵瀹曞爼鍩￠崘褏鐟濋梻渚€娼чˇ顐﹀疾濠婂牊鍋傞柡鍥ュ灪閻撶喖鏌￠崘銊у闁轰線浜堕弻娑㈡晲閸噥浠╃紓浣介哺鐢偟妲愰幒鎳崇喖鎳栭埡鍐╂緰濠电姷顣藉Σ鍛村磻閸曨垰鐤い鎰剁畱閻撯€愁熆鐠哄彿鍫ュ几鎼淬劎鍙撻柛銉ｅ妽鐏忣偊鏌熼懠顒€浜剧紒缁樼箞閹粙妫冨☉妤冩崟闂備礁顓介弶鍨瀷闂佸憡甯楃敮鎺楊敇婵傜鐐婄憸宥夋倶婵犲洦鈷戦悷娆忓缁€鍐┿亜閺囧棗娲︾€氬懘鏌ｉ弬鍨倯闁绘挾鍠栭弻锟犲磼濮樺彉铏庨梺璇″枟閸ㄥ潡寮诲☉娆戠瘈闁搞儯鍔嶉弳鐘绘⒑闂堟稒鎼愰悗姘嵆閻涱噣骞掑Δ鈧粻锝嗐亜閹捐泛鏅跨憸鏂款潖閻戞ê顕辨繛鍡樺灥閸╁矂姊虹紒妯煎⒈闁告鍥风稏闊洦鎷嬪ú顏嶆晜闁告洦浜為悙濠囨⒒娓氣偓濞佳囁囬銏犵？闁哄被鍎查崑鐔兼煥閻斿搫校闁抽攱鍨块弻鐔兼嚃閳轰椒绮舵繝娈垮枓閸嬫挻绻濋悽闈涗粶妞わ缚鍗抽幆鍕敍閻愯尙鐣烘俊銈忕到閸燁垶宕愭繝姘參婵☆垯璀﹀Σ娲煟閵堝倸浜鹃梻鍌氬€搁崐椋庢閿熺姴鐭楅煫鍥ㄧ⊕閳锋棃鏌涢弴銊ョ仩闁汇倗鍋撻妵鍕箳閹存繍浠鹃梺鎶芥敱閸ㄥ湱妲愰幘瀛樺濠殿喗鍩堟禍婵嬪箞閵婏箑绶為柟閭﹀幖娴犫晛鈹戦悙鏉戠仸闁绘娲熷畷婵嬫晜缁涘顔旈梺缁樺姌鐏忔瑧绮诲畷鍥╃＜閺夊牄鍔岀粭鎺楁懚閿濆鐓犳繛鏉戭儐濞呮捇鏌涢悢鐑藉弰婵﹥妞藉畷顐﹀礋閸倣锕傛⒑缂佹﹩娈旀俊顐ｇ懇瀵煡宕奸弴鐔蜂患閻庡厜鍋撻柍褜鍓熼幆宀勫箻濞ｎ兛绨婚梺鐟版惈缁夌懓顬婂畡鎳婄懓顭ㄩ崟顓犵厑闂侀潧娲ょ€氫即鐛崶顒€鐓涘ù锝呮惈婵埖绻濈喊妯活潑闁割煈浜畷鎴﹀箻缂佹鍘介柟鍏肩暘閸娿倕顭囬幇顓犵闁告瑥顧€閼拌法鈧娲栫紞濠傜暦缁嬭鏃堝焵椤掑倻涓嶉柡宥庡幗閻撳啴鏌涘┑鍡楊仼闁哄棛鍠撻惀顏堝箚瑜嶉幃鎴澢庨崶褝韬い銏＄☉椤劑宕橀妸銈囧笡濠德板€楁慨鐑藉磻濞戙垺鍋嬮柟鐐墯濞兼牗绻涘顔荤凹闁稿绻濋弻鈩冨緞鐎ｎ亶鍤嬮梺绋款煬閸ㄨ泛顫忛搹瑙勫珰闁炽儱纾禒鈺呮⒑濞茶骞栭柛濠冩倐閹箖鏌ㄧ€ｎ亶鍤ら梺鍝勵槹閸ㄧ敻宕妸鈺傚仭婵犲﹤鍠氬Ο鈧悗娈垮枤椤牓顢樻總绋跨倞闁挎繂鎳嶆竟鏇炍旈悩闈涗粶闁诲繑绻堝畷婵嗩潩椤撶姷顔曢梺鑲┾拡閸撴瑩鍩㈤崼鈶╁亾鐟欏嫭绀冮柛銊ユ健閻涱喖顫滈埀顒€顕ｉ鍕ㄩ柨鏂垮綖缁ㄦ挳姊婚崒娆戭槮濠㈢懓锕幃锟犲醇閵夈儳鐛ュ┑掳鍊曢幊蹇涘磹閸撲讲鍋撻獮鍨姎妞わ缚鍗冲畷鎴﹀焺閸愵亞鐦堟繝鐢靛Т閸婃悂顢旈鍡欑＜闁煎摜鏁搁崣鈧梺鍝勬湰閻╊垶骞冮姀銈呭窛濠电姴鍊搁弨顓㈡⒒娴ｈ鍋犻柛鏃€鍨圭划濠氬冀椤撶偞鐎梺鍛婂姦閸犳牜澹曢崗鍏煎弿闁挎繂鎳愬В鐐烘煛閸涱垰鈻堢€殿喛顕ч濂稿幢濡警娼梻浣筋潐椤旀牠宕板鑸靛仒闁靛繈鍊栭埛鎴︽煕濠靛嫬鍔氬ù鐘崇矒閺岋綁寮介銏犱粯濡炪値鍋勭换姗€骞栬ぐ鎺戞嵍妞ゆ挾濯寸槐鍙夌節绾版ɑ顫婇柛銊╂涧閻ｇ兘鎮界粙璺ㄧ厬闂佺硶鍓濈粙鎺楀煕閹寸偞鍙忛柣鐔哄閹兼劕鈹戦敍鍕珪缂佽鲸甯為幏鐘绘嚑椤掆偓椤ｆ椽姊烘导娆戞偧闁稿繑锕㈤悰顔锯偓锝庡枟閺呮繈鏌嶈閸撴稓鍒掔紒妯肩瘈婵﹩鍘兼禒顖炴⒑閹肩偛鍔橀柛鏂跨灱瀵板﹥绻濆顓犲幗闁瑰吋鎯岄崰鏍嵁閺嶃劊浜滄い蹇撳閺嗭絽鈹戦垾宕囧煟鐎规洖宕灃闁逞屽墴閿濈偞鎯旈姀銏㈢槇濠电偛鐗嗛悘婵嬪几濞戞氨妫紓浣靛灩瀵噣鏌熷畷鍥т沪婵炵厧绻樻俊姝岊槾闁伙箑鐗嗛埞鎴︻敊閺傘倓绶甸梺鍛娚戦幃鍌炵嵁韫囨拋娲敂閸涱垰骞楅梻浣虹帛閺屻劑骞楀鍫濈疇闁告劦鍠楅崐鍨叏濡厧甯跺褍顕埀顒冾潐濞叉鎹㈤崼鐔剁箚闁割偅娲栭悙濠囨煥濠靛棙鍣稿瑙勬礋閺岋綁鎮㈤崫銉﹀櫑闁诲孩鍑归崢钘夘嚗閸曨垰绀嬫い鏍ㄧ〒閸橀亶姊虹憴鍕婵炲鐩绋库槈濞嗘ɑ顔旈梺缁樺姈瑜板啴寮冲▎鎰╀簻妞ゆ劧绲块惌鎺楁煕閳规儳浜炬俊鐐€栫敮鎺斺偓姘ュ姂閸┾偓妞ゆ垼娉曠粣鏃傗偓娈垮櫘閸ｏ絽鐣烽悡搴樻斀閻庯綆浜濋弳顏堟⒒閸屾瑨鍏岀紒顕呭灦瀵濡搁埡浣侯啇濡炪倖鍔戦崐鏍汲濠婂嫪绻嗛柕鍫濇噺閸ｆ椽鏌嶉柨瀣诞闁哄本绋撴禒锕傚礈瑜庨崳顔碱渻閵堝繗顓虹紒鐘虫崌瀵鏁愰崼銏㈡澑闂佸搫鍟崐濠氭偪閸曨偒娓婚柕鍫濆暙閻忣亪鏌ｅΔ鍐ㄐ㈤柣锝囧厴婵℃瓕顦柛瀣尭閳藉鈻嶉褌閭柛鈹惧亾濡炪倖宸婚崑鎾绘煟閺嵮佸仮鐎殿喖顭烽幃銏ゆ惞閸︻叏绱查梻渚€娼х换鎺撴叏閻㈠憡鍊剁€广儱顦伴埛鎴犵磽娴ｈ偂鎴犱焊娴煎瓨鐓熼柍鍝勶工閻忥附顨ラ悙鎻掓殭妞ゎ偅绮撻崺鈧い鎺戝缁犳牗绻涢崱妯诲碍缂佺姷鏁婚弻鐔煎传閸曨剦妫￠柣搴＄仛閻楃姴顫忕紒妯诲闁告縿鍎查悗顔尖攽閳藉棗浜滈悗姘濡喖姊哄Ч鍥х伄妞ゎ厼鐗撻幏鎴︽偄鐞涒€充壕妤犵偛鐏濋崝姘箾鐠囇呯暤鐎规洑鍗冲畷鍗炩槈濞嗘垵骞堥梻浣告惈濞层垽宕濈仦鍓ь洸闁绘劘顥嗚ぐ鎺撳亗閹艰揪绲块崝顖炴⒑鐠団€虫灍妞ゃ劌鐗撻獮澶愬箻椤旇偐顦板銈嗗笒閸嬪棗危椤掑嫭鐓熼幖娣€ゅ鎰箾鐠囇呯暤鐎规洘绮岄埞鎴犫偓锝庡亜閳ь剙鐖奸弻娑㈠箛閵婏附鐝栫紓浣叉閸嬫捇姊绘担鍦菇闁搞劏妫勯…鍥槻闁活煈鍓熷缁樻媴閸濆嫬浠橀梺鍝ュ枑鐎笛囧疾閼稿灚鍎熼柕蹇嬪灮瑜伴箖姊洪崫鍕窛濠殿噣顥撴竟鏇㈠礂闂傚绠氬銈嗙墬缁诲倹绂嶈ぐ鎺撶厽闁挎洍鍋撻柣妤佹尭椤繘宕崟鎳峰洤鐐婄憸蹇斿閸績鏀介柣鎰緲鐏忓啴鏌涢垾宕囩鐟滅増妞藉濠氬磼濮橆兘鍋撻悜鑺ュ殑闁割偅娲栫粻鐘绘煙閹规劦鍤欓幖鏉戯躬閺岋紕浠︾拠鎻掑闂佹眹鍊ら崳锝夊蓟閳╁啫绶為悗锝庝簽娴犲ジ姊洪崫鍕紨缂佺姵鎹囧濠氭晲婢跺﹦鐫勯梺绋挎湰椤ㄥ棛鈧碍鐩鐑樻姜閹殿噮妲┑鈽嗗亝缁诲牆顕ｇ拠娴嬫婵﹩鍋呴崟鍐煟閻樺弶澶勭紒浣规尦瀹曟垿骞樼拠鎻掑敤濡炪倖鍨煎Λ鍕嚗閺冨牊鈷掗柛灞捐壘閳ь剚鎮傚畷鎰板箹娴ｅ摜锛欓梺褰掓？缁€浣哄閻熼偊娓婚悗锝庝簻椤掋垺銇勯幇顖毿撻柟渚垮妼椤粓宕卞Δ鈧埛鎺楁⒑缂佹ê绗ч柡鍜佸亰楠炲绮欏▎鎯ф倯闂佹悶鍎滈崘顏呯叄闂傚倸鍊搁崐椋庢濮橆剦鐒界憸鏃堝箖瑜斿畷鍗烆渻閺囩喐銇濋柟顔规櫇閹风娀鎳犻濠勫耿闂傚倷绀佸﹢閬嶅磻閹捐绀堟慨姗嗗墻閻掍粙鏌嶉崫鍕偓鑸电濠婂牊鐓欓柟浣冩珪濞呭懘鏌ｈ箛濠冩珖缂佽鲸甯楃粭鐔煎炊瑜庨悵婵嬫⒑鐠団€虫灍妞ゃ劌鎳橀崺銏ゅ箻鐠囨彃鐎銈嗘⒒閺咁偉銇愰鐐粹拻濞达絼璀﹂悞鐐亜閹存繃鍤囬柡浣稿暣婵¤埖寰勭€ｎ亙鎮ｉ梻浣虹帛閸ㄧ厧螞閸曨厾涓嶉柟鎯板Г閻撴瑩鏌熺喊鍗炲箹妤犵偞锕㈤弻娑樷枎韫囨棏妫﹀┑顔硷攻濡炶棄鐣烽妸锔剧瘈闁告劦鐓堝Σ閬嶆⒒娴ｄ警鐒炬い鎴濇噹铻炴繝濠傜墕妗呴梺鍦濠㈡绮荤紒妯镐簻闁哄啫娉﹂幒妤€绠紓浣诡焽缁犻箖寮堕崼婵嗏挃缂佸鍓氭穱濠囶敃閵忕媭浠剧紓浣稿€哥粔鐑藉Φ閹版澘绠抽柟鎹愭硾鐢鏌ｆ惔锝嗗殌閻庢凹鍘界粩鐔煎幢濡粯鐝烽梺鎸庢閺侇噣宕戦幘鑸靛枂闁告洦鍓涢ˇ銊ヮ渻閵堝啫濡奸柟铏耿瀹曟椽濡烽敃鈧欢鐐测攽閻樻彃顒㈢€点倖妞藉铏瑰寲閺囩偛鈷夊銈庡幖濞差參濡撮崨鎼晝闁挎棁袙閹风粯绻涙潏鍓ф偧闁稿簺鍊濆畷鐢稿焵椤掍胶绡€闁冲皝鍋撻柛娑卞枟閻濐亪姊洪崨濠傜瑲閻㈩垽绻濋妴浣糕槈閵忊€斥偓鐑芥煠绾板崬澧鐐茬墦濮婄粯鎷呮笟顖涙暞闂佽妞挎禍顏勵潖娴犲绀嬫い鏍ㄧ☉娴滄姊虹紒姗嗙劷缂侇噮鍨堕幃鈥斥枎閹扳晙绨婚梺瑙勫礃濞夋盯寮告惔锝囩＜婵°倓绀佸ù顕€鏌＄仦鍓ф创妤犵偞锚閻ｇ兘宕惰閸樹粙姊绘担鍛婃儓闁活剙銈稿畷浼村箻鐠囪尙鍔﹀銈嗗笒閸婂綊寮抽埡鍌樹簻妞ゆ挾鍋熸晶娑㈡煟濡も偓闁帮綁寮婚敐鍡樺劅闁靛牆瀛╃紞鍫濃攽閻愭彃绾ч柨鏇樺灲楠炲啴鏁撻悩铏闂佺粯顭堢亸顏堝箺閺囩偐鏀介柣鎰綑閻忕喖鏌涢埡浣割伃鐎殿噮鍋婂畷姗€顢欓悾灞藉箺婵犵妲呴崹鐢割敋瑜忓濠勬嫚濞村鏂€濡炪倖鏌ㄩ崥瀣箲閿濆洨纾奸弶鍫涘妽鐏忎即鏌熷畡鐗堝殗鐎规洦鍋婂畷鐔煎箣椤栨粎袦闂佸搫鐭夌徊鍊熺亙婵炶揪缍€濞咃綁鎮℃径濞㈡棃鎮╅棃娑楃捕濡炪倧濡囬弫濠氭偘椤曗偓瀵粙濡搁敃鈧鎾绘⒑閼姐倕鏋涙い锝忓缁辨棃寮撮姀鈾€鎷绘繛杈剧悼椤牓藟韫囨稒鐓曢悗锝庝簼閸ｄ粙鏌熼獮鍨伈鐎规洜鍠栭、娑樷槈濡湱鏁鹃梻鍌欑窔濞佳嗗櫣闂佸憡渚楅崹鐣屾閸愭祴鏀介柣鎰煐瑜把呯磼闊厾鐭欐鐐寸墵椤㈡洟鏁愰崶褉鍙為梻鍌氬€烽悞锕傛儑瑜版帒鏄ラ柛鏇ㄥ灠閸ㄥ倿鏌ｉ敐鍛拱鐎规洖寮剁换婵嬫濞戞瑱绱為梺鎼炲€栧ú鏍煘閹达附鍋愮€规洖娴傞弳锟犳⒑缁嬫鍎忛悗姘嵆瀵鈽夊鍛澑闂佸搫娲ㄩ崑娑欑濮椻偓濮婃椽鏌呴悙鑼跺濠⒀勫缁辨帡顢氶崨顓犱画缂備礁鐭佹ご鍝ユ崲濠靛鐐婇柤绋跨仛濞呮牗绻濋悽闈涒枅婵炰匠鍏犲綊宕掑☉姘兼祫闂佸啿鎼崐鑽ょ不妤ｅ啯鍊甸柣銏☆問閻掗箖寮介垾鏂ユ斀闁绘劖褰冮幃鎴︽煟閻斿弶娅婇柛銊╃畺瀵噣宕煎┑鍫О婵＄偑鍊栭弻銊ノｉ崼锝庢▌濡ょ姷鍋為悧婊堝礆閹烘绀傚璺猴梗婢规洟姊鸿ぐ鎺濇殥闁绘帪闄勭粋宥夊Χ閸涱亝鏂€闂佺粯蓱椤旀牠寮抽娑楃箚闁圭粯甯楅崵鍥┾偓瑙勬礃閸ㄥ灝鐣烽妸鈺婃晬婵炴垶锚缁ㄣ儵姊绘担渚劸闁活剙銈稿畷鏇㈠箣閻樼數顦梺鍝勭▉閸橀箖宕ｉ幘缁樼厱闁靛绲芥俊鍧楁煃椤栨稒绀嬮柡宀€鍠栭、娑橆煥閸愌勵潟闂備礁鎼張顒勬儎椤栫偛鏋侀柛灞剧矋鐎氭岸鏌熺紒妯虹妞ゆ梹甯″缁樻媴閻熼偊鍤嬬紓浣割儐鐢偤宕氶幒鎴犳殕闁告洦鍋嗛崢鎾⒑绾懏褰х紒鐘冲灴閹繝鏁撻悩宕囧幈闂佹枼鏅涢崰姘枔濡ソ褰掓偐閾忣偆绋囬梺瀹狀潐閸ㄥ潡骞冨▎鎾崇煑濠㈣泛锕﹁ぐ鍛磽閸屾艾鈧摜绮斿畷鍥偨婵ê澧庡畵渚€鏌涢幇闈涙灍闁稿﹤顭烽弻锕€螣閻氬绀嗛梺鍐叉惈閹冲繘鎮￠崘顔界厱婵犻潧妫楅鈺呮煃瑜滈崜娑㈠极婵犳氨宓侀煫鍥ㄦ媼濞差亶鏁傞柛娑变簼鐎氬ジ姊绘笟鈧鑽も偓闈涚焸瀹曪絾鎯旈妸锕€鈧灝螖閿濆懎鏆為柛濠傜仛缁绘盯骞嬮悙鑼懖濠电偛鎳庡Λ娑㈠焵椤掆偓閻忔艾顭垮Ο灏栧亾濮橆厽绶查柣锝囧厴閹垻鍠婃潏銊︽珖闂備線鈧偛鑻晶鎾煙椤旀儳鍘撮柡灞芥喘閺佸倿骞嗚濮规煡姊绘担鍛婅础闁告鍥ㄥ仱闁靛ě鍕簥闂佸憡娲﹂崹閬嶅磿鎼淬劌绠圭紒顔款潐椤﹥绻涢幘鎰佺吋闁哄本娲熷畷鐓庘攽閸ヨ埖锛佺紓鍌欑閻礁鈻旈弴鐘愁潟闁圭儤顨呯粻鐢告煙閻戞ê鐏嶉柟绋垮暣濮婃椽鎮烽幍顔炬殯闂佹悶鍔岀紞濠囧春閻愬搫绠ｉ柨鏇楀亾妤犵偑鍨介弻宥夊传閸曨偀鍋撻幖浣哥濠电姴鍟╃换鍡涙煟閹板吀绨婚柍褜鍓氶悧鐘荤嵁閹邦厹鍋呴柛鎰╁妿閿涙稑鈹戦埥鍡楃仴濠㈢懓鐗婇幈銊╁礃濞村鏂€闂佺粯锚绾绢參銆傞弻銉︾厓闂佸灝顑呯粭鎺楁婢舵劖鐓ユ繝闈涙閸ｆ椽鏌涢悢鍝勪槐闁哄本鐩幃銏ゅ传閸曨亝鍩涚紓鍌欐祰妞村摜鏁悙鍝勭劦妞ゆ帒锕︾粔鐢告煕鐎ｎ亜顏柟顔斤耿楠炴﹢顢欓悾灞藉箰濠电偠鎻徊鍧椻€﹂崼銉ユ辈闁挎繂顦伴悡娑樏归敐鍥剁劸闁逞屽墮閻忔繈鎮惧畡閭︾叆闁糕檧鏅滈瀷闂傚倷鑳剁划顖炲箰閹间緡鏁勫璺侯煬濞兼牗绻涘顔荤盎缂佲偓閸儲鐓欑紓浣姑粭鍌滅磼閵娧冨付闁宠鍨块幃娆撳箵閹烘棃鐛撻梻浣瑰濞插繘宕归挊澶屾殾闁告繂瀚уΣ鍫ユ煏韫囨洖啸妞ゆ挻妞藉娲箰鎼粹懇鎷婚梺鍝勬媼閸嬪﹤鐣烽姀銈呯闁绘劏鏅滈弬鈧梻浣虹帛閸旀牕顭囧▎鎾村€堕柨鏇炲€归悡銉╂煛閸ャ儱濡煎ù婊勫閳ь剚顔栭崰鎾诲礉瀹ュ洦宕叉繝闈涙－濞尖晜銇勯幘璺轰汗闁稿鎹囬幃浠嬪川婵犲嫬骞堟俊鐐€ら崢浠嬪垂閸偆顩查柣鎰劋閻撴瑩鏌涢幇顒€绾ч柛鐘筹耿閺屸€崇暆鐎ｎ剛锛熸繛瀵稿缁犳挸鐣峰鍡╂Х濠碘剝褰冮悧鍡涒€旈崘顔嘉ч幖绮光偓鑼嚬缂傚倷娴囬褔鎮ч幘鑽ゅ祦濠电姴鍋嗛悡銉╂煕椤愶絿绠撻柣搴幖椤啴濡堕崱妤€衼缂備焦鐓＄粻鏍€佸▎鎺旂杸婵炴垶鐟㈤幏濠氭⒑缁嬫寧婀伴柣鐔濆洤绀夌€广儱顦伴悡娆愩亜閺冨洤浜归柛鈺嬬悼閳ь剝顫夊ú妯兼崲閸曨剛顩烽柨鏂垮⒔妞规娊鎮楅敐搴″濠殿喗妞藉缁樻媴閸濆嫪缂撻梺绋垮閻撯€崇暦濠婂喚娼╅悹鍝勬惈閹偤姊洪崫鍕窛闁稿鍔欏銊︾鐎ｎ偆鍘藉┑鈽嗗灥濞咃綁鏁嶅鍡愪簻闁挎繂妫涢崣鈧梺鍝勭焿缁绘繂鐣烽妸鈺婃晩闁诡垎鍐摋闂傚倷绀侀幖顐﹀嫉椤掑嫭鍎庢い鏍仜閺勩儵鏌涢弴銊ョ仩缂佲偓閸岀偞鐓熸繛鍡楄嫰娴滅偓绻涚€涙鐭ゅù婊勭矒閹儳鐣￠幍铏杸闁诲函缍嗘禍婊呯玻濞戙垺鐓熼幖娣灮閳洟鎳ｉ妶澶嬬厵闂佸灝顑嗛妵婵囨叏婵犲偆鐓肩€规洘甯掗～婵嬪础閻戝棙婢戦梻鍌欒兌閸庣敻寮查埡鍛瀭闁割偅娲栫粻鏍煛婢跺鍎ラ柣鎾卞劦閺屾稑鈽夐崡鐐典化闂佹寧绋掔划鎾愁潖缂佹绡€閹肩补鈧尪鍩呴梻浣告啞閿曘垻绮婚弽顓炵畾閻忕偠袙閺嬪酣鏌熼悙顒佺稇闁谎冨濮婅櫣绱掑Ο鑽ゅ弳闂佺濮ょ划搴☆嚕瑜旈崺鈧い鎺戝閳锋帡鏌涚仦鐐殤濠⒀勭〒缁辨帞鈧綆鍋呯亸浼存煛閸涱厾鍩ｅ┑顔瑰亾闂侀潧绻堥崹娲晬韫囨稒鈷戦柛娑橈功閹斥偓闂佺瀛╂繛濠囧箚鐏炶В鏋庨柟鎯ь嚟閸樻悂鏌ｈ箛鏇炰粶濠⒀傜矙婵℃挳骞掗幊銊ョ秺閹亪宕ㄩ婊勬闂備浇顕栭崰妤呮偡閳哄懌鈧礁螖閸涱厾锛滃┑鐐村灥瀹曨剟骞冪€ｎ喗鈷掑ù锝囩摂閸ゆ瑩鎮楀☉鎺撴珖缂侇喗鐟╅獮鎺楀籍閳ь剙鐣烽弻銉︾厱妞ゆ劧绲跨粻銉︾箾閸忚偐澧紒缁樼☉椤斿繘顢欓懡銈囨晨缂傚倷鑳舵慨鎶藉础閹剁晫宓侀柡宥庣仈鎼搭煈鏁嗛柍褜鍓熼幃姗€鍩￠崒妯圭盎闂佹寧妫侀～澶娾枍閸℃瑧纾肩紓浣诡焽閵嗘帡鏌嶈閸撴氨绮欓幒妞烩偓锕傚炊椤掍礁鍓归柣搴秵閸撴稓澹曢崗绗轰簻闁哄啫娲よ閻庤娲栭惌鍌炲蓟閺囥垹骞㈤柡鍥╁濡差喖顪冮妶搴′簼缂佽鐗撻獮濠傤煥閸繂鐎銈嗗姧缁茶儻銇愰幖浣光拻闁稿本鐟ч崝宥夋煙椤旇偐鍩ｇ€规洘娲熼、娑㈡倷閼碱剦妲烽柣搴″帨閸嬫捇鏌涢弴銊ュ濞寸姍鍥ㄢ拺闁告繂瀚峰Σ鍝ョ磼閵娾晙鎲剧€殿喓鍔嶇粋鎺斺偓锝庡亞閸樹粙姊鸿ぐ鎺戜喊闁告挻鐩獮妤呮偐閻㈢數锛濋悗骞垮劚濡鎮橀敃鍌涚厪闁搞儜鍐句純濡ょ姷鍋涢鍛村煘閹达箑鐐婇柤鍝ヮ暜缁辨姊婚崒娆戝妽閻庣瑳鍛床闁告洦鍨扮壕缁樼箾閹存瑥鐒洪柡浣告喘閺屾稖绠涢弴鐐蹭粯缂備礁澧庨崑銈夊箖濡ゅ懏鏅柛妤冨仜椤忊晜銇勮箛鎾剁Ш婵﹤顭峰畷鎺戭潩椤戣棄浜鹃柟闂寸绾惧綊鏌熼梻瀵稿妽闁稿顑呴埞鎴︽偐閹绘帩浼€闂佹椿鍘介悷鈺呭蓟閻旂厧绠氱憸宥夈€傞崘娴嬫闁规儳纾晥闂佸搫鏈惄顖炵嵁閸ヮ剦鏁囬柣姗嗗枛娴狅箓姊绘担濮愨偓鈧柛瀣崌閺屻倝骞侀幒鎴濆婵炲瓨绮嶇划鎾诲蓟閿熺姴绀冮柨婵嗘噸婢规洟姊绘担渚劸閻庢稈鏅滅换娑欑節閸屾粍娈鹃梺闈涚箞閸婃洜绮荤紒妯镐簻闁哄啫鍊瑰▍鏇㈡煕濮椻偓濞佳団€旈崘顔嘉ч柛鈩冪懃椤冣攽椤旇婊堝礉瀹ュ鐤鹃柛顐ｆ礃閺呮粓姊哄▎鎯х仩濞存粓绠栭幃宄扳枎韫囨搩浠剧紓浣插亾闁割偁鍎查悡鏇㈡煟濡櫣锛嶅褏鏁婚弻鏇㈠幢閺囩媭妲梺瀹犳椤︻垵鐏掗梻浣哥仢椤戞劕顭囬弮鍫熲拻濞达絽鎲＄拹锟犳煕鎼存稑鈧繈鐛幇鏉跨濞撴艾娲﹂弲婊堟⒑闂堚晛鐦滈柛妯哄⒔婢规洘绂掔€ｎ偆鍘遍柣蹇曞仦瀹曟ɑ绔熷鈧弻宥堫檨闁告挾鍠栬棢闁规崘娉涢崹婵嬫煕椤愩倕鏋旈柣鐔风秺閺屽秷顧侀柛鎾跺枛婵″瓨绗熼埀顒€顕ｆ禒瀣垫晣闁绘劘灏埀顒€鐏濋埞鎴︽倷閸欏妫為梺鍏兼た閸ㄨ京绮嬪澶婄鐟滃繒澹曢崗闂寸箚闁靛牆瀚崗宀勬煃閽樺妯€闁哄本鐩幃鈺佺暦閸ャ劌鍨遍梻浣侯攰濞呮洟骞戦崶褏鏆︾紒瀣嚦閺冣偓閹峰懘宕崟顐ゎ啈婵犵數濮撮惀澶愬级鎼存挸浜鹃柟鐗堟緲绾惧鏌熼崜褏甯涢柣鎾跺枑娣囧﹪濡堕崒姘闂佽瀛╅崙褰掑储閸撗冨灊閻庯綆浜堕崥瀣煕濠娾偓閼冲爼宕ｉ崱娑欌拺闁告挻褰冩禍鐐烘煕閻樿櫕宕岀€规洏鍨介獮鏍ㄦ媴閸忓瀚奸梻浣侯攰閸嬫劙宕戝☉銏犵婵°倕鎳忛悡娑㈡煕濞戝崬鏋ゆ繛鎻掝嚟閳ь剚顔栭崰妤呭箖閸岀偟宓侀悗锝庡枟閺呮粓鏌﹀Ο渚Т闁稿鎹囧畷濂稿即閻樻彃绲奸梻浣规偠閸庮垶宕曢柆宥呯闁靛牆顦伴悡鐔兼煥濠靛棭妲烽柟鏌ョ畺閺岋紕浠﹂悾灞濄倝鏌熼崣澶嬪唉鐎规洜鍠栭、鏇㈠Χ閸曨厾顦梻鍌氬€峰ù鍥ь浖閵娧呯焼濞达綀娅ｉ惌鎾绘煟閻旂顥嬮柡鍡畵閺岋綁寮崒妤佸珱闂佽桨绀侀崯鎾蓟閺囷紕鐤€闁哄洨鍊妷锔轰簻闁规儳鐡ㄩ妵婵囨叏婵犲偆鐓肩€规洘甯掗埢搴ㄥ箛椤斿搫浠掑┑鐘殿暯閸撴繈濡靛鍫濈閹艰揪绲介崹婵嬫煕椤愩倕鏋旈柣鐔风秺閺屽秷顧侀柛鎾寸懇閹箖鎮滈挊澶岀杸闂佸搫顦悘婵嬫倵椤撶儐娓婚柕鍫濇閳锋帡鏌涚€ｎ偅灏柍缁樻崌楠炲棜顦柡鈧禒瀣厽婵☆垱顑欓崵瀣偓瑙勬偠閸庡弶绌辨繝鍥舵晝妞ゆ劑鍨圭粻鐟邦渻閵堝骸澧柨鏇ㄤ簼娣囧﹪骞栨担鍝ュ幐闂佺鏈〃鍡欌偓姘偢濮婄粯鎷呴崨濠傛殘缂備礁鎼鈥崇暦閺囥垺鐒肩€广儱鎳庨崵鎴︽煙閸忚偐鏆橀柛鏂跨Ч閸╂盯骞嬮敂钘変缓濡炪倖鐗徊浠嬪几閵堝鐓曢柟鐑樻煥椤ｅジ鏌曢崶褍顏い銏℃礋婵偓闁靛繈鍩勯崬铏圭磽閸屾瑦绁板鏉戞憸閺侇噣骞掗弴鐘辫埅闂備浇宕垫慨鏉懨洪妶鍛傜喐绻濋崶褏鍔﹀銈嗗笂閻掞妇浜搁悽鐢电＜閺夊牄鍔嶇亸浼存煙瀹勭増鍤囩€规洘绮撻獮鎾诲箳閹存繍妫婃繝纰夌磿閸嬫垿宕愰弽顓炲瀭闁割偅娲橀崑锟犳煏婢跺牆濡垮ù婊堜憾閺岋絾鎯旈垾鍐茶緟闂佺顑嗛幑鍥蓟濞戙垹鍗抽柕濞垮劚楠炲姊洪崷顓熺効婵炲拑绲垮Σ鎰板箻鐎涙ê顎撻梺鍛婃尰瑜板啴宕滈崡鐑嗘富闁靛牆妫楅悘锕傛煟閻斿弶娅呴柣锝囧厴瀵挳鏁嶉崟顓熸緫闂備礁鎼崐鍛婃櫠閻ｅ瞼涓嶆慨姗嗗墻濞撳鏌曢崼婵囶棞濠殿啫鍛＜闁艰壈鍩栫涵鍓佺磼椤旂⒈鐓兼鐐搭焽缁辨帒螣閻撳骸绠洪梻鍌欑劍鐎笛呮崲閸屾娲Χ婢跺﹤鍋嶉梺鍦檸閸犳鎮″▎鎰╀簻闁哄倽顕ф俊鍏肩箾鐏炲倸鈧繈寮荤€ｎ喖鐐婃い鎺嶈兌閸樹粙姊洪崫鍕偓鎼佹倶濠靛柈锝囨嫚濞村鏂€闂佹枼鏅涢崯顐﹀煝閺囩喐鍙忓┑鐘插鐢稓绱掑Δ鍐ㄦ灈闁糕斁鍋撳銈嗗坊閸嬫挻銇勯鐐寸┛妞わ箑缍婇弻宥夊Ψ椤栨粎鏆ゅ銈冨灪閿曘垽骞冮埡鍛闁告縿鍎抽幉褰掓⒒閸屾瑨鍏屾い銏狅躬楠炴捇顢旈崱娆庣瑝闂佺鎻梽鍕磹閸ф鐓曟い顓熷灥娴滄牕霉濠婂嫮鐭掗柡宀€鍠栧畷顐﹀礋椤掑顥ｅ┑鐐茬摠缁挾绮婚弽褜娼栭柧蹇撴贡绾惧吋淇婇婊冨付闁愁亜缍婂娲濞戞艾顤€闂佽鍠栭崐鍧楀箖娴兼惌鏁嬮柍褜鍓欓悾宄邦潨閳ь剚淇婇崨濠冨劅闁靛繆妲呭Λ鍐倵鐟欏嫭纾搁柛鏃€鍨块妴浣糕枎閹剧补鎷婚柣搴ㄦ涧閹芥粍寰勯崟顖涒拻闁稿本鑹鹃埀顒傚厴閹偤鏁冮崒娑樺壋閻熸粎澧楃敮鎺楀垂閸屾埃鏀介柣妯虹枃婢规鐥幆褜鐓奸柡灞诲妼閳规垿宕遍埡鍌傦箓姊虹紒妯虹瑨闁诲繑宀告俊鐢稿礋椤栨氨顔婇梺绯曞墲閵囩偞绔熼弴銏♀拻濞达絿鎳撻婊勭箾鐠囇囨缂佸倸绉归、鏃堝礋闂堟稒顓块梺鑽ゅТ濞诧妇绮婇弶鎳筹綁宕奸悢绋垮伎濠殿喗顨呴悧蹇涘礆閺夋５鐟邦煥閸垻鏆梺鍝勮閸斿矂鍩為幋锕€骞㈡繛鎴炲閸嬫挸螖閳ь剟婀佸┑鐘诧工鐎氼喚绮婚悙鐑樼厵妞ゆ梻鐡旈悞鍓х磼閻樺磭娲寸€殿喗鎸抽幃銏ゅ川婵犲嫭顫滈梻鍌氬€风粈渚€骞栭锔藉剶濠靛倻顭堢粣妤呮煛瀹擃喖鎳忓▓楣冩⒑閹稿海绠撴俊顐ｎ殘濞嗐垽鎮欓悜妯衡偓鐢告煥濠靛棛鍑圭紒銊ヮ煼閺岋繝鍩€椤掑嫷鏁囬柕蹇ョ磿閸樹粙鏌熼崗鑲╂殬闁稿鍊曢…鍥箛椤撶姷顔曢梺鍛婄懃椤﹂亶鎯岄幒鏂哄亾鐟欏嫭纾婚柛妤€鍟块锝嗙鐎ｅ灚鏅ｅ┑鐘欏嫬鍔ゅù婊勫劤闇夐柨婵嗙墕閳ь兛绮欏顕€宕煎┑鍡欑崺婵＄偑鍊栭幐鐐垔鐎靛憡顫曢柡灞诲劜閳锋帒銆掑锝呬壕濠电偘鍖犻崶銊ヤ罕闂佺硶鍓濈粙鎴︽嫅閻斿皝鏀介柣妯诲絻娴滅偞绻涚亸鏍ㄦ珚闁哄被鍊濆畷顐﹀Ψ椤旇姤鐦滈梻浣烘嚀閸㈡煡鈥﹀畡閭︽綎闁惧繐婀遍惌娆撴煙缁嬪灝顒㈤柟鐑戒憾濮婃椽鎳￠妶鍛€梺鍛婃⒐閸ㄧ敻鎮惧畡鎷旀棃鍩€椤掑嫭鍋╂繝闈涱儏閻掑灚銇勯幒鎴濃偓褰掞綖閺囥垺鐓欓柣鎴烇供濞堛垽鏌℃担鍓插剶闁哄本绋戦埥澶愬础閻愯尙顔掑┑鐐差嚟婵敻骞冮崒姘兼綎闁惧繗顫夌€氭岸鏌嶉妷銊︾彧婵炲牜鍙冨娲川婵犲倸顫屾繝鈷€鍌滅煓鐎规洩缍佸畷鍗炩槈濞嗗本瀚奸梻浣告啞閹告槒銇愰崘鈺冾洸婵犻潧鐗忕壕濂告偣閸ャ劌绲婚柍褜鍓欓悘婵嬫偩閻戣棄绠ｆ繝鍨姇閹垶绻濋姀锝嗙【妞ゆ垵鎳忕粋鎺楊敇閻愨晜鏂€闂佺粯顭堥婊冾啅閵夆晜鍊垫慨妯煎帶瀵噣鎸婇悢鍏肩厱妞ゆ劑鍊曞▍蹇旂箾閸粎鐭欓柡宀嬬秮楠炲洭顢橀悙鐢点偡闂備礁鎼Λ瀵稿緤妤ｅ啨鈧啴濡烽埡鍌氣偓鐑芥煕濞嗗浚妯堟俊顐犲劚椤啴濡惰箛娑欘€嶉梺鐟版啞婵炲﹤顕ｆ繝姘櫢闁绘ɑ褰冮懓鍨攽鎺抽崐鏇㈡晝閵堝绀堥悹楦裤€€閺€鑺ャ亜閺冨倶鈧寮ㄧ紒妯圭箚闁绘劖澹嗛惌娆撴煏閸℃洜顦︽い顐ｇ矒閸┾偓妞ゆ帊妞掔换鍡涙煙闂傚顦﹂崬顖氼渻閵堝棙顥嗘俊顐㈠瀵悂鎮╃紒妯锋嫽闂佺鏈悷褔藝閿曞倹鐓欓悹鍥囧懐鐦堥梺璇″枤閸忔ɑ淇婇悜钘壩ㄩ幖娣灮閳藉鏌嶇憴鍕伌鐎规洟浜堕崺锟犲磼濠婂應鏋嗛梻鍌氬€烽悞锕傚磿瀹曞洦宕查柟鐑橆殔缁犵娀鏌熼幆褏浜柛瀣崌瀹曞綊顢曢妶鍛哗闂備礁鎼惌澶屾崲濠靛棛鏆﹂柛顐ｆ礀閻撴稑霉閿濆懏璐＄憸鐗堢懇濮婄粯绗熼埀顒勫焵椤掑倸浠滈柤娲诲灡閺呭爼顢欓幋鎺旂畾濡炪倖鍔х徊璺ㄧ不閻愮鍋撳▓鍨灈妞ゎ厾鍏樺顐﹀箻缂佹ê浜归梺鍦帛鐢偤鎮伴埡鍌欑箚闁绘劦浜滈埀顒佺墵瀵濡搁敂鑺ョ彿婵炲濮撮鍡涘磹閸ф鐓犳繛鏉戭儐濞呭洨鐥幑鎰棄闂囧鏌ㄥ┑鍡╂Ч婵炴嚪鍐ｆ斀闁炽儴娅曢崑銉╂煛鐏炲墽鈯曢柟顖涙煥閳藉螣瀹勯澹曞┑掳鍊曢幊搴ｇ不?
				List curApdMediaUploads = mediaUploadService.listAllMediaUploadsByKeyAndPerson(CC.APD + key, person);
				long curCountOfExistingAptItem = curApdMediaUploads != null ? curApdMediaUploads.size()/2 : 0;
				key = CC.APD + key + "_(" + curCountOfExistingAptItem + ")"; //key adjusted to APT_service_
			}

			MediaUpload media = initializeMediaUpload(fileName, key, person);
			// only when selected replace, the delete magic word will work.
			if (CC.OVERWRITE.equals(operation) && checkIfIsDeleteCommand(fileName, media)) {
				if(media.getId() != null) {
					mediaUploadService.remove(media);
					if (taoUtil.isTHUMNeeded(request, key, media.getPerson())) {
						MediaUpload mediaThumb = mediaUploadService.findMediaByKeyAndPerson(media.getFilepath() + "_thum",
								media.getPerson());
						if (mediaThumb != null) {
							mediaUploadService.remove(mediaThumb);
						}
					}
				}
				// delete popup text.
				List<TextContent> textContents = textContentService.findAllMatchedTextContents("%_" + media.getFilepath(),
						null, person);
				if (textContents != null) {
					for (TextContent textContent : textContents) {
						textContentService.remove(textContent);
					}
				}
				// delete description text.
				textContents = textContentService.findAllMatchedTextContents("%_" + media.getFilepath() + "_description", null,
						person);
				if (textContents != null) {
					for (TextContent textContent : textContents) {
						textContentService.remove(textContent);
					}
				}
				// service and product
				if (!key.startsWith(CC.APD) && tKeyStr.startsWith("service_")) {
					Service service = serviceService.findServiceByCatalogAndPerson(tKeyStr.substring(8) + "_" + i, person);
					if (service != null) {
						serviceService.deleteService(service);
					}
				} else if (!key.startsWith(CC.APD) && tKeyStr.startsWith("product_")) {
					Product product = productService.findProductByCatalogAndPerson(tKeyStr.substring(8), person);
					if (product != null) {
						productService.remove(product);
					}
				}

				if (key.startsWith(CC.APD) && isSubImageOperation) {
					for (long j = appendSameIndex + 1; j < countOfExistingAptItem; j++) {
						String oldApdKey = apdBaseKey + "_(" + j + ")";
						String newApdKey = apdBaseKey + "_(" + (j - 1) + ")";
						MediaUpload oldApdMedia = mediaUploadService.findMediaByKeyAndPerson(oldApdKey, person);
						if (oldApdMedia != null) {
							oldApdMedia.setFilepath(newApdKey);
							mediaUploadService.persist(oldApdMedia);
						}
						if (isThumNeeded) {
							MediaUpload oldApdThumb = mediaUploadService.findMediaByKeyAndPerson(oldApdKey + "_thum", person);
							if (oldApdThumb != null) {
								oldApdThumb.setFilepath(newApdKey + "_thum");
								mediaUploadService.persist(oldApdThumb);
							}
						}
					}
				} else if (!key.startsWith(CC.APD)) {
					needCleanUp = true;
				}
			} else {
				// ------------if need to save to disk-------------
				// File tDiskFile = new File(serverInfo.getDataPath() + tKeyString + tFormat);
				// content.transferTo(tDiskFile);

				processFileType(fileName, media);

				// save images to db----------------------------------------------------------------
				mediaUploadService.saveImageToDisk(model, content, fileName, media, key, request, response);

				// service and product
				if(CC.APPEND.equals(operation) && !key.startsWith(CC.APD)) {
					if (key.startsWith("service_")) {
						Service service = new Service();
						service.setC1(key.substring(8));
						service.setRecordStatus((int)i);
						service.setPerson(person);
						serviceService.persist(service);
					} else if (key.startsWith("product_")) {
						Product product = new Product();// .findProductByCatalogAndPerson(tKeyStr.substring(8), person);
						product.setC1(key.substring(8));
						product.setPerson(person);
						productService.persist(product);
					}
				}
			}
			if (isSubImageOperation) {
				appendSameIndex++;
			}
			i++;
		}

		if (needCleanUp) {
			taoImage.vacumGellaryImages(person, tKeyStr);
		}

		response.setHeader("refresh","0;URL=menu_" + request.getParameter("position").substring(8));
		return buildPageForMenu(model, request, response, null);
	}

	private String checkValidation(String key, String langPrf, Person person) {
		StringBuilder sb = new StringBuilder();
		String[] strings = StringUtils.split(key, '_');
		int[] menuIdxes = new int[3];
		for (int i = 0; i < strings.length; i++) {
			try {
				int number = Integer.valueOf(strings[i]);
				menuIdxes[0] = number;
				try {
					menuIdxes[1] = Integer.valueOf(strings[i + 1]);
					menuIdxes[2] = Integer.valueOf(strings[i + 2]);
				} catch (Exception e) {
					// do nothing
				}
				break;
			} catch (Exception e) {
				sb.append(strings[i]);
				sb.append('_');
			}
		}
		sb.append(taoUtil.completeMenuIdx(menuIdxes[0], menuIdxes[1], menuIdxes[2], langPrf, person));
		return sb.toString();
	}

	private MediaUpload getOrInitializeMediaUpload(String tFileName, String keyStr, Person person) {
		MediaUpload media = null;

		// the types that always generate new ones.
		if (!(keyStr.contains("personalDocument") || keyStr.contains("careerApply")
				|| keyStr.contains("emailBackGround"))) {
			media = mediaUploadService.findMediaByKeyAndPerson(keyStr, person);
		}

		return media != null ? media : createEmptyMediaUpload(keyStr, person);
	}

	private MediaUpload initializeMediaUpload(String tFileName, String keyStr, Person person) {

		MediaUpload tMedia = mediaUploadService.findMediaByKeyAndPerson(keyStr, person);
		if (tMedia == null) {
			tMedia = createEmptyMediaUpload(keyStr, person);
		}

		return tMedia;
	}

	private MediaUpload createEmptyMediaUpload(String keyStr, Person person) {
		MediaUpload media;
		media = new MediaUpload();
		media.setPerson(person);
		media.setFilepath(keyStr);
		return media;
	}

	private boolean checkIfIsDeleteCommand(String fileName, MediaUpload media) {
		return (fileName.toLowerCase().startsWith(CC.cloak) || fileName.toLowerCase().startsWith(CC.delete));
	}

	private void processFileType(String tFileName, MediaUpload media) {
		media.setContentType(CC.DEFAULT_IMAGE_TYPE);
		int tPos = tFileName.lastIndexOf(".");
		if (tPos > 0 && tPos < tFileName.length() - 1 && tPos >= tFileName.length() - 5) {
			media.setContentType(tFileName.substring(tPos));
		}
	}

	private String checkSize(Model uiModel, String keyString, FileItem tFileItem, Person person) {
		if (tFileItem.getSize() > 6000000) {
			if (person != null) {
				if (CC.ADAM.equals(person.getName()) || person.getRecordStatus() == CC.UNLIMITED_UPLOD_SIZE) {
					return null;
				}
			}

			uiModel.addAttribute("notice",
					tFileItem.getName() + "is NOT submitted! Please submit only file less than 5M.");
			if ("personalDocument".equals(keyString)) {
				return "dashboard";
			} else if ("careerApply".equals(keyString)) {
				return "careerApply";
			} else {
				return "sendEmail";
			}
		} else {
			return null;
		}
	}

	// products------------------------------------------
	// =====================================product=====================================
	@RequestMapping(value = "/updateProductInfo", method = RequestMethod.POST)
	public String updateProductInfo(Model uiModel, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		Long id = Long.valueOf(request.getParameter("id"));
		String partNo = request.getParameter("partNo");
		Product tProduct = productService.findProductsByPartNoAndPerson(partNo, person);

		if (tProduct != null && tProduct.getId().longValue() != id.longValue()) {
			uiModel.addAttribute("Update_ErrorMessage",
					"the PartNumber is already used by an other product, please check and input again.");
			uiModel.addAttribute("product", tProduct);
			return "products/update";
			// return
			// SpringApplicationContext.getApplicationContext().getBean("userAccountController",
			// UserAccountController.class).updateForm(tUserAccount.getId(), model);//with
			// error message.
		} else if (tProduct == null) {
			tProduct = productService.findProduct(id);
		}

		tProduct.setPartNo(partNo);
		tProduct.setName(request.getParameter("name"));
		tProduct.setProducedplace(request.getParameter("producedplace"));
		tProduct.setC1(request.getParameter("c1"));
		tProduct.setC2(request.getParameter("c2"));
		tProduct.setC3(request.getParameter("c3"));
		tProduct.setLitorweight(request.getParameter("litorweight"));
		tProduct.setUnit(request.getParameter("unit"));
		tProduct.setPackagesname(request.getParameter("packagesname"));
		tProduct.setItemquantity(request.getParameter("itemquantity"));

		String tNoNumException = null;
		String tIntStr = request.getParameter("lenth");
		if (StringUtils.isNumeric(tIntStr))
			tProduct.setLenth(Integer.parseInt(request.getParameter("lenth")));
		else
			tNoNumException = "lenth";

		tIntStr = request.getParameter("width");
		if (StringUtils.isNumeric(tIntStr))
			tProduct.setWidth(Integer.parseInt(request.getParameter("width")));
		else
			tNoNumException = "width";

		tIntStr = request.getParameter("height");
		if (StringUtils.isNumeric(tIntStr))
			tProduct.setHeight(Integer.parseInt(request.getParameter("height")));
		else
			tNoNumException = "height";

		tIntStr = request.getParameter("price1");
		if (StringUtils.isNumeric(tIntStr))
			tProduct.setPrice1(Integer.parseInt(request.getParameter("price1")));
		else
			tNoNumException = "price1";

		tIntStr = request.getParameter("price2");
		if (StringUtils.isNumeric(tIntStr))
			tProduct.setPrice2(Integer.parseInt(request.getParameter("price2")));
		else
			tNoNumException = "price2";

		tIntStr = request.getParameter("price3");
		if (StringUtils.isNumeric(tIntStr))
			tProduct.setPrice3(Integer.parseInt(request.getParameter("price3")));
		else
			tNoNumException = "price3";

		if (tNoNumException != null) {
			uiModel.addAttribute("Update_ErrorMessage", tNoNumException
					+ " was input with character, while only number allowed. Please input right number.");
			uiModel.addAttribute("product", tProduct);
			return "products/update";
		}
		// return
		// SpringApplicationContext.getApplicationContext().getBean("userAccountController",
		// UserAccountController.class).updateForm(tUserAccount.getId(), model);//with
		// error message.

		tProduct.setDescription(request.getParameter("description"));
		productService.persist(tProduct);
		// return index(uiModel, request);
		String tURLStr = "menu" + request.getParameter("c1").substring(7);
		return "redirect:/" + tURLStr;
	}

	@RequestMapping(value = "/mycarts", produces = "text/html")
	public String getMyCarts(Model uiModel, Person person) {
		String userName = userContextService.getCurrentUserName();
		UserAccount userAccount = userAccountService.findUserAccountByName(TaoEncrypt.enrichName(userName, person));
		uiModel.addAttribute("carts", cartService.findCartsByUser(userAccount));
		return "carts/show";
	}

	// @NOTE:if put this method into product controller, then have to supply a
	// method to resonponse for
	// "/products/products/searchProductUrl".
	// @NOTE:if don't use a new path like following, while use only the parameter
	// instead, then can not pass compilation
	// when moved to MainController.java.

	@RequestMapping(value = "/searchServiceUrl", method = {RequestMethod.GET, RequestMethod.POST})
	public String searchServices(@RequestParam(value = "searchServices", required = false) String search, Model model,
			HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		String langPrf = taoUtil.getLangPrfWithDefault(request);
		String searchContent = request.getParameter("searchContent");
		if (StringUtils.isBlank(searchContent)) {
			searchContent = search;
		}
		String normalizedSearch = StringUtils.defaultString(searchContent).trim().toLowerCase();

		List<Feature> features = new ArrayList<>();
		List<List<String>> imageKeyLists = new ArrayList<>();
		List<List<String>> descriptions = new ArrayList<>();
		List<List<String>> prices = new ArrayList<>();
		List<List<String>> visibleStatusList = new ArrayList<>();
		List<String> serviceHeights = new ArrayList<>();
		List<String> groupTitles = new ArrayList<>();

		List<String> keys = new ArrayList<>();
		if (StringUtils.isNotBlank(normalizedSearch) && person != null) {
			boolean canSearchAllServices = hasEmployeeOrAboveSearchAccess(request.getSession());
			Set<String> visibleFeatureKeys = canSearchAllServices
					? null
					: new HashSet<>(featureService.findAllVisibleServiceKeysByPerson(person));
			Map<String, Service> dedupedServices = new LinkedHashMap<>();
			for (Service service : serviceService.searchServicesByBarcodeOrMnemonic(normalizedSearch, person)) {
				String imageKey = normalizeServiceImageKey(service.getC1());
				if (StringUtils.isBlank(imageKey) || dedupedServices.containsKey(imageKey)) {
					continue;
				}
				if (!canSearchAllServices && !visibleFeatureKeys.contains(imageKey)) {
					continue;
				}
				dedupedServices.put(imageKey, service);
			}
			keys.addAll(dedupedServices.keySet());
		}

		imageKeyLists.add(keys);
		taoUtil.fillInDescriptions(langPrf, person, descriptions, keys);
		taoUtil.fillInPrices(person, request.getSession(), prices, keys);
		List<String> visibleStatus = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			visibleStatus.add(null);
		}
		visibleStatusList.add(visibleStatus);

		Feature feature = new Feature();
		feature.setId(0L);
		feature.setMenuIdx("search");
		feature.setRefMenuIdx("search");
		features.add(feature);

		serviceHeights.add(String.valueOf(resolveSearchResultItemHeight(request.getSession())));
		groupTitles.add(StringUtils.isBlank(normalizedSearch) ? "Search Results" : "Search Results (" + normalizedSearch + ")");

		model.addAttribute("features", features);
		model.addAttribute("imageKeys", imageKeyLists);
		model.addAttribute("descriptions", descriptions);
		model.addAttribute("prices", prices);
		model.addAttribute("visibleStatusList", visibleStatusList);
		model.addAttribute("service_heights", serviceHeights);
		model.addAttribute("groupTitles", groupTitles);
		model.addAttribute("shoppingCartMode", CC.TRUE);
		model.addAttribute(CC.show_service_cBox, resolveSearchCustomizeValue(CC.show_service_cBox, person, CC.TRUE));
		model.addAttribute("support_times", resolveSearchCustomizeValue("support_times", person, "false"));

		return "/generalFeaturePage";
	}

	private String normalizeServiceImageKey(String imageKey) {
		if (StringUtils.isBlank(imageKey)) {
			return "";
		}
		return imageKey.startsWith("service_") ? imageKey.substring(8) : imageKey;
	}

	private boolean hasEmployeeOrAboveSearchAccess(HttpSession session) {
		Object userRole = session.getAttribute(CC.user_role);
		if (userRole == null) {
			return false;
		}
		String role = userRole.toString();
		return role.contains(CC.ROLE_EMPLOYEE)
				|| role.contains(CC.ROLE_MANAGER)
				|| role.contains(CC.ROLE_ADMIN)
				|| role.contains(CC.ROLE_PRINTER);
	}

	private int resolveSearchResultItemHeight(HttpSession session) {
		Person person = (Person) session.getAttribute(CC.CLIENT);
		int serviceHeight = 190;
		String configuredHeight = resolveSearchCustomizeValue(CC.service_height, person, "190");
		try {
			serviceHeight = Integer.parseInt(configuredHeight);
		} catch (NumberFormatException ignored) {
		}
		String showServiceBox = resolveSearchCustomizeValue(CC.show_service_cBox, person, CC.TRUE);
		if ("true".equalsIgnoreCase(showServiceBox) && serviceHeight < 260) {
			serviceHeight = 260;
		}
		return serviceHeight;
	}

	private String resolveSearchCustomizeValue(String key, Person person, String defaultValue) {
		Customize customize = person == null ? null : customizeService.findCustomizeByKeyAndPerson(key, person);
		if (customize == null || StringUtils.isBlank(customize.getCusValue()) || "null".equalsIgnoreCase(customize.getCusValue())) {
			customize = customizeService.findCustomizeByKeyAndPerson(key, personService.getAdam());
		}
		if (customize == null || StringUtils.isBlank(customize.getCusValue()) || "null".equalsIgnoreCase(customize.getCusValue())) {
			return defaultValue;
		}
		return customize.getCusValue();
	}
	@RequestMapping("/searchProductUrl")
	public String searchProduct(@RequestParam(value = "searchProducts", required = true) String search, Model uiModel,
			HttpServletRequest request) {
		if (StringUtils.isBlank(request.getParameter("searchContent"))) {
			uiModel.addAttribute("productAmount", 0);
			return "/generalCatalogPage";
		} else
			search = request.getParameter("searchContent").toString().toLowerCase();

		List<Product> tProducts = productService.findAllProducts();
		List tResultList = new ArrayList();
		for (int i = tProducts.size() - 1; i >= 0; i--) {
			Product tProduct = tProducts.get(i);
			StringBuilder tContent = new StringBuilder();

			String c2 = tProduct.getC2();
			if (StringUtils.isNotBlank(c2)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(c2.toLowerCase());
			}

			String c3 = tProduct.getC3();
			if (StringUtils.isNotBlank(c3)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(c3.toLowerCase());
			}

			String description = tProduct.getDescription();
			if (StringUtils.isNotBlank(description)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(description.toLowerCase());
			}

			String name = tProduct.getName();
			if (StringUtils.isNotBlank(name)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(name.toLowerCase());
			}

			String packagesname = tProduct.getPackagesname();
			if (StringUtils.isNotBlank(packagesname)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(packagesname.toLowerCase());
			}

			String partNo = tProduct.getPartNo();
			if (StringUtils.isNotBlank(partNo)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(partNo.toLowerCase());
			}

			String itemquantity = tProduct.getItemquantity();
			if (StringUtils.isNotBlank(itemquantity)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(itemquantity.toLowerCase());
			}

			String producedplace = tProduct.getProducedplace();
			if (StringUtils.isNotBlank(producedplace)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(producedplace.toLowerCase());
			}

			String unit = tProduct.getUnit();
			if (StringUtils.isNotBlank(unit)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(unit.toLowerCase());
			}

			String lenth = String.valueOf(tProduct.getLenth());
			if (StringUtils.isNotBlank(lenth)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(lenth);
			}

			String width = String.valueOf(tProduct.getWidth());
			if (StringUtils.isNotBlank(width)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(width);
			}

			String litorweight = String.valueOf(tProduct.getLitorweight());
			if (StringUtils.isNotBlank(litorweight)) {
				tContent.append(CC.TEMP_SEP_PROD_FIELD_INFO);
				tContent.append(litorweight);
			}

			if (tContent.indexOf(search) > 0)
				tResultList.add(tProduct);
		}
		tResultList = taoUtil.languageRelevantProcess(tResultList, taoUtil.getLangPrfWithDefault(request));

		uiModel.addAttribute("products", tResultList);
		uiModel.addAttribute("productAmount", tResultList.size());

		return "/generalCatalogPage";
	}

	@RequestMapping(value = "/showProductJson/{id}", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<String> showJson(@PathVariable("id") Long id, @RequestParam(CC.LANG) String lang) {
		Product product = productService.findProduct(id);
		HttpHeaders headers = new HttpHeaders();
		if (lang.length() == 2)
			lang = lang + "_";
		headers.add("Content-Type", "application/json; charset=utf-8");
		if (product == null) {
			return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
		}
		// set the display name for product
		String tName = product.getName();
		String tDescrip = product.getDescription();
		String[] tNames = tName.split("~");
		String[] tDescrips = tDescrip.split("~");
		boolean matched = false;
		for (int j = 0; j < tNames.length; j++) {
			if (tNames[j].toLowerCase().trim().startsWith(lang.toLowerCase())) {
				product.setLocalName(tNames[j].trim().substring(3));
				matched = true;
				break; // go and work on next product.
			}
		}
		// no match, then use the first name.
		if (!matched)
			product.setLocalName(tNames[0].indexOf("_") == 2 ? tNames[0].substring(3) : tNames[0]);

		matched = false;
		for (int j = 0; j < tDescrips.length; j++) {
			if (tDescrips[j].toLowerCase().trim().startsWith(lang.toLowerCase())) {
				product.setLocalDescription(tDescrips[j].trim().substring(3));
				matched = true;
				break; // go and work on next product.
			}
		}
		// no match, then use the first name.
		if (!matched)
			product.setLocalDescription(tDescrips[0].indexOf("_") == 2 ? tDescrips[0].substring(3) : tDescrips[0]);

		return new ResponseEntity<String>(product.toJson(), headers, HttpStatus.OK);
	}

	// ====================================feature=====================================
	@RequestMapping(value = "/deleteFeatureGroup", method = RequestMethod.POST)
	public String deleteFeatureGroup(Model model, HttpServletRequest request, HttpServletResponse response) {
		String refMenuIdx = request.getParameter("refMenuIdxForDelete");
		if (StringUtils.isBlank(refMenuIdx)) {
			return buildPageForMenu(model, request, response, null);
		}

		Person person = taoUtil.getCurPerson(request);
		String menuIdx = request.getSession().getAttribute(CC.menuIdx).toString();

		List<Feature> features = featureService.findAllFeaturesByMenuIdxAndPerson(menuIdx, person);
		if (features == null) {
			return buildPageForMenu(model, request, response, null);
		}

		String key = "";
		for (int i = features.size() - 1; i >= 0; i--) {
			Feature feature = features.get(i);
			if (refMenuIdx.equals(feature.getRefMenuIdx())) {
				key = menuIdx + "-" + feature.getRefMenuIdx();
				featureService.remove(feature);
				break;
			}
		}
		//delete relevant feature group title.
		List<TextContent> textContents = textContentService.findAllMatchedTextContents("%_feature_" + key + "_groupTitle", person);
		for (int i = textContents.size() - 1; i >= 0; i--) {
			TextContent textContent = textContents.get(i);
			textContentService.remove(textContent);
		}

		// return "redirect:/menu_" + menuIdx;
		return buildPageForMenu(model, request, response, null);
	}
	
	@RequestMapping(value = "/{client}/addNewService/{imageKey}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> addNewService(@PathVariable(CC.CLIENT) String client,
			@PathVariable("imageKey") String imageKey, HttpServletRequest request) {

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return addNewService(imageKey, request);
	}
	
	@RequestMapping(value = "addNewService/{imageKey}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> addNewService(@PathVariable("imageKey") String imageKey, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		String key = request.getParameter("featureId");
		key = key.substring(8);
		Service service = new Service();
		service.setPerson(person);
		service.setC1(key + "_" + (serviceService.countServicesByKeyAndPerson(key, person)  + 1));
		service.setRecordStatus((int)serviceService.countServicesByKeyAndPerson(key, person)  + 1);
		serviceService.persist(service);
		
		return new ResponseEntity<String>(HttpStatus.OK);
	}
	
	@RequestMapping(value = "/{client}/updateFeature/{imageKey}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> updateFeature(@PathVariable(CC.CLIENT) String client,
			@PathVariable("imageKey") String imageKey, HttpServletRequest request) {

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return updateFeature(imageKey, request);
	}

	@RequestMapping(value = "updateFeature/{imageKey}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> updateFeature(@PathVariable("imageKey") String imageKey, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		String featureId = request.getParameter("featureId");
		Feature feature = featureService.findFeature(Long.valueOf(featureId));
		String itemsToShow = feature.getItemsToShow();
		LinkedHashSet<String> selectedItems = new LinkedHashSet<>();
		if (StringUtils.isNotBlank(itemsToShow)) {
			for (String item : StringUtils.split(itemsToShow, ',')) {
				if (StringUtils.isNotBlank(item)) {
					selectedItems.add(item.trim());
				}
			}
		}
		if (!selectedItems.remove(imageKey)) {
			selectedItems.add(imageKey);

			String ref = feature.getRefMenuIdx();
			List<String> imageKeys = serviceService.listAllServiceKeyByKeyAndPerson(ref, person);

			StringBuilder sb = new StringBuilder();
			for (String item : imageKeys) {
				if (selectedItems.contains(item)) {
					if (sb.length() > 0) {
						sb.append(',');
					}
					sb.append(item);
				}
			}
			itemsToShow = sb.toString();
		} else {
			itemsToShow = StringUtils.join(selectedItems, ',');
		}
		feature.setItemsToShow(itemsToShow);
		featureService.persist(feature);
		return new ResponseEntity<String>(HttpStatus.OK);
	}

	@RequestMapping(value = "/{client}/updatePrinter/{imageKey}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> updatePrinter(@PathVariable(CC.CLIENT) String client,
			@PathVariable("imageKey") String imageKey, HttpServletRequest request) {

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return updatePrinter(imageKey, request);
	}

	@RequestMapping(value = "updatePrinter/{imageKey}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> updatePrinter(@PathVariable("imageKey") String imageKey, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		// String featureId = request.getParameter("featureId");
		// Feature feature = featureService.findFeature(Long.valueOf(featureId));
		// String itemsToShow = feature.getItemsToShow();

		Service service = serviceService.findServiceByCatalogAndPerson(imageKey, person);
		if (service == null) {
			service = new Service();
			service.setPerson(person);
			service.setC1(imageKey);
			service.setC3(",");
			service.setRecordStatus(Integer.valueOf(imageKey.substring(imageKey.lastIndexOf("_") + 1)));
		}
		String curPrinterName = TaoEncrypt.stripUserName(userContextService.getCurrentUserName());
		String printersStr = service.getC3();// c3 is now used to pu printers string.
		if (printersStr == null) {
			printersStr = ",";
		}
		int p = printersStr.indexOf("," + curPrinterName + ",");
		if (p < 0) {
			if (!printersStr.endsWith(",")) {
				printersStr = printersStr + ",";
			}
			printersStr = printersStr + curPrinterName + ",";
		} else {
			printersStr = printersStr.substring(0, p) + printersStr.substring(p + curPrinterName.length() + 1);
		}
		service.setC3(printersStr);
		serviceService.persist(service);
		return new ResponseEntity<String>(HttpStatus.OK);
	}

	@RequestMapping(value = "/{client}/updateSelection/{imageKey}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> updateSelection(@PathVariable(CC.CLIENT) String client,
			@PathVariable("imageKey") String imageKey, HttpServletRequest request) {

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return updateSelection(imageKey, request);
	}

	@RequestMapping(value = "updateSelection/{imageKey}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> updateSelection(@PathVariable("imageKey") String imageKey,
												  HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		String changeType = request.getParameter("change");

		@SuppressWarnings("unchecked")
		Map<String, Integer> selectedItemsMap = (Map<String, Integer>) request.getSession().getAttribute(CC.selectedItems);
		if (selectedItemsMap == null) {
			selectedItemsMap = new LinkedHashMap<>();
		} else if (!(selectedItemsMap instanceof LinkedHashMap)) {
			selectedItemsMap = new LinkedHashMap<>(selectedItemsMap);
		}
		//闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇缁♀偓闂侀€炲苯澧撮柡灞芥椤撳ジ宕ㄩ姘曞┑锛勫亼閸婃牜鏁幒妤€纾圭憸鐗堝笒閸氬綊鏌嶈閸撶喖寮婚敐鍡樺劅闁靛繒濮村В鍫ユ⒑閸涘﹦鎳冮柛鐕佸亰閹儳鐣￠幍顔芥闂佹悶鍎滅仦缁㈡％闂備浇顕ч崙鐣屽緤婵犳艾绀夐悗锝庘偓顖嗗吘鏃堝川椤旇瀚奸梻渚€娼荤€靛矂宕㈡總绋跨閻庯綆鍠楅悡鏇㈡煏婵炲灝鍔ょ紒澶庢閳ь剝顫夊ú姗€宕濆▎鎾崇畺婵炲棗娴氶崯鍛亜閺冨洦顥夐柣锔界矒濮婄粯绗熼埀顒€顭囪閹囧幢濡炪垺绋戣灃闁告劦浜為悾鍫曟⒑閸︻厼鍔嬮柟姝屽煐缁傚秴顭ㄩ崼鐔哄弳闂佸搫鍊藉▔鏇熺閵忊懇鍋撶憴鍕碍婵☆偅鐩崺鈧い鎺嶇贰閸熷繘鏌涢悩鎰佹疁闁诡噯绻濋弫鎾绘偐椤旂懓浜惧〒姘ｅ亾鐎殿噮鍣ｅ畷鐓庘攽鐎ｎ亝鏆梻鍌欒兌缁垶寮婚妸銉殨闁告挷璁查崑鎾愁潩椤戞儳浠┑顔硷功缁垶骞忛崨鏉戝窛濠电姴鍊瑰▓妯肩磽閸屾瑦绁版い鏇嗗洤纾瑰┑鐘宠壘閻掑灚銇勯幒宥囶槮闁搞値鍓熼弻娑㈡晲韫囨洜袦閻庤娲樺鑺ユ叏閳ь剟鏌嶉妷銉э紞缂佹劗鍋ゅ楦裤亹閹烘垳鍠婇梺鍛婏耿缁犳牕鐣烽姀銈呯闁绘劏鏅滈弬鈧梻浣哥枃濡嫬螞濡ゅ懏鍊堕柣妯肩帛閻撶喖鐓崶銊︹拻缂佺姵鐗滅槐鎺楀磼濮樻瘷褏鈧娲滈崗姗€銆佸鈧幃鈺冨枈婢跺苯绨ユ繝纰夌磿閸嬫垿宕愰弽顬稒绗熼埀顒€鐣烽幋锕€绠涙い鎾跺枑閻濈兘姊洪幐搴㈩棃闁轰緡鍣ｅ畷鎴﹀箻閼搁潧鏋傞梺鍛婃处閸撴盯藝閵夆晜鈷戦梻鍫熺〒婢ф盯鏌熼鐓庘偓鍧楁偘椤曗偓瀹曟﹢顢欑喊杈ㄧ秱闂備線娼ч悧鍡椢涘▎鎾崇？妞ゅ繐鐗婇埛鎴︽⒒閸碍娅呴柣锔界矒閺屾稑螣閸︻厾鐓撻梺鎸庣箘閸嬫盯鎮惧┑瀣妞ゆ垼濮ょ€氫粙姊绘担渚劸闁哄牜鍓熼幊婵囥偅閸愩劎鍔﹀銈嗗笂閻掞箓宕愰幇顔瑰亾鐟欏嫭绀冮柛鏃€鐟╅悰顕€寮介妸锕€顎撻梺鑽ゅ枛椤ｏ妇绱旈弴鐐╂斀闁绘ê鐏氶弳鈺佲攽椤旇姤灏︾€规洦鍨辩€佃偐鈧稒锚娴狀參鏌ｉ悢鍝ユ噧閻庢凹浜為幑銏ゅ幢濞戞瑧鍘梺鍓插亝缁诲啴宕冲ú顏呯厱闁挎繂鎳忕拹锟犳煟閵夘喕閭柟顔界懇椤㈡鍩€椤掑嫷鏁嬫繝濠傛噽绾捐偐绱撴担璇＄劷婵炴彃鐡ㄩ〃銉╂倷瀹割喖鍓伴梺瀹狀嚙濮橈妇绮诲☉銏犲嵆婵椴搁敍蹇涙⒒閸屾艾鈧兘鎳楅崜浣稿灊妞ゆ牜鍋涚粻浼存煠婵劕鈧鎯屽▎鎾寸厵閺夊牓绠栧顔剧磼閻樺啿娴慨濠勭帛閹峰懘鎮烽柇锕€娈濈紓鍌欐祰椤曆囧磹閼愁垱顥ら梻浣虹帛椤ㄥ懘鎮ц箛娑栤偓鍛村蓟閵夛妇鍘梺鍓插亝缁诲啰绮堝畝鍕厱閻庯綆鍋呯亸鐢告煙閸欏灏︾€规洜鍠栭、妤呭焵椤掍胶顩烽柍鍝勬噺閳锋垹绱掔€ｎ偄顕滈柟鐧哥秮閺屸剝鎷呯粵瀣濡炪倖娲╃徊鍧楀箯閻樺樊鍟呮い鏂垮悑閸婎垶姊绘担瑙勫仩闁稿寒鍨跺畷婵嗙暆閸曨偄鍤戦梺绋跨灱閸嬬偤鍩涢幋锔界厱婵炴垶锕弨璇差熆鐠哄搫顏柡灞剧〒閳ь剨缍嗘禍婵嬎夐悙鐑樺€甸柣銏ゆ涧鐢爼鏌ｉ敐鍡欑疄鐎规洜鍠栭、鏍嚍閵夈儺浼栭梻鍌欐祰瀹曞灚鎱ㄩ弶鎳ㄦ椽鎮╃拠鑼姦濡炪倖甯掗崯顖炴偟椤忓牊鐓熼煫鍥э工娴滈箖姊婚崒姘偓椋庣矆娓氣偓楠炴牠顢曢敃鈧粻鐘绘煙闁箑骞楅柛娆忕箻閺岀喓绱掗姀鐘崇亶闂佺顑傞弲鐘诲蓟閿濆绠ｉ柨婵嗘啗閹剧粯鐓涢柛娑卞枤閸欌偓闂佸搫鏈惄顖炲春閸曨垰绀冮柣鎰靛墰閺嗩厾绱撻崒娆戝妽闁告梹顨婂畷鎴︽倷閸偄搴婂┑鐘绘涧椤戝棝宕戦妸鈺傗拻闁割偆鍠庨崹渚€鏌曡箛瀣偓鏍磹閸偆绠鹃柟瀵稿仜缁楁碍绻涢崨顓犲ⅵ闁哄苯绉归、鏃堝川椤斿皷鍋撻幇顓熷弿?
		Float currentTotalPrice = (Float) request.getSession().getAttribute(CC.totalPrice);
		currentTotalPrice = currentTotalPrice == null ? 0f : currentTotalPrice;
		//闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇缁♀偓闂侀€炲苯澧撮柡灞芥椤撳ジ宕ㄩ姘曞┑锛勫亼閸婃牜鏁幒妤€纾圭憸鐗堝笒閸氬綊鏌嶈閸撶喖寮婚敐鍡樺劅闁靛繒濮村В鍫ユ⒑閸涘﹦鎳冮柛鐕佸亰閹儳鐣￠幍顔芥闂佹悶鍎滅仦缁㈡％闂備浇顕ч崙鐣屽緤婵犳艾绀夐悗锝庘偓顖嗗吘鏃堝川椤旇瀚奸梻渚€娼荤€靛矂宕㈡總绋跨閻庯綆鍠楅悡鏇㈡煏婵炲灝鍔ょ紒澶庢閳ь剝顫夊ú姗€宕濆▎鎾崇畺婵炲棗娴氶崯鍛亜閺冨洦顥夐柣锔界矒濮婄粯绗熼埀顒€顭囪閹囧幢濡炪垺绋戣灃闁告劦浜為悾鍫曟⒑閸︻厼鍔嬮柟姝屽煐缁傚秴顭ㄩ崼鐔哄弳闂佸搫鍊藉▔鏇熺閵忊懇鍋撶憴鍕碍婵☆偅鐩崺鈧い鎺嶇贰閸熷繘鏌涢悩鎰佹疁闁诡噯绻濋弫鎾绘偐椤旂懓浜惧〒姘ｅ亾鐎殿噮鍣ｅ畷鐓庘攽鐎ｎ亝鏆梻鍌欒兌缁垶寮婚妸銉殨闁告挷璁查崑鎾愁潩椤戞儳浠┑顔硷功缁垶骞忛崨鏉戝窛濠电姴鍊瑰▓妯肩磽閸屾瑦绁版い鏇嗗洤纾瑰┑鐘宠壘閻掑灚銇勯幒宥囶槮闁搞値鍓熼弻娑㈡晲韫囨洜袦閻庤娲樺鑺ユ叏閳ь剟鏌嶉妷銉э紞缂佹劗鍋ゅ楦裤亹閹烘垳鍠婇梺鍛婏耿缁犳牕鐣烽姀銈呯闁绘劏鏅滈弬鈧梻浣哥枃濡嫬螞濡ゅ懏鍊堕柣妯肩帛閻撶喖鐓崶銊︹拻缂佺姵鐗滅槐鎺楀磼濮樻瘷褏鈧娲滈崗姗€銆佸鈧幃鈺冨枈婢跺苯绨ユ繝纰夌磿閸嬫垿宕愰弽顬稒绗熼埀顒€鐣烽幋锕€绠涙い鎾跺枑閻濈兘姊洪幐搴㈩棃闁轰緡鍣ｅ畷鎴﹀箻閼搁潧鏋傞梺鍛婃处閸撴盯藝閵夆晜鈷戦梻鍫熺〒婢ф盯鏌熼鐓庘偓鍧楁偘椤曗偓瀹曟﹢顢欑喊杈ㄧ秱闂備線娼ч悧鍡椢涘▎鎾崇？妞ゅ繐鐗婇悡鐔兼煟濡搫绾х紒鈧畝鍕厓鐟滄粓宕滃┑鍡忔瀺闁哄洨鍋愰弸宥嗕繆閵堝懏鍣洪柍閿嬪浮閺屾稓浠﹂幑鎰棟闂侀€炲苯澧伴柛蹇旓耿楠炲﹤螖閸涱參鍞堕梺鍝勬川閸嬬喖鎮块崶顒佲拺闁革富鍘兼禍鐐繆閻愬弶鍋ョ€殿噮鍋嗛埀顒婄秵閸嬩焦绂嶅鍫熺厵闁诡垎鍐煘闂佽绻嗛弲婵嬫儉椤忓牆绠氱憸婊堟偂婵傚憡鐓涚€光偓鐎ｎ剙鍩岄柧浼欑秮閺屟嗙疀閺囩噥浠х紓浣戒含閸嬨倕顫忛搹鍦＜婵☆垵鍋愰悾楣冩⒑缁洘娅嗛柣妤冨█閻涱噣宕奸妷銉庘晠鏌曟径妯烘灍閻庨潧鐭傞弻锝夋偐閸欏鈹涢悗娈垮枟閹歌崵绮╅悢鐓庡嵆闁靛繆妾ч幏缁樼箾鏉堝墽鍒伴柟璇х節楠炲棝宕奸妷锔惧幐闂佸壊鍋掗崑鍕櫠閹绢喗鐓欐い鏍ㄨ壘椤忣厽銇勯姀锛勨槈闁宠棄顦灃闁告劦浜為弳浼存⒒閸屾瑧顦︾紓宥咃工铻為柛褎顨嗛崐鍧楁煥閺囩偛鈧悂鎮块悙顒傜瘈濠电姴鍊绘晶娑㈡煟閹惧瓨绀嬮柡宀€鍠栭幃娆擃敆婢跺鏆ラ梻浣烘嚀閸熻法鎹㈤幇顖ｆ毎闂備焦鎮堕崕娲礈濮樿泛鐓曢柟鐑橆殕閻撴洟鎮橀悙鎻掆挃闁瑰啿鎳愮槐鎺楀箛椤撶啿鎷圭紓浣虹帛缁嬫帡骞忛崨鏉戠闁圭粯甯掔粻璇测攽閻愬弶鈻曞ù婊勭矊濞插潡姊绘担瑙勫仩闁稿氦宕靛濠囨嚍閵壯屾锤闂佺粯鍔﹂崜姘跺矗韫囨梹鍙忔俊銈傚亾婵☆偅顨婇悰顔嘉旈崘顏嗭紲闁荤姴鎼幖顐ょ棯瑜旈弻鏇㈠炊瑜嶉顓犫偓娈垮枟閹告娊銆侀弮鍫濆耿婵炲懓娅曢崹鍦閹惧瓨濯寸紒瀣氨閸嬫捇宕烽鐔告闂佺粯鍔曢幖顐︽倿閸偁浜滈柟鐑樺煀閸旂喖鏌涢幒宥呭祮闁哄矉缍€缁犳盯寮崹顔芥嚈闁诲孩顔栭崰妤勩亹閸愵喖鐓橀柟杈剧畱楠炪垽鏌嶉崫鍕偓鎼佹儊濠婂牊鈷掑〒姘ｅ亾婵炰匠鍥ㄥ亱闁糕剝锕╁▓浠嬫煕濞戞﹫鍔熸い鈺傚絻闇夐柣妯烘▕閸庢劙鏌ｉ幘瀛樼闁逛究鍔岄—鍐嫚闊厼顥氱紓鍌氬€烽懗鑸垫叏閻㈠憡鍋嬮柣妯肩帛閸嬫ɑ銇勯弴妤€浜惧Δ鐘靛仜椤戝寮崘顔肩劦妞ゆ帒瀚涵鈧梺瑙勫劶婵倝鎮￠悢鍏肩厸闁告劑鍔嶆径鍕亜韫囨洖鈻堥柡?
		Integer currentTotalQt = (Integer) request.getSession().getAttribute(CC.itemNumber);
		currentTotalQt = currentTotalQt == null ? 0 : currentTotalQt;
		//婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柛娑橈攻閸欏繘鏌ｉ幋锝嗩棄闁哄绶氶弻娑樷槈濮楀牊鏁鹃梺鍛婄懃缁绘﹢寮婚敐澶婄闁挎繂妫Λ鍕⒑閸濆嫷鍎庣紒鑸靛哺瀵鈽夊Ο閿嬵潔濠殿喗顨呴悧濠囧极妤ｅ啯鈷戦柛娑橈功閹冲啰绱掔紒姗堣€跨€殿喖顭烽弫鎰緞婵犲嫷鍚呴梻浣瑰缁诲倸螞椤撶倣娑㈠礋椤栨稈鎷洪梺鍛婄箓鐎氱兘宕曟惔锝囩＜闁兼悂娼ч崫铏光偓娈垮枦椤曆囧煡婢跺á鐔兼煥鐎ｅ灚缍屽┑鐘愁問閸犳銆冮崨瀛樺亱濠电姴娲ら弸浣肝旈敐鍛殲闁抽攱鍨块弻娑樷槈濮楀牆濮涢梺鐟板暱閸熸壆妲愰幒鏃傜＜婵鐗愰埀顒冩硶閳ь剚顔栭崰鏍€﹂悜钘夋瀬闁归偊鍘肩欢鐐测攽閻樻彃顒㈢紓宥呭€垮缁樻媴閾忕懓绗￠梺鎸庣娣囧﹪顢涘鎯т紣闁捐崵鍋熼埀顒€鍘滈崑鎾绘煕閺囥劌鍘撮柟閿嬫そ閺屸剝寰勭仦鎴掓勃婵犮垻鎳撳Λ婵婃闂佹寧娲栭崐褰掓偂閵夆晜鐓涢柛銉ｅ劚婵℃椽姊洪褍鐏ｉ柍褜鍓氶鏍窗閺嶎厸鈧箓鎮滈挊澶婄€俊銈忕到閸燁偆绮诲☉妯忓綊鏁愰崨顔兼殘闂佺顭崹璺侯潖閾忚鍏滈柛娑卞幒濮规鏌ｉ悙瀵糕棨闁稿海鏁诲畷娲焵椤掍降浜滈柟鍝勬娴滃墽绱掗崜褑妾搁柛娆忓暣閵嗕礁鈻庨幘宕囩杸濡炪倖鏌ㄦ晶浠嬫晬濠靛鈷戠紒瀣濠€浼存煟閻旀繂娲ょ粈澶屸偓骞垮劚椤︿即鎮￠弴銏＄厸闁告劧绲芥禍楣冩⒑缂佹﹩娈樺┑鐐╁亾婵犵鍓濋幐鍐茬暦濮椻偓椤㈡瑩鎮℃惔銈傚亾閸喒鏀介柍钘夋閻忕娀鏌ｉ妸褍鍘寸€殿喓鍔戦幖褰掝敃閵堝洨妲囬梻浣圭湽閸ㄨ棄顭囪閻☆厽绻濋悽闈涗粶闁活亙鍗冲畷鎰板即閻忕粯妞藉畷绋课旀担鍙夊濠电偠鎻徊浠嬪箟閿熺姴鐤柣鎰湴閳ь剚甯掗～婵嬵敇瑜庨悿渚€鎮楃憴鍕８闁搞劋绮欓獮鎰節閸愩劎绐為梺绯曞墲椤洦绂嶉弽顓熲拻濞达絽鎲￠幆鍫ユ偠濮樼厧浜扮€规洘娲熷濠氬Ψ閵壯嶇串闂備焦鐪归崹褰掑箟閿熺姴纾婚柍鈺佸暟缁犻箖鏌涢埄鍐炬畼缂佺姷澧楅妵鍕疀閿濆嫮鏁栫紓浣介哺鐢繝宕洪埀顒併亜閹烘垵顏╃紒鈧崘鈹夸簻闁哄啫鍊瑰▍鏇㈡煕濡湱鐭欐慨濠冩そ閹兘骞嶉鏄忔闂佽瀛╅崙褰掑窗濮橆剦鍤曠憸鏃堝春閳ь剚銇勯幒鎴濐仾闁稿﹤鐏氱换娑㈠醇濠靛牅铏庨梺鍝勵儐缁嬫帡濡甸崟顖ｆ晣闁绘ɑ褰冮獮瀣旈悩闈涗粶闁哥喎鐡ㄩ幈銊╁焵椤掑嫭鐓忛柛顐ｇ箖閸ｈ姤銇勮熁閸ャ劉鎷洪梺绋跨箺閸嬫劙濡堕幘顔界厸闁告侗鍠氶埊鏇㈡煙娓氬灝濡奸悡銈嗐亜韫囨挻鍣抽柟椋庣帛缁绘稒娼忛崜褎鍋ч梺纭呮珪閹瑰洭銆佸顒夌叆闁告洦鍘鹃鏇㈡⒑閸︻厾甯涢悽顖滃仦閺呫儵鏌ｆ惔銈庢綈婵炲弶锕㈠畷褰掑锤濡ゅ啫绁﹀┑鈽嗗灥閸嬫劗澹曢崗闂寸箚妞ゆ牗绮岀敮鑸殿殽閻愭潙濮嶆慨濠呮閹风娀骞撻幒鎴炵槪缂傚倸鍊哥粔鏉懳涘┑鍡欐殾闁硅揪绠戦獮銏＄箾閹寸儐鐒搁柕澶嗘櫆閻撴瑩鏌ｉ幋鐐嗘垿鍩涢幒鏃傛／闁诡垎浣镐划閻庢鍠栭悥鐓庣暦閻撳簶妲堟俊顖欒閻庤櫕绻濋悽闈涗沪闁诡垰鐭傚畷鎶芥晲婢跺﹨鎽曢梺鎸庣箓椤︻垳绮绘總鍛婄厱闁规壋鏅涙俊濂告煃瀹勯偊鍎忛摶鏍煟濮椻偓濞佳勭閿曞倹鐓欓柣鐔哄閸犳﹢鏌ｅ☉鍗炴珝妤犵偞甯掕灃闁逞屽墴閻涱噣濮€閳ヨ尙绠氶梺缁樺姈濞兼瑩宕濋妶鍡愪簻闁靛濡囬。鑼磼缂佹绠為柟顔荤矙濡啫鈽夐幒鎾垛偓鐗堢節閻㈤潧浠掗柛鏍█瀹曞爼鎳滈崹顐ｇ彈闂傚倷绀侀幉锛勭矙閹达附鍋ら柕濞р偓閸嬫挸顫濋浣告畻闂佸搫鐭夌紞浣割嚕椤掑嫬鍨傛い鏃囨閳ь剦鍨跺铏圭磼濡钄奸梺绋挎捣閺佽顕ｆ繝姘櫢闁绘灏欓崐鐐差渻閵堝骸骞楅柛銊ョ埣閹箖鎳滈悽鐢殿啎闁诲孩绋掗…鍥儗閹烘梻纾奸柣妯虹－婢х數鈧鍠楀Σ鎺椝囬幎鑺ュ€甸柣銏ゆ涧鐢爼鏌嶇拠鏌ュ弰妤犵偛顑呴埞鎴﹀幢濞嗗繐绗撻梻鍌氬€搁崐椋庢濮橆剦鐒界憸宥堢亱濠电偛妫欓崹鍫曞汲濠婂牊鐓欓柣鎴烇供濞堬絿绱撳鍡欏缂佺粯绻堝Λ鍐ㄢ槈閸楃偛澹夐梻浣侯焾椤戝懘宕愰崸妤冨祦闁哄秲鍔嶇紞鍥煕閹炬鍠氶崵銈夋⒒娴ｅ湱婀介柛銊ㄥ吹閸掓帡顢涘☉姘ｆ敵婵犵數濮村ú锕傚磹闁垮浜滈煫鍥ㄦ尭椤忋倝鏌涚€ｎ偅宕岀€殿喗鎸抽幃銈嗘媴鐠団€虫櫖缂傚倸鍊风粈渚€顢栭崼銉晪闁告劖绁村Σ鍫熶繆椤栫偞鏁遍柡鍌楀亾闂傚倷鑳剁涵璺侯瀶瑜斿鎻掝煥閸涱垳顦柟鑲╄ˉ濡狙囧绩娴犲鐓熼柟閭﹀墮缁狙囨煃缂佹ɑ绀€闂囧绻濇繝鍌氼伀缂佺姷鍋熼埀顒冾潐濞叉粓寮拠鑼殾婵せ鍋撴い銏＄懇閹墽浠﹂挊澶岀懖闂傚倷绀侀幖顐λ囬柆宥呯；闁绘劕顕悵鍫曟煕閳╁啰鎳勯悗姘愁嚙闇夐柣鎾虫捣閹界娀鏌ｉ幘瀵告创闁哄本鐩崺锕傚焵椤掑嫬鐤炬繛鎴烇供閸ゆ洘銇勯弴妤€浜鹃梺鍝勫閳ь剙纾弳鍡涙倵閿濆骸澧伴拑閬嶆⒒娴ｇ瓔鍤冮柛銊ュ船铻為柛鎰靛枛閺嬩線鏌熼悧鍫熺凡闁绘劕锕弻鏇熺箾閸喖濮曢梺浼欑秮娴滆泛顫忔繝姘＜婵炲棙鍨垫俊浠嬫⒑鐠団€虫灈闁绘牜鍘ч悾鐑藉即閻忕粯妞介、鏃堝川椤撴稑浜鹃柤鍝ユ暩缁犻箖鏌熺€涙鎳冮柣蹇ｄ邯閺岋繝宕ㄩ鍓х杽闂佸搫鏈粙鎾寸閿曞倸绀堢憸瀣焵椤掍礁娴柡灞剧☉椤粓鍩€椤掍胶顩查悹杞拌濞兼牗绻涘顔荤盎濞磋偐濞€閺屾盯寮撮妸銉ヮ潻闂佺顑呯粔鐟邦潖濞差亜宸濆┑鐘插暟閸欏棝姊哄ú璇插箹闁稿﹥鎮傞、姘舵晲婢跺﹦顔愭繛杈剧秬閸婂€燁槻妞ゎ叀娉曢幑鍕瑹椤栨艾澹嬮梻浣告啞鐪夌紒顔界懃椤繐煤椤忓拋妫冨┑鐐村灦閼归箖銆傚ú顏呪拺缂佸灏呭銉╂煙鐠囇呯？缂侇喖顑呴濂稿幢閹邦兛绨奸梻浣告啞閸斿繘寮笟鈧獮蹇旂節濮橆厸鎷虹紓浣割儐鐎笛冿耿娴煎瓨鐓熼柣鏃€绻傚▔姘跺炊椤掍焦娅嗛梺浼欑到閻ジ鎯侀崼銉︹拺闂侇偆鍋涢懟顖涙櫠閹绢喗鐓涢悘鐐跺Г椤ユ粍銇勯幘鐐藉仮鐎规洏鍔戦、娑㈠Χ閸パ嗏偓鍧楁⒒閸屾艾鈧嘲霉閸ヮ剨缍栧璺猴功閺嗭附鎱ㄥ璇蹭壕濡炪們鍨烘穱娲囩€靛摜纾兼い鏃€顑欓崵娆愩亜椤愶絿绠為柟铏矒濡啫鈽夐幒鎴滃闂佸壊鍋呭ú姗€鎮￠悢鍏肩厵闂侇叏绠戦悘锛勭棯椤撱垻鐣洪柡灞界Ф閹风娀鎳犻鍌ゅ敼缂傚倷鑳剁划顖炴儎椤栫偟宓侀悗锝庝簴閺嬪酣鐓崶銊︾；闁汇儺浜缁樻媴閸涘﹥鍎撻梺鐟板暱缁绘﹢鐛崘顔肩＜闁绘劘灏幗鏇㈡⒑閹稿海绠撻柟鍙夛耿閹垽宕楅崗鐓庡姃闂備浇鐨崘鈺傚枑缂備讲鍋撳┑鐘叉处閳锋帒霉閿濆懏鍟為柛鐔哄仱閹洦寰勫畝鈧壕鍏笺亜閺冨倹娅曢柟鍐叉川閳ь剚顔栭崰鏍€﹂悜钘夌畺闁靛繈鍊栭崑鍌炲箹鏉堝墽绉垫俊宸灦濮婄粯鎷呯粙鎸庡€紓浣风劍閹稿啿顕ｉ幓鎺濈叆闁告劧绲鹃悘浣割渻閵堝棗绗掗悗姘煎弮閸╂盯骞嬮敂鐣屽幍闂佸吋绁撮弲娑欑濠婂啠鏀介梽鍥磻閹邦喗顫曢柟鐐墯閸氬鏌涘鍐ㄦ殭濞村吋鍔欏鐑樺濞嗘垶鍋ч梺绋跨箲閿曘垽鐛崘鈺冾浄閻庯綆浜為ˇ顓㈡⒑閸涘﹦鎳冩い锔芥緲閳诲秴鐣濋崟顑芥嫽婵炶揪绲介幉锟犲疮閻愬绠鹃悹鍥囧懐鏆ら悗瑙勬礃缁矂鍩ユ径鎰潊闁绘ɑ褰冩禍鍫曟⒒閸屾瑧璐伴柛鎾寸懅缁棃鎮界粙璺ㄥ幈闂佺鎻梽鍕磻閳╁啰绠鹃柛鈩冾殘缁犵増銇勮箛濠冩珖缂佽鲸甯￠崺鈧い鎺戝缁€鍐煃閸濆嫬鏆熸い鏃€娲樼换婵嬪閿濆棛銆愬銈嗗灥濞层倗鍙呴柣搴ｆ暩绾爼宕戦幘鏂ユ灁闁割煈鍠楅悘鎾愁渻閵堝骸骞栭柣妤佹崌瀹曟椽濡烽敃鈧欢鐐测攽閻樻彃顒㈤柛宥囨暬濮婃椽骞愭惔锝囩暤闂佺懓鍟跨粔鐢电博閻旂厧鍗抽柣鎰ㄦ櫆閺傗偓闂備胶绮崝妯间焊濞嗘劖娅犻柡鍥ュ灪閻撶喖鐓崶銊︹拻缂佺姷鍋熼埀顒侇問閸犳骞冮崒鐐叉瀬闁稿本绋掗崣蹇涙煙闁缚绨介柣鈺侀叄濮婄粯鎷呴崨濠冨創濠电偛鐪伴崐鏇＄亱濠德板€愰崑鎾绘偂閵堝鐓忛柛顐ｇ箥濡插綊鏌涚€ｎ亜鈧潡寮诲澶娢ㄩ柨鏇楀亾濠⒀屽灦閺岋綁寮幐搴＆闂佸搫琚崐婵嬬嵁閺嶃劍濯撮悷娆忓閺侇亜鈹戦悩鎰佸晱闁哥姵鐗曠叅闁挎洖鍊告闂佸憡娲﹂崹濂稿极閸愵喗鐓忛煫鍥堥崑鎾绘惞椤愩倖姣堥梻鍌氬€风粈渚€骞栭锔藉剹濠㈣泛鑻欢銈夋煕婵犲嫬鏋斿ù婊€绮欏缁樻媴娓氼垳鍔哥紓浣虹帛閸旀瑥鐣烽妷褉鍋撻敐搴樺亾椤栨稑娴€规洜鍠栭、娑樷槈閹烘挸顏归梻鍌欑閹诧紕鎹㈤崒婧惧亾濮橆剙妲婚崡杈╂喐閻楀牆绗氶柣鎾寸懄閵囧嫰寮崒姘粯闂侀€炲苯澧柟顔煎€块弫鎰版倷閸撲胶鏉搁梺鍝勬川婵兘鏁嶅鍐ｆ斀闁宠棄妫楅悘銉╂煕鐎ｎ偄濮嶆鐐诧工閳规垹鈧綆鍋€閹锋椽姊绘笟鍥т簽闁稿鐩幊鐔碱敍濞戞瑦鐝峰銈嗘煥椤洘绂嶅鍕╀簻闁规崘娉涙禒锔剧磼鐏炴儳顕滈柕鍥у閺佸啴鍩€椤掑倻绀婂┑鐘叉搐閽冪喓鈧箍鍎遍ˇ顖氭暜婵＄偑鍊栭悧妤冨垝鎼淬劌姹查柕鍫濇媼濞撳鏌曢崼婵嗘殭闁诲浚浜弻銊╁即濡櫣浠鹃梺閫涚┒閸旀垿宕洪埀顒併亜閹烘垵顏柣鎾冲暣濮婂搫鈻庨幆褏浠╂繛瀛樼矒缁犳牕顫忓ú顏勬嵍妞ゆ挆鍛Ъ闂備礁鎽滈崰鎰焽濞嗘搫缍栭煫鍥ㄦ媼濞差亶鏁傞柛鏇ㄥ亞閻涒晛鈹戦悩鍨毄濠殿喗鎸冲畷顖烆敃浣嶉崶顒€鐓涢柛娑卞枓閹锋椽姊虹涵鍛汗闁稿绋掗幈銊╁磼濮ｎ厾鎳撻…銊╁幢濡鈧劙姊烘导娆戞偧闁稿繑锕㈤悰顔锯偓锝庡枟閺呮粓鏌﹀Ο渚Х缂併劌顭峰缁樻媴娓氼垳鍔哥紓浣虹帛椤ㄥ﹤鐣烽幋锕€绠涢柡澶婄仢閸嬪秹姊鸿ぐ鎺擄紵闁绘帪绠撻崺娑㈠箣閿旂晫鍘甸梺鍏肩ゴ閺呮稒绂掑鍫熺厱闁哄啠鍋撴い銊ワ躬瀵鏁撻悩鎻掔獩濡炪倖妫冮弫顔芥叏閸モ晝纾藉ù锝囶焾閳ь剙鎽滅划鏃堟倻閽樺）锕傛煙閻楀牊绶茬紒鐘冲▕閺岀喖骞戦幇顓犮€愬Δ鐘靛仦閸旀瑥顫忕紒妯肩懝闁逞屽墴閸┾偓妞ゆ帒鍊告禒婊堟煠濞茶鐏￠柡鍛埣椤㈡稑顭ㄩ崨顖ょ床闂備胶顭堥張顒傜矙閹寸姷绠旂憸鏃堝蓟瀹ュ牜妾ㄩ梺鍛婃尪閸斿海妲愰悙鍝勫耿婵炴垶顭囬敍娑㈡⒑缁洖澧茬紒瀣灴瀹曟帡濡搁妷鍐ㄧ秺閹晛顔忛鐓庡闂備焦濞婇弨閬嶅垂瑜版帒绠熼柟闂寸劍閸嬪鏌涢锝囩畼闁荤喆鍔嶇换娑氣偓娑欘焽閻棝鏌涘Δ浣糕枙鐎殿喛顕ч埥澶愬閻樻牑鏅犻弻鏇㈠醇濠靛洤娅ｇ紓浣风贰閸ｏ絽顫忛搹瑙勫珰闁炽儱纾禒鈺呮⒑閸濄儱鏋戞繛鍏肩懇閹箖鎮滅粵瀣櫇闂佹寧娲嶉崑鎾剁磼閳ь剛鈧綆鍠楅悡鐔兼煏韫囧鈧牕鈽夎閺屸剝鎷呯憴鍕偓鎰版煛瀹€瀣М闁诡喓鍨藉畷顐﹀Ψ瑜忛崢鎰攽鎺抽崐妤佹叏閹绢喖绠烘繝濠傜墕閻鏌涘☉鍗炲箰闁衡偓閼恒儯浜滈柡鍌涱儥濞肩喎霉閻樺磭鐭掗柡灞剧〒閳ь剨缍嗛崑鍛焊椤撱垺鐓冮悷娆忓閻忔挳鏌℃担鍝バх€规洜鍠栭、妤佹媴閸︻厽顫滈梻浣筋嚙濮橈箓锝炴径濞掑搫顭ㄩ崼婵堢崶闂佸搫绋侀崢褰掑焵椤戣法顦﹂柍钘夘樀婵偓闁斥晛鍟悵鎶芥⒒娴ｈ櫣銆婇柛鎾寸箚閹筋偊姊虹紒妯肩畺缂佽鐗嗛～蹇旂節濮橆剛锛滃┑鐐叉鐢帡宕㈤敍鍕＝濞撴艾娲ゅ▍姗€鏌涢妸褎鏆鐐搭殜椤㈡洟鏁傞挊澶夌敾婵犵數濮撮敃銈団偓姘煎幘濞嗐垽寮婚妷锔惧幈闂佺粯鏌ㄩ幉锛勪焊閿曞倹鐓欐い鏃囶嚙瀹撳棙銇勯姀锛勬噰闁哄被鍔戦、娑橆潩椤愩埄妫滈梻鍌欑劍鐎笛呮崲閸屾侗娈介柟闂寸閸戠娀鏌ｉ弬鍨倯闁绘挻娲栭埞鎴︽偐閹绘巻鍋撶紒妯碱浄婵炴垶姘ㄧ壕鑲╃磽娴ｈ鐒芥繛鎻掝嚟閳ь剝顫夊ú婊堝储瑜旈崺鐐哄箣閿曗偓闁卞洦绻濇繝鍌涱棡妞ゅ孩顨婂濠氬磼濞嗘埈妲梺鍦拡閸嬪﹪銆侀弽顓炲耿婵炲棙鍨归悡鎾绘⒑閸︻厼鍔嬫い銊ユ噺椤㈠﹪姊绘担鍛婂暈婵炶绠撳畷瑙勭附缁嬫寧鐎梺鎼炲労閸撴岸鎮￠悢闀愮箚妞ゆ牗绻嶉崵娆戠磼閼碱剛甯涙い銊ｅ劦閹瑥顔忛鐓庡闂備浇顕栭崰妤呫€冮崨鏉戠厺闁规崘顕ч崹鍌涖亜閺冨倹娅曠憸閭﹀灦濮婄粯鎷呮笟顖滃姼濡炪倖鍨甸幊姗€寮崘顕呮晜闁割偆鍠庢禒鎺戭渻閵堝棙纾甸柛瀣尵閳ь剝顫夊ú鏍偉婵傛悶鈧礁螖娴ｅ摜绐為柣搴秵閸嬪懘鎮靛┑瀣拻闁稿本鐟ㄩ崗宀€绱掗鍛仸鐎规洘绻傝灃闁告侗鍘介悗顒勬⒑瑜版帗锛熼柣鎺炵畵瀹曟垵螣濮瑰洣绨婚梺鍝勫€藉▔鏇炐掗悙鐑樼厸闁逞屽墯缁傛帞鈧綆鍋嗛崢钘夆攽閳藉棗鐏ユ繛鍜冪稻缁傛帒鈽夐姀锛勫幐闂佺硶鈧磭绠叉繛鍛躬閺岋紕浠﹂崜褋鈧帡鏌嶈閸撱劎绱為崱娑樼；闁告洦鍓氶崣蹇曗偓骞垮劚濡厼鈻撴禒瀣厽闁归偊鍨奸崵瀣椤掑澧伴柍褜鍓濋～澶娒洪弽顓熷亯濠靛倹鎮堕埀顑跨铻栭柛娑卞幘閸旓箑顪冮妶鍡楃瑨閻庢凹鍙冨畷鎴︻敂閸曞孩妫冮弫鎰板川椤撶喐顔夐梻浣虹帛閹搁箖宕伴幇顓犫攳濠电姴娴傞弫宥嗙箾閹存繂鑸归柛鏂跨焸濮婃椽骞栭悙娴嬪亾閺嶎厽鍋￠柍鍝勬噹閽冪喖鏌ㄩ弴鐐测偓鍝ョ矆閸愨斂浜滈煫鍥ㄦ尭椤忣亪鏌ｈ箛鎾跺ⅵ婵﹥妞藉畷顐﹀礋椤撶姴濮界紓鍌欒兌缁垳鎹㈤崒鐐村仼闁绘垼妫勯柋鍥煛閸モ晛浠ч柛妯烘啞缁绘稒娼忛崜褍鍩屽┑鐘亾闂侇剙绉撮悞鍨亜閹烘垵鏆欓柛姘贡缁辨帗娼忛妸锕€闉嶉梺鐟板槻閹虫劗鍒掑▎蹇婃瀻闁绘劦鍓氶鎴︽⒒閸屾瑨鍏屾い顓炵墦椤㈡牠宕橀鑲╋紮濠电娀娼уΛ顓㈠吹閺囥垺鍋ｉ柛銉ｅ妿閵堝瓨淇婇幓鎺斿ⅵ闁哄本娲濈粻娑欑節閸愨晝褰嗛梻浣告惈閹冲繒鎹㈤崼婵愭綎婵炲樊浜滅粻浼村箹鏉堝墽鎮奸柣锝夋涧閳规垿鎮欓懠顒佸嬀闂佸憡姊归崹鍧楃嵁閸愨斂鍋呴柛鎰╁妼閻庮厼顪冮妶鍡楀闁稿﹥顨婇幆渚€宕煎┑鍐╂杸闂佺粯鍔樼亸娆愭櫠閺囥垺鐓熼煫鍥ㄦ⒒缁犵偟鈧鍠楅幃鍌炲极閸愵喖纾兼繛鎴炶壘楠炲牓姊绘担绋挎毐闁圭⒈鍋婂畷鎰磼濡粯鐝烽梻渚囧墮缁夌敻鎮″▎鎰垫闁绘劘灏欐禒銏ゆ煟韫囧﹥娅婇柡灞界Ч閹稿﹥寰勫Ο鎭嶏箑顪冮妶搴′簼缂佽鐗撳顐﹀箛閺夊灝绐涘銈嗗坊閸嬫挻銇勯妷锕€鍝烘慨濠勭帛閹峰懘宕烽鐔诲即闂備焦鎮堕崝宥咁渻娴犲鈧礁鈻庨幘鍐插敤濡炪倖鎸鹃崰搴ㄥ焵椤掆偓閻忔繆鐏冮梺鎸庣箓閹冲酣寮抽悙娣簻闁靛鍎哄Ο鈧梺璇″枟椤ㄥ﹪寮幇顓熷劅婵犻潧娲ら弸銈嗙節閻㈤潧浠﹂柟鍛婃倐閳ワ箓鎮滈挊澶庢憰闂佸搫娲㈤崹褰掓煁閸ャ劎绡€闂傚牊渚楅崕娑㈡煃瑜滈崜姘躲€冮崨瀛樼畳婵犵數濮撮敃銈団偓姘煎櫍閹瞼鈧綆鍠楅悡蹇涙煕閵夋垵鍠氭导鍐⒑鏉炴壆顦﹂柛濠傛健楠炴劖绻濋崘銊х獮濠碘槅鍨卞鍨涢崱娆戠＝闁稿本鑹鹃埀顒勵棑濞嗐垽鏁撻悩鑼崶濠德板€曢崯顐﹀几閸懇鍋撻獮鍨姎闁瑰嘲顑夐幃鐐寸鐎ｎ剙褰勯梺鎼炲劘閸斿酣鍩ユ径鎰畾闁绘柨鍚嬮崑鈩冪節婵犲倸鏆熺紒鐘差煼閺岀喖顢涘▎鎴犵崲閻庤娲栭妶鍛婁繆閻戣姤鏅滈悷娆忓椤忓綊姊婚崒娆掑厡妞ゃ垹锕畷鎴﹀箛閺夎法锛涘┑鐐村灟閸ㄥ湱绮堟径鎰厵缂備降鍨归弸鐔兼煟閹捐泛鏋涢柣鎿冨亰瀹曞爼濡搁敂瑙勫婵＄偑鍊ら崜娆撳触鐎ｎ喖桅闁告洦鍨遍弲婵嬫煃瑜滈崜娑氭閻愬绡€闁搞儜鍡樻啺闂備焦瀵х粙鎴犫偓姘间簻閳讳粙顢旈崼鐔哄幈闂佸湱鍋撻〃鍛偓姘煎墴瀹曞綊宕￠悙鈺傛杸闂佺粯蓱椤旀牠寮冲鍛＜閺夊牄鍔嶇粈瀣偓瑙勬礃椤ㄥ懘锝炲鍫濈劦妞ゆ帒瀚繚婵炶揪绲跨涵鍫曞几鎼淬劍鐓欓柟顖嗗拑绱為梺缁樻尰閸ㄥ灝顫忓ú顏咁棃婵炴垼椴歌倴闂備礁鎲￠弻銊х矓瑜版帇鈧線骞樺鍕瀹曘劑顢欑憴鍕伜闂傚倷鑳堕…鍫ュ嫉椤掑嫭鍋＄憸蹇曞垝閿濆绫嶉柍褜鍓涢幑銏犫槈濞嗘劕顎撻梺鍛婂姈瑜板啴顢旂€涙绠鹃柛顐ｇ矌閻瑩鏌ｉ幙鍐ㄤ喊鐎规洖鐖兼俊鎼佹晜缂併垺袨濠碉紕鍋戦崐銈夊磻閸涱劶娲偄閻撳氦鎽曢梺璺ㄥ枔婵挳鎮欐繝鍥ㄧ厓閺夌偞澹嗛幃濂告煏婢跺牆鍔楁俊鎻掔墦閺岀喖骞嗚閿涘秹寮崼銉︹拺闁告繂瀚﹢浼存煟閳哄﹤鐏﹂柣娑卞枛铻ｅ〒姘煎灡鐎靛矂鏌ｉ悩鍙夌┛鐎殿喗鎸荤粩鐔煎即閵忊檧鎷绘繛杈剧导鐠€锕傛倿閻愵兙浜滈柟瀛樼箖椤ャ垺顨ラ悙鏉戠伌鐎规洖鐖奸、妤呭焵椤掑倻涓嶉柡宥庡幗閻撶喖鏌熸潏鍓хɑ妞ゃ儱顦辩槐鎺楁偑濞嗗繐顏紒鐘荤畺閺屾盯鍩勯崘顏呭櫑婵炲瓨绮庨崑鎾诲Φ閸曨垰唯闁挎洍鍋撻柣蹇撶摠閹便劍绻濋崟顓炵闂佺懓鍢查幊妯虹暦閵婏妇绡€闁告劑鍔嶉悗鐐節閻㈤潧校妞ゆ梹鐗犲畷鏉款潩鐠虹儤鐎繝鐢靛У閼瑰墽绮婚弽顐ｅ枑闁哄啫鐗嗙粻鏍煃閸濆嫬鏆熺痪鎯у悑娣囧﹪顢涘┑鍥モ偓鍐磼椤斻劌瀚换鍡涙煟閹板吀绨婚柍褜鍓氶悧鏇犲弲閻庡箍鍎遍ˇ顖滃婵犳碍鐓欓柟顖嗗懍閭梺绋款儐閹告悂鍩㈤幘璇插瀭妞ゆ梻鏅禍鐐电磽閸屾瑧璐伴柛鐘愁殜閹兘鍩￠崨顓囥儱霉閿濆懎顥忔繛绗哄姂閺屽秷顧侀柛鎾跺枛閻涱噣宕橀纰辨綂闂侀潧鐗嗛幊宥囨閸洘鈷戦柛娑橈攻婢跺嫰鏌涚€ｎ亝顥㈡い銏＄懇楠炲鏁傜憴锝嗗闂備胶顭堥張顒勬嚌妤ｅ啫鐒垫い鎺戝€搁崢鎾煙椤旀儳浠遍柡浣稿€块弻銊╊敍濮橆偄顥氱紓鍌欑劍椤ㄥ棛绮欓幋锕€绠犻煫鍥ㄦ尵閺嗭箓鏌＄仦璇插姕闁绘挻娲熼幃姗€鎮欐担鍐╊€楅梺鎼炲€栧鍦崲濞戞﹩鍟呮い鏃囧吹閻╁酣鏌涢妷銉ф憼缂佺粯绻堟慨鈧柨婵嗘閵嗘劙姊洪幐搴㈢８闁稿﹥绻傞悾鐑藉箣濠垫劕鎮戞繝銏ｆ硾椤戝洭宕㈡禒瀣厵闁稿繗鍋愰弳姗€鏌涢妸銉吋闁糕晛锕鎾閳ヨ櫕顓块柣鐔哥矊缁夌懓锕㈡担绯曟斀闁绘顕滃銉╂倵濮樼厧娅嶉柟顖氱焸楠炴帒螖娴ｅ搫骞愰梺璇茬箳閸嬬喖宕戦幘鍓佺焼濠㈣泛鐬肩壕濂告煟濡櫣锛嶉柍顖涙礈缁辨帞绱掑Ο鑲╃杽閻庤娲樼敮鎺楋綖濠靛鏅查柛鈩冾殘瑜板墽绱撻崒姘偓鎼佸磹閹间礁纾瑰瀣婵ジ鏌＄仦璇插姎缁炬儳顭烽弻鐔煎礈瑜忕敮娑㈡煕婵犲嫭鏆柡灞诲妼閳规垿宕卞☉鎵佸亾濡ゅ懏鐓涢悗锝庡墮閺嬫盯鏌″畝瀣М妤犵偞顭囬埀顒傛暩绾泛危閸儲鈷掗柛銉戝本鈻堥梺鍝勮閸斿矂鍩為幋锕€骞㈡俊顖欒濡查亶姊绘担瑙勫仩闁稿﹥鍔欏畷鎴﹀箻閺傘儲鏂€闂佺粯蓱閸撴岸宕箛娑欑厱闁绘ê纾晶杈╃磼椤旇姤顥堢€规洖銈稿鎾倷闂堟稑袝濠碉紕鍋戦崐鏍暜閹烘绐楁慨姗嗗墻閻掍粙鏌熼柇锕€骞樼紒鐘荤畺閺屾稑鈻庤箛锝喰ㄦ繝鈷€灞奸偗闁哄矉缍侀弫鎰板炊瑜嶉獮瀣渻閵堝啫鐏い銊ワ工閻ｅ嘲顫滈埀顒勩€侀弮鍫濈妞ゅ繐娲ら崢顓炩攽閻樺灚鏆╁┑顔惧厴瀵偊宕ㄦ繝鍐ㄥ伎闂佺粯鍨煎Λ鍕几娓氣偓閺岀喖姊荤€靛壊妲紒鐐劤椤兘寮婚敐澶婄疀妞ゆ帊鐒﹂崕鎾剁磼閻愵剙鍔ゆ繛纭风節瀵槒顦剁紒鐘崇洴閺佹劙宕ㄩ鍌涙櫦濠碉紕鍋戦崐鎰板疾濠婂棎浜归柛鎰靛幘娴滄瑩姊绘担鐟邦嚋缂佽鍊块獮濠冩償閵娿儲杈堥梺缁樺姉閸庛倝鎮￠悢鍏肩厵闁诡垎灞芥闂佺懓鍟垮ú顓㈠蓟閿涘嫪娌柛鎾楀嫬鍨辨俊銈囧Х閸嬬偤鏁嬮柧缁樼墵閺屽秷顧侀柛鎾跺枛閻涱喗绻濋崶褏鐤€闂佺粯顨呴悧鍡涘礈閵娾晜鈷戦柛鎾村絻娴滄牠鏌涢妸銊ュ惞濠㈣娲熼幐濠冪珶濠靛棛绉洪柡浣瑰姍瀹曞爼鍩￠崒娑樻櫟闂傚倷鐒﹂幃鍫曞磹閺嶎厽鍋￠柨鏃傛櫕閳瑰秴鈹戦悩鍙夌ォ闁轰礁鍟撮弻銊モ攽閸℃ê绐涢梺姹囧€曠€氫即骞冨Δ鈧埢鎾诲垂椤旂晫浜俊鐐€ら崢楣冨礂濡警鍤曟い鎰剁畱缁犳稒銇勯幘璺轰户缂佹劗鍋炵换婵嬫偨闂堟刀銏ゆ倵濮樼厧娅嶇€殿喖鐖煎畷銊︾節娴ｈ櫣鐣鹃梻浣虹帛閸旓附绂嶅鍫濈劦妞ゆ帊鑳舵晶閬嶆煛娓氬洤鏋熺紒缁樼箞瀹曠喖顢楅埀顒勵敁濞戙垺鈷戦柛婵嗗椤箓鏌涙惔銊ゆ喚鐎规洘鐟╅幃鈺佺暦閸モ晩鍟庨梻浣虹《閸撴繆鎽柣蹇撶箣閸楁娊寮诲澶嬬叆閻庯綆浜炴禒鑲╃磽娴ｄ粙鍝洪悽顖滃仱閸┾偓妞ゆ帒锕︾粔闈浢瑰鍕疄鐎规洘鍨挎俊鐑芥晜鏉炴壆鐩庨梺鐟板悑閻ｎ亪宕洪崟顐熸灁婵☆垵鍋愮壕濂稿级閸稑濡肩紒妞﹀懐纾奸弶鍫涘妼缁楁帡鎽堕敐澶嬬厱婵犻潧妫楅瀛樼箾閸忚偐鎳囨慨濠勭帛閹峰懐绮电€ｎ亝顔勭紓鍌欒兌缁垶宕濆▎蹇曟殾妞ゆ牜鍋涚痪褔鏌熼幖顓炲箺缂傚秴閰ｅ娲箚瑜忕粻鐗堢節閳ь剚娼忛埡渚囨闂佺绻樺Λ璺ㄦ崲閸℃ǜ浜滈柡宥冨妿椤ｈ尙绱掗崒姘煎剰闂囧绻濇繝鍌氼伀闁活厽甯楅妵鍕閳ュ啿鎽甸梺杞扮劍閸旀牕顕ラ崟顒傜瘈闁搞儜鍕瘑闂傚倸鍊风粈渚€骞栭鈷氭椽濡搁埡浣猴紱闂佺粯鏌ㄩ崥瀣磻椤忓牊鐓曟繛鎴烆焽閹界娀鏌嶉柨瀣伌闁哄本绋戦埥澶婎潨閸繂顫犵紓鍌氬€搁崐鐟扮暆閹间礁钃熸繛鎴炵懄閸庣喖鏌ㄥ☉妯侯伒缂佽京鍋炵换婵嬪閿濆孩缍堝┑鐐跺皺閸犲酣鎮惧畡鎷旀棃宕橀鍡欐殽闂佸搫顦遍崑鐐寸珶閸℃稒鍎庨幖娣妽閳锋帒霉閿濆懏鍟為柟顖氱墦閺屾盯鎮㈤崫鍕ㄦ瀰閻庤娲橀崹鍧楃嵁濡懙搴敄鐠恒劎娉块梻鍌欒兌閹虫捇顢氶銏犵；闁绘梻鍘х粻鐘绘煕閵夈垺鏉归柛瀣尵閹叉挳宕熼鍌ゆЧ婵犵數鍋橀崠鐘诲炊瑜忛崢娲煙閸忚偐鏆橀柛鏂跨灱缁鈽夐姀锛勫帗闁荤偞绋堥埀顒€鍘栨竟鏇犵磽閸屾瑩妾烽柛鏂跨箻椤㈡俺顦归柍銉︽瀹曟﹢顢欓崲澹洦鐓曢柍鈺佸枤濞堟梻鎮伴懖鈺冪＝闁稿本鐟︾粊浼存倶韫囷絼绨婚柍缁樻瀵挳鎮╅幓鎺斾喊濠电姷鏁告慨鐢靛枈瀹ュ纾归柣鎴ｅГ閻撶喖鏌熺€电鍓遍柣鎺嶇矙閺屾稑螣閸忓吋姣堝┑顔硷攻濡炰粙鐛崱姘兼Ъ缂備礁鍊搁澶愬蓟濞戞埃鍋撻敐搴濈凹妞ゃ儯鍨介弻鐔碱敍濮樺啿顏梺浼欑稻缁诲牆鐣烽悢纰辨晢闁稿本鑹剧粻鐐烘⒒閸屾瑨鍏岀紒顕呭灦閳ワ箓宕奸妷顔芥櫔濠电姴锕ら崰姘跺疮閸涘瓨鐓忓璺烘濞呭棝鏌熼搹顐㈠摵闁哄被鍔岄埞鎴﹀幢閳哄倐褔姊虹粙娆惧劀缂傚秳绶氬璇测槈閵忕姈銊╂煙鐎涙绠撴い蹇嬪€濋弻锛勨偓锝庡亞閹冲嫮绱?
		Service service = serviceService.findServiceByCatalogAndPerson(imageKey, person);
		float price = resolveUnitPriceForSelection(service, request.getSession());
		// 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇川绾剧晫鈧箍鍎遍幏鎴︾叕椤掑倵鍋撳▓鍨灈妞ゎ厾鍏橀獮鍐閵堝懐顦ч柣蹇撶箲閻楁鈧矮绮欏铏规嫚閺屻儱寮板┑鐐板尃閸曨厾褰炬繝鐢靛Т娴硷綁鏁愭径妯绘櫓闂佸憡鎸嗛崪鍐簥闂傚倷娴囬鏍垂鎼淬劌绀冮柨婵嗘閻﹂亶姊婚崒娆掑厡妞ゃ垹锕ら埢宥夊即閵忕姷顔夐梺鎼炲労閸撴瑩鎮橀幎鑺ョ厸闁告劑鍔庢晶鏇犵磼閳ь剟宕橀埞澶哥盎闂婎偄娲ゅù鐑剿囬敃鈧湁婵犲﹤鐗忛悾娲煛鐏炶濡奸柍瑙勫灴瀹曞崬鈻庤箛鎾寸槗缂傚倸鍊烽梽宥夊礉瀹€鍕ч柟闂寸閽冪喖鏌ｉ弬鍨倯闁稿骸鐭傞弻娑樷攽閸曨偄濮㈤悶姘剧畵濮婄粯鎷呴崨濠冨創闂佹椿鍘奸ˇ杈╂閻愬鐟归柍褜鍓熸俊瀛樻媴閸撳弶寤洪梺閫炲苯澧存鐐插暙閳诲酣骞樺畷鍥跺晣婵＄偑鍊栭幐楣冨闯閵夈儙娑滎樄婵﹤顭峰畷鎺戔枎閹寸姷宕叉繝鐢靛仒閸栫娀宕楅悙顒傗槈闁宠閰ｉ獮瀣倷鐎涙﹩鍞堕梻鍌欑濠€閬嶅磿閵堝鈧啴骞囬鍓ь槸闂佸搫绉查崝搴ｅ姬閳ь剟姊婚崒姘卞濞撴碍顨婂畷鏇㈠箛閻楀牏鍘搁梺鍛婁緱閸犳岸宕ｉ埀顒勬⒑閸濆嫭婀扮紒瀣灴閸┿儲寰勯幇顒傤攨闂佺粯鍔曞Ο濠傤焽缂佹ü绻嗛柣鎰典簻閳ь剚鍨垮畷鏇㈠蓟閵夛箑娈炴俊銈忕到閸燁偊鎮″鈧弻鐔告綇妤ｅ啯顎嶉梺鍝勬媼閸撶喖寮诲☉銏犲嵆闁靛鍎扮花鐢告⒑閸濆嫷妯堥柛娆忓暙椤繘鎼归崷顓犵厯闂佸憡鐗戦崜婵嬪极妤ｅ啯鈷戦柟鑲╁仜婵″ジ鏌ｈ箛鏃傜疄闁诡噣绠栭幃婊堟嚍閵夈儰鐢婚梻浣烘嚀閸熷潡鏁冮妷銉富濞寸姴顑冮埀顑跨窔瀵噣宕煎┑鍡欑崺婵＄偑鍊栭幐鑽ゆ崲閸曨厾绀婄€广儱顦伴埛鎺楁煕鐏炲墽鎳嗛柛蹇撶灱缁辨帡顢氶崨顓犱桓闂佺偨鍎荤粻鎾崇暦婵傞潻缍栨い鎾跺Т婢х偓绻濋悽闈涗粶婵☆偅鐟╅獮鎰矙閸熷唲鍥ц摕闁靛濡囬崣鍡涙⒑閸濆嫭宸濋柛瀣洴閸┾偓妞ゆ巻鍋撶紓宥咃躬楠炲啴妾辩紒鐘崇洴楠炴﹢顢涘☉娆愭緫闂傚倷鑳剁划顖炲礉濞嗗浚鐒界憸搴ｇ矉瀹ュ鍊锋い鎺戝€婚鏇㈡⒑閸涘﹣绶遍柛鐘愁殔閻ｅ嘲鐣濋崟顒傚幐闁诲繒鍋熼…鍫熸櫠閺屻儲鐓欑€瑰嫮澧楅崵鍥┾偓瑙勬磸閸斿秶鎹㈠┑鍥ㄥ劅妞ゎ偒鍠氱粈鍌炴⒒閸屾艾鈧绮堟笟鈧獮鏍敃閵堝棗浠忓銈嗗姧缁犳垹澹曡ぐ鎺撶厸闁搞儯鍎遍悘顏嗙磼閻樺啿娴慨濠呮閹风娀骞撻幒鎴炵槪缂傚倸鍊哥粔鏉懳涘┑鍡欐殾闁硅揪绠戝婵嬫倵濞戞顏呯瑜斿娲传閸曨剙鍋嶉梺鎼炲妽濡炰粙宕哄☉銏犵闁挎梻鏅崢鐢告⒑閸忚偐銈撮柡鍛⊕缁傛帒顭ㄩ崼鐔哄帗閻熸粍绮撳畷婊冾潩鐠鸿櫣顦┑鐐叉缁绘帡寮抽敃鍌涚厵閺夊牆澧介悾杈╃磼閻樺搫鍚圭紒杈ㄦ尰閹峰懏顨ラ妸锕€鍝烘鐐茬箳缁辨帒螣閼测晩鍟庨梺鍝勵槸閻楀棙鏅堕悾宀€鐭撴い鏂款潟娴滄粓鏌曟径娑㈡閻忓繑澹嗙槐鎺斺偓锝庝憾濡偓闂佺懓纾繛鈧い銏☆殜瀹曟帒顭ㄩ幇顕€妫烽梻浣筋嚙妤犲摜绮诲澶婄？闂侇剙绉寸粻鐘荤叓閸ャ劍鎯勯柛鐔锋噺缁绘繈妫冨☉鍗炲壉闂佺粯鎸婚悷锕傘€冮妷鈺傚€烽柤纰卞厸閾忓酣姊虹拠鑼畾闁哄懐濞€瀵顓奸崶銊ユ瀭闂佸憡娲﹂崑鍡樼婵傚憡鈷戦梺顐ゅ仜閼活垱鏅堕崜褏纾界€广儱鎳忛ˉ鐐电磼閸屾氨效闁轰焦鍔栧鍕節閸曢潧鏅ラ梻鍌欑閹碱偄煤閵忋倕鍨傛繛宸簻閻撴洟鏌熸潏楣冩闁绘挻鐟╅弻銈吤圭€ｎ偅鐝旀繛瀵稿У缁捇寮诲☉銏犵睄闁逞屽墴楠炴劙骞庨挊澶庢憰濠电偞鍨崹鍦不婵犳碍鐓涘璺侯儏閻忋儵鏌熼悾灞炬毈闁诡喗顨呴埢鎾诲垂椤旂晫浜堕柣鐔哥矋濠㈡﹢宕幘顔兼槬闁绘劕鎼粻锝夋煟濮楀棗浜滃ù婊堢畺閺屻劌鈹戦崱娑扁偓妤€霉濠婂懎浠遍柡灞剧☉铻ｇ紓浣姑壕鎶芥⒑鐠団€虫灈闁搞垺鐓￠垾锕傚Ω閳轰胶顦板銈嗘尵婵兘宕㈠鍫熺厽閹兼番鍊ゅ鎰箾閸欏鐒介柛鎺撳浮楠炴鎷犻懠顒傛毇闂備線娼ф蹇曟閺囥垺鈷旈柛鏇ㄥ亽閻斿棝鎮归搹鐟扮殤闁衡偓缂佹ǜ浜?
		float taxRate = Float.parseFloat( (String) request.getSession().getAttribute("tax_rate"))/100f;
		// prepare the times and isAdd flag.
		int numberChanged = 0;
		boolean isAdd = true;
		try {
			if (changeType.startsWith("up")) {
				isAdd = true;
				numberChanged = Integer.parseInt(changeType.substring(2));
			} else if (changeType.startsWith("down")) {
				isAdd = false;
				numberChanged = Integer.parseInt(changeType.substring(4));
			} else if (changeType.startsWith("set:")) {
				String[] parts = changeType.substring(4).split(",");
				int oldVal = Integer.parseInt(parts[0].split("=")[1]);
				int newVal = Integer.parseInt(parts[1].split("=")[1]);
				numberChanged = Math.abs(newVal - oldVal);
				isAdd = newVal > oldVal;
			} else {
				isAdd = !selectedItemsMap.containsKey(imageKey);
				numberChanged = 1;
			}
		} catch (Exception e) {
			TaoDebug.warn(TaoDebug.getSB(request.getSession()), "non-number parameter 'change':{}", changeType);
		}

		if (isAdd) {
			selectedItemsMap.put(imageKey, selectedItemsMap.getOrDefault(imageKey, 0) + numberChanged);
			currentTotalPrice += numberChanged * price * (1 + taxRate);
			currentTotalQt += numberChanged;
		} else {
			int curCountOfCurrentItem = selectedItemsMap.getOrDefault(imageKey, 0);
			int realReduce = Math.min(numberChanged, curCountOfCurrentItem); //闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢弻銉﹀殞濡わ絽鍟悡姗€鏌熺€电浠滅紒鐘靛█濮婅櫣绮欓崠鈩冩暰濡炪們鍔屽Λ婵嬬嵁閸儱惟闁冲搫鍊搁埀顒€顭烽弻锕€螣娓氼垱楔闂佹寧绋掗惄顖氼潖閾忓湱纾兼俊顖氭惈椤酣姊虹粙璺ㄦ槀闁稿﹥绻傞悾鐑藉箣閻橆偄浜鹃柨婵嗛閺嬬喖鏌ｉ幘璺烘瀾濞ｅ洤锕、娑樷攽閸℃鍎繝鐢靛Л閸嬫挸霉閻樺樊鍎愰柣鎾冲暟閹茬顭ㄩ崼婵堫槯濠电偞鍨剁喊宥夘敃閼恒儲鍙忔慨妤€妫楁晶濠氭煕閵堝棙绀嬮柡宀€鍠撶槐鎺楀閻樺磭浜俊鐐€ら崑鍕箠濮椻偓瀵顓兼径濠勫幐婵炶揪绲介幉鈥斥枔閺屻儲鈷戠紓浣诡焽缁犳牗绻涢懠顒€鏋庢い顐㈢箳缁辨帒螣鐠囧樊鈧捇姊洪懞銉冾亪鏁嶅澶婄缂備焦顭囬崣鍡椻攽椤旀枻渚涢柛妯款潐缁傚秴顭ㄩ崼鐔哄弳闂佸搫鍟ù鍌炲吹濞嗘挻鐓涢悗锝冨妼閳ь剚顨堝Σ鎰板箳閹惧绉堕梺闈涱煭鐎靛苯螞閸愵喗鍊垫繛鍫濈仢閺嬬喖鏌熷灞剧彧闁逛究鍔戦崺鈧い鎺戝閻撳啴姊哄▎鎯х仩濞存粓绠栧楦裤亹閹烘繃顥栫紓渚囧櫘閸ㄦ娊骞戦姀鐘婵炲棙鍔楃粔鍫曟⒑閸涘﹥瀵欓柛鎰╁妿閸樻帡姊婚崒娆掑厡妞ゃ垹锕、姘煥閸涱喖搴婂┑鐘绘涧濡盯寮抽敂濮愪簻闁哄稁鍋勬禒锕傛煕婵犲嫬鍘撮柡宀嬬秮楠炲鎮滈崱娆忓Ш闂備礁鎲￠敃銏＄鐠轰警娼栨繛宸簻娴肩娀鏌涢弴銊ヤ簼婵炲牊鐓″铏规喆閸曨剙鈧劗绱掗悩宕囧⒌妤犵偛鍟妶锝夊礃閳轰椒缂撶紓浣稿⒔婢ф鏁嬫繛瀛樼矒缁犳牠骞冨畡鎵虫瀻闊洦鎼╂禒鍓х磼閻愵剙鍔ゆい顓犲厴瀵鈽夊Ο婊勬閸┾偓妞ゆ帒瀚悿顕€鏌涜椤ㄥ繘寮€ｎ喗鐓ユ繝闈涙椤ョ姵銇勯埡鍐ㄥ幋闁诡喗顨婇弫鎰板川椤栨瑧椹抽梻浣告惈閹冲繒鎹㈤崼婵愭綎婵炲樊浜濋ˉ鍫熺箾閹寸偞鐨戦柣锝夌畺濮婃椽宕崟顒€娅ょ紓渚囧枟閹逛礁螞閵忋倖鈷戦柣鎰閸旀粓鏌＄€ｎ亗鍋㈢€殿喓鍔嶇粋鎺斺偓锝庡亞閸樿棄鈹戦埥鍡楃仴婵炲拑绲剧粋鎺戔槈閵忥紕鍘搁梺绯曗偓宕囩婵炲懎鎳橀弻宥囨喆閸曨偆浼屽銈冨灪閻熲晠骞冨▎鎿冩晢濞达絽婀卞Σ鎴︽⒒閸屾瑧顦﹂柟鑺ョ矋閹便劑骞橀鑺ユ珖闂侀潧鐗嗛ˇ浠嬪极閸ヮ剚鐓曢煫鍥ㄦ礀鐢墎绱掗崜浣镐槐闁诡喗顨婇弫鎰償濠靛牐鍏掔紓鍌欓檷閸斿矂鎮樺杈ㄥ床婵犻潧顑嗛崑銊╂⒒閸喓鈼ョ紒顔挎硾閳规垿鍩ラ崱妞剧凹闂佽崵鍟块弲鐘绘偘椤曗偓楠炲洭顢橀悩鐢垫婵犳鍠楅敃鈺呭储閸忚偐顩叉俊銈呮噺閳锋垹绱撴担鑲℃垿鎮￠妷鈺傜厱婵﹩鍓﹂崕鏃堟煙椤斿厜鍋撻弬銉︾€婚悗鐢靛缁诲倻绱炴繝鍥х畺闁斥晛鍟崕鐔兼煥濠靛棙顥為柛鐘崇墬缁绘繈鎮介棃娑楁勃闂佹悶鍊ゆ禍顏勭暦濮椻偓瀹曟粏顦查柡浣规倐閺岋綁鎮㈤崫銉х厑缂備緡鍠楅幐鎼佹偩闁垮顕遍柡澶嬪灥閸炪劌顪冮妶鍡樺鞍缂佸鐗撳畷鎴﹀箻缂佹ɑ娅囬梺绋挎湰缁嬫垵鈻撻幋婵愭富闁靛牆妫涙竟鍕磼閺屻儳鐣烘い銏℃崌瀹曞爼顢楁担鍙夊濠电偠鎻紞鈧い顐㈩樀瀹曟垿鎮╃紒妯煎幈闂佸搫鍊藉▔鏇㈡倿閹间焦鐓欐い鏂挎惈閸旓箓鏌熼鐟板⒉闁诡垱妫冮、娆撳礂绾板崬鎮┑鐘垫暩婵兘寮崨濠冨弿闁汇垽娼ч弸鍫⑩偓骞垮劚椤︻垶鎮挎ィ鍐╃厵缂備焦锚娣囶垶鏌ｉ弬鎸庮棦闁诡喖缍婇幖褰掓偡閹殿喗顓婚梻浣姐€€閸嬫挾绱撴担楠ㄦ粍绂嶅鍫熺厪濠㈣埖绋戦々顒傜磼閻橀潧浠﹂柟渚垮妽缁绘繈宕橀埞澶歌檸闁诲氦顫夊ú婊堝礂濮椻偓閵嗕礁螣濞嗙偓鞋闂備礁鎲″褰掑箖閸岀偛钃熼柨婵嗘閸庣喖鏌ㄥ☉妯侯仹缂侇喚鏁婚弻锝夊閳轰胶浠柣銏╁灣閸犳牠骞冨Ο灏栧亾閿濆骸鏋熼柍閿嬪灴閺岀喓绮欓崹顕呭妷缂備讲鍋撻柍褜鍓熷铏圭矙閸栤€充紣缂傚倸绉撮敃顏勭暦閸濆嫧妲堥柕蹇曞Х椤撴椽姊洪幐搴⑩拻闁哄拋鍋婂畷銏ゅ础閻愨晜鏂€闂佺粯顭囩划顖氣槈瑜庢穱濠囶敃閿濆孩鐤佸銈冨灪濡啴銆佸Δ浣瑰闁稿繐鎽滈惄搴繆閻愵亜鈧牠宕濋敃鍌氱婵炴垯鍨洪崑鈺呮煟閵忕姵鍟為柣鎾寸洴閹﹢鎮欓幓鎺嗘寖闂佹剚鍨卞ú婊呮閹烘梻纾兼俊顖濆亹閻ゅ嫰鎮楃憴鍕闁挎洏鍨介獮鍐ㄢ枎閹垮啯鏅㈤梺绋挎湰缁嬫帡鎯侀弮鍫熲拻闁稿本鑹鹃埀顒勵棑缁牊绗熼埀顒勭嵁婢舵劖鏅柛鏇ㄥ墯濞堟澘鈹戞幊閸婃洟宕鐐叉辈婵犲﹤鐗婇悡娆撴煟閹寸儑渚涙繛鍫熸閺岋綁骞樼€靛摜鐤勫Δ鐘靛仦閻楁洝褰佸銈嗗坊閸嬫挸鈹戦檱閸嬫劗妲愰幒妤佸亹鐎瑰壊鍠氶崥瀣⒑閸濆嫭婀伴柣鈺婂灦閻涱噣宕堕澶嬫櫖濠殿喗顭堟ご鎼侇敋濡ゅ懏鈷掑〒姘ｅ亾闁逞屽墰閸嬫盯鎳熼娑欐珷濠电姵纰嶉悡鏇熶繆椤栨稐鑸柛瀣ㄥ灲閺屽秹濡烽敂绛嬫閻庤娲橀敃銏ゃ€佸▎鎾冲簥濠㈣鍨板ú锕傛偂閺囥垺鐓冮柍杞扮閺嬶箑銆掑顓犫姇闁逛究鍔嶇换婵嬪磼濠娾偓閾忓酣姊虹€圭媭娼愰柛銊ユ健瀹曟椽濡烽埡浣歌€垮┑锛勫仜婢т粙鎯勬惔銊︹拺閻犲洦褰冮銏ゆ煟椤撗冩珝鐎规洩绻濆畷姗€鎳犻浣诡啎婵犵數鍋涘Ο濠冪濠婂牊鍊块柛顭戝亖娴滄粓鏌熼崫鍕ф俊鎯у槻闇夋繝濠傚閻帡鏌″畝鈧崰鏍х暦椤愶箑绀嬫い鎺戭槹椤ワ絽鈹戦悙鑼憼缂侇喗鎸剧划濠氬冀椤撶偠鎽曢梺鎸庣箓濡瑩宕曢悢鍏肩厪闊洤锕ラ崳浼存煛鐎ｎ偅顥堟慨濠冩そ瀹曨偊宕熼鈧▍銈囩磽娴ｇ瓔鍤欓柣妤佹崌閹即顢氶埀顒勭嵁鐎ｎ喗鏅濋柍褜鍓熼敐鐐哄川鐎涙鍘藉┑顔筋殔濡绂嶅┑鍥ヤ簻妞ゆ劑鍨诲瓭闂佸疇顫夐崹鍧椼€佸▎鎾虫闁靛牆鎳庤ぐ鍡欑磽閸屾瑦绁伴悘蹇ｄ簼閹便劑濡堕崶鈺冪効閻庡箍鍎遍ˇ浼村磹闁垮浜滈柟鐑樺灥閳ь剙顭烽、娆撳即閵忊檧鎷洪梺鑽ゅ枛閸嬪﹪宕甸悢鍏肩厱閹兼番鍨规慨鍫㈢磼閸屾稑娴柟顔界懇椤㈡牠宕ｉ妷褏娉块梻鍌氼煬閸嬪嫬煤閿曞倸绠伴柟闂寸贰閺佸倿鏌曢崼婵愭Ч闁绘挻鐩幃妤呮晲鎼粹€茬凹濡炪們鍎遍悧鎾诲蓟閳ュ磭鏆嗛悗锝庡墮閸╁矂鏌ф导娆戠М闁哄被鍊曢湁閻庯綆鍋呴悵鏃傜磽娴ｈ娈ｇ紒缁樼箓椤繘鎼归崷顓狅紲濠碘槅鍨甸褔妫勫鍛斀闁绘劘娉涢惃娲煕閻樺磭澧い鏇稻缁绘繂顫濋鈧畵鍡涙⒑缂佹ɑ顥嗘繛鍜冪秮椤㈡瑩寮撮姀鈾€鎷洪梺鍛婃崄鐏忔瑩宕㈠☉銏＄厱闁绘ê鍟垮ù顔锯偓瑙勬礃閸ㄥ潡鐛Ο渚唵闁告洦鍓氶ˉ鍫⑩偓瑙勬磸閸旀垿銆佸▎鎾村亗閹肩补妲呭Λ妤佺節閻㈤潧啸闁轰焦鎮傚畷鎴濃槈濡粍绋戦埢搴ㄥ箻閸愯尙鈧參姊虹紒妯烩拻闁告鍛棜濠电姵纰嶉悡娆撴煟閹伴潧澧柣蹇涗憾閺岋紕鈧綆鍋呴ˉ鍫ユ煛鐏炲墽娲撮柍銉畵楠炲鈹戦崶鈺€鎲鹃梻鍌欐祰椤曆呮崲閹存緷娲冀椤撴稈鍋撻弮鍫濈妞ゆ柨妲堣閺屾盯鍩勯崘鍓у姼闂佺顑勭欢姘潖濞差亜绠归柣鎰絻婵⊙囨⒑閸涘﹣绶遍柛妯垮亹缁辨帡鍩￠崒婊呯槇闂佹眹鍨藉褑鈪烽梻浣规偠閸斿酣宕㈣閸┿垽寮惔鎾搭潔闂佸啿鐏堥弬渚€宕戦幘缁樻櫇闁稿被鍊栭弲銏ゆ⒑閸涘﹥澶勯柛鎾寸懄缁傚秴螖閸涱喒鎷洪梻鍌氱墛娓氭危閹绢喗鐓涢柛娑卞枤閻帡鏌熼鍡欑瘈鐎殿喗鎸抽幃銏ゆ惞鐠団€虫櫗闂傚倷鑳堕幊鎾绘偤閵娧勫床闁稿瞼鍋為崵瀣喐閻楀牆绗氶柣鎾跺枛閻擃偊宕惰瀹搞儱顭胯鐏忔瑧妲愰幒鏃傜＜婵☆垵鍋愰悿鍕節濞堝灝鏋撻柛瀣崌濮婃椽鎮欓挊澶婂缂佸墽铏庨崢鐣屸偓浣冨亹娴狅妇鎲撮敐鍡樻澑婵＄偑鍊栧濠氬Υ鐎ｎ喖绀夐柣鏃囨绾惧吋銇勯弴鐐村櫣闁诲骏闄勯〃銉╂倷閼碱剛顔掗悗瑙勬磸閸旀垵顕ｉ崼鏇炵婵犻潧鐗忓畷鐑樼節閻㈤潧啸闁轰礁鎲￠幈銊╁箻椤旇偐鏌堝銈嗗姧缁犳垹绮堥崼銉︾厽闁靛繒濮甸崯鐐烘煃闁垮绗掗棁澶愭煥濠靛棙鍣洪柛鐔哄仱閺岀喖宕ｆ径瀣偓鎰版煛鐏炲墽娲撮柡浣瑰姍瀹曟﹢濡搁妷銉愭洟鏌ｆ惔銈庢綈婵炲弶鐗滅槐鐐寸節閸屾粍娈炬繝闈涘€婚…鍫ュ础閹惰姤鐓熸俊顖氱仢閻ㄦ椽鏌曢崼顒傜暤婵﹦绮幏鍛存倻濡儤鐣俊鐐€栧ú锕傚矗閸愩劎鏆﹂柟杈鹃檮閸嬪嫰鏌涜箛鏇炲付濞寸媭鍙冮幃宄邦煥閸涱収鏆柣銏╁灡椤ㄥ﹪骞冮敓鐘茬妞ゅ繐鎳庨弸鎴濃攽閻樿宸ラ柣妤€妫涚划鍫ュ醇閵夛妇鍘介梺鍝勫暙閻楁粌螞閹达附鐓涢柛鈽嗗弮濡绢喚绱掔紒妯尖槈妞ゆ柨绻橀、娆撴嚒閵堝洦鏅ㄩ梻鍌欒兌缁垳鏁幒妤佸€舵慨妯挎硾妗呴梺鍛婃处閸ㄦ壆绮诲畷鍥ｅ亾楠炲灝鍔氭い锔垮嵆椤㈡捇寮介妸褏顔曢柣搴㈢⊕椤洭鎯岀€ｎ剛纾兼い鏃囧Г瀹曞瞼鈧鍠栭…鐑藉箖閵忋倕绀傞柣鎾崇岸閸嬫挾绱掑Ο鍦畾濡炪倖鐗楃换鍐敂閻樿褰掓偐閾忣偄鍞夊┑顔硷攻濡炶棄鐣烽锕€绀嬫い鎺戭槹閿熴儵姊绘担渚劸妞ゆ垵娲畷浼村冀椤撶偟鐣鹃梺閫炲苯澧柕鍥у缁犳盯骞樼捄琛℃嫛闂備浇妗ㄧ粈浣该洪銏犺摕闁挎繂顦粻娑㈡煟濡も偓閻楀﹪鏌ㄩ銏♀拺缂備焦蓱鐏忕敻鏌涢悩宕囧ⅹ妞ゎ偄绻愮叅妞ゅ繐瀚槐鍫曟⒑閸涘﹥澶勯柛鐘崇墵椤㈡棃顢橀悩鐢碉紳婵炶揪绲芥竟濠偽ｅú顏呯厱闊洦妫忛悞浠嬫煟閿旇澧紒缁樼洴楠炴﹢寮堕幋鐘插Р闂備胶顭堥鍡涘箲閸ヮ剙钃熼柨娑樺濞岊亪鏌涢幘妤€瀚崹杈ㄤ繆閵堝洤啸闁稿鍋ら垾锕傚炊椤掆偓閻掑灚銇勯幒鎴濐仾婵炴嚪鍥ㄧ厵闁兼亽鍎抽惌鎺楁煙椤斿吋鍋ョ€规洜鍠栭、娑樷槈閹烘挸顏归梻鍌欑濠€閬嶁€﹂崼婢濊顦版惔锝勭瑝濠殿喗銇涢崑鎾绘煛鐏炲墽銆掗柍褜鍓ㄧ紞鍡涘礈濞嗘劗顩锋繝濠傚娴滄粓鏌￠崶顭嬵亪宕濋敂閿亾鐟欏嫭绀堥柛鐘崇墵閵嗕礁螖閸涱厾锛滃┑鐘诧工妤犳悂寮查銏♀拻闁稿本鐟чˇ锔界節閳ь剟鏌嗗鍛紵闂侀潧鐗嗛ˇ顖炲垂閸岀偞鐓曟い鎰剁稻缁€鈧紓浣哄У瀹€鎼佸蓟濞戙埄鏁冮柕鍫濇噺閻庮厽绻涚€涙鐭嬬紒顔肩Ч婵＄敻宕熼姘辩杸闂佸憡鎸烽悞锕傚磻瑜斿鍝劽虹拠鎻掔闁汇埄鍨界换婵嗩嚕鐠囨祴妲堥柕蹇曞Х閸樻挳姊虹拠鈥崇€婚柛灞惧嚬濡粌鈹戦悩娈挎殰缂佽鲸娲熷畷鎴﹀箣閿曗偓绾惧綊鏌″搴′簼闁哄棙绮撻弻鐔兼倻濡崵鍘搁梺绋款儐閹告悂锝炲┑瀣亗閹肩补妲呭鍓х磽閸屾瑧璐伴柛鐘崇墵閳ワ箑鐣￠柇锕€娈ㄦ繛瀵稿Т椤戞劙寮崶顒佺厽闁哄倹瀵ч幆鍫燁殽閻愭惌娈滄慨濠勭帛閹峰懏绗熼娑欐殲闂備浇顫夊鎸庣閻愬樊鍤楀┑鐘插暔閸嬪懘鏌涜箛锝呯仩妞わ附婢橀銉╁礋椤栨氨鐤€濡炪倖甯掗崐濠氬汲娴煎瓨鈷掑ù锝堟鐢盯鏌＄仦璇插鐎规洏鍎抽埀顒婄秵閸犳牜澹曟繝姘厓闁宠桨绀侀弳鐔虹磼閳锯偓閸嬫挸鈹戦悩鍨毄濠殿喗鎸冲畷鎰板箹娴ｅ憡杈堥梺缁樻濞咃絿澹曟總鍛婄厽婵☆垳鍎ら幆鍫ユ煟閵娿劍顏犵紒杈ㄥ笚濞煎繘濡搁妷褜鍎岄梻渚€鈧偛鑻晶鍓х磽瀹ュ懏顥炵紒鍌氱Ф閹瑰嫭鎷呴弴顏嗙М鐎规洏鍔嶇换娑欏緞閸濄儳娉块梻鍌欒兌閹虫挻鏅舵禒瀣；闁靛ň鏅涚壕濠氭煏韫囧﹥鍣伴柛瀣尵閹叉挳宕熼鍌ゆК闁诲氦顫夊ú锕傚礈閻旂厧鏄ラ柕蹇婂墲閸庣喖鏌曟繛鍨姢妞ゆ挸鎼—鍐Χ閸℃ê鏆楅梺绋款儑閸嬫盯鍩㈠鍛傛棃宕ㄩ瑙勫闂備胶顭堥張顒勬嚌妤ｅ啫鐒垫い鎺戝€搁崢鎾煙閾忣偒娈滅€规洖銈稿鎾倷閻㈠灚鐎梻鍌欒兌閹虫挻鏅堕悾灞藉灊妞ゆ牜鍋涚壕濠氭煏閸繍妲归柣鎾崇箰閳规垿鎮欓棃娑楀濠电偛鎳庡Λ婵嬪蓟濞戞埃鍋撻敐鍛暢缂佲檧鍋撻梻浣告惈閺堫剙煤濡警鍤楅柛鏇ㄥ灠楠炪垺淇婇婵嗗惞鐟滄澘妫濆缁樻媴娓氼垳鍔搁梺鎸庢磸閸婃繈骞冮妷锔鹃檮缂佸鍎婚幗鏇㈡倵楠炲灝鍔氭い锔诲灡鐎靛ジ鎮╃紒妯煎幈闂佸搫娲㈤崝灞剧濠靛洨绠鹃柛顐ｇ箓椤ｅ磭绱掓潏銊ユ诞闁诡喗鐟╅、妤呭焵椤掑嫬绀夐柕鍫濐槹閻撳繘鏌涢銈呮瀾闁稿﹥鍔楅埀顒侇問閸犳牠鈥﹂悜钘夋瀬闁圭増婢樺婵嬫煕鐏炲墽鐭婇柡瀣洴閺岀喖顢欓悾宀€鐓€闁句紮缍佸娲垂椤曞懎鍓虫繛瀛樼矌閸嬨倕顫忔繝姘＜婵炲棙甯掗崢锛勭磽娓氬洤娅橀柛銊ョ埣閻涱噣骞囬弶璺啇婵炶揪缍€閸婂€燁樄闁哄瞼鍠栧鑽も偓闈涘濡差噣姊洪挊澶婃殶闁哥姵鐗犲濠氬Ω閵夈垺顫嶅┑鈽嗗灟鐠€锔界閻熸壋鏀介柣鎰皺閻掓儳霉濠婂簼閭鐐插暢閵囨劙骞掗幋鐘测偓鐐烘⒑闂堟丹娑欐媴闂€鎰媰闂傚倸鍊烽懗鍫曗€﹂崼銉晞闁糕剝绋戠粻浼存煙闂傚顦﹂柣鎾达耿閺岀喐娼忛崜褏鏆犲Δ鐘靛亼閸ㄨ櫣鎹㈠☉銏犲耿婵☆垰鎼～宥咁渻閵堝棙鈷愭繛鍙壣戠粚杈ㄧ節閸ャ劌鈧攱銇勮箛鎾愁仱闁稿鎹囧浠嬵敇閻愭妲烽柣鐔哥矊缁绘帡宕氶幒鎾剁瘈婵﹩鍓涢鍝勨攽閻樼粯娑ф繛璇х畱閳绘挻瀵肩€涙ǚ鎷婚梺绋挎湰閼归箖鍩€椤掑倸鍘撮柟顔惧仱閺佸倿鏌ㄩ姘缂備焦顨嗙粙鎴﹀触濡　妲堟繛鍡樺姇椤庢捇鏌ｉ悢鍝ユ噧閻庢凹鍓欓锝夊锤濡や讲鎷洪梻鍌氱墛缁嬫挻鏅堕弴鐔翠簻闁挎柨鐏濆畵鍡欌偓瑙勬礃濞叉鎹㈠┑瀣倞鐟滃繘顢欓幇顑芥斀闁绘垵娲ㄧ粔鐢告煕閹惧鎳囬柟顔斤耿椤㈡瑩宕滆閿涙粓姊洪幎鑺ユ殰闁稿鎹囬弻娑氣偓锝庡亝瀹曞矂鏌″畝瀣ɑ闁诡垱妫冮弫鎰板幢濡崵妲戞繝鐢靛Л閹峰啴宕熼崹顐ゆ澒闂備浇顕栭崰妤呮晪濡炪値鍋呯换鍕箲閸曨垱鎯為柣褍鎽滄稉鏌ユ⒒閸屾瑧绐旀繛浣冲洦鍋嬮柛鈩冭泲閸ヮ剦鏁嶉柣鎰蔼琚濋柣鐔哥矌婢ф鏁Δ鍛９闁汇垹鎲￠悡銉︾節闂堟稒顥㈡い搴㈩殔椤儻顦冲鐟版閹偓妞ゅ繐鐗嗙粻姘辨喐鐎ｎ喖纾婚柕蹇娾偓鑼畾濡炪倖鍔戦崹娲吹閸ャ劊浜滈柕蹇婂墲缁€瀣亜閵忊剝绀嬪┑顔瑰亾闂佺粯蓱椤旀牠宕€ｎ喗鈷掗柛灞剧懅閸斿秵淇婇悙鍙夊仴鐎规洏鍨虹粋鎺斺偓锝庡亜娴狀參姊洪崘鍙夋儓闁瑰啿閰ｉ幏鎴︽偄閸濄儳顔曢梺鐟扮摠閻熴儵鎮橀埡鍛厽闁规儳顕ú鎾煛鐏炲墽娲存鐐叉喘閸┾剝鎷呴崜鑼偓杈ㄧ節绾版ǚ鍋撻崘鑼獓闂佸憡姊瑰ú婵嬫倶閸愵喗鈷戠紓浣癸公閼版寧绻涙担鍐插椤╅攱绻濇繝鍌氼仴濞存粍绮撻弻鐔衡偓娑欘焽缁犮儲淇婇妤€浜鹃梺璇插椤旀牠宕抽鈧畷鎴炵節閸屾粍娈鹃梺鍦劋椤ㄥ棝宕愰悜鑺ョ厸濠㈣泛顑呴悘鈺備繆缂併垹鏋涢柍瑙勫灴閹晠顢欓懖鈺€绱樻繝鐢靛仜椤︿即鎯勯婵囶棨濠电偛顕崢褔鎮洪妸鈺佺？闁规崘宕甸崣鎾绘煕閵夛絽濡介悘蹇嬪姂閺岋繝鍩€椤掍胶顩烽悗锝庡亞閸欏棗鈹戦悙鏉戠仸闁挎碍銇勮箛濠冩珔闂囧绻濇繝鍌氭殧闁稿鍨介弻锛勪沪閸撗€濮囩紓浣虹帛缁诲牆鐣峰鈧、鏃堝礋閵娿儲鍊曢梻鍌欑劍瀹曟ê鐣烽悽绋跨；婵炴垯鍨硅繚闂佸湱鍎ら崺鍫濐焽閳哄懏鐓ユ繝闈涙閸ｇ顭跨憴鍕闁哄矉绲鹃幆鏃堝Χ鎼淬垻绉锋繝鐢靛仜瀵爼鏁冮姀鐘垫殾鐟滅増甯掗柨銈嗕繆閵堝懎鏆欑紒渚婄畵濮婃椽骞栭悙鎻掑Ф闂佸憡鎼粻鎾愁嚕椤愶箑鐐婃い鎺嶈兌閸樹粙姊洪悷鎵憼缂佽鍟磋棢婵犲﹤瀚粻楣冩煕椤愶絿缂氶柛瀣ㄥ灪閹便劍绻濋崟顓炵闂佺懓鍢查幊鎰板箟閹绢喖绀嬫い鎰ㄢ偓铏啟闂傚倸鍊烽懗鍫曗€﹂崼鈶╁亾濞戞帗娅呮い顏勫暣閹稿﹥寰勭仦鐐啎婵犵數濮撮敃銈夊窗濮橆剦鐒介柍鍝勬噺閸嬶絽螖閿旇棄顕滄い蹇曞█閺屾稑顫滈埀顒佺鐠轰警娼栨繛宸簼閸嬶繝姊洪銊х暠婵炲牐鍩栫换婵嗏枔閸喗鐝濆銈冨劜閹歌崵绮╅悢鐓庡嵆闁靛繒濮烽ˇ褏绱撴担璇℃畽婵℃ぜ鍔庨懞杈ㄧ節濮橆厸鎷洪梺鍛婄箓鐎氼喛鈪归梻浣告啞閺屻劑鏌婇敐澶婃槬濠电姴娲ら崡鎶芥煟濡吋鏆╅柨娑欑矒濮婃椽鏌呴悙鑼跺濠⒀冾嚟閳ь剝顫夊ú姗€鏁冮姀銈呮槬闁跨喓濮寸粈鍐┿亜韫囨挾孝闁哄拋鍙冨濠氬磼濞嗘埈妲梺瑙勭ゴ閸撴繄绮悢鑲烘棃宕ㄩ鐐蹭憾濠电偛顕慨鎾偡閵堝棗绶為柟閭﹀墻濞煎﹪姊洪棃娑氬婵☆偅鐩畷銏ゅ箹娴ｇ懓鈧敻鏌涜箛鎿冩Ц濞存粓绠栭弻锝嗘償椤栨粎校闂佸憡鎸婚悷鈺佺暦閸濆嫮鏆嗛柛鏇ㄥ墰閸欏嫮绱撻崒娆戝妽妞ゎ厼娲﹂弲鍫曟偨閸涘﹦鍘搁柣蹇曞仜婢ц棄煤鐎涙﹩娈介柣鎰级閸犳﹢鏌涢埞鎯у⒉闁瑰嘲鎳橀幊鏍р攽閸曨偄顫囧┑顔硷攻濡炶棄鐣烽锕€绀嬮梻鍫熺☉婢瑰牓姊绘担鐑樺殌闁哥喎鐏濋～婵嬫晝閸屾稑鈧爼鏌涢幇闈涙灍闁抽攱鍨圭槐鎾存媴閻х鍙曢梺鑲╁鐎笛呮崲濞戞瑦濯撮柛鎰絻椤忣偅銇勯埡鍛暠缂佺粯绻冪换婵嬪磼濠婂喚鏉搁梻浣虹帛閹稿爼宕曢悽绋胯摕闁跨喓濮撮悙濠冦亜椤撶儐鍎涘ù婊庝邯瀹曟椽鍩€椤掍降浜滈柟鍝勭Х閸忓瞼绱掗悩闈涗户闁逞屽墲椤煤濡警娓诲ù鐘差儏缁犳牗绻涢崱妯诲碍濞磋偐濞€閺屾盯寮撮妸銉ュ闂佸憡鑹鹃幊妯侯潖缂佹ɑ濯撮柣鐔煎亰閸ゅ鈹戦悙鏉戠祷缂佸鎳撻悾鐑藉箣閿曗偓缁犺崵绱撴担璇＄劷闁告ü绮欏楦裤亹閹烘垳鍠婇梺鍛婃尪閸旀垿鎮￠鍕垫晢闁告洦鍓涢崢鎼佹煟韫囨洖浠ч柛瀣尵缁牓宕橀浣镐壕闁割煈鍋呯欢鏌ユ倵濮橆厽绶查柣锝囧厴閹剝鎯斿Ο缁樻澑闂備胶纭堕崜婵嬨€冭箛鎿冪劷婵浜壕浠嬫煕鐏炴崘澹橀柍褜鍓涢崗妯侯潖閽樺鍚嬪璺猴功椤︻噣鎮楅崗澶婁壕闂佸憡娲﹂崐鐘侯槾缂佽鲸甯為埀顒婄秵閸嬫帡宕曢妷锔剧闁稿繗鍋愭晶杈╃磼鏉堛劍灏伴柟宄版嚇閹虫牠鍩勯崘銊т桓闂佸憡甯掗敃顏堢嵁濮椻偓椤㈡瑩鎮剧仦钘夌疄濠电姷鏁搁崑鐐哄垂閸洘鏅濇い鎰╁焺閸熷懘鏌曟径鍡樻珕闁绘挻鐩弻娑㈠焺閸愮偓鐣兼繝鈷€宥夋闁逛究鍔嶇换婵嬪礃閳瑰じ铏庨柣搴ゎ潐濞插繘宕濆鍥ㄥ床婵犻潧顑呯粈鍐┿亜韫囨挸顏╂慨纭呭皺缁辨捇宕掑顑藉亾閻戣姤鍤勯柛鎾茬閸ㄦ繃銇勯弽顐沪闁哄拋浜濇穱濠囨倷椤忓嫧鍋撻弽顬℃椽鍩￠崘顏嗩槸婵炴挻鍩冮崑鎾绘煟濞戝崬娅嶆鐐搭焽閹风娀鎳犻澶婃暯闂傚倷鑳剁涵鍫曞礈濠靛鏅濋柍杞扮贰閻掍粙鏌￠崘銊у闁抽攱甯掗妴鎺戭潩閿濆懍澹曟繝鐢靛仒閸栫娀宕ㄩ鐓庣哎闂備礁婀遍崕銈夊箰婵犳艾鐭楅煫鍥ㄧ⊕閻撶喖鏌熺€涙绠撴い銉у仱閺屾盯濡烽鐓庘拻闂佽桨绀佸ú顓㈠蓟閿濆绠ｆ繝闈涙祩濡繝鏌ｆ惔銊︽锭闁活厼鍊搁～蹇撁洪鍜佹濠电偞鍨堕懝楣冦€傞崫鍕ㄦ斀闁宠棄妫楁禍婵嬫煟閻旀繂瀚々鏌ユ煙闂傚鍔嶉柛瀣ㄥ姂閺屾稑鈽夊鍫濆闂佸憡锕㈡禍鍫曞蓟閻旂厧绀傞柤娴嬫櫆濞堫剟姊洪幖鐐插妞ゎ偄顦甸獮鍫ュΩ閳轰胶鍊炲銈嗗笂閼冲爼宕㈤鍫熲拺缂侇垱娲栨晶鏌ユ煥濮樿京纾兼俊銈勭劍閻ㄦ垹绱掓潏銊ョ缂佽鲸甯掕灒闁兼祴鏅濋弳銈嗕繆閻愵亜鈧倝宕戦崨顖涘床闁割偁鍎埀顑跨椤粓鍩€椤掑嫬绠栭柍鍝勬噹缁犵敻鏌熼悜妯虹劸婵炲樊鍙冨濠氬磼濮橆兘鍋撻幖浣哥９濡炲娴烽惌鍡椕归敐鍛础闁绘繆娉涢埞鎴︽偐閸欏鎮欑紒鐐劤椤兘寮诲☉妯锋闁告鍋熸禒顓㈡倵鐟欏嫭绀冮柣鎿勭節瀵鈽夐姀鐘电杸闂傚倸鐗婄粙鎺戔枔濞嗘劗绡€闁靛骏绲介悡鎰版煕閺冣偓閻楃姴鐣烽幋锕€绠涙い鏃傛嚀娴滈箖鏌ｉ悢鍛婄凡妞ゃ儱绻橀弻娑㈡偐閸愭彃鎽甸悗瑙勬处閸撶喖骞冩禒瀣窛濠电姴鍊烽悽缁樼節閻㈤潧孝闁挎洏鍊楅埀顒佸嚬閸欏啴宕哄☉銏犵疀妞ゆ柨澧介敍婊堟⒑缁嬫寧婀版慨妯稿妽閺呫儵鏌ｆ惔銈庢綈婵炲弶鐗犳俊鍓佺矙濞嗘儳娈ㄩ梺鍦檸閸犳宕愰悜鑺ョ厾缂佸娉曟禒娑㈡煕閻旈攱鎯堥柍瑙勫灴閹晠宕归锝嗙槑闂備胶顭堥鍥磻閵堝懐鏆︾憸鐗堝笒闁卞洭鏌￠崶鈺佷沪婵″樊鍓熷娲川婵犲倸袝闂佸摜濮甸悧婊勭珶閺囩喓绡€婵﹩鍘鹃崢鐢告⒑閸涘﹥瀵欓柛娑卞幘椤愬ジ姊绘担鍛婃儓闁瑰啿绻橀幃锟犳晸閻橀潧绁﹂梺鍝勭▉閸嬪嫰宕瑰┑瀣厱闊洦鎼╁Σ绋棵瑰鍫㈢暫闁哄瞼鍠愰敍鎰媴閸濆嫬顬夊┑鐐茬摠閸ゅ酣宕愰弽顐ｅ床婵犻潧顑嗛崑銊╂⒒閸喓鈼ョ紒顔挎硾椤啴濡堕崱妯侯槱闂佸憡眉缁瑩鐛崼銉ノ╅柍鍝勫€搁埀顒勬敱閵囧嫰骞掗幋婵呯敖濠电偠灏欓…鍫モ€旈崘顔嘉ч柛鈩冾殘閻熴劑鏌ｆ惔銏犲毈闁告挾鍠栭獮?
			if (realReduce > 0) {
				if (curCountOfCurrentItem == realReduce) {
					selectedItemsMap.remove(imageKey);
				} else {
					selectedItemsMap.put(imageKey, curCountOfCurrentItem - realReduce);
				}
				currentTotalPrice -= realReduce * price * (1 + taxRate);
				if (Math.abs(currentTotalPrice) < 0.005f) {
					currentTotalPrice = 0.0f;
				}
				currentTotalQt -= realReduce;
			}
		}

		request.getSession().setAttribute(CC.itemNumber, currentTotalQt);
		request.getSession().setAttribute(CC.totalPrice, currentTotalPrice);
		request.getSession().setAttribute(CC.selectedItems, selectedItemsMap);

		Map<String, Object> result = new HashMap<>();
		result.put("itemNumber", currentTotalQt);
		result.put("totalPrice", currentTotalPrice);
		result.put("selectedItems", selectedItemsMap);

		return ResponseEntity.ok(result);
	}

	@RequestMapping(value = "/{client}/updateGeo/{latitude}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> updateGeo(@PathVariable(CC.CLIENT) String client,
			@PathVariable(CC.latitude) String latitude, HttpServletRequest request) {

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return updateGeo(latitude, request);
	}

	@RequestMapping(value = "updateGeo/{latitude}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> updateGeo(@PathVariable(CC.latitude) String latitude, HttpServletRequest request) {
		request.getSession().setAttribute(CC.latitude, latitude);
		request.getSession().setAttribute(CC.longitude, request.getParameter(CC.longitude));

		return new ResponseEntity<String>(HttpStatus.OK);
	}

	// ===================Operations for dashboard=======================================

	/**
	 * this method is called when visitor click the shopping car, it will show what visitor has selected, and above that, will
	 * show the info-collection form.
	 * before return back to a freature page, very importantly, it will set up the showCollectForm flag. (a normal feature page without this flag will hide the info-collection form)
	 */
	@RequestMapping(value = "/showSelection")
	public String showSelectionPage(Model model, HttpServletRequest request) {
		Person person = taoUtil.getCurPerson(request);
		String langPrf = taoUtil.getLangPrfWithDefault(request);
		String menuPRF = langPrf + "menu_";

		HttpSession session = request.getSession();
		TaoDebug.info(TaoDebug.getSB(request.getSession()),
				"start to initFeatureSubPage for showSelection, for client: {}, tableID is {}", person.getName(),
				session.getAttribute(CC.tableID));

		Map<String, Integer> selectedItems = (Map<String, Integer>) session.getAttribute(CC.selectedItems);
		Map<String, String> noteOfSelectedItems = (Map<String, String>) session.getAttribute(CC.noteOfSelectedItems);
		if (selectedItems == null) selectedItems = new HashMap<>();
		if (noteOfSelectedItems == null) noteOfSelectedItems = new HashMap<>();

		List<Feature> features = new ArrayList<>();
		List<List<String>> imageKeyLists = new ArrayList<>();
		List<List<String>> descriptions = new ArrayList<>();
		List<List<String>> prices = new ArrayList<>();
		List<List<String>> visibleStatusList = new ArrayList<>();
		List<String> groupTitles = new ArrayList<>();

		Map<String, Map<String, Integer>> grouped = new HashMap<>();
		for (Map.Entry<String, Integer> entry : selectedItems.entrySet()) {
			String imageKey = entry.getKey();
			String note = noteOfSelectedItems.getOrDefault(imageKey, " ");
			String menuIdx = taoUtil.getMenuIdxFromImageKey(imageKey);
			grouped.computeIfAbsent(menuIdx, k -> new HashMap<>());

			String compositeKey = imageKey + "@@" + note; // key with note to distinguish different notes
			Map<String, Integer> subMap = grouped.get(menuIdx);
			subMap.put(compositeKey, entry.getValue());
		}

		List<String> menuTypes = Arrays.asList(CC.SERVICE, CC.HTML_SERVICE);
		List<String> menusForRef = taoUtil.fetchAllMenuByType(menuTypes, request, langPrf, person);

		boolean groupedSelection = CC.TRUE.equals(session.getAttribute("dsp_groupedSelection"));
		for (String menuSring : menusForRef) {
			Map<String, Integer> itemMap = grouped.get(menuSring);
			if (itemMap == null) continue;

			List<String> keys = new ArrayList<>();
			List<String> visibility = new ArrayList<>();
			for (Map.Entry<String, Integer> itemEntry : itemMap.entrySet()) {
				String[] parts = itemEntry.getKey().split("@@");
				String imageKey = parts[0];
				String note = parts.length > 1 ? parts[1] : " ";

				keys.add(imageKey);
				visibility.add(String.valueOf(itemEntry.getValue()));
			}

			visibleStatusList.add(visibility);
			imageKeyLists.add(keys);
			taoUtil.fillInDescriptions(langPrf, person, descriptions, keys);
			taoUtil.fillInPrices(person, request.getSession(), prices, keys);

			Feature feature = new Feature();
			feature.setId(0l);
			feature.setMenuIdx(menuSring);
			features.add(feature);

			TextContent textContent = textContentService.findContentsByKeyAndPerson(menuPRF + menuSring, person);
			groupTitles.add(textContent != null ? textContent.getContent() : "");
		}

		model.addAttribute("features", features);
		model.addAttribute("imageKeys", imageKeyLists);
		model.addAttribute("descriptions", descriptions);
		model.addAttribute("prices", prices);
		model.addAttribute("visibleStatusList", visibleStatusList);
		model.addAttribute("groupTitles", groupTitles);
		model.addAttribute("shoppingCartMode", CC.TRUE);
//		model.addAttribute("support_times", groupedSelection);
//		model.addAttribute("show_service_cBox", groupedSelection);
//
//		Customize curStatusTotal = customizeService.findCustomizeByKeyAndPerson("show_status_total", person);
//		if (curStatusTotal != null && CC.TRUE.equals(curStatusTotal.getCusValue())) {
//			model.addAttribute("show_status_total", CC.TRUE);
//		}
//		model.addAttribute("show_status_break", CC.TRUE);
//		model.addAttribute("show_status_message", null);
//		model.addAttribute("show_status_submit", CC.TRUE);

		// Form section loading
		String menuIdxString = (String) session.getAttribute(taoUtil.INFO_COLLECT_FORM_CUST_PAGE_PATH);
		if (menuIdxString != null && menuIdxString.toLowerCase().startsWith("menu_")) {
			model.addAttribute("showCollectForm", menuIdxString);
			int[] menuIdxes = taoUtil.splitMenuIdxToAry(menuIdxString.substring(5));
			taoUtil.initFormSubPage(request, model, langPrf, menuIdxes[0], menuIdxes[1], menuIdxes[2], person);
		}

		return "/generalFeaturePage";
	}

	@RequestMapping(value = "/{client}/updateAnOrderStatus/{mainOrderID}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> updateAnOrderStatus(@PathVariable("client") String client,
			@PathVariable("mainOrderID") String mainOrderID, Model model, HttpServletRequest request) {

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return updateAnOrderStatus(mainOrderID, model, request);
	}

	@RequestMapping(value = "updateAnOrderStatus/{mainOrderID}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> updateAnOrderStatus(@PathVariable("mainOrderID") String mainOrderID, Model model,
			HttpServletRequest request) {

		UserAccount user = (UserAccount) request.getSession().getAttribute(CC.currentUser);

		String[] ids = mainOrderID.split(",");
		for (String orderId : ids) {

			Long id = Long.valueOf(orderId);
			MainOrder mainOrder = mainOrderService.findMainOrder(id);
			String recordStatus = request.getParameter("recordStatus");
			int status = Integer.valueOf(recordStatus);
			if (status == CC.STATUS_PRINTED) {
				// not really setting the status, but up the level of each material. and put
				// name to mainOrder.
				// later on when someone fetching order to print, will update the order to
				// "PRINTED".
				// (to avoid multi-thread issue)
				List<Material> materials = materialService.findAllMaterialsByMainOrderIdAndPrinter(mainOrder, user);
				String printerName = TaoEncrypt.stripUserName(user.getLoginname());
				for (Material material : materials) {
					String printersStr = material.getMenFu();
					String[] ary = printersStr.split(",");
					int step = CC.LEVEL_FULL / (ary.length - 1);// @NOTE:7 and 9 will be trouble number. don't let a
					// dish link to7 or 9 printer.
					material.setRecordStatus(material.getRecordStatus() + step);
					materialService.persist(material);
				}
				String printerStr = mainOrder.getColorCard();
				if (printerStr == null) {
					printerStr = "," + printerName + ",";
				} else {
					printerStr = printerStr + printerName + ",";
				}
				mainOrder.setColorCard(printerStr);
			} else {
				mainOrder.setRecordStatus(status);
				mainOrder.setContactPerson(user);
			}
			mainOrderService.persist(mainOrder);
		}
		return new ResponseEntity<String>(HttpStatus.OK);
	}


	@RequestMapping(value = "/{client}/cleanHistoricalMainOrder/", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> cleanHistoricalMainOrder(@PathVariable("client") String client, Model model,
			HttpServletRequest request) {

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return cleanHistoricalMainOrder(model, request);
	}

	@RequestMapping(value = "cleanHistoricalMainOrder/", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> cleanHistoricalMainOrder(Model model, HttpServletRequest request) {

		Person person = taoUtil.getCurPerson(request);

		List<MainOrder> mainOrders = mainOrderService.findCompletedMainOrdersByPerson(person, "desc");
		for (MainOrder mainOrder : mainOrders) {
			deleteAnOrder(mainOrder);
		}
		return new ResponseEntity<String>(HttpStatus.OK);
	}

	public void deleteAnOrder(MainOrder mainOrder) {
		List<Material> materials = materialService.findAllMaterialsByMainOrder(mainOrder);
		for (Material material : materials) {
			materialService.remove(material);
		}
		List<TaxonomyMaterial> taxonomyMaterials = taxonomyMaterialService.findAllTaxonomyMaterialsByMainOrder(mainOrder);
		for (TaxonomyMaterial taxonomyMaterial : taxonomyMaterials) {
			taxonomyMaterial.setMainOrder(null);
			taxonomyMaterialService.persist(taxonomyMaterial);
		}
		mainOrderService.remove(mainOrder);
	}

	@RequestMapping(value = "/{client}/generateQRCodes/", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> generateQRCodes(@PathVariable("client") String client, Model model,
			HttpServletRequest request) {

		return generateQRCodes(model, request);
	}

	@RequestMapping(value = "generateQRCodes/", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> generateQRCodes(Model model, HttpServletRequest request) {

		Person person = taoUtil.getCurPerson(request);

		HttpSession session = request.getSession();
		String menuIdx = (String) session.getAttribute(CC.default_feature_menu);

		// start
		String table = (String) session.getAttribute("start_table");
		String customer = (String) session.getAttribute("start_customer");
		int tableStart = 1;
		int customerStart = 1;
		try {
			tableStart = Integer.valueOf(table);
		} catch (Exception e) {
		}
		try {
			customerStart = Integer.valueOf(customer);
		} catch (Exception e) {
		}
		// limit
		table = (String) session.getAttribute("limit_table");
		customer = (String) session.getAttribute("limit_customer");
		int tableLimit = 0;
		int customerLimit = 0;
		try {
			tableLimit = Integer.valueOf(table);
			tableLimit = tableLimit > 200 ? 200 : tableLimit;
		} catch (Exception e) {
		}
		try {
			customerLimit = Integer.valueOf(customer);
			customerLimit = customerLimit > 100 ? 100 : customerLimit;
		} catch (Exception e) {
		}

		String textFix = composeQRCodeText(request, menuIdx);
		String text = null;
		for (int i = tableStart; i <= tableLimit; i++) {
			if (customerLimit > 0) {
				for (int j = customerStart; j <= customerLimit; j++) {
					String filePath = "QRCode_temp_" + i + "_" + j;
					text = textFix + "?t=" + i + "_" + j;
					generateQRCode(request, filePath, text, person);
				}
			} else {
				String filePath = "QRCode_temp_" + i;
				text = textFix + "?t=" + i;
				generateQRCode(request, filePath, text, person);
			}
		}
		return new ResponseEntity<String>(HttpStatus.OK);
	}

	private String composeQRCodeText(HttpServletRequest request, String menuIdx) {
		HttpSession session = request.getSession();

		String app_WebsiteAddress = (String) session.getAttribute(CC.app_WebsiteAddress);
		if (StringUtils.isBlank(app_WebsiteAddress)) {
			if (session.getAttribute(CC.app_name) != null) {
				app_WebsiteAddress = "https://" + session.getAttribute(CC.app_name);
				if(!app_WebsiteAddress.contains(".")) {
					app_WebsiteAddress += ".com/";
				}
			} else {
				TaoDebug.error("app_name not set, so cannot do {}! ", "composeQRCodeText");
				return null;
			}
		} else if (!app_WebsiteAddress.startsWith("http")) {
			app_WebsiteAddress = "https://" + app_WebsiteAddress;
		}
		String menuPrf = app_WebsiteAddress.endsWith("/") ? "menu_" : "/menu_";
		if (menuIdx.startsWith("menu_")) {
			menuIdx = menuIdx.substring(5);
		}
		String text = app_WebsiteAddress + menuPrf + menuIdx;
		return text;
	}

	// ====================================services=====================================
	@RequestMapping(value = "/{client}/serviceDetailJSon/{keyStr}", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> showDetailByKey1(@PathVariable(CC.CLIENT) String client,
												 @PathVariable("keyStr") String keyStr, HttpServletRequest request) {
		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return showDetailByKey(keyStr, request);
	}
	@RequestMapping(value = "serviceDetailJSon/{keyStr}", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> showDetailByKey(@PathVariable("keyStr") String keyStr, HttpServletRequest request) {

		// 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇川绾剧晫鈧箍鍎遍幏鎴︾叕椤掑倵鍋撳▓鍨灈妞ゎ厾鍏樺顐﹀箛椤撶偟绐炴繝鐢靛Т鐎氱兘宕ラ崨瀛樷拻濞达絿鎳撻婊呯磼鐠囨彃鈧潡鐛径濞炬闁靛繒濮烽鎺旂磽閸屾瑧鍔嶆い顓炴喘瀹曘垽鏌嗗鍡忔嫼闂傚倸鐗婄粙鎾存櫠閺囥垺鐓欓柛鎰叀閸欏嫭銇勯姀鈩冾棃妞ゃ垺锕㈡慨鈧柨娑樺楠炴劙姊虹拠鑼闁稿绋掗弲鍫曟寠婢规繆娅ｉ埀顒佺⊕鑿уù婊勭矒閺屾洝绠涙繝鍌氣拤缂備讲鍋撻悗锝庡枟閻撴稑霉閿濆洦鍤€濠殿喖绉堕埀顒冾潐濞叉牕鐣烽鍕厺閹肩补鍨鹃悢鐓庣畳鐎广儱娲ゆ禒杈ㄦ叏婵犲偆鐓肩€规洘甯掗埢搴ㄥ箛椤斿搫浠掑┑锛勫亼閸婃牕煤濮椻偓閹囨偐閼碱剚娈鹃悷婊呭鐢帞绮婚鈧弻锕€螣娓氼垱楔闂佹寧绋掗惄顖氼潖濞差亝顥堟繛鎴炶壘椤ｅ搫顪冮妶蹇曠暠鐎规洦鍓熼幃楣冨垂椤愩倗鎳濋梺閫炲苯澧寸€殿喖顭烽崹楣冨箛娴ｅ憡鍊梺纭呭閹活亞寰婇懖鈺佸灊闁瑰墽绮悡娆撴煕韫囨挸鎮戦柛搴㈩殜閺岋綁骞樺畷鍥р叺閻庢鍣崑濠傜暦閸楃偐妲堟繛鍡樺灥鐢鏌ｉ悢鍝ョ煁缂侇喗鎸搁悾宄邦煥閸愮偓鍍靛銈嗘尵婵參寮ㄩ搹顐ょ瘈闁汇垽娼у瓭闂佸摜鍠嶉崡鍐差潖娴犲绀嬫い鏍ㄧ〒閸樺崬鈹戦悙鏉戠仸闁挎洦鍋婂畷婵嬫偄閸忚偐鍘卞┑鈽嗗灡鐎笛囁夋径瀣ㄤ簻妞ゅ繐瀚弳锝呪攽閳ュ磭鍩ｇ€规洖宕灒闁绘垶蓱椤斿倿姊婚崒娆戠獢婵炶壈宕靛濠冪節濮橆剛锛熼梻渚囧墮缁夋挳鎮″┑瀣厵闁绘劦鍓欐晶顖炴煟閹惧崬鍔﹂柡灞剧洴瀵挳鎮欓崗鍝ラ┏婵＄偑鍊栧鐟拔涢崘顭戞綎闁惧繐婀辩壕鍏间繆椤栨碍鎯堟い顐㈣嫰椤啴濡堕崱妯侯槱闂佸憡眉缁瑥顕ｉ弻銉ラ唶闁哄洢鍔嶉弲婊堟⒑閸撴彃浜為柛鐘查閳绘捇濡搁埡鍌楁嫼閻熸粎澧楃敮鎺撶娴煎瓨鐓曟俊顖涱儥濞兼劗绱掗崒姘毙㈡い顓滃姂瀹曞ジ鎮㈤崫鍕闂傚倷绀侀幉锛勬崲閸屾壕鍋撳鐓庡籍閽樼喐绻濇繝鍌氬箻闁荤喖鍋婇悡銉╂煕濞戝崬骞掑瑙勬礈缁辨挻鎷呴搹鐟扮缂備浇顕ч崐鍧椼€佸璺何ㄩ柍杞扮閸ゆ垵顪冮妶鍡楀闁瑰啿娲畷鎴﹀箻鐠囨彃宓嗛梺缁樻煣鐠€锕€煤閹间焦鈷戦梻鍫氭櫅閻︽粓鏌涘Ο鍝勨挃濠㈣娲樼缓鐣岀矙鐠恒劑鐛撻梻浣稿閸嬪懐绮欓幋鐐扮箚闁稿繗鍋愮粻楣冩煙绾板崬骞栭柡瀣枑閵囧嫰濡搁敐鍛Е闂佽鍠楅悷鈺佺暦閿濆棗绶炲┑鐘插暟娴滃嘲鈹戦敍鍕杭闁稿﹥鍨垮畷鐟懊洪浣插亾閿曞倸閱囬柕澶婎儏濞差參鐛惔銊﹀殟闁靛／鍐ㄧ闂傚倷绶氬褔鎮ч崱娆屽亾濮樼厧鏋ょ紒顕嗙秮瀵噣鍩€椤掑嫬绠為柕濠忓缁♀偓闂佸憡鍔忛弬鍌涚閵忋倖鍊甸悷娆忓婢跺嫰鏌涚€ｎ亷宸ラ柣锝囧厴閹垻鍠婃潏銊︽珝闂備胶绮摫鐟滄澘鍟冲鏇犵磽閸屾艾鈧悂宕愰幖浣哥９闁绘垼濮ら崵鍕煕閹捐尙顦﹂柛銊︾箖閵囧嫰寮介妸褏鐓侀悗瑙勬礃閻擄繝寮诲☉妯兼殕闁逞屽墴瀹曟垿鎮欓崫鍕紱闂佸啿鎼幊蹇涙偂閻斿吋鐓欓弶鍫濆⒔缁嬬粯銇勯妷銉█婵﹥妞藉畷锟犳倷閺夋垶鐏庨梻浣告惈閻鎹㈠┑鍡欐殾闁割偅娲栭悡娑樏归敐澶嬫暠闁糕晜鐩缁樻媴閸涘﹤鏆堥梺鎸庡哺閺岀喓鎷犺绾惧潡鏌熼獮鍨仼闁宠棄顦埢搴ㄥ箛椤旀嫎銈夋⒒娴ｅ憡鍟為柟绋挎瀹曟﹢濡歌鐢挻绻濋悽闈浶ラ柡浣规倐瀹曟垿鎮欏顔惧姺濠电偛妫欓崹鐔煎磻閹炬剚娼╅柣鎾冲閻忓牆顪冮妶鍡樼８闁稿氦灏欓幑銏犫攽鐎ｎ亞鍊為悷婊勬瀵偄顭ㄩ崘锝嗘杸闁圭儤濞婂畷鎰板箛閺夎法锛涢梺鐟板⒔缁垶鎮￠悢鍏肩厵闁绘垶锚閻忊晛霉閻樺磭娲撮柡灞炬礋瀹曟儼顦叉い蹇ｅ幗椤ㄣ儵鎮欓弶鎴炶癁濡ょ姷鍋涢鍛村煘閹达箑鐐婇柍琛″亾濠㈣娲栭埞鎴︽偐閸偅姣勯梺绋款儐閻╊垰鐣烽妷褉鍋撻敐搴℃灈缂佲偓婢舵劖鐓涚€广儱楠告禍婵堚偓瑙勬礀瀵墎鎹㈠☉銏犵婵炲棗绻掓禒濂告⒑濞茶骞楁い銊ユ嚇閳ワ妇鎹勯妸锕€纾俊銈忕畱閻忔繈顢旈銏♀拺闁归妞掔花浠嬫煙閾忣偅宕岄柍銉︽瀹曟﹢顢欓崲澹洦鐓曢柍鈺佸枤閻掍粙鏌嶇憴鍕€掔紒杈ㄦ崌瀹曟帒顫濆В娆欑磿缁辨帡鍩€椤掍胶鐟归柍褜鍓熼獮鍐┿偅閸愨晝鍙嗛柣搴秵閸撶喎顬婇鍓х＝濞达絽澹婇崕蹇涙煟韫囨梻绠撻柣锝囨暬瀹曞崬鈽夊▎灞惧缂傚倸鍊烽悞锕傛晪婵犳鍠栭ˇ鐢稿蓟閳╁啯濯撮悷娆忓閳ь剚鍔楅埀顒侇問閸犳盯宕洪弽顐ょ焿闁圭儤鏌￠崑鎾绘晲鎼粹€茬爱闂佸綊顥撶划顖滄崲濞戙垹绠婚柡澶嬪灩閸斾即姊虹粙娆惧剱闁规瓕宕甸幑銏犫槈濞嗘劕顎撻梺鍛婂姇瀵墎绮堝畝鍕厓鐟滄粓宕滈妸褏涓嶉柟鎯х－閺嗭箑鈹戦崒婊庣劸閸烆垶姊洪棃娑崇础鐎广儱娲ら幆鐐烘⒒閸屾艾鈧绮堟笟鈧獮澶愭晬閸曨剙搴婇梺绋挎湰婢规洟宕戦幘鎰佹僵闁告鍋涢獮瀣⒑鐠団€虫灓闁稿鍋熺划瀣箳閺傚搫浜鹃柨婵嗛娴滅偤鏌涘鍡曠凹缂佺粯绻堥幃浠嬫濞戞鍩涢梻浣侯焾椤戝棝骞愰幖浣哥厺鐎广儱顦獮銏＄箾閹寸偟鎳呴柛妯圭矙濮婅櫣鎲撮崟顐㈠Б闂佸鏉垮缂侇喖鐗忛埀顒婄秵閸犳鎮″☉銏″€堕柣鎰仛濞呮洟寮崼銉︹拺缂侇垱娲橀弶褰掓煕閹惧绠炴鐐茬箻瀹曘劑寮堕幋婵堢崺濠电姷鏁告慨瀵糕偓姘间簼缁旂喖寮撮悙鈺傛杸闂佺粯锚閻忔岸寮抽埡鍛厱閻庯綆鍋嗗ú鎾煙椤斻劌瀚弧鈧梺绋胯閸婃牜绱炴惔銊︹拺闁绘挸瀵掑鐔兼煕婵犲啰澧甸柨婵堝仱瀵挳濮€閿涘嫬骞嶉梻浣虹帛閸ㄥ爼鏁冮埡浣叉灁闁哄洢鍨洪悡鏇㈡倵閿濆骸浜滃┑顔肩Ч閺岋紕浠﹂崜褎鍒涢梺鐐藉劵缁犳捇鐛€ｎ亖鏀介柛娑卞灣濞夊灝鈹戦悩鍨毄闁稿鐩幃褔宕卞☉娆忔濡炪倖鐗滈崑鎰板焵椤掍焦顥堢€规洘锕㈤、娆撳床婢诡垰娲ょ粻褰掓倵濞戞瑯鐒界紒鐘筹耿閺岋綁鍩℃笟鈧崣鍕叏婵犲啯銇濈€规洦鍋婃俊鐑藉Ψ閿旈敮鍋撴繝姘拺閻庣櫢闄勫妯讳繆閻ｅ瞼纾肩€光偓閸愵喖鎽电紓浣虹帛缁诲牆鐣烽幆閭︽▌闂佸摜鍋涢悥鐓庮潖濞差亜浼犻柛鏇ㄥ墮缁愭盯姊虹粙娆惧剱闁烩晩鍨堕獮濠囧箻鐠囨彃鐎銈嗘椤寮埀顒勬⒒娴ｅ摜鏋冩俊顐㈠铻炴俊銈呮噺閸婂爼鏌ㄥ┑鍡╂Ч闁绘挸鍟伴埀顒傛嚀鐎氼厽绔熼崱妯绘珷闁哄洢鍨圭涵鈧柡澶婄墐閺備線宕戦幘缁樺仭闁哄顑欐禒鐐節閵忋垺鍤€妞ゆ垵顦锝夊Ω閿旂虎娴勯柣搴秵閸嬪棝宕ｉ崱妞绘斀闁绘劖娼欓悘锕傛煥濮橆厹浜滈煫鍥э攻濞呭懘妫佹径鎰厽婵☆垵娅ｉ敍宥夋煟椤撶喎娴柡灞糕偓宕囨殕閻庯綆鍓涜摫婵°倗濮烽崑娑㈡晝椤忓牆钃熸繛鎴欏灩缁秹鏌嶈閸撶喎鐣疯ぐ鎺戦敜婵°倓绀佹禒鎺戭渻閵堝棙纾甸柛瀣尵閳ь剝顫夊ú姗€鏁冮姀銈冣偓浣糕枎閹炬潙娈愰梺鍐叉惈椤戝洦鎯旀繝鍥ㄢ拻闁稿本鐟ㄩ崗宀€绱掗鍛仸鐎规洘绻傞埢搴ょ疀閿濆洤濮洪柣鐔哥矊缁绘帡宕氶幒鎾剁瘈闁搞儯鍔嶅▍鍥⒑缁嬫寧婀扮紒瀣墦楠炲繐煤椤忓應鎷洪梺鍛婄☉閿曘儳浜搁幍顔藉枑闁哄鐏濋弳鐐电磼閸屾氨效鐎殿喕绮欓、姗€鎮㈤崫鍕疄闂備胶鎳撻崥瀣偩椤忓牆纾规繝闈涱儏娴肩姵绻涘顔荤凹闁绘挻娲熼獮鏍箹椤撶偞鐏堟繛瀛樼矌婢ф骞堥妸锔剧瘈闁告洦鍘肩粭锛勭磽娴ｈ娈橀柛鐘崇墵瀹曟椽鍩€椤掍降浜滈柟鍝勬娴滄儳顪冮妶搴″箹婵炴祴鏅犺棟鐟滅増甯楃€电姵绻濋棃娑欏偍濞存粍绮撻弻鈥愁吋鎼达絽甯ラ梺鎼炲€栧濠氬箟閹间焦鍋嬮柛顐ｇ箘閻熸煡姊虹粙鍖″伐妞ゎ厾鍏樺濠氭晝閳ь剝鐏掓繛鎾村嚬閸ㄨ鲸顨欓梻鍌欒兌缁垳绮欒箛娑樼？闂傚牊渚楀鏍ㄧ箾瀹割喕绨诲ù鑲╁█閺屾盯寮撮妸銉ョ闂佸搫顑呭Λ婵嗩潖濞差亜宸濆┑鐘叉捣閸旀悂姊虹粙娆惧剱闁告梹顨婂畷姘跺箳閹惧墎鐦堝┑顔斤供閸撴艾鐣靛鍜佹富闁靛牆妫欑亸鐢告煕閻樻煡鍙勭€殿喗鎮傞獮瀣晜鐟欙絾瀚肩紓鍌氬€烽悞锕傛晝閳轰讲鏋旈柡鍐ㄧ墛閻撶喐淇婇妶鍌氫壕濠碘槅鍋呯粙鎾诲礆閹烘鏁囬柣鎴濇閸嶇敻姊洪悡搴㈡喐闁轰焦鎮傚畷銏ゆ焼瀹ュ棛鍘甸梺鐓庢贡婢ф銇愯缁辨帗娼忛妸锔绢槹闂佽鍠氶崑銈夌嵁閺嶃劍濯撮柛锔诲幖楠炴劙姊虹拠鑼闁稿绋掗弲鑸电鐎ｎ亞鍙€婵犮垼鍩栭崝鏍偂閸愵喗鍋℃繛鍡楃箰椤忊晠鏌ｈ箛鎿冨殶闁逞屽墲椤煤濠婂牆绐楅柡宥庡幑閳ь兛绶氬鎾閻欌偓濞煎﹪姊虹紒妯曟垼鎽梺鎼炲姀閸╂牜绮氭潏銊х瘈闁搞儯鍔岄埀顒勬敱閵囧嫯绠涢幘鎰佷槐闂佺顑嗛幑渚€藝閹绢喗鐓冪憸婊堝礈閻斿鐒界憸鏃堝箚瀹€鍕＜婵椴稿▓楣冩煟閻斿摜鎳冮悗姘煎墴椤㈡岸鏌嗗鍡欏幐婵犮垼娉涢鍛存倶閳哄懏鐓曟俊顖滅帛鐏忥附鎱ㄦ繝鍛仩婵炴垹鏁诲畷銊╊敊閸忓ジ鏁梻鍌氬€搁崐鍝モ偓姘煎墰閳ь剚纰嶅姗€鎮鹃悜钘夌闁挎洍鍋撶紒鐘崇⊕閵囧嫰骞樼捄鐩掋儲銇勯弮鈧ú鐔奉潖濞差亜绀堥柟缁樺笂缁ㄤ粙姊洪崫銉バ㈤梺甯秮閵嗕礁顫濋澶屽弳闂佸憡娲﹂崜娆忣嚕閸ф鈷戦柟绋挎捣缁犳捇鏌熼搹顐ｅ暗闁逞屽墯閻噣宕￠幎钘夎摕鐎广儱娲﹂崰鍡涙煕閺囥劌浜炲ù鐓庣焸濮婅櫣鍖栭弴鐔哥彅闁诲孩纰嶅姗€鎮惧畡閭︽建闁逞屽墯娣囧﹤顫㈠畝濠冃梻渚€娼ч悧蹇涘礂濡警娼栫紓浣股戞刊鏉戙€掑鐓庣仭闁轰緡鍨伴埞鎴︽倷閸欏娅ゅ┑鐐跺皺閸犳牕顕ｉ銈傚亾閿濆骸鏋熼柍閿嬪灥闇夐柛蹇氬亹閹冲懐绱掓径妯烘灍闁靛洤瀚版俊鐑芥晜閽樺锛撴俊銈囧Х閸嬫盯藝閻㈠摜宓佹慨妞诲亾妞ゃ垺鐟╅幊鏍煛婵犲唭褍鈹戦敍鍕杭闁稿﹥鐗犲畷婵嬫晝閸屾氨锛涢梺鍛婃处閸嬧偓闁哄鏌ㄩ埞鎴﹀磼濮橆厼鏆堥梺鎶芥敱閸ㄥ潡骞冭ぐ鎺戠倞闁靛鍎崇粊宄邦渻閵堝棗绔鹃柛銊ㄥ吹濡叉劙骞掗弬鍝勪壕闁挎繂楠告禍婊勭箾閹碱厼娅嶉柡灞稿墲閹峰懘宕滈幓鎺戝缂傚倷鑳剁划顖滅矙閹捐鐒垫い鎺嶈兌閳洘銇勯鐐村枠闁诡垰鐭傞獮鎺楀箠閵娧勫殌妞ゎ厹鍔戝畷鍗炍熺粭琛″亾閹邦剦娓婚柕鍫濋娴滄粎绱掔紒姗堣€挎鐐寸墵瀹曠兘顢橀悢閿嬬€梻浣告啞濞诧箓宕戦崨瀛樺€垫い鎾卞灪閳锋帒霉閿濆懏鍟為柛鐔哄仦缁绘稓鎷犺閻ｇ數鈧娲橀崹鍨暦閸楃伝褰掑级閹稿骸鏋犳繝纰樺墲閹倹淇婇悜绛嬫晩闁芥ê顦卞Σ锝夋⒒閸屾瑧绐旀繛浣冲洦鍋嬮柛鈩冦亗濞戞ǚ鏋庨柟鐐綑濞堟劙姊虹紒姗嗙劷缂侇噮鍨堕幃锟犲即閵忥紕鍘繝銏ｅ煐缁嬫捇宕氶弶搴撴斀闁炽儳鍋ㄩ崑銏ゆ煛瀹€瀣埌闁宠鍨垮畷鍫曗€﹂幋鐑嗘濠电姵顔栭崰鏍晝閵娿儮鏋嶉柨婵嗘处椤洟鏌熼悜妯烘闁绘柨鍚嬮崵鎺楁煏閸繃鍣规慨瑙勭叀濮婂宕掑▎鎺戝帯濡炪們鍨归敃銉╁箞閵娾晛围闁搞儜鍐喊闂佺澹堥幓顏嗗緤娴犲鍨傛繝闈涚墢绾捐棄霉閿濆鈧洤顭ㄩ崼婵堬紱闂佸憡绋戦悺銊╂偂閺囥垺鐓欓柤鍓插墮婵¤姤绻涢崨顓熷枠闁哄本绋戣灒闁稿繐鍚嬪В鍫ユ⒑閸濆嫮鐒跨紒鏌ョ畺楠炲棝寮崼婵愭綂闂佸啿鐏堥弲娑綖鐏炲彞绻嗛柣鎰典簻閳ь兙鍊濆畷銏ゆ偂楠炵喐妞介、姗€濮€閵忕姷銈﹀┑鐘垫暩婵潙煤閵堝洨鐭嗛柛鎰ㄦ杺娴滄粓鏌￠崘銊モ偓鐟扳枍閺囥垺鐓曟俊顖氭贡閻瑦淇婇銏″殗妞ゃ垺娲熸慨鈧柍鈺佸暞閸婄兘姊绘担鐟邦嚋婵炴彃绻樺畷鏇㈡焼瀹ュ懐鍘洪悗骞垮劚椤︻垶鎮欐繝鍥ㄧ厓闁告繂瀚弳鐔兼煥濞戞瑧鐭掗柡灞稿墲瀵板嫭绻濋崟顐殽闂備礁鎲￠弻銊╂嚐椤栫偞绠掓俊鐐€栧ú宥夊磻閹剧粯鐓涚€光偓閳ь剟宕伴弽顓溾偓浣糕枎閹寸偛鏋傞梺鍛婃处閸撴稖銇愮€ｎ喗鐓熼幖娣€ゅ鎰箾閸欏鑰块柡浣割儔濮婂宕惰濡偓闂佽鍠楅〃濠囧极閹邦厽鍎熼柍銉ㄥ皺閺佹牕鈹戦悙鑼憼缂侇喖閰ｅ畷鎴﹀Χ閸滀焦缍庢繝鐢靛У閼瑰墽澹曢崗鑲╃闁糕剝锚閻掔偓淇婂顔兼珝婵﹥妞藉畷鐑筋敇濞戞瑥鐝遍梻浣侯焾椤戝懘鏁冮鍫㈠祦闁糕剝锚缁剁偤鎮楅敐搴″⒋婵＄虎鍠氱槐鎾存媴閸撴彃鍓靛┑鐐差槹濞茬喖骞冩导鏉懳ㄩ柍鍝勫€甸幏娲⒑閸涘﹦绠撻悗姘煎櫍閸┿垺寰勯幇顓犲幈濠碘槅鍨甸褔宕濋妶澶嬬厽闁挎繂鎳愭禒娑氱磼濡ゅ啫鏋涢柛鈹惧亾濡炪倖甯掔€氼剟鎷戦悢鍝ョ闁瑰瓨鐟ラ悘鈺呮煟閹烘挻銇濋柡灞剧洴楠炲洭鍩℃担鍓茬€虫繝娈垮櫙缁蹭粙鎮ч崱娑樼厴闁硅揪闄勯鎰版煟韫囨挻绂嬪ù婊庝邯閵嗕礁顫濋幇浣光枌闂備胶鍋ㄩ崕鎻捨涢崘銊ф殾婵せ鍋撴い銏＄懇瀹曞弶绔熼姘窛闁告瑦鎹囬弻娑㈠Ψ閿濆懎顬夌紓浣插亾闁割偁鍎茬€氬懘鏌涘Δ鍐ㄤ汗闁衡偓娴犲鐓熸俊顖濇娴犳盯鏌￠崱蹇旀珚闁哄本绋撻埀顒婄秵閸嬪棗煤閹绢喗鐓涘ù锝囶焾閺嗭絾銇勯姀鈩冾棃鐎规洜鍠栧Λ鍐ㄢ槈濞嗘ê鎮┑鐘垫暩婵兘寮崨濠冨弿闁汇垽娼ч弸鍫⑩偓骞垮劚椤︻垶鎮挎ィ鍐╃厵缂備焦锚娣囶垶鏌ｉ弬鎸庮棦闁诡喛顫夊顏堝箯瀹€濠傚Τ闂備胶顭堢€涒晠宕濋弽顐ｅ床婵炴垯鍨瑰婵囥亜閺傚灝鈷旈柣鎾茶兌缁辨挻鎷呴搹鐟扮缂備浇顕ч悧濠囧箞閵娾晛鐒垫い鎺戝閻撱儲绻濋棃娑欘棡妞ゃ儳濞€閺屾洟宕卞Ο鍝勵潓濡炪値浜滈崯瀛樹繆閸洖骞㈡俊銈呭暕濞ｎ喗绻濈喊妯活潑闁稿鎳愰幑銏ゅ醇濠垫劕娈ㄦ繝鐢靛У閼圭偓鍎梻浣稿暱閹碱偊鏁冮妷鈺佺柧闁挎繂顦伴埛鎴犵磽娴ｅ顏堟倶閵夛缚绻嗘い鎰剁悼閹冲懘鏌嶈閸撶喎顭囪閹矂宕掑鍏肩稁濠电偛妯婃禍婵嬎夐崼鐔虹闁瑰鍋犳竟妯汇亜閿濆牊顏犵紒杈ㄦ尰閹峰懘妫冨☉姗嗘綂闂備胶顭堥鍡涘礉濞嗘挸绠犻柣鎰摠缂嶅洭鏌曟繛褍鎳愬Σ鍥ㄧ節濞堝灝鏋熼柨鏇楁櫊瀹曘垺銈ｉ崘銊ュ亶闂備緡鍓欑粔鐢稿煕閹寸姷纾藉ù锝堝亗閹次诲宕奸悢缈犵盎闂侀潧顧€闂勫嫬鐣风仦缁㈡闁绘劖鎯屽▓婊堟煙椤旇娅婇柟顖氱墦椤㈡鍩€椤掑嫭鐓€闁挎繂鎳愰弳锕傛煙椤栫偛浜版俊缁邯濮婄粯绻濇惔鈥斥拻闂佸憡鎸鹃崰鏍ь嚕婵犳碍鍋勯柛蹇撳悑閸庮亜顪冮妶鍡楀闁稿﹥顨婇幃鈩冨緞閹邦厼鈧敻鎮峰▎蹇擃仾缂佲偓閳ь剛绱撻崒姘毙㈤柨鏇ㄤ邯婵″瓨绗熼埀顒€顕ｉ幘顔碱潊闁绘瑢鍋撴繛鐓庯躬濮婅櫣绱掑Ο蹇ｄ簻椤曪綁顢楅崟顐嬔冾熆鐠轰警鍎岄柟宄邦煼濮婅櫣绮欓幐搴㈡嫳闂佽崵鍠嗛崝鎴濈暦濡も偓閻ｆ繈宕熼鍌氬箞闂佽鍑界徊濠氬礉鐏炵偓鍙忛柛灞惧閸嬫挾鎲撮崟顒傤槰濠电偛寮剁划鎾诲春閳ь剚銇勯幒鎴濃偓褰掑汲椤掑嫭鐓涢柛娑卞枤閸欌偓闂佽鍠氶崑鐐垫崲濠靛绀冩い蹇撴－閸熷酣姊绘担铏广€婇柛鎾寸箚閹筋偊鏌ｈ箛鎾寸闁告ê澧藉Σ鎰板箳濡も偓閻掑灚銇勯幒鎴濐仼缂佲偓鐎ｎ偁浜滈柟鎹愭硾閺嬪酣鏌＄€ｎ偄鐏存慨濠呮缁辨帒顫滈崱妯兼殽闂備胶绮〃鍛涘☉姘灊妞ゆ挾鍎愬鈺呮煠閸濄儲鏆╅柛妯绘崌濮婄粯鎷呴崫銉よ檸濡炪倖鍨甸幊鎰垝椤撱垹绠虫俊銈勭贰濮婃寧绻濋姀锝呯厫闁告梹鐗犻幃锟犲Ψ閿斿墽鐦堥梻鍌氱墛缁嬫帡藟閻愮儤鐓曢柨婵嗘閵囨繈鏌＄仦鍓ф创闁诡喓鍨藉畷顐﹀礋椤忓拋娼熼梻鍌欑閹碱偊寮甸鍕剮妞ゆ牜鍋涚粻鏍煏韫囧鈧洘瀵奸悩缁樼厱闁哄洢鍔嬬花濂告煕閺囩偛鏆ｆ慨濠勭帛缁楃喖鍩€椤掑嫬鐒垫い鎺嶈兌閵嗘帡鏌嶇憴鍕诞闁哄本鐩顒傛崉閵婃劑鍊濋弻鐔肩嵁閸喚浠奸梺瀹狀潐閸ㄥ綊鍩€椤掑﹦绉靛ù婊呭仱閺佸秴顭ㄩ崘锝嗘杸闂佺粯顭堥婊冾啅閵夆晜鐓欓柛娆忣槺濞叉潙鈹戦埄鍐╁€愰柛鈺嬬節瀹曟帒螖娴ｅ壊浼栭梻鍌欐祰瀹曞灚鎱ㄩ弶鎳ㄦ椽鎮╃拠鑼姦濡炪倖甯掗崯顖炴偟椤忓牊鐓熼煫鍥ㄦ⒐瀹曞本鎱ㄦ繝鍛仩缂侇喗鐟ч幑鍕Ω瑜滈崬褰掓⒑閽樺鏆欑€规洦鍓濊ぐ渚€姊洪幖鐐插妧鐎广儱鐗嗛幆鍫ユ⒒娴ｇ瓔鍤冮柛鐘愁殘閸掓帡骞樼拠鑼唶婵犵數濮撮崯顐ゆ閻愮儤鍊堕柣鎰煐椤ュ绱掗悩鍗炲祮婵﹤顭峰畷鎺戔枎閹烘垵甯俊鐐€ら崢濂告偋閹炬剚鍤曢柣銏犲閺佸棝鏌涢弴銊ュ闁告挾鎳撻—鍐Χ閸℃锛曢梺绋款儐閹稿銆?
		makesureSessionInitialized(request);

		// 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢幓鎺旀殕缂佸顑欏鏍煣韫囨凹娼愰悗姘哺閺屾稑鈻庤箛锝喰﹂梺琛″亾妞ゆ挾鍎愬〒濠氭煏閸繈顎楀ù婊勭矋閵囧嫰鏁傜拠鑼化闂佸憡鐟ラ幊妯何涢崨鎼晝闁靛繆鈧剚妲辩紓鍌欑椤戝棝宕圭憴鍕彾闁哄洢鍨归柨銈嗕繆閵堝嫯鍏岄柛妯兼暬濮婅櫣鎷犻垾宕囦化婵犫拃鍕垫疁閽樼喎顭块懜闈涘闁绘挾鍠栭弻鐔兼倻濡櫣浠兼繛瀵稿У閻╊垶寮婚弴銏犵倞鐟滃秹顢旈鐕佹闁绘劖娼欏ù顔筋殽閻愬樊鍎旀い銏＄☉椤繈顢楁径瀣ギ缂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾閽樻繈姊洪鈧粔鎾几娴ｇ硶鏀介柣妯虹枃婢规﹢鏌﹂崘顏勬瀾缂佺粯鐩獮瀣枎韫囨洑鐥梻浣侯焾椤戝棝宕濆Δ鍛劦妞ゆ帒鍠氬鎰箾閸欏澧悡銈夋煏閸繍妲搁柣鎾存礋閺屾盯顢曢妶鍛亖缂備讲妾ч崑鎾绘⒒娓氣偓濞佳勵殽韫囨洜绀婇柛鈩冾殢閻掍粙鏌熼柇锕€骞樼紒鐘荤畺閺屻倗鍠婇崡鐐差潻闂佸搫顑呴柊锝夊蓟閿濆鏅插鑸瞪戦ˉ鏍磽娓氬洤鏋熼柣鐔濆洤绠柣妯肩帛閸婇攱銇勯幒宥堫唹闁瑰嘲缍婂娲偡閺夋寧些闂佺娅曢敋闁伙絿鍏橀、鏇㈡晝閳ь剛绮堢€ｎ偁浜滈柟鎵虫櫅閳ь兙鍊濆畷銉р偓锝庡枟閳锋帡鏌涚仦鎹愬闁逞屽墯閹倸鐣烽幇顒傛殝闁瑰啿锕ょ紞濠傜暦閸洦鏁嗗璺侯儐濞呮棃姊绘担鍛婃儓缂佸绶氬畷銏＄附缁嬪灝鐎梺绋跨灱閸嬬偤鎮￠敐澶屽彄闁搞儵顥撻崚鏉棵瑰鍕畺缂佺粯鐩畷銊╊敍濮ｅ尅绲借彁闁搞儜宥堝惈婵犵鈧磭鍩ｇ€规洏鍔戦、娆撴偂鎼粹槅娼氬┑鐘垫暩婵即宕归悡搴樻灃婵炴垯鍩勯弫鍕煕濞戝崬鐏℃い鎰矙閺岋綁骞囬鐓庡闂佺粯鎸婚悷鈺侇潖濞差亶鏁囬柕濞у懏娈稿┑鐘媰閸涱収鏆梺闈涙搐鐎氭澘顕ｉ鈧崺鈧い鎺戝缁€澶嬬箾閸℃绂嬫繛鍏肩墬缁绘稑顔忛鑽ょ泿婵炵鍋愭繛鈧柡灞剧洴瀵挳鎮欓崗鍝ラ┏闂佽桨绀侀悧鎾愁潖缂佹ɑ濯撮柧蹇撶畭閳ь剙锕弻锟犲川椤旂偓鍒涢梺杞扮閸熸挳銆侀弮鍫濋唶闁绘棁娓圭花鍨繆閻愵亜鈧牜鏁幒鏂哄亾濮樺崬鍘寸€殿噮鍋婂畷鍗炩槈濞嗘垵骞堥梻浣告惈濞层垽宕濈仦鐐珷濞寸厧鐡ㄩ悡鏇熶繆椤栨粎甯涢柛濠冨姍閺屸€崇暆鐎ｎ剛袣缂備胶濮甸惄顖氼嚕閹绢喗鍊烽柡澶嬪閹层垹鈹戦敍鍕杭闁稿﹥鐗曢埢鏃堟晝閸屾氨鍊為悷婊冪箻瀹曟繈鏁冮埀顒勨€旈崘顔嘉ч柛鈩冾殘閻熸劙姊洪悡搴℃毐濠⒀冩捣閸掓帗绻濆顓炩偓鐑芥煙绾板崬骞楁い鏃€妫冨铏圭磼濡搫顫戝銈嗘礃缁矂顢欒箛娑樜ㄩ柍鍝勫€婚崢鎾绘煟閻斿摜鎳冮悗姘煎墴閹﹢鍩￠崨顔惧帗閻熸粍绮撳畷婊堝Ω瑜忕粈濠囨煕閳╁喚鐒芥い鈺傜叀閺岋綁骞囬鍥跺妳闂佽桨绀佸ú顓㈠蓟閿濆绠婚柛鎰皺濡叉澘鈹戦悙瀛樺碍妞ゎ厾鍏樺濠氬即閵忕娀鍞跺┑鐘绘涧濞村倸螞閳ユ剚娓婚柕鍫濆暙閸旀碍淇婇锝囩疄鐎殿喛顕ч埥澶婎潩閿濆懍澹曢梺鎸庣箓妤犲憡绂嶅鍐ｆ斀妞ゆ棁鍋愭晶锕傛煛瀹€瀣瘈鐎规洏鍔戦、姗€鎮╅懠顒€骞楀┑锛勫亼閸婃垿宕归搹鍦煓闁硅揪璁ｇ紞鏍ㄧ節闂堟侗鍎愰柡鍛叀閺屾盯鍩勯崗鈺傚灥椤潡骞嬮敂瑙ｆ嫽闂佺鏈悷銊╁礂瀹€鈧槐鎺楊敋閸涱厾浠搁悗瑙勬礃缁诲倽鐏掗梻鍌氬€搁顓㈠礈閵娿儮鏀介柣鎰级椤ョ偤鏌熺粙鎸庢喐缂侇喛顕х叅妞ゅ繐鎳愰崢钘夆攽閳藉棗鐏犻柛姘儔瀵娊鍩￠崘顏咃紡闂佽鍨庨崘鈺佲偓顖炴⒑闂堟稒鎼愰悗姘緲椤曪綁顢氶埀顒€鐣烽幆閭︽Ш濠殿喛顫夐〃濠傤潖閾忚瀚氶柤纰卞墰椤旀垶绻涚€涙鐭ら柛鎾寸☉鍗遍柟鐗堟緲缁犳娊鏌熼幆褜鍤熺紒澶婄埣濮婅櫣鈧湱濮甸妴鍐╀繆閻愭潙绗掗柍璁崇矙椤㈡棃宕卞▎鎴闯濠电偠鎻紞鈧繛鍜冪悼閺侇喖鈽夊杈╋紲闁荤姴娲╃亸娆愭櫠閺囥垺鐓熼柨婵嗘搐閸樺瓨顨ラ悙鍙夘棦闁哄苯娲弫鍌滄喆閿濆棛鈧亶姊婚崒姘偓鐑芥嚄閸洏鈧焦绻濋崘鈺佸伎闂侀潧鐗嗛幃鍨洪鍕敤濡炪倖鎸鹃崑鐔煎储閹绢喗鈷戠痪顓炴噺瑜把囨⒒閸曨偄顏€规洘鍔欓幃婊堟嚍閵壯冨箰濠电姰鍨煎▔娑㈩敄閸涘瓨鍊堕柍杞版€ヨぐ鎺撳亹婵炲棗绻戞径鍕煃闁垮鐏撮柡宀€鍠栭幊鏍煛娴ｉ鎹曞┑鐘殿暯閳ь剝娅曢崑銉╂煛瀹€鈧崰鏍箹瑜版帩鏁冮柨婵嗘噽閿涙捇鏌ｆ惔銏╁晱闁革綆鍣ｅ畷鎴炵節閸ャ儮鍋撴担绯曟瀻闁规儳纾娲⒑閸︻厼鍔嬫慨濠傤煼閹偓銈ｉ崘鈹炬嫽婵炶揪缍€濞咃絿鏁☉銏＄厱闁哄啯鎹囧顔剧磼閸屾氨孝妞ゎ厹鍔戝畷鐓庘攽閸繂袝濠碉紕鍋戦崐鏍暜婵犲洦鍤勯柤绋跨仛濞呯姵銇勮箛鎾跺闁硅櫕宀搁弻锝夊箛椤栵絾鈷栭悷婊呭鐢寮查弻銉︾厱婵炴垵宕弸鐔兼煕閻樺啿濮嶆慨濠冩そ瀹曨偊宕熼濠勫窛缂傚倷鑳舵慨鐢稿箲閸パ屽殨濠电姵纰嶉弲鎻掝熆鐠轰警鐓繛鐓庯躬濮婃椽妫冨☉姘辩暰闂佸搫鎳忕换鍫濈暦濠靛牏鐭欓幖瀛樻尰閺傗偓婵＄偑鍊栧濠氬磻閹捐鍚归柍褜鍓熼幃妤€鈻撻崹顔界仌闂佺硶鏅滈悧婊呭垝濮橆厽缍囬柕濞у懐妲囬梻浣规偠閸庢椽鎮樺☉婊庢▌闂佸搫鏈惄顖氼嚕閹绢喖惟闁靛濡囨禍顏堟⒒娴ｅ憡鍟為柨鏇ㄥ亞濡叉劙寮撮悩鎰佹綗闂佽鍎抽悺銊﹀垔閹绢喗鐓曟繝闈涙椤忋倝鏌＄€ｎ亝鍣界紒杈ㄦ尰閹峰懘宕崟鎴秮閺岋絽螖閸愩劉鏋呴梺缁樹緱閸ｏ絽鐣峰鈧俊鍛婃償閵忊槅妫冮梺绯曟杹閸嬫挸顪冮妶鍡楃瑨閻庢凹鍓熼幃锟犲灳閹颁胶鍞甸柣鐘叉惈瑜板潡宕奸妷銉ュ殤闁瑰吋鎯屽鈧紒璇叉閵囧嫰寮介妸褏鐓€闁汇埄鍨靛▔娑㈠煘閹达富鏁婇柤鎭掑劘娴犮垹鈹戦纭峰姛缂侇噮鍨堕獮蹇涘川椤栨稑鏋傞梺鍛婃磵閺呮瑩骞嬮悜鑺モ拻濞达絽鎲￠崯鐐烘煥濮樿埖鐓忛柛顐ゅ枑閸婃劗鈧鍣崑濠傜暦濮椻偓椤㈡瑩鎳為妷锔惧絾闂傚倷鑳剁划顖炲礉閿曞倸绀堥柨鏇楀亾妞ゎ厼娲幃銏ゅ礂鐏忔牗瀚奸梻渚€娼荤€靛矂宕㈤崗鑲╊洸濡わ絽鍟悡鏇㈡煙閻愵剚缍戦柛婵婃閳ь剝顫夊ú蹇涘磿閹惰棄鐒垫い鎺戯功缁夌敻鏌涢悩鎰佹疁闁诡噯绻濆鎾偄閾忓湱妲囧┑鐘垫暩婵挳宕愰幖浣告辈婵炲棙鎸婚悡娑㈡煕閳╁啫濮囬柣蹇旂叀閺岋紕浠﹂崜褉妲堝銈庡亝缁诲牓骞冮埄鍐╁劅婵犻潧妫欓悗鎶芥⒒閸屾瑦绁版い鏇熺墵瀹曟澘螖閸涱偀鍋撻崘顔煎窛闁哄鍨归崢娲⒑鐟欏嫬绀冩い鏇嗗洤鍨傞柛灞剧◤娴滄粓鏌￠崘銊モ偓鐟扳枍閺囩伝鐟邦煥鎼存繄鍑规繛锝呮搐閿曨亪骞冨▎鎿冩晜闁告洏鍔屾禍楣冩煏閸繍妲搁柛灞诲妼铻栭柨婵嗘噹閺嗘瑧绱掗埀顒勫磼濞戞瑥寮垮┑锛勫仩椤曆勭妤ｅ啯鈷戠紓浣姑肩欢閬嶆煕閿濆繒鍒伴柣锝呭槻椤粓鍩€椤掑嫨鈧礁鈻庨幋婵囩€抽柡澶婄墑閸斿海绮旈柆宥嗏拻闁稿本鐟х粣鏃€绻涙担鍐叉处閸嬪鏌涢埄鍐︿簵婵炴垶顭傞弮鈧幏鍛瑹椤栨盯鏁滃┑鐘茬棄閺夊簱鍋撻弴銏犵疇闊洦绋掗崑鍌涚箾閸℃ɑ灏伴柣鎾跺█閺屾盯鏁傜拠鎻掔濡炪倧缍€閸嬫劙骞夐幖浣哥濠电姴鍊归崳鐣岀棯閹佸仮闁哄瞼鍠撶槐鎺懳熼搹鍦嚃闂備胶顭堥鍐磿閹惰棄鐒垫い鎺嶇贰閸熷繘鏌涢悩宕団槈闁宠绉瑰鎾偐椤愵澀澹曟繛杈剧到閸燁偅绂嶈ぐ鎺撶厓鐟滄粓宕滃杈╃煓闁圭儤銇涢埀顒婄畵瀹曞爼顢楅埀顒勫磼閵娿儍褰掓偐瀹割喖鍓遍梺鎶芥敱閸ㄥ湱妲愰幘瀛樺闁兼祴鍓濋崹鍧楀箖閿熺姴唯闁冲搫鍊婚崢鐢告⒑閸忛棿鑸柛搴㈠▕閹矂骞掑Δ浣哄幍濡炪倖鐗楃喊宥咃耿閹殿喚纾肩紓浣诡焽缁犵偟鈧娲滈崰鏍€佸鈧幃顏堝川椤栨氨鍝庢繝鐢靛Х閺佸憡鎱ㄦ导鏉戝瀭闁绘挸绨堕弸鏍ㄧ箾閹存瑥鐏╅悗姘槹閵囧嫰骞掗幋婵愪患闂佸憡鑹剧紞濠囧蓟瀹ュ牜妾ㄩ梺鍛婃尰閻熲晠鍨鹃弮鍫濈妞ゆ柨妲堣閺屾盯鍩勯崘鍓у姺闂佸疇顫夌粙鎾舵閹捐纾兼慨妯荤樂閿涘嫮纾奸柤鎼佹涧閸濇椽鏌ｅ☉鍗炴珝鐎殿喕绮欓、鏍矗閵夛妇娼栭梻鍌欑濠€閬嶅磿閵堝洦宕查柛鎰靛枛閻ょ偓绻濋棃娑卞剱闁抽攱鍨块弻鐔虹矙閹稿孩宕崇紓浣哄У閹歌崵鎹㈠☉姘勃闁稿本鑹鹃～鍛存⒑缁嬪尅鏀婚柟绋垮暱椤繑绻濆顒傦紲濠殿喗锕╅崗姗€宕戦幘鎼Ч閹兼番鍩勯崑銊モ攽椤旀枻渚涢柛鎿勭畵閹偤鎮剧仦鎯ф瀾闂佸搫鍟悧鍡涙倶閹惰姤鐓ラ柡鍥╁仜娴滅偤鏌℃担鍛婂枠闁哄矉绲借灒闁割煈鍠氬В銏㈢磽娴ｅ湱鈽夌紒缁樏～蹇撁洪鍕槯闂佸吋绁撮弬鍌炲磻閻愮儤鈷戦柛婵嗗閸ｄ粙鏌涚€ｎ剙浠辨鐐茬墦婵℃悂鍩℃担渚О婵犵數鍋炲娆擃敄閸℃稑妫橀柍褜鍓熷缁樻媴閾忕懓绗￠梺鎸庢皑閻ヮ亞绱掗姀鐘茬睄闂侀潧妫楅崐鍨嚕婵犳艾唯闁挎梹鍎崇敮妤呮⒒閸屾瑧顦﹂柣蹇旂箞椤㈡牠宕ㄧ€涙ê鈧爼鎮楀☉娅虫垿宕ｈ箛鎾斀闁绘ɑ褰冩禍鐐烘煟閹剧懓浜归柍褜鍓濋～澶娒洪弽顓炍ч柟闂寸缁犳牠鏌曡箛瀣偓鏇犵不閹烘鐓欓柛婵嗗椤ユ粌霉濠婂牏鐣洪柡宀€鍠栭幃婊兾熼搹鐟板Ъ闂備胶绮弻銊ノ涘☉銏犵疅闁告稑锕ラ崕鐔兼煃椤撯€崇厫婵＄偘绮欓獮鍐閵堝棗浜楅柟鍏肩暘閸婃牠骞夋导瀛樷拻濞达綀娅ｇ敮娑欐叏婵犲偆鐓肩€规洏鍨奸ˇ瀵哥磼椤旇偐澧︾€规洘锕㈤、娆撴偩鐏炶棄绠荤紓鍌欒兌閸嬫挸顭垮Ο铏规殾妞ゆ帊鐒﹀▍鐘绘煟濡も偓閻楀嫭绂嶅鍫熺厸闁告劧绲芥禍楣冩煟閵忊晛鐏茬紒缁樼箞閻涱噣宕橀妸搴㈡瀹曟﹢鍩℃担鍦偓顓㈡⒒娴ｅ憡鍟炴繛璇х畵瀹曟粌鈽夐埗鍝勬喘椤㈡﹢濮€閳锯偓閹锋椽鏌℃径灞戒户濠⒀勵殘缁﹪鎮ч崼娑楃盎闂佸搫娲ゅΛ妤€煤閿曞倸纾婚柍鍝勬噺閻撶喖鏌熸潏鍓хɑ妞ゃ儱顦靛娲础閻愭潙鏋犻梺鍝勭焿缁绘繂鐣烽崼鏇炍ㄩ柕澶堝労閻庢挳姊绘担鍛婃儓闁瑰啿鐭傚畷褰掓惞鐟欏嫬搴婂┑鐐村灦閻燂妇绱為崶顒佺厱闁圭偓顨呴崯顐︽偂閸岀偞鈷掑ù锝呮啞鐠愶繝鏌嶅畡鎵ⅵ鐎规洘鍨剁换婵嬪炊閵娿儰绮ф俊鐐€栧濠氬煕閸儱瑙﹂悗锝庡枟閻撴洟鏌嶉埡浣告殲缂佲偓閸垻纾奸柣妯哄暱閸濈儤鎱ㄦ繝鍛仩缂佽鲸甯掕灒闁告繂瀚呴弴鐔虹閻庢稒顭囬惌瀣磼椤旇姤宕岀€规洘妞介崺鈧い鎺嶉檷娴滄粓鏌熼崫鍕ラ柛蹇撹嫰椤儻顧佸ù婊庝邯楠炴宕奸弴鐔封偓鐑芥倶閻愭彃鈧敻宕戦幘璇茬闁规惌鍘介崓鐢告⒑閹稿海绠撻柟鍐茬У缁旂喖寮撮姀锛勫幗闁瑰吋鐣崐銈咁焽閹邦厾绠鹃柛娆忣檧閼拌法鈧娲栫紞濠傜暦缁嬭鏃堝礃閵娧佸亰闂傚倷鐒﹂幃鍫曞磿濞差亜绀堥柨鏇炲€搁弸浣糕攽閻樺弶澶勯柣鎾存礋閺岀喖鎮欓澶嬬暥闂佺懓鍟垮ù鐑藉极椤曗偓椤㈡宕熼鑺ュ闂備胶顭堥張顒勬偡瑜斿畷婵嗩吋婢跺鍘遍柟鑹版彧缁辨洟鏁嶅澶嬬厵妤犵偛鐏濋悘鍙夘殽閻愬弶鍠樼€殿喖鐖煎畷褰掝敊闂傚瓨鍠氭繝鐢靛Х閺佸憡鎱ㄩ幘顔芥櫇闁靛牆顦伴崑瀣煕閳╁啰鎳冩い銉﹁壘閳规垿鎮╁畷鍥舵殹闂佺粯鎸婚悷鈺呭蓟閻旂儤鍠嗛柛鏇ㄥ亜椤矂姊虹紒妯诲鞍闁告梹鐟╁璇测槈閵忊€充汗闂佸憡鐟ラˇ钘壩涢敃鍌涒拺闁告繂瀚﹢浼存煟閳哄﹤鐏犳い鏇秮楠炴﹢顢欓崲澶嗘櫊閺屾洘寰勯崼婵嗗Ф濠电偠鍋愰崑鎾寸┍婵犲洦鍊锋い蹇撳閸嬫捁顦撮柟宄扮秺閹垽鎮℃惔锝忕吹闂備浇鍋愰埛鍫ュ礈濮橆剛鏆﹂柛娆忣槺缁♀偓闂傚倸鐗婃笟妤呭磿韫囨稒鐓熼柟鎯х摠缁€瀣煛鐏炵晫啸妞ぱ傜窔閺屾盯骞樼捄鐑樼€婚梺浼欑到閸㈡煡锝炲鍫濈劦妞ゆ帒瀚ㄩ埀顑跨閳诲酣骞橀弶鎴滅暗闂佺鍋愮悰銉╁焵椤掑啯鐝悘蹇擃儏閳规垿鎮╅懠顒侇棄闂佸搫顦花閬嶅磻閹炬緞鏃€鎷呴悷鏉夸紟闂備胶绮崹鐓幬涢崟顓烆棜濠电姵纰嶉悡鐔兼煙闁箑澧柟顖氱墦閺屾盯濡搁妷銉㈠亾閸ф钃熸繛鎴旀噰閳ь剨绠撻獮瀣攽閸モ晙澹曢梻鍌欑閹碱偄螞鐎靛摜涓嶉柟鎹愵嚙閽冪喖鏌ㄥ┑鍡╂Ц缂侇偄绉归弻娑氫沪缂併垺鐣烽柣搴㈢閻╊垰顫忛悜妯诲闁规鍣Σ顔剧磽娴ｅ壊妲洪柡浣割煼閻涱噣宕奸妷銉庘晠鏌曟径妯烘灍鐎点倖妞藉铏瑰寲閺囩偛鈷夐梺闈涙川閸嬬喖鎳炴潏銊ь浄閻庯綆鍋€閹疯櫣绱撻崒娆戝妽闁挎艾鈹戦鍏煎枠闁哄被鍔戝鎾Ω閵堝洨娉挎俊鐐€ら崑鍕崲閹烘梹顫曢柟鐑樺殾閻旂儤瀚氱€瑰壊鍠掗崑鎾诲箻缂佹ǚ鎷洪梺鍛婄☉閿曪箓鍩ユ径鎰叆闁哄洦锚閳ь剚绻傞锝夊箮閽樺鍞跺┑鐘绘涧閸燁垶寮埀顒勬⒒娴ｈ櫣甯涢柛鏃€鐟╄矾闁稿瞼鍋涢崥褰掓倵閿濆骸澧扮痪鎯с偢閺岋絽螣閹稿海褰ч柣蹇撴禋閸欏啴寮婚敐澶嬫櫜闁搞儜鍐ㄧ濠电姵顔栭崰鎺楀磻閹剧粯鈷戦柟绋挎捣閳藉鎮楀顓熺凡妞ゎ剙锕、娆撳礈瑜忛敍婵囩箾鏉堝墽鍒板鐟帮躬瀹曟洟骞樼€靛摜顔曟繝銏ｆ硾椤戝棛绮堢€ｎ偁浜滈柕蹇ョ磿閹冲洦顨ラ悙宸剶闁轰礁鍊块幃鍓т沪閽樺顔戦梻鍌欒兌閹虫捇宕ョ€ｎ喖绠氱€光偓閸曨偄鐎悗瑙勬礀濞层劑鎯岄幘缁樼厽闁靛繒濮甸崯鐐烘煟閹惧瓨绀嬮柡宀嬬到铻ｉ悹鍥ㄥ絻椤亜鈹戦悙鑼缂侇喗鎹囧璇测槈閵忕姷鍘搁梺绋挎湰缁秹宕氬☉妯滄棃鎮╅棃娑楁勃闁汇埄鍨辩敮鐔兼倶閸愨晝绠鹃弶鍫濆⒔閸掍即鏌熺喊鍗炰喊闁绘侗鍣ｅ畷濂稿Ψ閿旇瀚奸梺鑽ゅТ濞测晝浜稿▎鎴犱笉闁规儼濮ら悡鏇㈡倵閿濆骸浜為柍顖涙礋閺岋繝宕ㄩ弶璺ㄥ帿缂備礁鍊圭敮鐐哄焵椤掑﹦绉靛ù婊呭仱閹潡宕堕浣叉嫼闂佸憡绋戦敃锝囨闁秵鐓曢柣妯挎珪鐏忔壆绱掗崒姘毙㈡い顓滃姂瀹曞ジ鎮㈤崫鍕辈闂傚倷绀侀幖顐﹀疮椤愨挌娲Χ閸℃瑯娲搁悷婊呭鐢鎮￠悢鍏肩叆婵犻潧妫欐径鍕煛閸♀晛澧撮柡宀嬬磿娴狅妇鍖栭弴鐐板閻庤娲栧ú锕€鈻撻弴銏＄厽閹兼惌鍨崇粔鐢告煕閹惧顬奸柍顏勭仛缁绘繈鎮介棃娴躲儲銇勯敐鍕煓闁糕斁鍋撳銈嗗坊閸嬫挾鐥紒銏犲籍鐎规洘妞介弫鎰板炊閿濆懍澹曢柣鐔哥懃鐎氼厾澹曢幖浣圭厱闁哄啠鍋撻柛銊ユ健婵″瓨鎷呯化鏇熺€婚梺瑙勫劤绾绢參藝瑜斿娲礂閼测斂鍋為梺鍝勬噺閻熲晠鐛崘顭戝悑闁糕剝鐟ч惁鍫㈢磼閻愵剚绶叉い锕佷含婢规洟宕稿Δ浣哄幈闁诲函缍嗛崑鍕倶鐎电硶鍋撶憴鍕闁告梹鐟╅悰顔锯偓锝庡枟閺呮繈鏌嶈閸撶喎鐣疯ぐ鎺戠＜闁绘劕顕崢浠嬫⒑閻熼偊鍤熼柛搴㈠姍閹偞绂掔€ｎ偆鍘撻梺鑽ゅ枑婢瑰棙鐗庣紓鍌欒兌缁垳绮欓幋锔绘晩闊洦渚楅弫濠囨煟閻旂⒈鏆掑瑙勬礀椤啴濡堕崨顖滎唶闁诲孩鍑归崢鍓у垝濮樿泛閿ゆ俊銈勮兌閸橆亪姊洪幖鐐插姌闁告柨鏈幈銊ヮ吋閸♀晜顔旈梺缁樺姈濞兼瑥霉椤旂瓔娈介柣鎰儗閻掍粙鏌熼悷鏉款伃鐎规洦鍋婂畷鐔碱敇瑜嶉幃鎴炵節閻㈤潧啸妞わ綆鍠氬Σ鎰板即閵忕姷锛涢梺纭呮彧缁犳垹澹曡ぐ鎺撶厸鐎广儱楠搁獮鏍煢閸愵亜鏋涢柡灞诲妼閳规垿宕遍埡鍌傦妇绱撴担鎻掍壕婵犮垼鍩栭崝鏍磹閸洘鐓熼柟閭﹀枟閺嗏晠鏌涘▎鎰鞍缂佺粯绋戦蹇涱敊閼姐倗娉块梺鎼炲劜閹稿啿顫忓ú顏勭闁绘劖褰冩慨澶愭⒑閸濆嫭鍣洪柣鐔濆懎鍨濇い鎾跺枎缁剁偤鏌熼柇锕€骞愰柟閿嬫そ濮婅櫣绱掑Ο璇茬闁诲骸鐏氱敮鎺椻€﹂崶顒佸亜闁稿繗鍋愰崢杈ㄧ節閻㈤潧孝闁哥噥鍋呴幈銊ヮ吋閸ワ絽浜鹃悷娆忓缁€鍐煕閵娿儲鍋ラ柣娑卞枛椤粓鍩€椤掑嫬绠犳繝濠傜墛椤ュ牊绻涢幋鐐垫噮闁逞屽墻閸欏啫顫忕紒妯诲闁告稑锕ら弳鍫濃攽閻愰鍤嬬紒鐘虫尭閻ｅ嘲顭ㄩ崘锝嗩潔闂佸疇娉涢幖顐⒚洪鐔剁箚闁绘垼妫勫敮闂侀潧绻嗛崜婵嬪级閹间焦鈷掑ù锝堟鐢盯鏌涢弮鈧悷鈺勭熅闂佺鐬奸崑娑㈠触瑜版帗鍊甸柨婵嗛婢ф壆鐥幆褋鍋㈤柡宀嬬到铻ｉ柛婵嗗濮ｆ劙姊虹拠鑼闁瑰憡濞婂璇测槈閵忕姷鍘撮梺鍓茬厛閸犳碍瀵奸崼婢棃鎮╅棃娑楁勃闂佹寧宀搁幊锝夊箛椤撴粈绨婚梺鐟版惈濡绂嶉悙顒傜閻庢稒顭囬惌瀣磼鐠囪尙澧曟い顐㈢箰鐓ゆい蹇撳缁愭稒绻濋悽闈浶㈤柛濠冩倐閿濈偤宕ㄧ€涙ǚ鎷洪梺鍛婄箓鐎氼剟鍩€椤掑嫷妫戞繛鍡愬灩椤繈宕滄担鍝ャ偊闂傚鍋勫ú锔剧矙閹烘绠氶柣鎰劋閻撴洟鏌ㄩ弮鍥跺殭妤犵偞锕㈤弻銊モ槈濞嗘垹鐓傜紓浣介哺閹瑰洤鐣烽幒鎴旀瀻闁圭偓鎯岄埀顒佺墵濮婃椽骞栭悙鎻掝瀳缂傚倸绉撮敃顏堝箖娴兼惌鏁嬮柍褜鍓欓锝嗙鐎ｅ灚鏅ｉ梺缁樻煥閹碱偊鐛澶嬧拻濞达絽鎲￠崯鐐烘煕閺冣偓椤洦绂嶇粙搴撴瀻闁瑰濮崇粭澶娾攽閻愬弶鈻曞ù婊勭箞閹ょ疀濞戞瑧鍘遍梺闈涱檧缁茶姤淇婇悡搴樻斀妞ゆ柨鍚嬪﹢浼存煏閸パ冾伃闁轰礁鍟撮崺鈧い鎺戝閺佸銇勯幘璺盒ｆい顐ｆ礋閺岀喖鎮滃Ο璇查瀺缂傚倸绉村ú顓㈠蓟閺囩喓绠鹃柛顭戝枛婵洟鏌ｉ悩鍙夊窛闁稿绋栭悘鍐⒑缂佹﹫鑰挎繛浣冲嫮顩烽柨鏇炲€归悡娆撴煟閻斿搫顣奸柛鐘筹耿閺岋絽鈽夐崡鐐寸彎濡ょ姷鍋炵敮鎺曠亙闂侀€炲苯澧扮紒顔剧帛缁绘繂顫濋鐘插箞闂傚鍋勫ú锕傚箰婵犳艾闂柣鎴灻肩换鍡樸亜閺嶃劎鍟查棅顒夊墯閹便劍绻濋崒銈囧悑閻庤娲樼敮鎺楀煝鎼淬劌绠涙い鏂挎惈娴犳帒鈹戦敍鍕杭闁稿﹥鐗曠叅婵せ鍋撻柟顔ㄥ洦鍋愰柤纰卞墯濞堢偓绻涢弶鎴濇倯婵炲吋鐟╁畷姗€鍩€椤掑嫭鍋℃繝濠傚閻帡鏌熼鎯т沪缂佸倹甯為埀顒婄秵閸嬪棝宕ｉ崱娑欌拺缂備焦蓱閻撱儵鏌熺喊鍗炰簻闁烩槅鍙冨缁樻媴閸涘﹥鍎撻柣搴㈠搸閸旀垵鐣烽弴銏″仺缂佸鍎婚幗鏇㈡倵楠炲灝鍔氶柣妤佺矊椤﹪濡搁埡鍌楁嫼闂佸憡绋戦敃銉т焊閹殿喒鍋撻崗澶婁壕闂佸憡绋戦敃锝囨崲閸℃稒鐓熼柕蹇嬪焺閻掑墽绱掗埀顒傗偓锝庡亖娴滄粓鏌″鍐ㄥ闁汇劍鍨堕妵鍕棘閸柭ゅ惈濠殿喖锕ュ浠嬪箖閻戣棄绠ユい鏃囧Г閸犳牜绱撻崒娆愮グ妞ゆ泦鍛煋闁汇垻鏁搁惌澶愭煙閻戞ɑ灏柛瀣嚇閺屾盯骞囬妸锔芥緬闂佸綊鏀卞钘夘潖閾忚鍠嗛柛鏇ㄥ亜婵憡绻濆▓鍨灁闁稿﹥绻傞锝夘敃閿旂晫鍔堕悗骞垮劚濡盯宕㈤柆宥嗏拺闂傚牊渚楅悞楣冩煕鎼淬垻鍙€鐎规洘鍨挎俊鐑芥晜缂佹﹩鍟嶉梻浣虹帛閸旀牞銇愰崘顔兼辈婵炲棙鎸婚悡鐔兼煥濠靛棛绠崇紒鈾€鍋撴俊銈囧Х閸嬫稓绮旇ぐ鎺戠鐟滅増甯╅弫鍐煏閸繂鈧憡绂嶉幆褉鏀介柣妯虹－椤ｅ弶銇勯妷銉敾闁靛洤瀚伴獮鍥煛娴ｈ桨鐥┑鐘灱濞夋稓鈧矮鍗冲濠氭偄鏉炴壆鍓ㄩ梺鍝勭Р閸斿秹鎮甸弴鐘电＝濞达絿顭堥埀顒€婀遍弫顔嘉旈崘顏嗗婵炲濮撮鍛啅濠靛棌鏀介柣鎰版涧娴滈箖鎮楀顑惧仮婵﹦绮幏鍛村川婵犲倹娈樼紓鍌欑椤戝棝宕归崸妤€鏋侀柛鎰靛枛鍞梺鍐叉惈閸婂憡绂嶅Δ鍛拺缂備焦蓱閻撱儵鏌涘顒夊剶闁诡噯绻濆鎾倷閳哄倹鏉搁梻浣虹帛钃辩憸鏉垮暣瀵悂濡堕崱娆戭啎闂佸壊鍋嗛崰鎾绘儗閹烘鐓涢悘鐐插⒔濞叉挳鏌涢埡鍐ㄤ槐妤犵偛妫滈ˇ鍗烆熆鐟欏嫭绀€闁宠鍨块弫宥夊礋椤愨剝婢€闂備胶顭堥敃銉╂偋濠婂牆鏋佹い鏇楀亾妤犵偞甯掕灃闁逞屽墰缁絽螖娴ｇ懓寮垮┑顔筋殔濡鏅舵繝姘厽闁瑰搫绉堕惌娆撴煛瀹€鈧崰鏍蓟閸ヮ剚鏅濋柍褜鍓熼悰顔嘉熼懖鈺冿紲闂佺粯顭堝畷鐢告偩濞差亝鐓欐い鏃囧亹缁夎櫣鈧娲栭悥鐓庣暦閹偊妲奸梺闈╃导缁瑩寮婚敓鐘插耿闁归偊鍏橀崑鎾斥攽鐎ｎ亞鐣哄┑掳鍊曢崯顖炲窗閸℃稒鐓曢柡鍥ュ妼婢х増銇勯敂鍝勫缂佽鲸鎸荤粭鐔煎炊瑜庨悘鍫ユ⒑濞茶骞栭柛濠傛健瀵偊骞囬悧鍫濃偓閿嬬箾閺夋埈鍎愰柡鍌楀亾闂傚倷鐒﹂幃鍫曞磿椤栫偛绀夐幖杈剧悼閻捇鏌涢锝嗙闁抽攱鍨块弻娑㈠箛椤掆偓缁狙囨煙椤栨氨澧﹂柡灞剧洴閹垻鎹勯崫鍕偖闁诲氦顫夊ú妯兼崲閸℃稑桅闁圭増婢樼粻鎶芥煙缂併垹鐏￠悗姘虫閳规垿鎮欓懜闈涙锭缂備浇寮撶划娆撶嵁婢舵劖鏅柛鏇ㄥ墮椤忔悂姊虹捄銊ユ灁濠殿喚鏁婚幃锟犲即閵忕姷顔愰柣搴㈢⊕椤洭鍩ユ径鎰厓鐟滄粓宕滃杈ㄥ床闁稿瞼鍋為崑鈺呮煟閹达絽袚妞ゃ儱鐗婄换娑㈠箣閿濆鎽甸梺绋跨Ч濞佳団€旈崘顔嘉ч柛鈩冾殘娴犲ジ姊虹粙娆惧剳闁稿鐩垾鏃堝礃閳哄喚娴勯柣搴祷閸斿宕戦幘瀵哥懝闁逞屽墮椤曪綁顢楅埀顒勫煝鎼淬劌绠氱憸搴ㄥ磹椤栫偞鈷掑ù锝堟閵嗗﹪鏌涢幘瀵哥畾闁圭瓔鍋婂铏圭磼濡偐鐣哄┑鐐跺皺閸犲酣锝炶箛鏃傜瘈婵﹩鍓涢敍婊冣攽閻愬弶顥為柛鈺佺墕鍗辨い鏇楀亾婵﹨娅ｉ幉鎾礋椤撴粌浠﹂梻浣告啞閹歌崵鎹㈤崟顓炲灊妞ゆ挶鍨洪崑鍕煠閼碱剙鍔嬫慨濠傜秺楠炲繘宕ㄩ鍓ф嚌濡炪倖鐗楅惄顖炲极濠婂啠鏀介柣妯虹仛閺嗏晛鈹戦悙鈺佷壕婵犵數鍋橀崠鐘诲炊閵娿儰缃曢梻浣告啞閸旓箓宕伴弽顐㈩棜濠电姵纰嶉悡娆撴煟閹伴潧澧褜鍨辩换娑氫焊閺嶃劍鐝曢梺闈涙搐鐎氫即鐛€ｎ喗鍊烽柟缁樺笒琚樻繝鐢靛仜椤曨厽鍒婇銏犵獥闁哄稁鍘奸悡姗€鏌熸潏鎯х槣闁轰礁锕﹂惀顏堫敇閻愬弶些婵犳鍠楃粙鎾舵閹惧鐟归柛銉戝嫮褰梻浣规偠閸斿矂鎮ユ總鍛婂仒妞ゆ棁娉曢悿鈧┑鐐村灦閿氱紒鎰洴濮婃椽宕崟鍨ч梺鎼炲妿閺佸摜鍒掗敐鍛婵炲棙鍨归惁鍫ユ⒑濮瑰洤鐏叉繛浣冲啰鎽ラ梻鍌欒兌椤牓鏁冮敃鍌氱疇闁规崘顕ч悡婵嬫煙閻愵剛绐為柣鏂垮悑閸ゅ啴鏌嶉崫鍕灕缂佽鲸濞婂缁樻媴娓氼垳鍔搁梺鍝勭墱閸撶喖骞冮悿顖ｆ▌闁芥ɑ绻堥弻銊╁棘濞嗙偓缍楁繛瀛樼矋缁捇寮婚悢琛″亾濞戞瑯鐒界紒鐘崇墬閵囧嫰濡烽敐鍛紕闂侀€炲苯澧叉い顐㈩槸鐓ら柡宥庡亞缁€濠傗攽閻樻彃鈧寮抽敂鍓х＜閻庯綆鍙庨崵锕傛煛閸愩劎澧曢柣鎺戠仛閵囧嫰骞掗幋婵愪患闂佸憡鍨规慨鎾Υ閹烘埈娼╅柨婵嗘噸婢规洟姊绘担鍛婃儓闁哄牜鍓熼幆鍕敍閻愰潧绁﹂梺鎼炲労閸撴岸藟閸喓绠鹃柟瀵稿仧閹冲懎霉濠婂啰鍩ｆ慨濠勭帛閹峰懘宕ㄦ繝鍐ㄥ壍闂備胶鎳撻崯璺ㄦ崲閹烘绠查柕蹇嬪€曢柋鍥煏婢跺牆鍔ラ柟鑺ユ礀閳规垿鎮欓弶鎴犱桓闂佹椿鍙庨崰鏍煝娴犲鏁傞柛顐ゅ枔閸橀亶姊洪崷顓炰壕闁靛洦鐩畷鎴﹀箻缂堢姷绠氶梺鍦帛鐢偞鏅堕敂鑺ュ弿濠电姴鍟妵婵堚偓瑙勬磸閸斿秶鎹㈠┑瀣闁靛ě宥囩婵犵绱曢崑鎴﹀磹閵堝纾婚柛鏇ㄥ瀬閸ヮ剚鐒肩€广儱鎳愰澶愭⒑闂堟稓绠冲┑顕呭弮瀵劍绂掔€ｎ偄鈧敻鏌ㄥ┑鍡涱€楀ù婊勭矊闇夐柣妯挎珪閵囨繃鎱ㄦ繝鍌ょ吋鐎规洘甯掗～婵嬪础閻戝棙婢戦梻鍌欑閹测€愁啅婵犳艾纾婚柟鍓х帛閳锋垿姊婚崼鐔烘创闁绘稒绮庣槐鎺撴綇閵婏妇顦紓渚囧枤缁垶濡堕敐澶婄闁宠桨妞掗崠鏍⒒娴ｈ鍋犻柛搴″船鐓ら柕鍫濐槸缁犵娀鏌熼悧鍫熺凡婵鐓￠弻鏇㈠醇濠垫劖效闂佹娊鏀遍崹鍧楀箖瑜版帒鐐婇柨婵嗗€告禍楣冩煣韫囷絽浜炵憸鎷屽皺缁辨捇宕掑▎鎺戝帯缂備緡鍣崹鎶藉箲閵忋倕閱囬柕澹啰銈﹂梻浣告啞閸旓箓宕伴弽顓ㄧ稏闁告稑鐡ㄩ悡蹇涙煕椤愶絿绠栭柛锝堝Г閵囧嫰寮埀顒勫礉濞嗗浚娼栭柧蹇撴贡绾惧吋淇婇姘儓妞ゎ偄绉电换婵嬪閵忊€虫畬濠碘槅鍋呯换鍫ョ嵁閸℃稑绀嬫い鎺嶇閸斿懘姊洪棃娑辩叚闁奸潧缍婇獮蹇曠磼濡偐顔曢柡澶婄墕婢т粙骞冩總鍛婄厱閹煎瓨绋戦埀顒佺箓閻ｇ兘鎮界粙璺ㄥ姦濡炪倖甯掔€氼參鍩涢幋锔界厱闁挎棁顕ч獮鎰版煙閸戙倗绡€闁哄本绋撻埀顒婄秵娴滄粓顢旈鍕厽闊洦鎼╅崕蹇涙煃鐟欏嫬鐏︽鐐诧躬閺屾稒绻濋崘鈺佺３闂佺娅曢〃濠傜暦婵傜鍗虫俊顖氬槻娴滈箖鏌涘☉娆愮稇缂佺姷鏁婚弻鐔肩嵁閸喖顬夊┑鈽嗗亜閸熸潙顕ｉ锕€绠涢柡澶婄仢缁愭稑顪冮妶鍡樺暗闁哥姵鍔欓、娆愬緞閹邦厸鎷绘繛杈剧悼閹虫捇鍩㈤弴銏＄厸闁逞屽墴閺屽棗顓奸崨顓犫偓顓㈡⒑閸撹尙鍘涢柛锝庡枛椤洭寮借閺€浠嬫煟濡櫣鏋冨瑙勶耿濮婃椽宕￠悙鏉戞灎闂佸搫鐭夌紞浣割嚕椤曗偓閸┾偓妞ゆ帊鑳堕々鎻捗归悩宸剰闁绘帒鐏氶妵鍕箳瀹ュ浂妲紓浣诡殕鐢濡甸崟顖ｆ晣闁炽儱鍟挎慨宄邦渻閵堝繒绱伴柛妤佸▕瀵鈽夐姀鐘栤晠鏌ㄩ弬娆炬綗濞寸姰鍨虹换婵堝枈婢跺瞼锛熼梺杞版祰椤曆囨偩閻戣姤鍋勭痪鎷岄哺閺咁剟姊洪幐搴ｇ畵婵☆偅鐟ラ埢宥咁吋婢跺鎷绘繛杈剧秬濞咃絿鏁☉銏＄厱闁哄啠鍋撻柣鐔村劦閳ワ箓宕堕宥嗏枌闂備胶纭堕弲娑㈠箠濡綍娑㈠礃閵娿垺顫嶉柡澶婄墑閸斿矂鎯佸鍫熲拻濞撴埃鍋撴繛浣冲洦鍋嬮柛鈩冪☉閻ゎ喗銇勯幇鍫曟闁稿﹨鍩栭妵鍕疀閹炬惌妫忛梺绋款儐閹瑰洤鐣峰Δ浣哥窞濠电姳绀侀ˉ姘舵⒒閸屾瑧顦︾紓宥咃工铻為悹鎭掑妿閻棗銆掑锝呬壕閻庢鍠栭幉锛勭箔閻旂厧鐒垫い鎺戝閽冪喖鏌ｉ弬鍨倯闁绘帊绮欓弻銊╁即濡も偓娴滄儳螖閻橀潧浠﹂柣妤佺矌濡叉劙骞掑Δ鈧粈鍫澝归敐澶嬫珳婵炲矈浜濈换娑㈡晲閸涱喗鎮欓梺鎸庢处娴滎亪濡存担绯曟婵妫涢崣鍡涙⒑閸涘﹣绶遍柛姗€绠栭、鏇㈠礂閼测晝鐦堥梺姹囧灲濞佳勭濠婂牊鐓欓柛鎰叀椤庢鎮￠妶鍡愪簻闁规崘娉涢崜杈ㄧ箾閸忚偐澧辩紒杈ㄦ尰閹峰懘鎳栭埄鍐ㄧ仸闁告帒锕獮姗€顢欓悾灞藉箺闂備胶绮濠氬储瑜庣粋宥嗗鐎涙鍘遍梺闈涚墕閹峰宕曡箛娑欑厓閻熸瑥瀚悘鎾煙椤旂晫鎳囩€殿喖鐖奸獮瀣偐閻愮懓浠紓鍌氬€搁崐椋庣矆娓氣偓钘濋梺顒€绉寸粻鐘绘煙閸撗呭笡闁稿鏅犻弻锝夊箣閿濆棭妫勭紓浣哄缂嶄線寮婚妸鈺佸嵆婵°倐鍋撳ù婊冨⒔缁辨挻鎷呴崫鍕戙垺銇勯鐘插幋妤犵偛鍟抽妵鎰板箳閹寸姴鈧偛顪冮妶鍛閻庢凹鍠栬闁靛繈鍊栭埛鎴犵磼鐎ｎ亜鐨￠柛鏃傚枛閺屾稓鈧絻鍔岄埀顒佺箓椤曪絾绻濆顓熸珳闂佺硶鍓濋敋闁告柨鎳樺娲濞戞氨鐣鹃梺鐟板暱濞诧箓骞堥妸鈺佺倞鐟滄粌銆掓繝姘厪闁割偅绻堥妤侇殽閻愬澧紒缁樼〒娴狅箓宕掑顒夌€虫俊鐐€栧ú鈺冪礊閳ь剚銇勯敃鈧紞濠囧蓟濞戙垹鐓橀柟顖嗗倸顥氭繝纰夌磿閸嬫垿宕愰弽顐ｆ殰闁圭儤鏌￠崑鎾愁潩閻撳骸绫嶉梺绯曟櫆閻╊垶鐛€ｎ喗鏅滈悷娆欑稻鐎氫粙姊绘担绋款棌闁稿妫濆畷浼村箻鐡掍焦妞介獮姗€顢欓悾灞藉箺婵犵數鍋為崹鍫曟偡瑜旈幆灞界暆閳ь剛妲愰幒鎾寸秶闁靛鍎茬拠鐐参旈悩闈涗沪闁圭懓娲悰顕€骞掑Δ鈧粻锝夋煛閸愶絽浜鹃柣搴＄仛閻楁鎹㈠┑瀣仺闂傚牊绋愬▽顏堟⒑閹肩偛濡块柡浣割煼楠炲啫螣娓氼垱鍍甸柣鐘烘〃缂嶅秷顦归柡灞诲妼閳规垿宕遍埡鍌傦妇绱撴担鎻掍壕闂佸憡鍔﹂崰妤呮偂閸愵喗鐓曟繝闈涙椤忊晠鏌￠崱妯兼噮闁逞屽墯椤旀牠宕板Δ浣虹濠电姴鍋嗗鏍磽娴ｈ偂鎴炲垔閹绢喗鐓曟繛鎴烇公閺€濠氭煕鎼淬垺灏柍瑙勫灴閹瑧鈧稒顭囩粙鍥⒑缁嬪尅宸ユ繝鈧潏鈺佸灊閻庯綆浜堕崥瀣熆鐠轰警鍎忛柍褜鍓涢崗姗€寮婚敐澶婄闁诲繑妞挎禍婵堢矉閹烘挾闄勯柛娑樑堥幏娲⒑閸涘﹥宕勯悘蹇旂懇瀹曘垹鈽夐姀锛勫幈闁诲函绲洪弲婵嬪礉瀹ュ洨纾奸柛灞炬皑瀛濆銈庡幑閸旀垵鐣锋總鍛婂亜闁告繂瀚俊鍥⒒閸屾凹鐓柛瀣鐓ら柕鍫濐槹閺呮繈鏌曢崼婵愭Ч闁稿鍊块弻鐔衡偓鐢殿焾娴滅厧霉濠婂嫮鐭掗柣鎿冨亰瀹曞爼濡搁敃鈧棄宥咁渻閵堝啫鍔滅紒顔奸叄閸┾偓妞ゆ帊绶￠崯蹇涙煕閻樺磭澧い鏇秮閹虫粓鎮界喊妯绘暤濠电姷鏁告慨鏉懨洪敃鍌氱９闂佸灝顑冩禍婊堢叓閸ャ劍灏い蹇嬪€濋弻娑氣偓锝庡亝瀹曞瞼鈧鍠楅幐鎶藉箖閳哄懎绀冮柟缁樺笧閸濆酣姊婚崒娆戭槮婵犫偓鏉堛劍娅犳俊銈傚亾妞ゎ厼鐏濊灒闁兼祴鏅欑粭澶愭⒑缂佹◤顏勎熸繝鍥ㄥ亜闁糕剝绋掗悡鐘绘煙闂傚鍔嶆繛鎳峰啠鏀芥い鏂垮悑瀹告繄绱掓潏銊ユ诞闁诡噮浜崺鈧い鎺戝閸ㄥ倿寮堕崼姘珕鐎规洖寮剁换娑㈠箣閻戝洣绶甸梺鍝ュ枎閹冲繘濡甸崟顖氱睄闁搞儜鍐╁劒闂備礁鎲￠崹鐢电礊婵犲偆娼栨繛宸簻缁狀噣鏌ら幁鎺戝姕闁哄棛濞€濮婃椽骞栭悙鎻掝瀳濠电姭鍋撻柛妤冨€ｉ敐澶婄疀闁哄娉曢鎺楁煟鎼淬垻鈯曢柨姘亜鎼淬垺灏扮紒缁樼箓閳绘捇宕归鐣屼憾闂備胶纭堕弲娑㈡偋婵犲嫮鐭夌€广儱鎳夐弨浠嬫倵閿濆簼绨介柣銈呭濮婃椽妫冨ù銉ョ墦瀵彃顭ㄩ崼婵堟煣濠电娀娼ч鍡涙偂閺囥垺鍊堕柣鎰絻閳锋棃鏌曢崱妯烘诞闁哄本绋戦～婊堝焵椤掍胶顩查悹杞拌濞兼牠鏌ц箛姘兼綈闁哥姴妫濋弻娑㈠即閵娿儰绨绘繝娈垮枔閸ㄥ綊鈥旈崘顔嘉ч柛鈩兠ˇ鈺呮⒑閸涘⊕顏呮櫠娴犲绀嗛柟鐑樻尵缁♀偓濠殿喗锕╅崜娆撳船閵娿儺娓婚柕鍫濇婵倿鏌涙繝鍐炬疁闁轰礁绉撮…銊╁醇閻斿弶瀚奸梻浣告啞缁诲倻鈧凹鍓熷鍐测枎閹惧鍘辨繝鐢靛Т閸燁垶宕愰幇顔瑰亾濞堝灝鏋涢柟璇х節楠炲棝寮崼顒傜◤濡炪倖鍨煎▔鏇㈡儎鎼搭澀绻嗛柣鎰典簻閳ь剚鐗犲畷婵婎槾鐎垫澘锕畷绋课旀担绋垮闂備礁婀遍崕銈夈€冮崱娑欏亗闁哄洢鍨洪悡鍐偡濞嗗繐顏╅柛鏂诲€曢湁婵犲﹤鎳庢禍鎯庨崶褝韬柟顔界懇椤㈡棃宕熼妸銉ゅ闂佸搫绋侀崑鍛村汲濠婂牊鐓欓柣鎴炆戦悡娑㈡煕鐎ｎ偅灏い顐ｇ箞閹剝鎯旈埦鈧幏顐︽⒒娴ｅ摜绉洪柡鈧柆宥呭瀭闁秆勵殔缁犳牠鏌曡箛瀣偓鏇熷閻樼粯鐓忓璇″灠閼活垶寮查銏♀拻闁稿本鐟ㄩ崗宀勬煙閾忣偅宕屾い銏″哺椤㈡﹢濮€閻樼绱遍梻浣虹帛濮婂宕㈣缁鎮╃紒妯煎幈濠电偞鍨靛畷顒勬倶閿濆洨妫柟顖嗕礁浠梺鍝勮嫰缁夊綊寮婚妸鈺婃晬婵﹩鍋勯ˉ姘舵⒒閸屾瑨鍏屾い顓炵墦椤㈡牠宕卞☉妯碱唹闂佹悶鍎滅仦鍓х▉缂傚倸鍊烽悞锕佹懌婵犳鍨遍幐鎶藉箖瀹勬壋鏋庨煫鍥ㄦ惄娴犲吋绻涚€电袥闁哄懐濮撮～蹇涙惞閸︻厾鐓撻梺鍓茬厛閸ｎ噣宕濇径鎰拺闁革富鍙庨悞楣冩煕閻旈攱鍋ラ柟顔藉劤閻ｏ繝骞婃繝鍐┿仢妞ゃ垺妫冨畷鐔碱敃閵忊剝娈㈡繝纰夌磿閸嬫垿宕愰弽顓炵闁告縿鍎崑鎾愁潩閻撳骸鈷嬮柦妯荤箞濮婂宕奸悢鍓佺箒闂佹悶鍊栧ú鏍箒闂佺粯锚濡﹪宕曞☉銏＄厸濞达絽鎲￠崰姗€鏌″畝瀣М鐎殿噮鍓熷畷褰掝敊妤ｅ啰宕滈梻浣筋嚙鐎涒晠鎳濇ィ鍐ㄎх紒瀣儥濞兼牜绱撴担鑲℃垶鍒婇幘顔界參婵☆垯璀﹀Σ鎼佹煙椤栨粌浠辨慨濠冩そ瀹曨偊濡烽妷鎰剁秮閺屾盯寮埀顒勬偋濠婂懎鍨濋柍鍝勬噺閸嬪嫰鏌ｉ幘鎶筋€楅柤鏉跨仢閳规垿鎮欓弶鎴犱桓闂佽崵鍠嗛崕鑼矉閹烘鍋傞幖鎼枟鐎靛矂姊洪棃娑氬婵☆偅鐟ф禍鎼侇敂閸喓鍘遍梺闈涱煭闂勫嫰濡靛┑瀣厵妞ゆ洖妫涚弧鈧悗娈垮枟閹歌櫕鎱ㄩ埀顒勬煥濞戞ê顏╅悽顖涘劤閳规垿鎮╅崹顐ｆ瘎闂佺顑嗛惄顖炲箖濡　鏀介柛銉缁犳岸姊洪崗闂磋埅闁稿寒鍨辩粋宥咁煥閸喓鍘繝銏ｆ硾閻楀棝宕濈€涙ü绻嗘い鏍殔娴滈箖姊婚崒娆戭槮闁圭⒈鍋婇幆灞惧緞瀹€鈧粈濠傘€掑锝呬壕閻庢鍣崑濠囩嵁閸ヮ剙绾ч悹鎭掑妼閳ь剛鍋ら幃宄邦煥閸愵€囨煟濞戝崬鏋ら柍褜鍓ㄧ紞鍡涘窗濡ゅ懎纾归柣銏犳啞閸嬧剝绻涢崱妤冪妞ゅ浚浜炵槐鎺戠暆閸愨晝銆婄紓浣介哺鐢偟妲愰幒鎳崇喖鎳栭埡鍐╂緰濠德板€楁慨鐑藉磻濞戙垹鐤い鎰剁畱閻撴繈骞栧ǎ顒€濡肩紒鐘哄吹閳ь剝顫夊ú鏍洪妶澶屽祦闁规壆澧楅埛鎺懨归敐鍕劅闁绘帡绠栭幃璺侯潩閻撳簼鍠婇悗瑙勬礃閸旀瑥顕ｆ禒瀣垫晝闁靛牆娴傚Σ鎼佹⒑鐠囪尙绠抽柛瀣Ь椤ｉ箖姊虹悰鈥充壕婵炲濮撮鍡涙偂閻旈晲绻嗘い鏍ㄧ箖椤忕姴霉閻樺磭娲撮柡宀嬬節瀹曡精绠涢弮鈧悵姘舵⒑閸濆嫭婀扮紒瀣崌閸┾偓妞ゆ帒锕︾粔鐢告煕鐎ｎ亝顥㈤柛鈹垮劜瀵板嫰骞囬鐔锋畽闂備胶绮崝鏇㈩敋椤撶姷涓嶅Δ锝呭暞閻撴洟鏌嶉埡浣稿絹闁瑰濮抽悞濠冦亜閹惧崬鐏柣鎾存礋閺屾洘绻涢崹顔煎闂侀€炲苯澧繛灏栤偓鎰佸殨濠电姵纰嶉弲鎻掝熆鐠轰警鐓繛鐓庯躬濮婅櫣绱掑鍡欏姼濠电偛鎳忓ú鐔风暦娴兼潙绠涢柣妤€鐗嗛埀?
		Object langPrf = taoUtil.getLangPrfWithDefault(request);
		Person person = taoUtil.getCurPerson(request);

		// 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢幓鎺旀殕缂佸顑欏鏍煣韫囨凹娼愰悗姘哺閺屾稑鈻庤箛锝喰﹂梺琛″亾妞ゆ挾鍎愬〒濠氭煏閸繈顎楀ù婊勭矋閵囧嫰鏁傜拠鑼化闂佸憡鐟ラ幊妯何涢崨鎼晝闁靛繆鈧剚妲辩紓鍌欑椤戝棝宕圭憴鍕彾闁哄洢鍨归柨銈嗕繆閵堝嫯鍏岄柛妯兼暬濮婅櫣鎷犻垾宕囦化婵犫拃鍕垫疁閽樼喎顭块懜闈涘闁绘挾鍠栭弻鐔兼倻濡櫣浠兼繛瀵稿У閻╊垶寮婚弴銏犵倞鐟滃秹顢旈鐕佹闁绘劖娼欏ù顔筋殽閻愬樊鍎旀い銏＄☉椤繈顢楁径瀣ギ缂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾閽樻繈姊洪鈧粔鎾几娴ｇ硶鏀介柣妯虹枃婢规﹢鏌﹂崘顏勬瀾缂佺粯鐩獮瀣枎韫囨洑鐥梻浣侯焾椤戝棝宕濆Δ鍛劦妞ゆ帒鍠氬鎰箾閸欏澧悡銈夋煏閸繍妲搁柣鎾存礋閺屾盯顢曢妶鍛亖缂備讲妾ч崑鎾绘⒒娓氣偓濞佳勵殽韫囨洜绀婇柛鈩冾殢閻掍粙鏌熼柇锕€骞樼紒鐘荤畺閺屻倗鍠婇崡鐐差潻闂佸搫顑呴柊锝夊蓟閿濆鏅插鑸瞪戦ˉ鏍磽娓氬洤鏋熼柣鐔濆洤绠柣妯肩帛閸婇攱銇勯幒宥堫唹闁瑰嘲缍婂娲偡閺夋寧些闂佺娅曢敋闁伙絿鍏橀、鏇㈡晝閳ь剛绮堢€ｎ偁浜滈柟鎵虫櫅閳ь兙鍊濆畷銉р偓锝庡枟閳锋帡鏌涚仦鎹愬闁逞屽墯閹倸鐣烽幇顒傛殝闁瑰啿锕ょ紞濠傜暦閸洦鏁嗗璺侯儐濞呮棃姊绘担鍛婃儓缂佸绶氬畷銏＄附缁嬪灝鐎梺绋跨灱閸嬬偤鎮￠敐澶屽彄闁搞儵顥撻崚鏉棵瑰鍕畺缂佺粯鐩畷銊╊敍濮ｅ尅绲借彁闁搞儜宥堝惈婵犵鈧磭鍩ｇ€规洏鍔戦、娆撴偂鎼粹槅娼氬┑鐘垫暩婵即宕归悡搴樻灃婵炴垯鍩勯弫鍕煕濞戝崬鐏℃い鎰矙閺岋綁骞囬鐓庡闂佺粯鎸婚悷鈺侇潖濞差亶鏁囬柕濞у懏娈稿┑鐘媰閸涱収鏆梺闈涙搐鐎氭澘顕ｉ鈧崺鈧い鎺戝缁€澶嬬箾閸℃绂嬫繛鍏肩墬缁绘稑顔忛鑽ょ泿婵炵鍋愭繛鈧柡灞剧洴瀵挳鎮欓崗鍝ラ┏闂佽桨绀侀悧鎾愁潖缂佹ɑ濯撮柧蹇撶畭閳ь剙锕弻锟犲川椤旂偓鍒涢梺杞扮閸熸挳銆侀弮鍫濋唶闁绘棁娓圭花鍨繆閻愵亜鈧牜鏁幒鏂哄亾濮樺崬鍘寸€殿噮鍋婂畷鍗炩槈濞嗘垵骞堥梻浣告惈濞层垽宕濈仦鐐珷濞寸厧鐡ㄩ悡鏇熶繆椤栨粎甯涢柛濠冨姍閺屸€崇暆鐎ｎ剛袣缂備胶濮甸惄顖氼嚕閹绢喗鍊烽柡澶嬪閹层垹鈹戦敍鍕杭闁稿﹥鐗曢埢鏃堟晝閸屾氨鍊為悷婊冪箻瀹曟繈鏁冮埀顒勨€旈崘顔嘉ч柛鈩冾殘閻熸劙姊洪悡搴℃毐濠⒀冩捣閸掓帗绻濆顓炩偓鐑芥煙绾板崬骞楁い鏃€妫冨铏圭磼濡搫顫戝銈嗘礃缁矂顢欒箛娑樜ㄩ柍鍝勫€婚崢鎾绘煟閻斿摜鎳冮悗姘煎墴閹﹢鍩￠崨顔惧帗閻熸粍绮撳畷婊堝Ω瑜忕粈濠囨煕閳╁喚鐒芥い鈺傜叀閺岋綁骞囬鍥跺妳闂佽桨绀佸ú顓㈠蓟閿濆绠婚柛鎰皺濡叉澘鈹戦悙瀛樺碍妞ゎ厾鍏樺濠氬即閵忕娀鍞跺┑鐘绘涧濞村倸螞閳ユ剚娓婚柕鍫濆暙閸旀碍淇婇锝囩疄鐎殿喛顕ч埥澶婎潩閿濆懍澹曢梺鎸庣箓妤犲憡绂嶅鍐ｆ斀妞ゆ棁鍋愭晶锕傛煛瀹€瀣瘈鐎规洏鍔戦、姗€鎮╅懠顒€骞楀┑锛勫亼閸婃垿宕归搹鍦煓闁硅揪璁ｇ紞鏍ㄧ節闂堟侗鍎愰柡鍛叀閺屾盯鍩勯崗鈺傚灥椤?`textContent1`
		TextContent serviceName = textContentService.findContentsByKeyAndPerson(langPrf + keyStr + "_description" , person);
		if (serviceName == null) {
			serviceName = new TextContent();
			serviceName.setPosInPage(langPrf + keyStr + "_description");
		}
		serviceName.setRecordStatus(1);

		// 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢幓鎺旀殕缂佸顑欏鏍煣韫囨凹娼愰悗姘哺閺屾稑鈻庤箛锝喰﹂梺琛″亾妞ゆ挾鍎愬〒濠氭煏閸繈顎楀ù婊勭矋閵囧嫰鏁傜拠鑼化闂佸憡鐟ラ幊妯何涢崨鎼晝闁靛繆鈧剚妲辩紓鍌欑椤戝棝宕圭憴鍕彾闁哄洢鍨归柨銈嗕繆閵堝嫯鍏岄柛妯兼暬濮婅櫣鎷犻垾宕囦化婵犫拃鍕垫疁閽樼喎顭块懜闈涘闁绘挾鍠栭弻鐔兼倻濡櫣浠兼繛瀵稿У閻╊垶寮婚弴銏犵倞鐟滃秹顢旈鐕佹闁绘劖娼欏ù顔筋殽閻愬樊鍎旀い銏＄☉椤繈顢楁径瀣ギ缂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾閽樻繈姊洪鈧粔鎾几娴ｇ硶鏀介柣妯虹枃婢规﹢鏌﹂崘顏勬瀾缂佺粯鐩獮瀣枎韫囨洑鐥梻浣侯焾椤戝棝宕濆Δ鍛劦妞ゆ帒鍠氬鎰箾閸欏澧悡銈夋煏閸繍妲搁柣鎾存礋閺屾盯顢曢妶鍛亖缂備讲妾ч崑鎾绘⒒娓氣偓濞佳勵殽韫囨洜绀婇柛鈩冾殢閻掍粙鏌熼柇锕€骞樼紒鐘荤畺閺屻倗鍠婇崡鐐差潻闂佸搫顑呴柊锝夊蓟閿濆鏅插鑸瞪戦ˉ鏍磽娓氬洤鏋熼柣鐔濆洤绠柣妯肩帛閸婇攱銇勯幒宥堫唹闁瑰嘲缍婂娲偡閺夋寧些闂佺娅曢敋闁伙絿鍏橀、鏇㈡晝閳ь剛绮堢€ｎ偁浜滈柟鎵虫櫅閳ь兙鍊濆畷銉р偓锝庡枟閳锋帡鏌涚仦鎹愬闁逞屽墯閹倸鐣烽幇顒傛殝闁瑰啿锕ょ紞濠傜暦閸洦鏁嗗璺侯儐濞呮棃姊绘担鍛婃儓缂佸绶氬畷銏＄附缁嬪灝鐎梺绋跨灱閸嬬偤鎮￠敐澶屽彄闁搞儵顥撻崚鏉棵瑰鍕畺缂佺粯鐩畷銊╊敍濮ｅ尅绲借彁闁搞儜宥堝惈婵犵鈧磭鍩ｇ€规洏鍔戦、娆撴偂鎼粹槅娼氬┑鐘垫暩婵即宕归悡搴樻灃婵炴垯鍩勯弫鍕煕濞戝崬鐏℃い鎰矙閺岋綁骞囬鐓庡闂佺粯鎸婚悷鈺侇潖濞差亶鏁囬柕濞у懏娈稿┑鐘媰閸涱収鏆梺闈涙搐鐎氭澘顕ｉ鈧崺鈧い鎺戝缁€澶嬬箾閸℃绂嬫繛鍏肩墬缁绘稑顔忛鑽ょ泿婵炵鍋愭繛鈧柡灞剧洴瀵挳鎮欓崗鍝ラ┏闂佽桨绀侀悧鎾愁潖缂佹ɑ濯撮柧蹇撶畭閳ь剙锕弻锟犲川椤旂偓鍒涢梺杞扮閸熸挳銆侀弮鍫濋唶闁绘棁娓圭花鍨繆閻愵亜鈧牜鏁幒鏂哄亾濮樺崬鍘寸€殿噮鍋婂畷鍗炩槈濞嗘垵骞堥梻浣告惈濞层垽宕濈仦鐐珷濞寸厧鐡ㄩ悡鏇熶繆椤栨粎甯涢柛濠冨姍閺屸€崇暆鐎ｎ剛袣缂備胶濮甸惄顖氼嚕閹绢喗鍊烽柡澶嬪閹层垹鈹戦敍鍕杭闁稿﹥鐗曢埢鏃堟晝閸屾氨鍊為悷婊冪箻瀹曟繈鏁冮埀顒勨€旈崘顔嘉ч柛鈩冾殘閻熸劙姊洪悡搴℃毐濠⒀冩捣閸掓帗绻濆顓炩偓鐑芥煙绾板崬骞楁い鏃€妫冨铏圭磼濡搫顫戝銈嗘礃缁矂顢欒箛娑樜ㄩ柍鍝勫€婚崢鎾绘煟閻斿摜鎳冮悗姘煎墴閹﹢鍩￠崨顔惧帗閻熸粍绮撳畷婊堝Ω瑜忕粈濠囨煕閳╁喚鐒芥い鈺傜叀閺岋綁骞囬鍥跺妳闂佽桨绀佸ú顓㈠蓟閿濆绠婚柛鎰皺濡叉澘鈹戦悙瀛樺碍妞ゎ厾鍏樺濠氬即閵忕娀鍞跺┑鐘绘涧濞村倸螞閳ユ剚娓婚柕鍫濆暙閸旀碍淇婇锝囩疄鐎殿喛顕ч埥澶婎潩閿濆懍澹曢梺鎸庣箓妤犲憡绂嶅鍐ｆ斀妞ゆ棁鍋愭晶锕傛煛瀹€瀣瘈鐎规洏鍔戦、姗€鎮╅懠顒€骞楀┑锛勫亼閸婃垿宕归搹鍦煓闁硅揪璁ｇ紞鏍ㄧ節闂堟侗鍎愰柡鍛叀閺屾盯鍩勯崗鈺傚灥椤?`textContent2`
		TextContent serviceDescription = textContentService.findContentsByKeyAndPerson(langPrf + keyStr, person);
		if (serviceDescription == null) {
			serviceDescription = new TextContent();
			serviceDescription.setPosInPage(langPrf + keyStr);
		}
		serviceDescription.setRecordStatus(1);

		// 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢幓鎺旀殕缂佸顑欏鏍煣韫囨凹娼愰悗姘哺閺屾稑鈻庤箛锝喰﹂梺琛″亾妞ゆ挾鍎愬〒濠氭煏閸繈顎楀ù婊勭矋閵囧嫰鏁傜拠鑼化闂佸憡鐟ラ幊妯何涢崨鎼晝闁靛繆鈧剚妲辩紓鍌欑椤戝棝宕圭憴鍕彾闁哄洢鍨归柨銈嗕繆閵堝嫯鍏岄柛妯兼暬濮婅櫣鎷犻垾宕囦化婵犫拃鍕垫疁閽樼喎顭块懜闈涘闁绘挾鍠栭弻鐔兼倻濡櫣浠兼繛瀵稿У閻╊垶寮婚弴銏犵倞鐟滃秹顢旈鐕佹闁绘劖娼欏ù顔筋殽閻愬樊鍎旀い銏＄☉椤繈顢楁径瀣ギ缂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾閽樻繈姊洪鈧粔鎾几娴ｇ硶鏀介柣妯虹枃婢规﹢鏌﹂崘顏勬瀾缂佺粯鐩獮瀣枎韫囨洑鐥梻浣侯焾椤戝棝宕濆Δ鍛劦妞ゆ帒鍠氬鎰箾閸欏澧悡銈夋煏閸繍妲搁柣鎾存礋閺屾盯顢曢妶鍛亖缂備讲妾ч崑鎾绘⒒娓氣偓濞佳勵殽韫囨洜绀婇柛鈩冾殢閻掍粙鏌熼柇锕€骞樼紒鐘荤畺閺屻倗鍠婇崡鐐差潻闂佸搫顑呴柊锝夊蓟閿濆鏅插鑸瞪戦ˉ鏍磽娓氬洤鏋熼柣鐔濆洤绠柣妯肩帛閸婇攱銇勯幒宥堫唹闁瑰嘲缍婂娲偡閺夋寧些闂佺娅曢敋闁伙絿鍏橀、鏇㈡晝閳ь剛绮堢€ｎ偁浜滈柟鎵虫櫅閳ь兙鍊濆畷銉р偓锝庡枟閳锋帡鏌涚仦鎹愬闁逞屽墯閹倸鐣烽幇顒傛殝闁瑰啿锕ょ紞濠傜暦閸洦鏁嗗璺侯儐濞呮棃姊绘担鍛婃儓缂佸绶氬畷銏＄附缁嬪灝鐎梺绋跨灱閸嬬偤鎮￠敐澶屽彄闁搞儵顥撻崚鏉棵瑰鍕畺缂佺粯鐩畷銊╊敍濮ｅ尅绲借彁闁搞儜宥堝惈婵犵鈧磭鍩ｇ€规洏鍔戦、娆撴偂鎼粹槅娼氬┑鐘垫暩婵即宕归悡搴樻灃婵炴垯鍩勯弫鍕煕濞戝崬鐏℃い鎰矙閺岋綁骞囬鐓庡闂佺粯鎸婚悷鈺侇潖濞差亶鏁囬柕濞у懏娈稿┑鐘媰閸涱収鏆梺闈涙搐鐎氭澘顕ｉ鈧崺鈧い鎺戝缁€澶嬬箾閸℃绂嬫繛鍏肩墬缁绘稑顔忛鑽ょ泿婵炵鍋愭繛鈧柡灞剧洴瀵挳鎮欓崗鍝ラ┏闂佽桨绀侀悧鎾愁潖缂佹ɑ濯撮柧蹇撶畭閳ь剙锕弻锟犲川椤旂偓鍒涢梺杞扮閸熸挳銆侀弮鍫濋唶闁绘棁娓圭花鍨繆閻愵亜鈧牜鏁幒鏂哄亾濮樺崬鍘寸€殿噮鍋婂畷鍗炩槈濞嗘垵骞堥梻浣告惈濞层垽宕濈仦鐐珷濞寸厧鐡ㄩ悡鏇熶繆椤栨粎甯涢柛濠冨姍閺屸€崇暆鐎ｎ剛袣缂備胶濮甸惄顖氼嚕閹绢喗鍊烽柡澶嬪閹层垹鈹戦敍鍕杭闁稿﹥鐗曢埢鏃堟晝閸屾氨鍊為悷婊冪箻瀹曟繈鏁冮埀顒勨€旈崘顔嘉ч柛鈩冾殘閻熸劙姊洪悡搴℃毐濠⒀冩捣閸掓帗绻濆顓炩偓鐑芥煙绾板崬骞楁い鏃€妫冨铏圭磼濡搫顫戝銈嗘礃缁矂顢欒箛娑樜ㄩ柍鍝勫€婚崢鎾绘煟閻斿摜鎳冮悗姘煎墴閹﹢鍩￠崨顔惧帗閻熸粍绮撳畷婊堝Ω瑜忕粈濠囨煕閳╁喚鐒芥い鈺傜叀閺岋綁骞囬鍥跺妳闂佽桨绀佸ú顓㈠蓟閿濆绠婚柛鎰皺濡叉澘鈹戦悙瀛樺碍妞ゎ厾鍏樺濠氬即閵忕娀鍞跺┑鐘绘涧濞村倸螞閳ユ剚娓婚柕鍫濆暙閸旀碍淇婇锝囩疄鐎殿喛顕ч埥澶婎潩閿濆懍澹曢梺鎸庣箓妤犲憡绂嶅鍐ｆ斀妞ゆ棁鍋愭晶锕傛煛瀹€瀣瘈鐎规洏鍔戦、姗€鎮╅懠顒€骞楀┑锛勫亼閸婃垿宕归搹鍦煓闁硅揪璁ｇ紞鏍ㄧ節闂堟侗鍎愰柡鍛叀閺屾盯鍩勯崗鈺傚灥椤?bottom description
		TextContent serviceBottomDescription = textContentService.findContentsByKeyAndPerson(langPrf + keyStr + "_bottom", person);
		if (serviceBottomDescription == null) {
			serviceBottomDescription = new TextContent();
			serviceBottomDescription.setPosInPage(langPrf + keyStr + "_bottom");
		}
		serviceBottomDescription.setRecordStatus(1);

		Service service = serviceService.findServiceByCatalogAndPerson(keyStr, person);

		// 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢幓鎺旀殕缂佸顑欏鏍煣韫囨凹娼愰悗姘哺閺屾稑鈻庤箛锝喰﹂梺琛″亾妞ゆ挾鍎愬〒濠氭煏閸繈顎楀ù婊勭矋閵囧嫰鏁傜拠鑼化闂佸憡鐟ラ幊妯何涢崨鎼晝闁靛繆鈧剚妲辩紓鍌欑椤戝棝宕圭憴鍕彾闁哄洢鍨归柨銈嗕繆閵堝嫯鍏岄柛妯兼暬濮婅櫣鎷犻垾宕囦化婵犫拃鍕垫疁閽樼喎顭块懜闈涘闁绘挾鍠栭弻鐔兼倻濡櫣浠兼繛瀵稿У閻╊垶寮婚弴銏犵倞鐟滃秹顢旈鐕佹闁绘劖娼欏ù顔筋殽閻愬樊鍎旀い銏＄☉椤繈顢楁径瀣ギ缂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾閽樻繈姊洪鈧粔鎾几娴ｇ硶鏀介柣妯虹枃婢规﹢鏌﹂崘顏勬瀾缂佺粯鐩獮瀣枎韫囨洑鐥梻浣侯焾椤戝棝宕濆Δ鍛劦妞ゆ帒鍠氬鎰箾閸欏澧悡銈夋煏閸繍妲搁柣鎾存礋閺屾盯顢曢妶鍛亖缂備讲妾ч崑鎾绘⒒娓氣偓濞佳勵殽韫囨洜绀婇柛鈩冾殢閻掍粙鏌熼柇锕€骞樼紒鐘荤畺閺屻倗鍠婇崡鐐差潻闂佸搫顑呴柊锝夊蓟閿濆鏅插鑸瞪戦ˉ鏍磽娓氬洤鏋熼柣鐔濆洤绠柣妯肩帛閸婇攱銇勯幒宥堫唹闁瑰嘲缍婂娲偡閺夋寧些闂佺娅曢敋闁伙絿鍏橀、鏇㈡晝閳ь剛绮堢€ｎ偁浜滈柟鎵虫櫅閳ь兙鍊濆畷銉р偓锝庡枟閳锋帡鏌涚仦鎹愬闁逞屽墯閹倸鐣烽幇顒傛殝闁瑰啿锕ょ紞濠傜暦閸洦鏁嗗璺侯儐濞呮棃姊绘担鍛婃儓缂佸绶氬畷銏＄附缁嬪灝鐎梺绋跨灱閸嬬偤鎮￠敐澶屽彄闁搞儵顥撻崚鏉棵瑰鍕畺缂佺粯鐩畷銊╊敍濮ｅ尅绲借彁闁搞儜宥堝惈婵犵鈧磭鍩ｇ€规洏鍔戦、娆撴偂鎼粹槅娼氬┑鐘垫暩婵即宕归悡搴樻灃婵炴垯鍩勯弫鍕煕濞戝崬鐏℃い鎰矙閺岋綁骞囬鐓庡闂佺粯鎸婚悷鈺侇潖濞差亶鏁囬柕濞у懏娈稿┑鐘媰閸涱収鏆梺闈涙搐鐎氭澘顕ｉ鈧崺鈧い鎺戝缁€澶嬬箾閸℃绂嬫繛鍏肩墬缁绘稑顔忛鑽ょ泿婵炵鍋愭繛鈧柡灞剧洴瀵挳鎮欓崗鍝ラ┏闂佽桨绀侀悧鎾愁潖缂佹ɑ濯撮柧蹇撶畭閳ь剙锕弻锟犲川椤旂偓鍒涢梺杞扮閸熸挳銆侀弮鍫濋唶闁绘棁娓圭花鍨繆閻愵亜鈧牜鏁幒鏂哄亾濮樺崬鍘寸€殿噮鍋婂畷鍗炩槈濞嗘垵骞堥梻浣告惈濞层垽宕濈仦鐐珷濞寸厧鐡ㄩ悡鏇熶繆椤栨粎甯涢柛濠冨姍閺屸€崇暆鐎ｎ剛袣缂備胶濮甸惄顖氼嚕閹绢喗鍊烽柡澶嬪閹层垹鈹戦敍鍕杭闁稿﹥鐗曢埢鏃堟晝閸屾氨鍊為悷婊冪箻瀹曟繈鏁冮埀顒勨€旈崘顔嘉ч柛鈩冾殘閻熸劙姊洪悡搴℃毐濠⒀冩捣閸掓帗绻濆顓炩偓鐑芥煙绾板崬骞楁い鏃€妫冨铏圭磼濡搫顫戝銈嗘礃缁矂顢欒箛娑樜ㄩ柍鍝勫€婚崢鎾绘煟閻斿摜鎳冮悗姘煎墴閹﹢鍩￠崨顔惧帗閻熸粍绮撳畷婊堝Ω瑜忕粈濠囨煕閳╁喚鐒芥い鈺傜叀閺岋綁骞囬鍥跺妳闂佽桨绀佸ú顓㈠蓟閿濆绠婚柛鎰皺濡叉澘鈹戦悙瀛樺碍妞ゎ厾鍏樺濠氬即閵忕娀鍞跺┑鐘绘涧濞村倸螞閳ユ剚娓婚柕鍫濆暙閸旀碍淇婇锝囩疄鐎殿喛顕ч埥澶婎潩閿濆懍澹曢梺鎸庣箓妤犲憡绂嶅鍐ｆ斀妞ゆ棁鍋愭晶锕傛煛瀹€瀣瘈鐎规洏鍔戦、姗€鎮╅懠顒€骞楀┑锛勫亼閸婃垿宕归搹鍦煓闁硅揪璁ｇ紞鏍ㄧ節闂堟侗鍎愰柡鍛叀閺屾盯鍩勯崗鈺傚灥椤?`imageList`
		List<ImageData> imageList = mediaUploadService.listAllMediaUploadsByPositionAndPerson(keyStr, person);

		// 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇川绾剧晫鈧箍鍎遍幏鎴︾叕椤掑倵鍋撳▓鍨灈妞ゎ厾鍏橀獮鍐閵堝懐顦ч柣蹇撶箲閻楁鈧矮绮欏铏规嫚閺屻儱寮板┑鐐板尃閸曨厾褰炬繝鐢靛Т娴硷綁鏁愭径妯绘櫓闂佸憡鎸嗛崪鍐簥闂傚倷鑳剁划顖炲礉閿曞倸绀堟繛鍡樻尭缁€澶愭煏閸繃宸濈痪鍓ф櫕閳ь剙绠嶉崕閬嶅箯閹达妇鍙曟い鎺戝€甸崑鎾斥枔閸喗鐏堝銈庡幘閸忔﹢鐛崘顔碱潊闁靛牆鎳愰ˇ褔鏌ｈ箛鎾剁闁绘顨堥埀顒佺煯缁瑥顫忛搹瑙勫珰闁哄被鍎卞鏉库攽閻愭澘灏冮柛鏇ㄥ幘瑜扮偓绻濋悽闈浶㈠ù纭风秮閺佹劖寰勫Ο缁樻珦闂備礁鎲￠幐鍡涘椽閸愵亜绨ラ梻鍌氬€峰ù鍥敋閺嶎厼鍨傞幖娣妼缁€鍐煥濠靛棙顥滈柣锕備憾濮婂宕掑▎鎺戝帯濡炪們鍨归敃銈夊煝瀹ュ鍗抽柕蹇曞Х椤斿姊洪幖鐐插姶闁告挻鐟╅幃姗€骞庨懞銉у幐闂佸憡鍔戦崝搴㈡櫠閺囩姷纾奸柍褜鍓熷畷姗€鍩炴径鍝ョ泿闂傚鍋勫ú銈吤归悜鍓垮洭鏁冮埀顒勬箒濠电姴锕ら悧蹇涙偩濞差亝鐓涢悘鐐额嚙婵″ジ鏌嶇憴鍕伌鐎规洖宕埢搴ょ疀閹惧妲楃紓鍌氬€搁崐鐑芥⒔瀹ュ绀夌€光偓閸曨倠褔鏌熼梻瀵割槮闁藉啰鍠栭弻锝夊棘閸喗鍊梺绋块閻倿寮诲☉妯锋斀闁告洦鍋勬慨銏ゆ偠濮樺墽鐣垫慨濠勭帛閹峰懘宕ㄦ繝鍐ㄥ壍闂備焦妞块崢濂杆囨潏鈺傤潟闁绘劕顕悷褰掓煃瑜滈崜鐔镐繆鐎涙ɑ濯撮柛鎾冲级瀵ゆ椽姊洪柅鐐茶嫰婢у瓨顨ラ悙鎻掓殭闁宠閰ｉ獮妯虹暦閸ヨ泛鏅ｅ┑锛勫亼閸婃牠骞愭ィ鍐ㄩ棷闁靛鍎欏☉婊庢▌濠殿喖锕ら…宄扮暦閹烘埈娼╂い鎴ｆ娴滃墽绱掔€ｎ偓绱╂繛宸簻鍥存繝銏㈡缁犳垵煤椤撱垹绠栭柣锝呯灱閻瑩鏌ら幇浣哥仭闁硅弓鍗冲缁樼瑹閳ь剙顭囪閳ワ箓宕奸妷銉э紵濡炪倖娲嶉崑鎾垛偓娈垮枔閸斿秶绮嬮幒鏂哄亾閿濆骸浜為柛姗€浜跺娲棘閵夛附鐝旈梺鍝ュ枍閸楁娊鐛繝鍥х闁崇懓銇樼花濠氭⒑閹稿海绠撻柟铏姍瀹曟椽鏁愰崱妯哄伎婵犵數濮撮崯顖炲Φ濠靛鐓涢悘鐐跺Г閸ゅ洭鏌熼鐣屾噰闁瑰磭濮烽幑鍕瑹椤栨瑧纾鹃梻鍌氬€风粈渚€骞夐檱閹筋偊姊洪悷鐗堝暈濠电偛锕ら锝囨嫚濞村顫嶅┑顔斤公缁茶姤绂嶆ィ鍐╃叆婵犻潧妫濋妤€霉濠婂嫮绠橀柍褜鍓濋～澶娒洪弽顓炲珘妞ゆ帒瀚粻鐐烘煏婵犲繐顩紒鈾€鍋撻梻浣告啞娓氭宕抽鐣岊浄闁冲搫鍟扮壕钘夈€掑顒佹悙闁哄鍠栭弻锝夋偄閺夋垵濮﹂梺绯曟杺閸ㄦ椽骞嗛弮鍫澪╅柕澹懏姣庨梺鑽ゅ枑缁瞼绮旈悽鐢靛崥闁绘梻顭堢欢鐐烘煙闁箑骞愰柟椋庣帛缁绘稒娼忛崜褎鍋ч梺绋跨昂閸婃繂鐣烽悽绋跨劦妞ゆ帒瀚埛鎴犵棯椤撶偞濯奸柛銈咁儑缁辨帞绱掑Ο鑲╃杽闂佺粯渚楅崳锝呯暦濮椻偓椤㈡瑩鎳栭埡瀣簥闂傚倷鑳剁划顖炲礉閺囥垺鍋ら柕濞垮劗閺嬪秵绻濇繝鍌滃闁抽攱鍨圭槐鎾存媴婵埈浜濋幈銊╁醇閵忋垻锛滄繛杈剧秬椤浜搁敂閿亾濞堝灝鏋旈柛鏂跨Ф缁骞掗弬鍝勪壕闁挎繂楠告禍婊堟煃瑜滈崜娑㈠极婵犳艾钃熼柕濞垮劗閺€浠嬫煕閳╁啩绶遍柍褜鍓氶〃鍛存箒濠电姴锕ょ€氼噣鎯屾繝鍥梿濠㈣泛顑囩弧鈧繝鐢靛Т閸婃悂顢旂€涙绠鹃柛顐ゅ枔閻帡鏌＄仦绯曞亾瀹曞洦娈曢梺閫炲苯澧寸€规洑鍗冲浠嬵敇閻愮數鏋冮梻浣告惈缁嬩線宕㈡總鍛婂亗婵炴垯鍨洪悡鏇㈡煛閸モ晛鍓崇€规悶鍎查妵鍕晜閹屾毉缂備礁鐭佹ご鍝ユ崲濠靛鐐婇柕濞垮劙閺夘厽绻濋悽闈涗粶婵☆偅顨堥幑銏ゅ箣閿曗偓缁犲綊鏌熼柇锕€鏋ょ痪鎯с偢閺岋絽鈻庣仦鎴掑闂備焦鎮堕崝鎴炵閼稿吀绻嗛柣銏㈩焾缁€瀣亜閺嶃劍鐨戦柣銈傚亾闂傚倷绀侀幉锟犲箰妞嬪海鐭嗗〒姘ｅ亾妞ゃ垺纰嶅蹇涘煛閸愵亷绱?JSON 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備浇顕栭崹搴ㄥ礃閿濆棗鐦遍梻鍌欒兌椤㈠﹤鈻嶉弴銏犵闁搞儺鍓欓悘鎶芥煛閸愩劎澧曠紒鈧崘鈹夸簻闊洤娴烽ˇ锕€霉濠婂牏鐣洪柡灞诲妼閳规垿宕卞▎蹇撴瘓缂傚倷闄嶉崝宀勫Χ閹间礁钃熼柣鏂垮悑閸庡矂鏌涘┑鍕姢鐞氾箓姊绘担鍛婃儓闁活厼顦辩槐鐐寸瑹閳ь剟濡存担鍓叉建闁逞屽墴楠炲啫鈻庨幘宕囶啇濡炪倖鎸鹃崳銉ノ涜濮婂宕掑▎鎴犵崲濠电偘鍖犻崗鐐☉閳诲酣骞嬮悙瀛橆唶闂備礁婀遍崕銈夈€冮幇顔剧闁哄秲鍔庣弧鈧梻鍌氱墛娓氭宕曢幇鐗堢厸闁告侗鍠氶崣鈧梺鍝勬湰缁嬫垿鍩ユ径鎰闁绘劕妯婂缁樹繆閻愵亜鈧垿宕曢弻銉﹀殞濡わ絽鍟悡姗€鏌熺€电浠滅紒鐘靛█濮婅櫣绮欓崠鈩冩暰濡炪們鍔屽Λ婵嬬嵁閸儱惟闁冲搫鍊搁埀顒€顭烽弻锕€螣娓氼垱楔闂佹寧绋掔粙鎴﹀煘閹达附鍊烽柡澶嬪灩娴滃爼姊洪悷鎵紞闁稿鍊曢悾鐑藉醇閺囥劍鏅㈡繛杈剧秮閺呰尙绱撻幘鍓佺＝闁稿本鐟чˇ锔姐亜閹存繃顥犻柍褜鍓涢悷鎶藉炊閵娿儮鍋撻崹顐犱簻闁圭儤鍨甸顏堟煕婵犲倻浠涙い銊ｅ劦閹瑩鎳犻鑳闂備礁鎲″鍦枈瀹ュ桅闁告洦鍨遍弲婊堟偣閸ヮ亜鐨哄ù鐙€鍨崇槐鎾寸瑹閸パ勭亪闂佺粯顨呯换姗€宕洪埀顒併亜閹烘埊鍔熺紒澶屾暬閺屾稓鈧絺鏅濋崝宥囩磼閸屾氨孝妞ゎ厹鍔戝畷濂告偄閸濆嫬绠ラ梻鍌欑閹诧紕鎹㈤崒婧惧亾濮樼厧寮挊鐔哥節婵犲倸骞橀柣鐔煎亰閻撱儵鏌涢鐘茬伄闁哄棭鍋婂娲传閸曨厾浼囬梺鍝ュУ閻楁洟鎮惧畡鎷旀棃宕ㄩ鐙€妲梻浣稿悑缁佹挳寮插┑瀣厺闊洦绋掗埛鎺懨归敐鍥ㄥ殌妞ゆ洘绮嶇换娑氭嫚瑜斿顔剧磼閸屾氨孝妞ゎ厹鍔戝畷濂割敆閸屾壕妫ㄩ梻鍌氼煬閸嬪嫬煤閿斿墽鐭欓柟鐑橆殔缁犳岸鏌ｉ幇顔煎妺闁绘挻娲熼弻銊╁籍閸ヨ泛娈Δ鐘靛仦閿曘垽寮婚敐澶婄閻庢稒锚绾惧啿鈹戦纭锋敾婵＄偠妫勯悾鐑芥倻缁涘鏅ｉ悷婊冪箻瀹曟繈骞栨担鍦幗闁瑰吋鐣崹濠氬煝閹剧粯鐓涢柛娑卞灠閳诲牓鏌ｅ☉鍗炴灕缂佺姵鐩顕€鍩€椤掑倻绀婇柛銉墯閻撳啴鏌涘┑鍡楊仼闁哄棛鍠栭弻銊モ槈濞嗘垹鐣虹紓浣虹帛閻╊垶鐛Ο鐓庮棜閻庯綆浜跺Λ鐔哥節閻㈤潧浠滈柣顏冨嵆瀹曟劕鈹戦崱鈺佹缂備礁顑呯花鍗炍ｉ崼銉︾厪闊洤锕ラ鍡涙煙闁垮銇濋柡宀嬬秮婵偓闁宠桨鑳舵导鍥⒑閸濄儱校婵ǜ鍔庨崚鎺楀籍閸偅鏅╃紓浣圭☉椤戝棝顢欓弮鍫熲拺缂備焦锚婵洨鈧鍠栭悥鑹版＂濠电姴锕ら悧濠囨偂閻斿吋鐓ユ繝闈涙椤ョ姷绱掗悩闈涗户闁逞屽墲椤煤濠婂牆绐楅柡宥庡幑閳ь兛绶氬鎾閻樻爠鍥х缂侇喚鎳撴晶鍙夌箾閸涱喗鈷掔紒杈ㄦ崌瀹曟帒顫濋钘変壕闁归棿绀佺壕褰掓煙闂傚顦︾痪鎯х秺閺岋綁骞嬮敐鍛呮捇鏌涙繝鍌涘仴闁哄被鍔戝鎾倷濞村浜剧憸鐗堝笒閻ょ偓銇勮箛鎾跺闁抽攱甯掗妴鎺戭潩閿濆懍澹曢梻渚€鈧偛鑻晶浼存煕鐎ｎ偆娲撮柟宕囧枛椤㈡稑鈽夊▎鎰娇濠电偛顕崢褔鎮洪妸鈺傚亗婵炴垶鈼よぐ鎺撴櫜闁告侗鍙庡Λ宀勬⒑缂佹ɑ宕勯柛瀣姍閸┾偓妞ゆ帊绶￠崯蹇涙煕閻樺磭娲存い銏′亢椤﹀綊鏌涢埞鍨姕鐎垫澘瀚伴獮鍥敆閸屾艾绠戦梻鍌欑閻ゅ洭锝炴径鎰？闁惧浚鍋嗛々鎻捨旈敐鍛殲闁绘挶鍎甸弻锟犲炊閳轰椒绮堕梺閫炲苯澧柟鑺ョ矌閸掓帗绻濆顓熸珳闂佺硶鍓濋悷褔鎯侀崼銉︹拻闁稿本姘ㄦ晶娑氱磼鐎ｎ偄娴€规洝娅曠粋鎺斺偓锝庡亞閸樺憡绻涙潏鍓у埌婵犫偓鏉堛劍娅犻柛顭戝亝閸欏繐鈹戦悩鎻掝伀閻㈩垱鐩弻鐔风暋閻楀牆娅ょ紓浣诡殘閸犳牠銆佸☉妯锋婵炲棙鍔栭澶嬬節閻㈤潧啸妞わ綀妫勫嵄闁告稒娼欑壕濠氭煕閺囥劌骞愰柛瀣尭椤繈顢橀悢鍝勫殥闂備椒绱徊鍧楀礂濮椻偓閵嗕礁鈽夐姀鈥斥偓濠氭煕閳╁叐鎴濃枍閸ヮ剚鈷掑ù锝囨嚀椤曟粍绻涢幓鎺斝х€规洘鍨块獮妯肩磼濡厧骞?
		Map<String, Object> response = new HashMap<>();
		response.put("serviceName", serviceName);
		response.put("serviceDescription", serviceDescription);
		response.put("serviceBottomDescription", serviceBottomDescription);
		response.put("servicePrice", resolveVisibleServicePrice(service, request.getSession()));
		response.put("imageList", imageList);
		if (service != null) {
			response.put("serviceBasePrice", formatCentPrice(service.getPrice()));
			response.put("servicePartnerPrice", StringUtils.defaultString(service.getDescription()));
		}
		if (isManagerOrAdmin(request.getSession())) {
			response.put("showManagerMetrics", true);
			response.put("serviceCost", formatCentPrice(service == null ? null : service.getCost()));
			response.put("serviceStock", service == null || service.getStock() == null ? "" : String.valueOf(service.getStock()));
			response.put("serviceWeeklySalesQty", service == null || service.getWeeklySalesQty() == null ? "" : String.valueOf(service.getWeeklySalesQty()));
			response.put("serviceMonthlySalesQty", service == null || service.getMonthlySalesQty() == null ? "" : String.valueOf(service.getMonthlySalesQty()));
		}

		String serviceOption = service == null ? null : service.getC2();
		if (serviceOption != null) {
			response.put("serviceOptionName", serviceOption);
			Customize options = customizeService.findCustomizeByKeyAndPerson(serviceOption, person);
			if (options != null) {
				response.put("serviceOptions", options.getCusValue());
			}
		}
		// 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇川绾剧晫鈧箍鍎遍幏鎴︾叕椤掑倵鍋撳▓鍨灈妞ゎ厾鍏樺顐﹀箛椤撶偟绐炴繝鐢靛Т鐎氱兘宕ラ崨瀛樷拻濞达絿鎳撻婊呯磼鐠囨彃鈧潡鐛径濞炬闁靛繒濮烽鎺旂磽閸屾瑧鍔嶆い顓炴喘瀹曘垽鏌嗗鍡忔嫼闂傚倸鐗婄粙鎾存櫠閺囥垺鐓欓柛鎰叀閸欏嫭銇勯姀鈩冾棃妞ゃ垺锕㈡慨鈧柨娑樺楠炴劙姊虹拠鑼闁稿绋掗弲鍫曟寠婢规繆娅ｉ埀顒佺⊕鑿уù婊勭矒閺屾洝绠涙繝鍌氣拤缂備讲鍋撻悗锝庡枟閻撴稑霉閿濆洦鍤€濠殿喖绉堕埀顒冾潐濞叉牕鐣烽鍕厺閹肩补鍨鹃悢鐓庣畳鐎广儱妫欓惃鎴犵磼鏉堛劍宕岀€规洘甯掗埢搴ㄥ箳閹存繂鑵愰梻鍌欒兌椤牓顢栭崱娑樼闁搞儜鍛闂佸搫娲㈤崹鍦矆閸愵亖鍋撻悷鏉款棌闁哥姵宀稿畷婵嬫倻閼恒儮鎷洪柣鐘充航閸斿矂寮搁弬搴撴斀妞ゆ棁濮ょ亸锕傛煙闁垮鐏撮柟顔哄灲瀹曟鎳栭埡浣告辈濠电姷鏁告繛鈧繛浣冲吘娑㈩敇閻愬稄缍佸畷銊︾箾婢跺摜鐩庢俊鐐€栭崝鎴﹀垂濞差亜鍚归柛銉墯閻撴盯鎮橀悙闈涗壕缂佲偓婢跺瞼纾奸柣妯虹－閳洜绱掔紒妯肩疄鐎规洘锕㈤崺锟犲礃閵娧勫仹闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鍨鹃幇浣圭稁閻熸粎澧楃敮鎺旂不閺嶎厽鐓曢悘鐐村劤閸ゎ剚绻涢崗鑲╁⒌闁哄本绋戦埞鎴﹀幢濡ゅ啫绲介柣鐔哥矋閺屻劑鎮鹃悜钘夌畾闂侇叏闄勫暩闂傚倷鑳堕…鍫ヮ敄閸℃稑绠版慨婵嗙灱娴滄瑩姊绘担鍛婂暈濞撴碍顨婂畷銏°偅閸愨晜娅栧┑鐐村灟閸ㄦ椽鍩涢幋锔藉仭婵炲棗绻愰鈺冪磼閳ь剟宕橀鐣屽幈闂佸啿鎼崐缁樻櫠椤忓牊鐓熼柨婵嗘搐閸樺鈧娲╃徊鎯ь嚗閸曨偆鏆嗛柍褜鍓熼、娆撳即閵忊檧鎷洪梺鑽ゅ枑婢瑰棝骞楅悩缁樼厽闁绘梹娼欓崝锕傛煙椤旀枻鑰块柛鈺嬬節瀹曟﹢顢旈崱顓犲簥闂傚倷鑳剁划顖炴偡閵夆晛绠犻柟閭﹀枤閻瑩鏌ゆ慨鎰偓妤冨閼测晝纾藉ù锝堫嚃閻掕姤绻涢幓鎺濆殶闁逞屽墲椤煤閺嵮呮殾妞ゆ帒鍟版禍娆撴⒒娴ｅ憡鍟為柛鏃撻檮缁傚秹宕奸弴鐐跺煘闂佸憡绻傜€氀囧绩娴犲鐓熼柟閭﹀墯閳绘洟鏌涢妶鍥ф瀻闁宠鍨块幃娆撳煛娴ｅ嘲顥氱紓鍌氬€搁崐鐑芥嚄閼稿灚鍙忛柣銏犳啞閸嬪鏌￠崶鑸电窙缂傚秵鐗犻悡顐﹀炊閵娧€濮囬梺璇″灣閸嬨倝寮诲☉銏℃櫆閻犲洦褰冪粻濠氭⒑缂佹ɑ灏伴柟鍛婃倐閸╃偤骞嬮敂钘夆偓鐑芥煕濞嗗浚妯堟俊顐節濮婅櫣绮欓崹顕呭妷婵犵數鍋涢敃銈夋偩瀹勯偊鐓ラ柛顐亝閺咃綁姊洪棃娑氱畾婵＄嫏鍥х闁惧繐婀辩壕钘壝归敐鍛棌闁稿骸绻橀幃妯跨疀閹炬潙娈楅悗娈垮枔閸旀垿骞婇敓鐘参ч柛銉㈡櫔缁辨煡姊洪懡銈呅㈡繛娴嬫櫇娴滅鈻庨幇顓熺彿闂佸搫娲ㄩ崰鍡樼濠婂牊鐓涢柛銉︽构缁ㄧ晫绱掔€ｎ厼鍚圭紒杈ㄥ笧缁辨帒螣閼测晝鏆ゆ俊鐐€ら崑鍛崲閸儯鈧礁螖閸涱厾鍔﹀銈嗗坊閸嬫捇鏌ｉ敐鍥ㄦ毈鐎规洖宕埥澶娾枎鐏炴儳顏圭紓鍌氬€搁崐鐑芥倿閿曚焦鎳屾俊鐐€戦崕鍗炍涘▎鎴炲床婵炴垯鍨瑰浠嬫煕閹板吀绨界悮锔戒繆閻愵亜鈧垿宕瑰ú顏呭仭闁宠桨绶￠崵鏇㈡煙缂併垹鏋涙い顐㈡嚇閺屽秹濡烽妷銉ョ闂佹眹鍨荤划顖滄崲濠靛鍋ㄩ梻鍫熷垁閵忋倖鐓ラ柡鍥埀顒佺箓閻ｇ兘顢涢悙鑼唵闁诲繒鍋犺闁稿鎹囬獮姗€顢欑憴锝嗗濠电偞鎸婚崺鍐磻閹剧粯鐓冪憸婊堝礈濞戙垹纾绘繛鎴欏灪閸ゆ劖銇勯弽銊р姇婵炲懐濮甸妵鍕冀椤愵澀娌梺缁樻尰濞茬喖寮婚悢琛″亾濞戞瑯鐒界紒鐘愁焽缁辨帗寰勬惔銏╀哗闂侀€炲苯澧叉い顐㈩槸鐓ら煫鍥ㄧ☉閸ㄥ倿鏌ц箛鎾磋础缂佹唻绠撻弻锝夋晲閸涱垳浼囬梺娲诲幗閻熲晠骞冭ぐ鎺戠畳闁圭儤鍨甸‖鍫澪旈悩闈涗沪閻㈩垪鈧剚娼栫紓浣股戞刊鎾煕濞戞﹫榫氱痪顓涘亾闂傚倷鑳堕…鍫ヮ敄閸℃稑绠伴柟闂磋閳ь剨绠撳畷鎯邦槷闁哄閰ｉ弻鐔煎垂椤斿吋娈查梺鍝勬媼娴滎亜顫忕紒妯肩懝闁搞儜鍐炬交缂傚倷绀侀ˇ閬嶅极鐠囪尙鏆﹂柟鐗堟緲閸ㄥ倹銇勯幇鈺佲偓妤咁敊閺囥垺鈷戦柣鐔煎亰閸ょ喖鏌涚€ｎ剙鏋涙鐐诧躬楠炲洭鎮ч崼銏犲箺闂備胶鎳撻幖顐ょ矓閸洘鍎楁繛鍡樻尰閻撴盯鎮橀悙鎻掆挃闁宠棄顦甸幗鍫曟晲婢跺鍘遍梺鎸庢椤曆囩嵁濮椻偓閺岋綁骞樼€涙ê纾冲┑顔硷攻濡炶棄鐣烽锕€绀嬫い鎾跺С缁辨﹢姊绘担鍛婃喐濠殿喚鏁婚幃褔寮撮姀鈥充患閻庣懓瀚€氬牓寮崼婵嗙獩濡炪倖鏌ㄩ崥瀣偩娴煎瓨鈷掑ù锝囶焾閺嗛亶鏌熺喊鍗炰喊鐎规洘鍔橀妵鎰板箳濠靛洦娅撻梻浣告啞閸旓箓宕板Δ鍛厱闁圭儤鍤氳ぐ鎺撴櫜闁割偒鍋呯紞鍫ユ⒑缁嬫鍎戝┑鐐╁亾闂佸搫鏈惄顖氼嚕椤掑倹宕夐柕濞у啫袩闂傚倷鑳堕崑銊╁磿閼姐倗涓嶉柟鎹愵嚙缁犳牗绻濇繝鍌滃閻庣數濮撮…璺ㄦ崉娓氼垰鍓遍悶姘剧稻缁绘繈鎮介棃娴躲儵鏌℃担鍛婂暈闁逛究鍔戝鍫曞箠閵婏附銇濆┑陇鍩栧鍕節閸曞灚鏁ら梻鍌欐祰椤宕曢崗鍏煎弿闁靛牆顦伴崑鍌炵叓閸ャ劎銆掗柛娆愭崌閺屾盯濡烽鍙ヨ埅闂佸壊鍋侀崕杈╁婵犳碍鐓欓柟顖嗗拑绱為梺鍝勬４缁犳捇寮诲☉銏犖ㄩ柟瀛樼箖閸ｅ綊鏌涢埡浣虹劯婵﹤顭峰畷鎺戭潩椤戣棄浜鹃柟闂寸绾剧懓顪冪€ｎ亝鎹ｉ柣顓炴閵嗘帒顫濋敐鍛婵°倗濮烽崑娑⑺囬悽绋挎瀬闁瑰墽绮崑鎰亜閺冨倹鍤€濞存粓绠栭弻娑㈠箛闂堟稒鐏堥梻浣稿船濞差參寮婚敐澶婃婵炲棛鍋撻悿鍥⒑閹肩偛鈧垿宕愰弴銏犵厴闁硅揪闄勯崑鎰磽娴ｈ偂鎴︽煥椤撶儐娓婚柕鍫濇閻忋儲淇婇锝囩畵闁伙絿鍏橀獮瀣晜閼恒儲顏熼梻浣虹帛閿氱痪缁㈠弮瀹曘垽宕归銈囶啎闁哄鐗嗘晶浠嬪礆娴煎瓨鐓曢柣鏇氱閻忥箓鏌熼鐐毈妞ゃ垺妫冨畷濂告偄閸濆嫬绠版繝鐢靛仩閹活亞寰婃禒瀣妞ゆ劧绠戦悡婵嬫煕椤愩倕鏋戦柛娆忕箰閳规垿鎮╅幓鎺撴闂侀潧娲︾换鍕焵椤掑喚娼愭繛鍙夘焽閸掓帡骞樼拠鑼枃闂佺粯蓱濡炲潡寮崱娑欑厓鐟滄粓宕滃☉銏犳瀬妞ゆ洍鍋撳┑锛勫厴閸╋繝宕掑Δ浣割伖闂傚倷绀侀幉锛勭紦閸ф纾块弶鍫涘妿婢э繝姊绘担鍦菇闁糕晛瀚板畷褰掝敂閸繄鍔﹀銈嗗坊閸嬫挾鐥紒銏犲籍鐎殿喗濞婇弫鍐磼濞戞艾骞嶉梺鍝勵槸閻楀棙鏅舵禒瀣仼閺夊牃鏅濈壕濂告煟濡儤鈻曢柣鎾炽偢閺屸€崇暆鐎ｎ剛鐦堥悗瑙勬礃鐢帟鐏掗梺鍛婄箓鐎氼剟锝炲鑸碘拻濞达絽鎲￠幆鍫ユ煕婵犲嫭鏆€规洘鍨块獮妯肩磼濡攱瀚藉┑鐐舵彧缁插潡鎮洪弮鍫濆惞婵炲棙鎸婚悡鏇㈡煙鐎电校婵炲懏娲滅槐鎺撴綇閳轰椒妲愰悗瑙勬礀瀹曨剝鐏掔紒鐐妞存悂鏌ㄩ妶澶嬧拻濞达絿鎳撻婊呯磼鐠囨彃鈧儻妫熷銈嗙墬缁孩绋夊鍡欑鐎瑰壊鍠曠花濂告煟閹惧娲撮柡灞剧洴婵＄兘鏁愰崨顓烆潛闂備浇顕уù姘椤忓牆钃熺€广儱顦伴悡銉╂倵閿濆簼绨藉ù鐓庣焸濮婅櫣鎷犻垾铏亪缂傚倸绉撮敃顏堟偘椤旂晫鐟归柍褜鍓熼悰顕€骞掑Δ鈧粻锝嗙節閸偄濮夐柍褜鍓氭繛濠傤潖缂佹ɑ濯撮柛娑橈攻閸庢挸顪冮妶鍡楃仴婵☆偅绻傞锝嗙節濮橆儵鈺呮煃鏉炴媽鍏岄柛妯绘尦濮婅櫣娑甸崨顔兼锭闁诲酣娼ч惌鍌氼嚕椤愶絿绡€闁告洦鍘介敍蹇涙⒑閸濆嫷妲搁柣蹇旂箞閹虫粎鎲撮崟顓狀啎闂佸壊鍋撶徊浠嬫偂閹邦喚纾肩紓浣诡焽缁犵偛鈹戦鐟颁壕闂備焦瀵х换鍌溾偓娑掓櫇缁辩偤宕煎┑鍐╂杸闂佺粯鍔樺▔娑㈡儍濞差亝鐓曢柣妯荤叀閸欏嫰鏌熼钘夊姢闁伙綇绻濋獮蹇涘籍閳ь剙螞閸愩劎鏆﹂柕濞炬櫓閺佸﹪鏌涜箛姘汗闁哄棛鍠栧濠氬磼濞嗘埈妲梺鍝ュ枎濞硷繝鐛弽顓ф晝闁挎洍鍋撶紒鎰殕閹便劌螣閹稿海銆愰梺姹囧€ら崳锝夌嵁閺嶎灔搴敆閳ь剚淇婃禒瀣厓闂佸灝顑呯粭鎺楁婢舵劖鐓ユ繝闈涙閹兼劙鏌涢弬娆惧剶闁哄本鐩俊鍫曞磼濮橆偄顥氬┑鐘殿暜缁辨洟宕戦幋锕€纾归柡宥庣亹濞差亝鏅濋柛灞炬皑閻撴垿姊洪崨濠傚闁告柨閰ｅ鎶芥晝閸屾稓鍘搁梺鎼炲劘閸庨亶鎮橀埡鍐＜闁绘瑥鎳愮粔顕€鏌″畝瀣М妤犵偛娲、娆撴嚍閵夛妇妲戦梻鍌欒兌椤㈠﹤鈻嶉弴鐘冲床闁稿瞼鍋涢悡姗€鏌熸潏楣冩闁稿﹦鍏橀弻鈩冨緞鐎ｎ亞浠兼繛瀵稿閸ャ劉鎷洪梺纭呭亹閸嬫稒淇婇懞銉ょ箚闁绘劖澹嗛惌娆愵殽閻愭彃鏆ｉ柡浣规崌閹晠骞撻幒鎴犮偖闂傚倷绶氬褎顨ヨ箛鏇燁潟闁哄洢鍨归崒銊︺亜韫囨挾澧涢柍閿嬪灴閺屾盯骞橀弶鎴犵シ婵炲瓨绮嶇换鍕閹烘梹瀚氶柟缁樺笚濞堟煡姊洪崨濠傜瑨闁挎洏鍎崇紓鎾寸鐎ｎ亜绐涙繝鐢靛Т鐎氼剟鐛崼銉︹拺闁荤喐婢橀幃鎴︽煟閿濆簼閭€规洜顢婇妵鎰板箳閹炬枼鍋撻崼鏇熺厓闁告繂瀚弳鐔虹棯閹佸仮鐎殿喖鐖煎畷鐓庘槈濡警鐎崇紓鍌欑劍椤ㄥ棛鍠婂鍥ㄥ床婵炴垯鍨洪崑鍌炲箹鏉堝墽鎮兼い鏃€甯楃换婵堝枈濡搫鈷夐梺璇″枛閸婂灝顕ｉ锕€绀冩い鏃囧亹閸旓箑顪冮妶鍡楃瑨閻庢凹鍙冭棢濠㈣泛澶囬崑鎾荤嵁閸喖濮庡銈忕細閸楁娊宕哄☉銏犵闁挎梻鏅崢浠嬫⒑缂佹ɑ鐓ラ柟璇х磿濞嗐垽宕ｆ径澶岀畾闂佸湱绮敮鈺呮偂婵傚憡鐓涚€光偓閳ь剟宕伴弽顓炵鐟滅増甯╅弫鍐┿亜閹烘垵鏆婇柛瀣崌瀹曠螖娴ｅ搫骞楁繝鐢靛仦閸ㄩ潧鐣烽鈧埢宥咁吋閸♀晜顔旈梺缁樺姈閹矂鎮炴禒瀣厵濡炲楠搁埢鍫ユ煙瀹曞洤鈻堟い銏☆殕瀵板嫭绻濋崘鈺傜槣闂傚倸鍊峰ù鍥敋瑜庨〃銉╁传閵壯傜瑝閻庡箍鍎遍ˇ顖滃閸ф鈷戞い鎺嗗亾缂佸顕划濠氼敍閻愭潙鈧敻鏌ㄥ┑鍡欏哺闁稿鎹囬、姗€鎮㈢悰鈩冾潔婵＄偑鍊ら崢褰掑礉閹存繄鏆﹀┑鍌氭啞閸嬪嫰鏌涘▎蹇ｆЦ妞?`ResponseEntity.ok()`闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇川绾剧晫鈧箍鍎遍幏鎴︾叕椤掑倵鍋撳▓鍨灈妞ゎ厾鍏樺顐﹀箛椤撶偟绐炴繝鐢靛Т鐎氱兘宕ラ崨瀛樷拻濞达絿鎳撻婊呯磼鐠囨彃鈧潡鐛径濞炬闁靛繒濮烽鎺旂磽閸屾瑧鍔嶆い顓炴喘瀹曘垺娼忛妸褏鐦堥梺姹囧灲濞佳冪摥闂備胶顭堥敃銈囩礊婵犲倻鏆﹂柟杈剧畱鍞梺鍐叉惈閸婂宕㈤幖浣圭厽闊洦娲栨禒鈺傘亜閺囧棗瀚崣蹇涙煕鐏炴儳顥氶柛瀣尭閳绘捇宕归鐣屽蒋闂備線娼荤紞鍥╁緤娴犲鍋╅梺鍨儑闂勫嫰鏌涘☉姗堝伐濞存粍绮撳娲川婵犲嫧妲堝銈庡弾閸撶喎鐣烽鍕唶闁哄洨鍟块幏濠氭⒑缁嬫寧婀伴柣鐔村姂瀹曟浠︽穱鍙樼盎濡炪倖鎸鹃崑鐔告櫠閿斿浜滈柕蹇ョ磿婢х數鈧娲栧畷顒勫煡婢跺á鐔哄枈閸楃偞鏆梻鍌氬€风粈渚€骞夐敓鐘茬闁糕剝绋戠粈鍐煏婵炑冨€搁弸鍫熺節绾板纾块柛瀣灴瀹曟劙寮介鐐殿唶婵°倧绲介崯顐ゅ婵犳碍鐓欓梺顓ㄧ畱閸旓箓鏌ㄥ┑鍡╂Ц闂佸崬娲弻锝夊棘閸噮鏆㈢紓浣哄У閼归箖鈥旈崘顔嘉ч柛鈩冪懃椤呯磽娴ｇ瓔鍤欑紒澶屾嚀閻ｇ兘妫冨☉鎺撶€婚梺褰掑亰閸犳岸顢欓弴銏♀拺闁告稑锕﹂埥澶嬬箾鐏炲倸鈧洜鍙呴梺鎸庢礀閸婃悂鎮￠弴銏＄厵閻庣數顭堟禒褏绱掗悩鑽ょ暫闁哄被鍊栫缓浠嬪礈閸欏绶梻浣规偠閸婃牠鎮烽敃鍌樷偓鍐Ψ閳哄倸鈧兘鏌熺喊鍗炲箺闁汇倓绶氬娲传閸曨厾鍔圭紓鍌氱С缁舵岸鐛崘顔碱潊闁靛牆鎳愰悡鎴炵節閵忥絾纭炬い鎴濇穿閵囨劗鈧綆鍠楅悡鐔煎箹濞ｎ剙鐒洪柛鐔风箻閺屾盯鎮╁畷鍥р拰闂佺偨鍎荤粻鎾诲箖濠婂牊瀵犲璺虹灱閺嗩偊姊绘笟鈧褏鎹㈤崼銉ョ９闁哄洨鍎愰崯鍛節闂堟稒顥戦柡鈧禒瀣厽婵☆垵顕х徊缁樸亜韫囷絽浜伴柟铏墵瀵粙濡歌閻﹀牓鎮楅崗绋垮祮闁衡偓閸楃儐鐔嗘慨妞诲亾闁糕斁鍋撳銈嗗焾閸撴瑩骞婃惔锝嗩偨闁靛牆鎮胯ぐ鎺撳亹鐎瑰壊鍠栭崜浼存⒑濮瑰洤鈧倝宕板Δ鍛﹂柛鏇ㄥ灠閸愨偓濡炪倖鍔戦崹瑙勭珶婢舵劖鈷戦弶鐐靛椤︾兘鏌ら崘鑼煟闁糕斁鍋撳銈嗗坊閸嬫挾绱掗悩鑼х€规洘娲熼弻鍡楃暤閵夈儲澶勯悗闈涖偢瀵爼骞嬮悩鎻掔疄闂傚倷鑳堕幊鎾活敋椤撶喐鍙忓瀣椤ユ艾鈹戦崒姘暈闁稿﹦鏁婚弻銊モ攽閸℃瑥鍤紓浣靛姀濡嫰婀侀梺缁橈供閸犳牠鎮橀鍫熺厪闁搞儜鍐句純濡ょ姷鍋炵敮鐐哄焵椤掑﹦绉靛ù婊呭仦缁傛帗銈ｉ崘鈹炬嫼闂傚倸鐗婄粙鎺楀箹閹扮増鍊垫慨妯哄船椤ｇ厧菐閸パ嶈含妤犵偞鐗楅幏鍛存嚃濠靛嫬濮傞柡灞诲姂瀵潙螖閳ь剚绂嶉幆褉鏀介柣鎰皺婢ф稒銇勯妸銉︻棤闁瑰箍鍨归埞鎴﹀醇濠靛洨鈧啿鈹戦埥鍡楃仩闁圭⒈鍋婇崺娑欏緞閹邦厸鎷洪梺鑽ゅ枔婢ф宕悙娴嬫斀闁绘劘顕滈煬顒勬煙椤旇宓嗘い銏″哺閸┾偓妞ゆ帒瀚畵渚€鏌熼悜妯烘闁哄啫鐗嗛悞鍨亜閹烘垵鈧粯绋夊澶嬬叆婵犻潧妫欓ˉ鎾趁瑰鍕煉闁哄瞼鍠栭獮鎴﹀箛椤撶姰鈧劙姊洪崫鍕靛剰闂佸府缍佸璇测槈濠婂懐鏉搁梺鐟扮仢鐎氼垶宕ラ鈶╂斀妞ゆ梻銆嬮崝鐔虹磼椤曞懎鐏︽鐐茬箻瀹曘劎鈧稒锚娴狀參姊绘笟鍥у伎缂佺姵鍨甸埢鎾斥攽閸垻锛濋梺绋挎湰閻燂妇绮婇悧鍫涗簻闁哄洤妫楀ú銈囧瑜版帗鐓曠憸搴ㄣ€冮崼婵堟殼濞达絿纭堕弨浠嬫煟濡绲诲ù婊呭仱閺屾稑顫濋澶婂壎闂佸搫鏈惄顖炲春閸曨垰绀冩い蹇撴噹濞呮﹢鏌ｉ悢鍝ョ焼闁告纾划濠氬冀瑜滃鏍ㄧ箾瀹割喕绨诲ù鑲╁█閺屾盯寮撮妸銉ヮ潻缂備焦鍔栭惄顖氼潖濞差亜浼犻柕澶堝剾閵忋垻纾煎璺猴功閸╋絾顨ラ悙鏉戠伌濠殿喒鍋撻梺闈涚墕閹冲酣鎳撻崹顔规斀闁绘劖娼欓悘锕傛煟閻旀繂娲ら崒銊ッ归悩宸剱闁抽攱鍨块幃宄扳枎韫囨搩浠剧紓浣藉煐閻擄繝寮诲☉姗嗘僵闁挎繂鎳嶆竟鏇犵磽閸屾艾鈧兘姊藉澶婄鐎光偓閸曘劉鍋撻敃鍌涘€婚柦妯侯槼閹芥洟鎮楅獮鍨姎闁绘绮岄‖濠囧Ω閳哄倵鎷洪梺鍛婄☉閿曘儳浜搁幍顔瑰亾閸忓浜剧紓浣割儓濞夋洟寮抽敂鐐枑闁绘鐗嗙粭鎺楁煕閵娿儱鈧綊濡甸崟顖氱睄闁稿本鐭竟鏇炩攽閻愯尙澧︾紒鐘崇墪椤繐煤椤忓懎娈ラ梺闈涚墕閹冲繘鎮楁ィ鍐┾拺闂傚牃鏅濈粙缁樼箾閸忚偐鎳呴柛娆忔噹椤啴濡堕崨顖滎唶缂備礁顦遍弫璇差嚕閹绘帩鐓ラ柛顐ゅ暱閹锋椽姊洪崨濠勨槈闁挎洩绲垮▎銏ゆ焼瀹ュ棛鍘卞┑鈽嗗灣缁垶濡靛┑瀣厵妞ゆ柨鐏濊缂備胶绮换鍌烇綖濠靛鏁囨繛鎴炵懃椤忕儤绻濋悽闈涗哗闁规椿浜炲濠囧锤濡炵儵鍋撻敃鍌氶唶闁靛鍎叉潏鍫ユ⒒娓氬洤澧紒澶屾暬閹€斥枎閹寸姵锛忛梺鍝勵槸閻忔繈鎳滈悷鎳婄懓顭ㄩ崨顓ф毉濡炪們鍔婇崕鐢稿箖濞嗘垶瀚氱憸搴ㄥ极妤ｅ啯鈷戦悹鍥ｂ偓宕囦画濠碘槅鍋勯崯顖涚┍婵犲洤绠瑰ù锝堝€介妸鈺傜厪濠㈣泛鐗嗛崝婊堟倶韫囨搩鍎旀慨濠傤煼瀹曟帒鈻庨幋鐘靛床婵犵數鍋橀崠鐘诲炊娴ｅ憡鍠樻い銏℃礋閺佸啴鍩€椤掑嫭鍋柣鎰靛墰缁♀偓婵犵數濮撮崐缁樻櫠濞戙垺鐓?Spring 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇川绾剧晫鈧箍鍎遍幏鎴︾叕椤掑倵鍋撳▓鍨灈妞ゎ厾鍏樺顐﹀箛椤撶偟绐炴繝鐢靛Т鐎氱兘宕ラ崨瀛樷拻濞达絿鎳撻婊呯磼鐠囨彃鈧潡鐛径濞炬闁靛繒濮烽鎺旂磽閸屾瑧鍔嶆い顓炴喘瀹曘垽鏌嗗鍡忔嫼闂傚倸鐗婄粙鎾存櫠閺囥垺鐓欓柛鎰叀閸欏嫭銇勯姀鈩冾棃妞ゃ垺锕㈡慨鈧柨娑樺楠炴劙姊虹拠鑼闁稿绋掗弲鍫曟寠婢规繆娅ｉ埀顒佺⊕鑿уù婊勭矒閺屾洝绠涙繝鍌氣拤缂備讲鍋撻悗锝庡枟閻撴稑霉閿濆洦鍤€濠殿喖绉堕埀顒冾潐濞叉牕鐣烽鍕厺閹肩补鍨鹃悢鐓庣畳鐎广儱妫楅崫鐑樻叏婵犲偆鐓肩€规洘甯掗埢搴ㄥ箳閹存繂鑵愬┑锛勫亼閸娿倝宕㈡總鍛婂亱闊洦绋戠粻鏍ㄣ亜閺囨浜惧銈冨灪濞茬喖寮崘顔肩劦妞ゆ帒鍊婚惌鍡楊熆閼搁潧濮囩紒鐘侯潐缁绘盯鏁愭惔鈥愁潻婵犲痉銈呬汗缂佽鲸甯￠幃鈺呮濞戞瑧浜梻浣告惈閻ジ宕伴弽褜娼栭柤濮愬€愰崑鎾绘濞戞﹩妫屽┑鈥虫▕閸犳氨妲愰幘瀵哥懝闁搞儜鍌滅泿缂傚倷绀侀ˇ顖滅礊婵犲偆鍤曢悹鍥ㄧゴ濡插牓鏌曡箛濠冩珕闁哄懏绻堝娲箰鎼达絿鐣靛┑鈽嗗亝椤ㄥ﹤鐣峰鍫熷€婚柤鎭掑劗閹锋椽姊婚崒姘卞濞撴碍顨呭嵄闁圭虎鍠楅悡娑㈡煕濞戝崬鏋ら柣顓熷浮閺岀喖顢氶崱娆戠槇閻庢鍠楅幐铏叏閳ь剟鏌ㄥ☉妯侯仼妤犵偛绉瑰缁樻媴閸濆嫬浠樻繝銏㈡嚀閿曨亜鐣峰鍐ｆ瀻闁归偊浜炵粻姘舵⒑閸忛棿鑸柛搴＄－缁粯銈ｉ崘鈺冨幈濠电偞鍨靛畷顒勭嵁濡や降浜滈柕澶涚到閻忔煡鏌″畝瀣？濞寸媴绠撳畷婊嗩槷濠殿喖娲铏规嫚閳ュ磭浠┑鈽嗗亜閸熸潙顕ｉ锕€绠涢柡澶婄仢缁愭稑顪冮妶鍡樼叆鐟滄壆鍋ら弻鍥敍閻愮补鎷婚梺绋挎湰閻熝囁囬敃鍌涚厵缁炬澘宕禍鎵偓娈垮枤閺佸銆佸Δ鍛劦妞ゆ帒鍊稿鍙夌節閻㈤潧鈻堟繛浣冲洦鍋嬮煫鍥ㄦ⒒閳绘梻鈧箍鍎卞Λ娑氱不妤ｅ啯鐓欓悗鐢殿焾娴犳粓鏌ｉ幒鎴犵Ш闁哄本绋撻埀顒婄秵閸嬪懎鐣峰畝鍕厸濞达絿鎳撴慨鍫ユ煙椤栨稒鐓ラ柍瑙勫灴瀹曞ジ鎮㈤崨濠庝化闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鍨鹃幇浣圭稁闂佽崵鍠栭崑濠囨倿閼恒儳绠鹃柛鈩兩戠亸顓㈡煟閹捐揪鑰块柡宀€鍠愬蹇斻偅閸愨晩鈧秹姊虹粙鍖″姛闁稿繑锕㈤獮鍐喆閸曗晛浜伴梺鍦焾鐎垫帗绂掗埡浣叉斀妞ゆ洖妫涚粊鐑芥煕閵娿儳绉洪柍銉︽瀹曟﹢顢欓崲澹洤绠圭紒顔煎帨閸嬫挸鐣烽崶璺烘櫍闂傚倸鍊烽懗鍓佸垝椤栫偑鈧啴宕卞☉妯碱唹闂佹悶鍎滈崨顔筋啎婵犵數鍋涘Ο濠冪濠靛纾归柣鎴ｅГ閻撴洘绻濋棃娑欘棞妞ゅ浚鍋呴妵鍕箻鐎涙ǜ浠㈠┑顔硷攻濡炰粙鐛幇顓熷劅闁靛繆鏅滈埛鏍ㄧ節绾版ɑ顫婇柛瀣閳ь剚鍑归崢鍓у垝鐎ｎ亶鍚嬮柛顐ｇ◥濮规姊洪崷顓炲妺闁规悂绠栧畷銏＄鐎ｎ偀鎷虹紓鍌欑劍閳笺倝顢旈崟闈涙闂佸壊鍋呭ú鏍磼閵娾晜鐓曠€光偓閳ь剟宕戦悙鐑樺亗闁哄洢鍨洪悡娆撴煛婢跺骸鍔甸悗姘嵆閺岀喖鎼归銏狀潚闂佽鍣换婵嬬嵁鐎ｎ喗鏅滈悷娆欑稻鐎氫粙姊绘担渚劸闁哄牜鍓熼幃鐑芥晜閸撗呯劸闂佺粯顨呴悧鍕濠婂牊鐓涢柛銉戝嫷妫為梺鐟板暱椤﹂潧鐣烽幋锕€绠婚柡鍌樺劜椤秹姊洪崷顓炲妺闁圭顭烽幃闈涱潩閼搁潧鈧敻鎮峰▎蹇擃仾缂佲偓閸愵喗鐓熼柣鏃€娼欐慨鍌氣攽閿涘嫭鏆鐐叉喘瀵墎鎹勯…鎴濇暯闂傚倷鑳堕幊鎾活敋椤撶喐鍙忛柣鎴ｆ閸屻劍銇勯幒鎴濐仾闁抽攱甯掗湁闁挎繂鎳忛崯鐐烘煕閻斿搫浠遍柡灞剧洴瀵噣鍩€椤掑嫬鍨傞柛顭戝枤閺嗭附绻濋棃娑欏闁稿海鍠栭弻鏇熺箾閸喖濮曢柡澶婄墕閻楁挸顫忓ú顏勭閹兼番鍨婚ˇ銉╂⒑缁嬪尅宸ョ紓宥咃躬楠炲啴鏁撻悩鍙傘劑鏌ㄩ弬鍨挃闁伙箑鐗撳濠氬磼濮樺崬顤€缂備礁顑嗙敮陇妫㈤梺缁樏Ο濠囨偄閸℃瑦鍠愰悗锝庡枛閸ㄥ倸霉閻樺樊鍎忛柦鍐枛閺屻劌鈹戦崱鈺傂﹂柛銉︽尦濮婅櫣绮欓幐搴㈡嫳闂佹椿鍙庨崰妤冪博閻旂厧鍗抽柣鏃囨閻﹀牓姊哄Ч鍥х伈婵炰匠鍐╂瘎闂傚倷娴囧銊╂倿閿曞倸绠栭柛灞炬皑閺嗭箓鏌熸潏楣冩闁稿﹦鍏橀弻娑樷攽閸℃浠奸梺璇插瘨閸欏啫顫忓ú顏勭闁圭粯甯婄花鐓庘攽閻愭彃绾ч柨鏇樺灩閻ｇ兘骞嬮敂鑺ユ珫闂佸憡娲﹂崜娆撳几閹达附鈷戦柛婵嗗閳ь剝妫勯埢鏃堝即閵忕姵杈堥梺鎸庢礀閸婂綊鎮¤箛娑氬彄闁搞儜灞藉壈闂侀€炲苯澧繛鍙夌矌閸掓帗绻濆顓炴闂侀潧鐗嗗ú銊╂晬濠靛洨绠鹃弶鍫濆⒔缁夘喗銇勬惔鈩冩悙妞ゎ偅绻勯幑鍕惞鐠団€虫暩濠电姷鏁搁崑娑樜熸繝鍐洸婵犻潧顑呴悡鏇㈡煙鏉堥箖妾柣鎾存礃缁绘盯骞嬮悜鍥у彆闂佸憡姊婚崰鏍蓟瀹ュ瀵犲鑸瞪戦埢鍫ユ⒑閸濆嫯顫﹂柛鏃€鍨甸锝夊箻椤曞懏鏅滈梺鍛婃礀閻忔岸宕板Ο鑲╃＝闁稿本鐟х拹浼存煕閻樺磭澧甸柍銉畵閹粓鎮剧仦鐣屻偊婵犵妲呴崹鐢稿磻閹邦喖顥氶柛蹇涙？缁诲棙銇勯弽銊х細闁逞屽厴閸嬫捇姊虹粙娆惧剱闁圭懓娲ら悾鐤亹閹烘繃鏅濋梺鎸庣箓閹冲孩瀵兼惔銏㈢瘈缁剧増蓱椤﹪鏌涢妸锕€鈻曠€规洏鍨奸ˇ鎾煛娓氬洤鏋ら柟鐟板缁楃喖顢涘鍐╂毄闂佽崵鍠愮划宀勬煀閿濆宓侀柟鐗堟緲缁狀噣鏌﹀Ο渚Ъ闁硅姤娲栭埞鎴︽倷閺夋垹浠ч梺鎼炲妼缂嶅﹪骞冮垾鎰佹建闁逞屽墴瀵鎮㈢喊杈ㄦ櫖闂佺硶鍓濋〃鍛村焵椤掍焦鍊愰挊鐔告叏濮楀棗骞樼紒鈾€鍋撶紓浣哄亾濠㈡﹢藝鏉堚晛顥氶柛褎顨嗛悡鏇㈡倵閿濆骸浜滈柣蹇擃嚟閳ь剝顫夊ú姗€宕濆▎蹇ｅ殨闁稿﹦鍣ュ銊╂⒑閸濄儱鏋庨柟鍐查叄閸╃偤骞嬮敂钘変汗闁诲骸婀辨慨鎾夊┑鍫㈢＜闁绘劦鍓欓崝銈嗐亜椤撶姴鍘寸€殿喖顭烽幃銏ゆ偂鎼达絿鏆伴柣鐔哥矊缁夊綊濡撮崒姘辨殾闁搞儮鏅滈敍蹇涙⒑閸涘﹥瀵欓柛鏇ㄥ墰瑜把囨⒒娓氣偓閳ь剛鍋涢懟顖涙櫠椤斿墽纾奸柣妯垮皺鏁堥悗瑙勬礃濞茬喖寮婚崱妤婂悑闁告侗鍨奸崬鐢告⒒娴ｇ儤鍤€闁宦板妿閹广垹顓兼径濠勬焾闂佸憡绋戦悺銊╂偂韫囨搩鐔嗛悹铏瑰劋椤ョ偞绻涢崨顓燁棦闁哄矉缍侀獮妯兼崉閻戞浜梻浣告惈鐞氼偊宕濆畝鍕厴闁硅揪绠戦獮銏′繆椤栨粎甯涢柛濠勫厴濮婄粯鎷呴崨濠傛殘闂佹寧纰嶉妵鍕即閵娿儱绫嶉梺绯曟杹閸嬫挸顪冮妶鍡楃瑐缂佽绻濆畷顖濈疀濞戞瑧鍘遍梺缁樕戦崜姘枔濠婂嫷娈介柣鎰綑婵秹鏌熼悡搴ｇШ妞ゃ垺娲熼弫鎰板川椤撴繄绀嬮梻鍌氬€风欢姘跺焵椤掑倸浠滈柤娲诲灡閺呭爼骞橀鐣屽幐闁诲繒鍋熼弲顐ｆ櫏濠电姷顣槐鏇㈠礂濡绻嗛柣鎴ｆ鍞梺闈涳紡鐏炶棄澹嶇紓鍌氬€搁崐鎼佸磹閻戣姤鈷旂€广儱顦粈澶屸偓鍏夊亾闁告洏鍔夐崑鎾绘晝閳ь剟鎮鹃敓鐘崇劷闁挎梻鏅粙浣虹磽閸屾艾鈧兘鎮為敃浣规噷濠电姵顔栭崹浼搭敋椤撶姵顫曢柟鐑樻煛閸嬫捇鏁愭惔鈥茬盎濡炪倖娲樼划鎾诲蓟濞戞瑧绡€閹艰揪绱曟禒顖滅磽娴ｄ粙鍝洪柟绋款煼楠炲繘宕ㄩ鍛ч柟鑲╄ˉ閳ь剚鍓氶悗鎾⒒閸屾瑧顦︽繝鈧柆宥佲偓锕傚醇閵夈儳鏌堥梺绉嗗嫷娈㈤柡浣割儔閺屻劌鈹戦崱鈺傂︾紓浣哄Х閺佸寮婚悢鐓庝紶闁告洦鍓﹀Λ鈥愁渻閵堝骸浜滅紒澶嬫尦閸╃偤骞嬮敂钘変汗濠电偛妫楃换鎰板汲閺傛５褰掓偐閸愯尙鐟查梺閫涚┒閸斿酣骞夐幘顔肩闁割煈鍋嗛弶瑙勭節濞堝灝鏋涢柨鏇樺劚椤啴鎸婃径灞炬闂侀潧顭俊鍥╁姬閳ь剟姊洪崨濠傚Е闁绘挸鐗嗗嵄闂侇剙绉甸埛鎺懨归敐鍫綈闁稿濞€閺屾盯寮埀顒勫垂閸撲胶鐭夌€广儱顦柋鍥煛閸モ晛浠︽俊宸墮閳规垿鎮欓弶鎴犱户闂佹悶鍔屽﹢杈╁垝婵犳碍鏅插璺侯儑閸欏棝姊洪崫鍕殭闁稿﹦鎳撻埢宥夊即閵忥紕鍘卞┑鈽嗗灠閸氬寮抽浣瑰弿濠电姴鍟妵婵堚偓瑙勬礃椤ㄥ﹪骞婇弽顓炵厴闁割煈鍠曞▽顏堟⒒閸屾瑧顦﹂柟纰卞亰楠炲﹨绠涢弴鐘电厯闂佸湱鍎ゅ濠氭儗婢舵劖鐓欓柣鎴灻悞楣冩煟閹惧崬鍔﹂柡灞剧洴椤㈡洟濡堕崨顔句簴闂備礁鎲￠敃銏＄鐠轰警娼栨繛宸簻娴肩娀鏌涢弴銊ヤ簼婵炲牊绮撳铏圭矙濞嗘儳鍓遍梺鍦嚀濞差厼顕ｆ繝姘╅柍杞扮瀹撳棗鈹戦埥鍡楃仴婵炲拑绲剧€靛ジ宕奸妷锕€鈧灚绻涢崼婵堜虎闁哄闄勯妵鍕即閸℃鎼愰柣鎾偓鎰佺唵闁兼悂娼ф慨鍥ㄣ亜椤愩垺鍤囬柡宀嬬到铻ｉ悹鍥ㄥ絻椤亜鈹戦埥鍡椾簽闁哥姵鐗曢～蹇撁洪鍛闂侀潧鐗嗛幉娑㈡晲閸氥倗鎳撻…銊╁焵椤掑倸鍨濇繛鍡楃箳閺嗭附绻濇繝鍌滃闁绘帟鍋愰埀顒€绠嶉崕閬嶅箟濡妯勯梺鍝勬湰缁嬫垿锝炲┑瀣ㄩ柕澶堝灩娴滅偓淇婇妶鍛櫣缂佺姷濞€閺岀喖宕滆閸旓箓鏌嶈閸撱劎绮婚幘缈犵箚闁归棿绀侀悡娑樏归敐鍕劅婵¤尪顫夋穱濠囨倷椤忓嫧鍋撻弽褜鍟呭┑鐘宠壘绾惧鏌熼崜褏甯涢柣鎾冲暟閹茬顭ㄩ崼婵堫槶濠殿喗顭堝▔娑氱矆婢舵劖鐓涚€广儱楠搁獮鏍ь浖閸涘瓨鈷戦柛娑橈攻婢跺嫰鏌涢妸銈呭祮闁挎繄鍋ゅ畷銊р偓娑欘焽閸樻捇姊洪懞銉冾亪藝娴兼潙鍚归柍褜鍓熷娲焻閻愯尪瀚板褜鍣ｉ弻锝堢疀閺冨倻鐤勯梺绯曟櫇閸嬨倝鐛€ｎ喗鏅滅紓浣股戝▍鎾绘⒒娴ｈ棄鍚归柛鐘崇墵閵嗗倿鏁傞悾宀€褰鹃梺鍝勬储閸ㄦ椽鍩涢幒妤佺厱閻忕偛澧介幊鍛存煕閺傝法效闁诡喗顨堢划顓炩槈濡娅ч梺娲诲幗閻熲晠寮婚悢琛″亾閻㈡鐒剧悮銉╂⒑缁嬫鍎愰柟鍛婃倐閿濈偛鈹戠€ｎ偄浜楅柟鑲╄ˉ濡狙囧箯椤愶附鈷掑ù锝堟鐢稑銆掑顓ф疁鐎规洑鍗冲浠嬵敆閸屾瑧鑳洪梻鍌氬€风粈渚€骞夐埄鍐懝婵°倕鎳庣粣妤呮煙閻戞﹩娈旂紒鐙€鍨崇槐鎾存媴鐠囷紕鍔风紓浣叉閸嬫捇姊绘担鍦菇闁搞劏妫勯…鍥槻闁烩槅鍘奸埞鎴炲箠闁稿﹥顨嗛幈銊╁箻椤旇偐鐤呴梺鎸庣☉鐎氼噣鎯屾径灞稿亾楠炲灝鍔氶柟宄邦儔閹繝濡烽埡鍌滃幈闂佸搫娲㈤崝宀勭嵁濡眹浜滈柡鍐ｅ亾婵炶尙鍠栧濠氭晲婢跺浜归梺鍛婄懃椤︻垰顔忛崼鏇熲拺闁告稑锕ｇ欢閬嶆煕閵娿儱鑸归柍缁樻崌瀹曘劍绻濇担铏圭暰闂備胶绮崝锔界濠婂牆鐒垫い鎴炲劤閳ь剚绻傞悾鐑藉箣閿曗偓缁€瀣亜閹捐泛鏋庨幖鏉戯躬濮婃椽鎮烽柇锕€娈舵繝娈垮櫍濞佳囨偩閻戣棄鐭楀璺虹灱閻﹀牓姊婚崒姘卞缂佸甯￠弫宥咁吋閸℃瑧顔曢柣蹇撶箲閻楁鈻嶉崱娑欑厪闁搞儜鍐句純濡ょ姷鍋炵敮锟犵嵁鐎ｎ亖鏀介柛鎰╁妺婢规洘绻濋悽闈浶ｉ柤褰掔畺瀹曟瑩鎮╃紒妯煎幈闂侀€涘嵆濞佳囧几閻斿吋鐓冮梺鍨儏缁楁帡妫佹径鎰叆婵犻潧妫涙晶杈ㄧ箾閸忕厧濮嶉柡灞剧〒閳ь剨缍嗛崜娆撳煝閸儲鐓涢悘鐐插⒔閳洟鏌熼娑欘棃濠殿喒鍋撻柟楦挎珪缁傛帡濮€閵堝棗鈧敻鎮峰▎蹇擃仾缂佲偓閸愵亞纾兼い鏃囧亹閻掓悂鏌ㄥ┑鍫濅粶闁宠鍨归埀顒婄秵閸嬪嫬鈻撳┑瀣拻闁稿本姘ㄦ晶娑氱磼鐎ｎ偄濮夐柤娲憾瀹曞綊顢曢妶鍥╂闂備焦鐪归崹钘夘焽瑜庣粋鎺楁晝閸屾稓鍘卞銈庡幗閸ㄧ敻鎮橀敂閿亾鐟欏嫭绀堥柟铏尵閸欏懎顪冮妶鍛閻庢凹鍣ｉ弫宥呪枎閹剧补鎷洪梺鍛婃尰瑜板啯绂嶅┑鍥╃闁告瑥顧€閼板潡鏌熼鍡欑瘈闁诡喓鍨藉畷妤呮嚃閳轰礁绠ラ梻鍌氬€风欢锟犲矗韫囨洜涓嶉柟杈惧瀹撲線鏌″搴′簽缁炬儳銈搁弻鐔虹磼濡桨鍒婇梺缁樻尭閵堟悂寮婚敍鍕勃闁伙絽鐫楄閺岋紕浠︾拠鎻掝瀷闂侀€炲苯澧剧紓宥呮瀹曟垿鍩€椤掑嫭鐓欑€瑰嫮澧楃拹锛勭磼鏉堛劍灏伴柟宄版嚇閹煎綊鎮烽幍顕呭仹闂傚倷鑳堕崕鐢稿疾閳哄懎鍨傞柛顐ｆ礃閸嬫ɑ銇勯弴妤€浜惧Δ鐘靛仦椤洭骞忛悩缁樺殤妞ゆ帒顦弫钘夆攽閻樺灚鏆╁┑鐐╁亾濠电偘鍖犻崗鐐☉閳诲酣骞欓崘鈺傛珦闁诲骸绠嶉崕閬嵥囨导鏉戠厱闁圭儤鍨埀顒佸笒椤繈鏁愰崨顒€顥氶梻鍌欑窔閳ь剛鍋涢懟顖涙櫠娴煎瓨鐓曢悗锝庡亞濞叉挳鏌熷畷鍥ф灈妞ゃ垺绋戦埥澶娾枎閹邦喖濞囧┑锛勫亼閸婃牠鎮уΔ鍐煓闊洦姊婚々鍙夌節婵犲倻澧涢柣鎾寸洴閺屾盯骞囬崜浣稿煂婵炲瓨绮撶粻鏍ь潖閾忓湱纾兼慨妤€妫欓悾鍏肩節濞堝灝鏋熼柟鍛婂▕楠炲啯銈ｉ崘鈺冨弳闁诲函缍嗘禍鍫曞吹閵堝鈷戦柛婵嗗濡插綊鏌涢妸鈺傛锭闁伙絿鏁诲畷鍗炩槈濞嗗本瀚肩紓鍌氬€烽悞锕傚Υ閻愬搫绠ｉ柨婵嗗暕濮规姊洪崨濠傚Е闁哥姵顨婂畷鎰崉鐞涒剝鏂€闂佺粯蓱瑜板啴鍩€椤掑倹鏆€殿喓鍔嶇粋鎺斺偓锝庡亞閸樹粙姊鸿ぐ鎺戜喊闁告挻鐟ч惀顏囶槼闁靛洤瀚版俊鐤槻濞寸娀浜堕弻锛勪沪閸撗勫垱閻庢鍣崳锝夊春閳ь剚銇勯幒鎴濃偓褰掑汲濠婂牊鐓熼柟閭﹀墯閹牏绱掗悩宕囧弨闁哄本娲濈粻娑氣偓锝庝簴閸嬫捇寮撮姀銏犵ウ闂侀潧绻嗛埀顒冨皺缁犳艾顪冮妶鍡欏缂佽绉瑰畷闈涒枎閹存柨浜炬繛鍫濈仢閺嬫盯鏌ｈ箛鏃傜疄闁糕晝鍋ら獮瀣晜閽樺姹楃紓鍌氬€烽悞锕佹懌闂備焦顑欓崢濂稿煘閹达箑鐓￠柛鈩冦仦缁ㄥジ姊洪悜鈺傛珦闁搞劌鐖奸悰顕€宕橀钘夆偓鐑芥煟閹寸伝顏堝焵椤掑嫭鏁遍柕鍥у楠炲鏁愰崱娆嶁偓濠勭磽娴ｅ搫校鐎光偓缁嬫娼栨繛宸簻閹硅埖銇勯幘璺盒㈢紒妤嬬節閺岋綁濮€閳轰胶浠氶梺鍝勬噽婵炩偓妤犵偛鍟撮弫鎾绘偐閼碱剦妲梻浣侯焾缁绘帡宕㈣椤曪綁骞愭惔锝囩槇闂佹眹鍨藉褍鐡梻浣告憸閸ｃ儵宕归懜鍏哥箚闂傚牊绋堥弨浠嬫煕閵夈劌鐓愭い鏃€妫冨铏圭磼濡搫顫岄悗娈垮枦閸╂牕顕ラ崟顖氱妞ゆ牗绋撻崢鐢告倵閻熸澘顏褎顨婂畷鎶芥倻濡晲绨婚梺鎸庢⒐閸庢娊銆傛總鍛婄厵妞ゆ牗绋掗ˉ鍫ユ煛娴ｇ懓濮嶇€规洖宕灒闁煎鍊曠紞鏃堟⒒閸屾艾鈧悂宕愰幖浣哥９闁归棿绀佺壕褰掓煟閹达絽袚闁搞倕瀚伴弻娑㈠箻閼碱剦妲梺鎼炲妽缁诲啴濡甸崟顖氬唨妞ゆ劦婢€濞岊亪姊虹粙娆惧剰妞ゆ垵顦靛濠氭晸閻樿尙鍊為梺闈涱槶閸庤櫕绂掓總鍛婄厱闁靛鍎洪崵鐔虹磼鏉堛劍灏伴柟宄版嚇瀹曟粓骞撻幒鎴濐棊濠碉紕鍋戦崐鏍垂閸洘鍋￠柍杞拌兌閺?JSON
		return ResponseEntity.ok(response);
	}

	private String resolveVisibleServicePrice(Service service, HttpSession session) {
		if (service == null) {
			return "";
		}
		if (isPartnerOrClient(session) && StringUtils.isNotBlank(service.getDescription())) {
			return service.getDescription();
		}
		if (service.getPrice() != null) {
			return formatCentPrice(service.getPrice());
		}
		return StringUtils.defaultString(service.getDescription());
	}

	private float resolveUnitPriceForSelection(Service service, HttpSession session) {
		if (service == null) {
			return 0f;
		}
		if (isPartnerOrClient(session) && StringUtils.isNotBlank(service.getDescription())) {
			return parseDisplayedPrice(service.getDescription());
		}
		if (service.getPrice() != null) {
			return service.getPrice() / 100f;
		}
		return parseDisplayedPrice(service.getDescription());
	}

	private float parseDisplayedPrice(String rawPrice) {
		if (StringUtils.isBlank(rawPrice)) {
			return 0f;
		}
		String token = rawPrice.split("/")[0].trim();
		token = token.replaceAll("[^0-9.\\-]", "");
		if (StringUtils.isBlank(token)) {
			return 0f;
		}
		try {
			return Math.round(Float.parseFloat(token) * 100) / 100f;
		} catch (NumberFormatException e) {
			return 0f;
		}
	}

	private boolean isPartnerOrClient(HttpSession session) {
		String level = resolveSessionSecurityLevel(session);
		if (StringUtils.isBlank(level)) {
			return false;
		}
		return CC.ROLE_PARTNER.equalsIgnoreCase(level) || CC.ROLE_CLIENT.equalsIgnoreCase(level);
	}

	private boolean isManagerOrAdmin(HttpSession session) {
		String level = resolveSessionSecurityLevel(session);
		if (StringUtils.isBlank(level)) {
			return false;
		}
		return CC.ROLE_MANAGER.equalsIgnoreCase(level) || CC.ROLE_ADMIN.equalsIgnoreCase(level);
	}

    private String resolveSessionSecurityLevel(HttpSession session) {
		UserAccount currentUser = (UserAccount) session.getAttribute(CC.currentUser);
		if (currentUser != null && !StringUtils.isBlank(currentUser.getSecuritylevel())) {
			return currentUser.getSecuritylevel();
		}
		Object userRole = session.getAttribute(CC.user_role);
		if (userRole != null) {
			String role = userRole.toString();
			if (role.contains(CC.ROLE_ADMIN)) {
				return CC.ROLE_ADMIN;
			}
			if (role.contains(CC.ROLE_MANAGER)) {
				return CC.ROLE_MANAGER;
			}
			if (role.contains(CC.ROLE_PARTNER)) {
				return CC.ROLE_PARTNER;
			}
			if (role.contains(CC.ROLE_CLIENT)) {
				return CC.ROLE_CLIENT;
			}
		}
		return null;
	}

    private void prepareLuckyWheelState(Model model, HttpServletRequest request, HttpServletResponse response, Person person,
            int[] menuIdxes) {
        if (person == null || !isHomePage(menuIdxes) || !isLuckyWheelEligibleRole(request.getSession())) {
            model.addAttribute("showLuckyWheel", false);
            model.addAttribute("showLuckyPrizeReminder", false);
            return;
        }
        model.addAttribute("luckyWheelLang", taoUtil.getLangPrfWithDefault(request));

        UserAccount currentUser = (UserAccount) request.getSession().getAttribute(CC.currentUser);
        if (currentUser != null) {
            attachPendingLuckyPrizeToUser(request, response, person, currentUser, true);
        }

        List<LuckyPrizeOption> prizeOptions = buildLuckyPrizeOptions(person);
        if (currentUser != null) {
            luckyDrawRecordService.expireOldPendingPrizes(person, currentUser);
        }
        LuckyDrawRecord pendingRecord = luckyDrawRecordService.findPendingPrize(person, currentUser);
        if (pendingRecord != null) {
            model.addAttribute("showLuckyWheel", false);
            model.addAttribute("showLuckyPrizeReminder", true);
            model.addAttribute("luckyPrizeName", pendingRecord.getPrizeName());
            model.addAttribute("luckyPrizeImageUrl", resolveLuckyPrizeImageUrl(prizeOptions, pendingRecord.getPrizeCode()));
            model.addAttribute("luckyPrizeNeedsAuth", false);
            return;
        }

        String pendingPrizeCode = resolvePendingLuckyPrizeCode(request, response);
        if (StringUtils.isNotBlank(pendingPrizeCode)) {
            model.addAttribute("showLuckyWheel", false);
            model.addAttribute("showLuckyPrizeReminder", true);
            model.addAttribute("luckyPrizeName", resolveLuckyPrizeLabel(prizeOptions, pendingPrizeCode));
            model.addAttribute("luckyPrizeImageUrl", resolveLuckyPrizeImageUrl(prizeOptions, pendingPrizeCode));
            model.addAttribute("luckyPrizeNeedsAuth", true);
            return;
        }

        if (currentUser != null && luckyDrawRecordService.hasSpinToday(person, currentUser)) {
            model.addAttribute("showLuckyWheel", false);
            model.addAttribute("showLuckyPrizeReminder", false);
            return;
        }

        if (!isLuckyWheelEnabled(person) || prizeOptions.isEmpty()) {
            model.addAttribute("showLuckyWheel", false);
            model.addAttribute("showLuckyPrizeReminder", false);
            return;
        }

        model.addAttribute("showLuckyWheel", true);
        model.addAttribute("showLuckyPrizeReminder", false);
        model.addAttribute("luckyWheelPrizeCards", buildLuckyWheelPrizeCards(prizeOptions));
    }

    private boolean isHomePage(int[] menuIdxes) {
        return menuIdxes != null && menuIdxes.length >= 3 && menuIdxes[0] == 1 && menuIdxes[1] == 0 && menuIdxes[2] == 0;
    }

    private boolean isLuckyWheelEligibleRole(HttpSession session) {
        String role = resolveSessionSecurityLevel(session);
        return role == null || CC.ROLE_CLIENT.equals(role) || CC.ROLE_PARTNER.equals(role);
    }

    private boolean isLuckyWheelEnabled(Person person) {
        Customize customize = customizeService.findCustomizeByKeyAndPerson(CC.show_lucky_wheel, person);
        if (customize == null || StringUtils.isBlank(customize.getCusValue())) {
            return false;
        }
        String value = customize.getCusValue().trim();
        return CC.TRUE.equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    private String chooseLuckyPrizeCode(HttpSession session, List<LuckyPrizeOption> prizeOptions) {
        if (prizeOptions == null || prizeOptions.isEmpty()) {
            return "";
        }
        int totalWeight = 0;
        for (LuckyPrizeOption option : prizeOptions) {
            totalWeight += Math.max(DEFAULT_LUCKY_WEIGHT, option.weight);
        }
        Random random = new Random(session.getId().hashCode() ^ System.currentTimeMillis());
        int picked = random.nextInt(totalWeight);
        int cursor = 0;
        for (LuckyPrizeOption option : prizeOptions) {
            cursor += Math.max(DEFAULT_LUCKY_WEIGHT, option.weight);
            if (picked < cursor) {
                return option.code;
            }
        }
        return prizeOptions.get(0).code;
    }

    private String resolveLuckyPrizeLabel(List<LuckyPrizeOption> prizeOptions, String prizeCode) {
        LuckyPrizeOption option = findLuckyPrizeOption(prizeOptions, prizeCode);
        return option == null ? prizeCode : option.label;
    }

    private String resolveLuckyPrizeImageUrl(List<LuckyPrizeOption> prizeOptions, String prizeCode) {
        LuckyPrizeOption option = findLuckyPrizeOption(prizeOptions, prizeCode);
        return option == null ? "" : option.imageUrl;
    }

    private List<Map<String, String>> buildLuckyWheelPrizeCards(List<LuckyPrizeOption> prizeOptions) {
        List<Map<String, String>> cards = new ArrayList<>();
        for (LuckyPrizeOption option : prizeOptions) {
            Map<String, String> card = new LinkedHashMap<>();
            card.put("code", option.code);
            card.put("label", option.label);
            card.put("hint", option.hint);
            card.put("imageUrl", option.imageUrl);
            card.put("weight", String.valueOf(option.weight));
            cards.add(card);
        }
        return cards;
    }

    private List<LuckyPrizeOption> buildLuckyPrizeOptions(Person person) {
        List<LuckyPrizeOption> options = new ArrayList<>();
        if (person == null) {
            return options;
        }

        Customize customize = customizeService.findCustomizeByKeyAndPerson(CC.lucky_wheel_prizes, person);
        String rawConfig = customize == null ? null : customize.getCusValue();
        if (StringUtils.isBlank(rawConfig)) {
            return options;
        }

        Map<String, Service> serviceByBarcode = new LinkedHashMap<>();
        List<Service> services = serviceService.findServiceByPerson(person);
        if (services != null) {
            for (Service service : services) {
                if (service == null || StringUtils.isBlank(service.getC2()) || StringUtils.isBlank(service.getC1())) {
                    continue;
                }
                String barcode = service.getC2().trim();
                if (!serviceByBarcode.containsKey(barcode)) {
                    serviceByBarcode.put(barcode, service);
                }
            }
        }

        Set<String> seenCodes = new LinkedHashSet<>();
        for (String token : rawConfig.split("[,;\\r\\n]+")) {
            String entry = StringUtils.trimToEmpty(token);
            if (StringUtils.isBlank(entry)) {
                continue;
            }
            String[] parts = entry.split(":");
            String barcode = StringUtils.trimToEmpty(parts[0]);
            if (StringUtils.isBlank(barcode) || seenCodes.contains(barcode)) {
                continue;
            }
            int weight = DEFAULT_LUCKY_WEIGHT;
            if (parts.length > 1 && StringUtils.isNotBlank(parts[1])) {
                try {
                    weight = Math.max(DEFAULT_LUCKY_WEIGHT, Integer.parseInt(parts[1].trim()));
                } catch (Exception ignore) {
                    weight = DEFAULT_LUCKY_WEIGHT;
                }
            }
            Service service = serviceByBarcode.get(barcode);
            LuckyPrizeOption option = new LuckyPrizeOption();
            option.code = barcode;
            option.weight = weight;
            option.label = service != null && StringUtils.isNotBlank(service.getName()) ? service.getName() : barcode;
            option.hint = weight + "% chance";
            option.imageUrl = service != null ? "getImage/service_" + service.getC1() + "_thum" : "";
            options.add(option);
            seenCodes.add(barcode);
        }
        return options;
    }

    private LuckyPrizeOption findLuckyPrizeOption(List<LuckyPrizeOption> options, String prizeCode) {
        if (options == null || StringUtils.isBlank(prizeCode)) {
            return null;
        }
        for (LuckyPrizeOption option : options) {
            if (option != null && StringUtils.equalsIgnoreCase(option.code, prizeCode)) {
                return option;
            }
        }
        return null;
    }

    private String resolvePendingLuckyPrizeCode(HttpServletRequest request, HttpServletResponse response) {
        Long pendingAt = resolvePendingLuckyPrizeTimestamp(request);
        if (pendingAt != null && pendingAt.longValue() < startOfTodayMillis()) {
            clearPendingLuckyPrize(request, response);
            return null;
        }
        Object pendingPrize = request.getSession().getAttribute(CC.lucky_pending_prize);
        if (pendingPrize != null) {
            return pendingPrize.toString();
        }
        Cookie cookie = findCookie(request, CC.lucky_pending_prize_cookie);
        if (cookie == null || StringUtils.isBlank(cookie.getValue())) {
            return null;
        }
        try {
            String prizeCode = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8.name());
            request.getSession().setAttribute(CC.lucky_pending_prize, prizeCode);
            return prizeCode;
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolvePendingLuckyPrizeTimestamp(HttpServletRequest request) {
        Object pendingAt = request.getSession().getAttribute(CC.lucky_pending_prize_at);
        if (pendingAt != null) {
            try {
                return Long.parseLong(pendingAt.toString());
            } catch (Exception ignored) {
            }
        }
        Cookie cookie = findCookie(request, CC.lucky_pending_prize_cookie_time);
        if (cookie != null && StringUtils.isNotBlank(cookie.getValue())) {
            try {
                return Long.parseLong(cookie.getValue());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private long startOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void storePendingLuckyPrize(HttpServletRequest request, HttpServletResponse response, String prizeCode) {
        request.getSession().setAttribute(CC.lucky_pending_prize, prizeCode);
        request.getSession().setAttribute(CC.lucky_pending_prize_at, String.valueOf(System.currentTimeMillis()));
        try {
            Cookie prizeCookie = new Cookie(CC.lucky_pending_prize_cookie,
                    URLEncoder.encode(prizeCode, StandardCharsets.UTF_8.name()));
            prizeCookie.setMaxAge(60 * 60 * 24 * 30);
            prizeCookie.setPath("/");
            response.addCookie(prizeCookie);

            Cookie timeCookie = new Cookie(CC.lucky_pending_prize_cookie_time, String.valueOf(System.currentTimeMillis()));
            timeCookie.setMaxAge(60 * 60 * 24 * 30);
            timeCookie.setPath("/");
            response.addCookie(timeCookie);
        } catch (Exception ignored) {
        }
    }

    private void clearPendingLuckyPrize(HttpServletRequest request, HttpServletResponse response) {
        request.getSession().removeAttribute(CC.lucky_pending_prize);
        request.getSession().removeAttribute(CC.lucky_pending_prize_at);

        Cookie prizeCookie = new Cookie(CC.lucky_pending_prize_cookie, "");
        prizeCookie.setMaxAge(0);
        prizeCookie.setPath("/");
        response.addCookie(prizeCookie);

        Cookie timeCookie = new Cookie(CC.lucky_pending_prize_cookie_time, "");
        timeCookie.setMaxAge(0);
        timeCookie.setPath("/");
        response.addCookie(timeCookie);
    }

    private Cookie findCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    private void attachPendingLuckyPrizeToUser(HttpServletRequest request, HttpServletResponse response, Person person,
            UserAccount userAccount, boolean clearPendingState) {
        if (person == null || userAccount == null) {
            return;
        }
        LuckyDrawRecord pendingRecord = luckyDrawRecordService.findPendingPrize(person, userAccount);
        if (pendingRecord != null) {
            if (clearPendingState) {
                clearPendingLuckyPrize(request, response);
            }
            return;
        }

        if (luckyDrawRecordService.hasSpinToday(person, userAccount)) {
            if (clearPendingState) {
                clearPendingLuckyPrize(request, response);
            }
            return;
        }

        String pendingPrizeCode = resolvePendingLuckyPrizeCode(request, response);
        if (StringUtils.isBlank(pendingPrizeCode)) {
            return;
        }

        luckyDrawRecordService.createWinningRecord(person, userAccount, pendingPrizeCode,
                resolveLuckyPrizeLabel(buildLuckyPrizeOptions(person), pendingPrizeCode));
        if (clearPendingState) {
            clearPendingLuckyPrize(request, response);
        }
    }

    private static class LuckyPrizeOption {
        private String code;
        private String label;
        private String hint;
        private String imageUrl;
        private int weight;
    }

	private String formatCentPrice(Integer priceInCent) {
		if (priceInCent == null) {
			return "";
		}
		return String.format(java.util.Locale.US, "%.2f", priceInCent / 100.0);
	}

	@RequestMapping(value = "/{client}/textcontentJSon/{keyStr}", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<String> showJsonByKey1(@PathVariable(CC.CLIENT) String client,
			@PathVariable("keyStr") String keyStr, HttpServletRequest request) {

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return showJsonByKey(keyStr, request);
	}

	@RequestMapping(value = "textcontentJSon/{keyStr}", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<String> showJsonByKey(@PathVariable("keyStr") String keyStr, HttpServletRequest request) {

		makesureSessionInitialized(request);// if first time visit, session is till empty, then initialise it.

		Object langPrf = taoUtil.getLangPrfWithDefault(request);
		Person person = taoUtil.getCurPerson(request);
		TextContent textContent = textContentService.findContentsByKeyAndPerson(langPrf + keyStr, person);
		if (textContent == null) {
			textContent = new TextContent();
			textContent.setPosInPage(langPrf + keyStr);
		}

		textContent.setRecordStatus(1); // This property could be used to present the current selected items, while the
										// try failed, because the updateStatus method not unified.
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");
		return new ResponseEntity<String>(textContent.toJson(), headers, HttpStatus.OK);
	}

	@RequestMapping(value = "/{client}/showNotes/{keyStr}", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<String> showNote(@PathVariable(CC.CLIENT) String client,
			@PathVariable("keyStr") String keyStr, HttpServletRequest request, HttpServletResponse response) {

		if (taoUtil.hasNotLoggedIn(request)) {
			dirtFlagCommonText = taoUtil.switchClient(request, client);
		}
		return showNote(keyStr, request);
	}

	@RequestMapping(value = "showNotes/{keyStr}", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<String> showNote(@PathVariable("keyStr") String keyStr, HttpServletRequest request) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");
		return new ResponseEntity<String>(taoUtil.getNoteJson(request, keyStr), headers, HttpStatus.OK);
	}

	@RequestMapping(value = "updateNotes/{keyStr}", headers = "Accept=application/json", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> updateNotes(@PathVariable("keyStr") String keyStr, HttpServletRequest request) {

		String langPrf = taoUtil.getLangPrfWithDefault(request);
		String allNotesStr = (String)request.getSession().getAttribute(langPrf + CC.notes);

		String noteIdx = request.getParameter("nodeId");
		
		Person person = taoUtil.getCurPerson(request);
		Service service = serviceService.findServiceByCatalogAndPerson(keyStr, person);

		//get all current supported note into an array.
		String[] noteIdsOfCurService = service.getC2() != null ? service.getC2().split(";") : new String[0];

		//get new all current supported note into a string.
		StringBuilder newNoteIds = new StringBuilder();
		
		UserAccount curUser = (UserAccount) request.getSession().getAttribute(CC.currentUser);
		if(curUser != null && (CC.ROLE_MANAGER.equals(curUser.getSecuritylevel()) || CC.ROLE_ADMIN.equals(curUser.getSecuritylevel()))) {
			boolean isAddingNewNote = true;
			for(int i = 0; i < noteIdsOfCurService.length; i++) {
				if(!noteIdsOfCurService[i].equals(noteIdx)) {		//if not equals this one then add this one into the new string.
					newNoteIds.append(noteIdsOfCurService[i]).append(";");
				}else {								//if equals to this one, then don't add this one. while, turn down the isAdding flag.
					isAddingNewNote = false;
				}
			}
			if(isAddingNewNote) {
				newNoteIds.append(noteIdx).append(";");
			}
			
			//save new c2 into db.
			service.setC2(newNoteIds.toString());
			serviceService.persist(service);
			
			//generate a service for front end display, c2 is all displayable notes, c3 is selected idx in c2.
			service.setC2(allNotesStr);
			String[] allNotes = allNotesStr.split(";");
			StringBuilder visibleNoteStr = new StringBuilder();
			for (int i = 0; i < allNotes.length; i++) { //from 0 to allNotes.length - 1, if it appear in c2 string (not the temperal C2, but the content in sb) 
				visibleNoteStr.append((newNoteIds.toString().startsWith(i + ";") || newNoteIds.indexOf(";" + i + ";") > 0) ? "1;" : "0;");
			}
			service.setC3(visibleNoteStr.toString());

		}else {//for customer level, will change the note selections status maintained in session.

			List<List<Integer>> selectedNote = (List<List<Integer>>) request.getSession().getAttribute(CC.selectedNotes);
			if(selectedNote == null) {
				selectedNote = new ArrayList<List<Integer>>();
			}
			//fetch out the selected note status of the service item which matching to the parameter: keyStr
			List<Integer> selectedNotesOfCurService = new ArrayList<Integer>();
			Object rawSelectedItems = request.getSession().getAttribute(CC.selectedItems);
			if (rawSelectedItems instanceof Map<?, ?>) {
				int index = 0;
				for (Object selectedItemKey : ((Map<?, ?>) rawSelectedItems).keySet()) {
					if (keyStr.equals(selectedItemKey)) {
						selectedNotesOfCurService = taoUtil.initAndReturnSelectedNote(selectedNote, index);
						break;
					}
					index++;
				}
			} else if (rawSelectedItems instanceof String) {
				String[] selectedItems = ((String) rawSelectedItems).split(",");
				for(int i = 0; i < selectedItems.length; i++) {
					if(keyStr.equals(selectedItems[i])){
						selectedNotesOfCurService = taoUtil.initAndReturnSelectedNote(selectedNote, i); break;
					}
				}
			}
			
			//update the note selection status list maintained in seesion.
			for(int i = 0; i < noteIdsOfCurService.length; i++) {
				if(selectedNotesOfCurService.size() == i) {
					selectedNotesOfCurService.add(0);
				}
			}
			Integer curValue = selectedNotesOfCurService.get(Integer.valueOf(noteIdx));
			selectedNotesOfCurService.set(Integer.valueOf(noteIdx), 1 - curValue);
					
			//save back to session
			request.getSession().setAttribute(CC.selectedNotes,selectedNote);
			
			//generate a service for front end display.
			StringBuilder allAvailableNotes = new StringBuilder();
			if(service.getC2() != null) {
				String allNotes = (String) request.getSession().getAttribute(taoUtil.getLangPrfWithDefault(request) + CC.notes);
				if(allNotes == null) {
					allNotes = "";
				}else {
                    allNotes = allNotes.replace(",", ";").trim();
					if(!allNotes.endsWith(";")) {
						allNotes += ";";
					}
				}
				String[] notesStrAry = allNotes.split(";");
				
				String[] noteIdxs = service.getC2().split(";"); //c2 saves only the index, because need to distinguish language.
				for(String idx : noteIdxs) {
					try {		//some time, the c2 might be like "null;null;1;4;6;", so need to ignore the case when TODO: remove this check when bug fixed.
						int i = Integer.valueOf(idx);
						if(i < notesStrAry.length) {
							allAvailableNotes.append(notesStrAry[i]);
							allAvailableNotes.append(";");
						}
					}catch(Exception e) {}
				}
			}
			service.setC2(allAvailableNotes.toString());	//temperally replace the c2 with language string for front end to display.
			
			StringBuilder visibleNoteStr = new StringBuilder();
			for (int i = 0; i < selectedNotesOfCurService.size(); i++) { //from 0 to allNotes.length - 1, if it appear in c2 string (not the temperal C2, but the content in sb) 
				visibleNoteStr.append(selectedNotesOfCurService.get(i)).append(";");
			}
			service.setC3(visibleNoteStr.toString());
		}
		//prepare visible list:  the c2 will be used to save all available notes, and c3 will be used to save visible flags..

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");
		return new ResponseEntity<String>(service.toJson(), headers, HttpStatus.OK);
	}
	
	@RequestMapping(value = CC.resetQRCode, method = RequestMethod.POST, produces = "text/html")
	public String generateQRCode(Model model, @RequestParam(CC.position) String menuIdx, HttpServletRequest request,
			HttpServletResponse response) {
		String filePath = "QRCode_" + menuIdx;

		String text = composeQRCodeText(request, menuIdx);

		Person person = taoUtil.getCurPerson(request);

		generateQRCode(request, filePath, text, person);
		return buildPageForMenu(model, request, response, null);
	}

	private void generateQRCode(HttpServletRequest request, String filePath, String text, Person person) {
		if (StringUtils.isBlank(text)) {
			TaoDebug.error("non-null parameter expected! param name : {}", "text");
			return;
		}
		Hashtable<EncodeHintType, String> hints = new Hashtable<EncodeHintType, String>();
		hints.put(EncodeHintType.CHARACTER_SET, "utf-8"); // routine code.

		MediaUpload media = mediaUploadService.findMediaByKeyAndPerson(filePath, person);
		if (media == null) {
			media = new MediaUpload();
		}

		String format = ".jpg";
		media.setContentType(format);
		media.setFilepath(filePath);

		try {
			BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300, hints);
			BufferedImage image = TaoImage.toBufferedImage(bitMatrix);
			// the following line could die if you debug over it:(
			image = TaoImage.resizeAndRotateImage(request, image, media.getFilepath());
			byte[] tContent = TaoImage.getByteFormateImage(image, format);

			MediaUpload logo = mediaUploadService.findMediaByKeyAndPerson("logo", person);
			byte[] imageBytes = null;
			if (logo != null && logo.getContent() != null) {
				imageBytes = logo.getContent();
			} else if(logo != null){
				String diskFilePath = mediaUploadService.generateFilePathForPerson(person, logo.getFilepath(), logo.getContentType()); // 濠电姷鏁告慨鐑藉极閸涘﹥鍙忛柣鎴ｆ閺嬩線鏌涘☉姗堟敾闁告瑥绻橀弻锝夊箣閿濆棭妫勯梺鍝勵儎缁舵岸寮诲☉妯锋婵鐗婇弫楣冩⒑閸涘﹦鎳冪紒缁橈耿瀵鏁愭径濠勵吅闂佹寧绻傚Λ顓炍涢崟顖涒拺闁告繂瀚烽崕搴ｇ磼閼搁潧鍝虹€殿喛顕ч埥澶娢熼柨瀣垫綌婵犳鍠楅〃鍛存偋婵犲洤鏋佸Δ锝呭暞閳锋垿鏌涘☉姗堝姛闁瑰啿鍟扮槐鎺旂磼濮楀牐鈧法鈧鍠栭…鐑藉极閹邦厼绶炲┑鐘插閸氬懘姊绘担鐟邦嚋缂佽鍊歌灋妞ゆ挾鍊ｅ☉銏犵妞ゆ牗绋堥幏娲⒑閸涘﹦绠撻悗姘卞厴瀹曟洘鎯旈敐鍥╋紲闂佸吋鎮傚褔宕搹鍏夊亾濞堝灝鏋涙い顓犲厴楠炲啴濮€閵堝懐顦ч柣蹇撶箲閻楁鈧矮绮欏铏规嫚閺屻儱寮板┑鐐板尃閸曨厾褰炬繝鐢靛Т娴硷綁鏁愭径妯绘櫓闂佸憡鎸嗛崪鍐簥闂傚倷鑳剁划顖炲礉閿曞倸绀堟繛鍡樻尭缁€澶愭煏閸繃顥撳ù婊勭矋閵囧嫰骞樼捄鐩掋垽鏌涘Ο渚殶闁逞屽墯椤旀牠宕伴弽顓涒偓锕傛倻閽樺鐎梺鍦濠㈡ê顔忓┑瀣厱閻忕偛澧介惌濠囨煛鐎ｎ亪鍙勯柡灞界Ф閹风娀寮婚妷銉ュ強婵°倗濮烽崑娑樜涘鍩跺洭鎼归鐘辩盎闂佸搫鍊搁悘婵嬵敂椤愩倗纾奸弶鍫涘妽瀹曞瞼鈧娲樼敮鎺楀煝鎼淬劌绠抽柟瀛樼箓閼垫劖绻濋悽闈浶ｆい鏃€鐗犲畷鏉课旈崨顓狀唶闂佽鍎煎Λ鍕不閺嶃劋绻嗘い鏍ㄧ懆椤掔喐绻涚亸鏍ㄦ珚闁诡喛顫夊顏堝箥椤旀嫎顓㈡⒑閹稿孩绀€闁稿﹤缍婇幃锟犲即閵忥紕鍘搁梺鎼炲劘閸庤鲸淇婃總鍛婄厸閻庯絻鍔岄埀顒佺箞瀵鏁愭径濞⒀囨煕鐏炲墽鐓瑙勬礋濮婂宕掑顑藉亾妞嬪海鐭嗗ù锝夋交閼板潡姊洪鈧粔瀵稿婵犳碍鐓欓柟瑙勫姦閸ゆ瑧绱掗悩鍐插摵闁诡喗锕㈤幃娆戞崉鐞涒剝鐎兼繝鐢靛仦閸垶宕归悷鐗堢函闂傚倷鐒﹂幃鍫曞磿椤曗偓瀵彃鈹戠€ｎ偅娅栧┑鐘绘涧濞层劎绮绘ィ鍐ㄧ骇闁割偅绻傞埛鏃傜磼鐎ｎ厼鍚归柟鍙夋倐瀵爼宕归鑺ヮ唹缂傚倷绀侀崐鍝ョ矓瑜版帒绠栨繛鍡樻惄閺佸棝鏌涚仦鍓х煂婵℃彃娲缁樻媴閸︻厽鑿囬梺鎼炲姀濡嫰鈥﹂崶顏嶆Щ闂佺儵妲呴崢鐣屾崲濠靛鍋ㄩ梻鍫熺◥閸濇顪冮妶鍡樼缂侇喖娴烽崚鎺撶節濮橆剛顔掗柣鐘叉穿鐏忔瑩鎮惧ú顏呪拺閻犳亽鍔岄弸鏂库攽椤旂⒈鍤熺紒顔肩墦瀹曟﹢鍩炴径鍝ョ泿闂傚鍋勫ú锕傚箰婵犳艾闂憸鐗堝笚閻撴盯鎮楅敐搴′簻闁诲繈鍎甸弻宥囨喆閸曨偆浼岄梺璇″枓閺呮繈骞忛悩渚Щ婵炴潙鍚嬮崝娆忣潖閾忓湱纾兼慨妤€妫欓悾璺衡攽閳藉棗浜濈紒瀣崌瀹曟岸骞掑Δ鈧柋鍥煏婢跺牆鍔﹂柛鐐茬埣濮婅櫣绱掑Ο鐑╂嫽濡炪値鍘奸悧鍡涱敋閿濆鍨傛い鏃囶潐閺傗偓闂備礁鐤囧Λ鍕涘Δ鍛€堕柣妯虹－缁犲墽鐥銏╂缂佲檧鍋撻柣搴ゎ潐濞诧箓宕戞繝鍥х鐟滅増甯掔粈鍌氼熆鐠虹尨姊楀瑙勬礋閺岋綁鎮㈤崫銉﹀櫑闁诲孩鍑归崢鍓у垝閸儱鐒垫い鎺戝閸婂灚绻涢崼婵堜虎闁哄绋戦…鑳槻闂佸府缍侀悰顕€骞嬮敃鈧～鍛存煟濮楀棗浜濇い顐㈢Ч濮婃椽妫冨☉姘鳖唺婵犳鍠氶崗姗€銆佸Ο濂芥椽顢旈崨顏呭缂傚倸鍊烽悞锕傛晪婵犳鍠栧ú顓㈠蓟閿濆牏鐤€闁哄倸鐏濋幗鐢告倵鐟欏嫭绀€闁汇倕娲棟閻庨潧鎽滅壕濂稿级閸稑濡介悘蹇庡嵆閺岀喎鐣￠悧鍫濇缂備緡鍠楅悷銉╁煝鎼淬劍鍊锋い鎺嶇贰閸熷牓姊婚崒姘偓鐑芥嚄閸洍鈧箓宕奸妷顔芥櫈闂佸憡鍔﹂崰鏍鐟欏嫮绡€闂傚牊渚楅崕鎴犫偓瑙勬尫缁舵岸寮诲☉銏犵疀闁宠桨绀侀ˉ瀣⒑閸濆嫬鏆欓柣妤€妫楅蹇撯攽閸ャ儰绨婚梺鍝勫暙濞层倛顣块梻浣虹帛缁诲啯绻涙繝鍌ゆ綎闁惧繗顫夐崗婊堟煕濞戝崬骞掓慨瑙勵殜閺岋綁鎮欑€电硶妫ㄩ梺绋挎捣閺佽鐣峰ú顏勭劦妞ゆ帊闄嶆禍婊堟煙閻戞ê鐏ラ柡鍡樼懇閺岋綁濡搁敂濮愪虎闂佸搫澶囬崜婵嬪箯閸涙潙鎹舵い鎾跺剱濞煎嘲鈹戦悜鍥╁埌婵炲眰鍊濋弫鍐敂閸繄鐣哄┑鐐叉閹尖晠寮崒鐐寸厱闁哄洦锚婵＄厧霉濠婂啫鈷旈柟鍙夌摃缁犳盯寮撮悤浣圭稐闂備礁婀遍崑鎾诲礈濮橆剦鐒介柍鍝勬噺閸嬶絽螖閿旇棄顕滄い蹇曞У閵囧嫰濮€閻樺啿娈楀┑顔硷龚濞咃綁骞夐幘顔肩妞ゆ劑鍨圭瑧濠电姷顣藉Σ鍛村磻閸曨厸鍋撳鐓庢灓闁告帗甯楃换婵嗩潩椤掆偓閸炪劑姊洪棃娑辩劸闁稿﹥顨堢划璇差潩閼哥鎷洪梻鍌氱墛缁嬫挻鏅堕弴銏＄厱濠电姴鍊归崯鐐电磼閸屾氨校闁靛牞缍佸畷姗€鍩為悙顒€顏归梺璇叉唉椤煤閺嶎灐娲Ω瑜庨弳婊勭箾閹存瑥鐏柣鎾寸懇閺岋綁鎮㈤崫鍕垫毉闂佺懓鍚嬮崝娆撳蓟閿濆鏅查柛銉ｅ妿閸斿綊姊洪棃娑欏闁告梹鐟╅悰顕€骞掗幋鏃€鏂€闂佹悶鍎崝宥夋偟閿曞倹鈷掗柛灞剧懄椤﹂绱掓鏍ф灓鐎垫澘锕ョ粋鎺斺偓锝庡亜濞堬絽顪冮妶鍡欏⒈闁稿鍋熼埀顒佽壘閸熸挳骞冨Δ鍛櫜閹肩补鍓濋悘宥夋⒑閹惰姤鏁遍悽顖ょ節瀵鈽夊顐ｅ媰闂佸憡鎸嗛崟顐ｇ彲闂傚倷鑳堕…鍫㈡崲閹版澘瀚夋い鎺戝妗呴梺鍛婃处閸ㄥジ寮崶顒佺厽闁规壆澧楀☉褔鏌嶉鍫熸锭妞ゎ亜鍟存俊鍫曞礃閵娧傜棯闂備胶绮〃鍫熸叏閹绢喗鍋╅柣鎴ｆ鎯熼悷婊冾樀瀹曟垿骞橀弬銉︻潔濠电偛鎳撳▍鏇㈠垂閻㈢鐓濈€广儱顦悙濠冦亜閹哄秷顔夐柟閿嬫そ濮婂宕掑鍗烆杸婵炴挻纰嶉〃鍛粹€栨繝鍜佹Щ闂佸疇顫夐崹鍧楀垂妤ｅ啯鎯炴い鎰╁灪閻忓酣姊绘担铏广€婇柡鍛箞瀹曟垵鈽夐姀鈺傛櫍婵犻潧鍊婚…鍫濐啅濠靛洢浜滈煫鍥ㄦ尰閸ｆ椽鏌℃径瀣€愭慨濠勭帛閹峰懘鎼归悷鎵偧闂備焦瀵у銊╁焵椤掍緡鍟忛柛鐘崇洴瀹曟椽寮借閸ゆ鏌涢弴銊ョ仩缂佲偓鐎ｎ偁浜滈柡宥冨妿閵嗘帡鏌涘鈧禍璺侯潖濞差亝顥堟繛鎴炶壘椤ｆ椽姊洪悷鏉挎Щ妞ゆ垵顦悾鐑藉即閵忕姷鐫勯梺閫炲苯澧存鐐茬墦婵℃悂鏁傞崜褏妲囬梻浣侯焾鐞氼偊宕愬Δ鍛Е閻庯綆鍠楅崐鐢告偡濞嗗繐顏紒鈧埀顒勬⒑闂堟稓澧涢柟顔煎€块悰顕€宕橀纰辨綂闂侀潧鐗嗛幊鎰版偩閹惰姤鈷戦柣鐔哄閹牊淇婇锝庢畷缂佸倸绉甸妶锝夊礃閳哄啫骞嶉梻浣虹帛閸ㄦ儼鎽梺鎶芥敱鐢偤鎯€椤忓牆绠氱憸瀣磻閵忋倖鐓涚€光偓閳ь剟宕伴弽顓炶摕闁搞儺鍓氶弲婵嬫煃瑜滈崜鐔奉嚕缁嬪簱妲堟俊顖氱箰缂嶅﹪骞冮鍫濆窛妞ゆ挆鍕灎缂傚倸鍊峰ù鍥ㄣ仈閸濄儲鏆滈柣鎰仛椤洟鏌熼悜妯烘闁绘柨鍚嬮崵鍐煃閸濆嫬鏆炴繝銏＄墵濮婂宕掑▎鎰偘濡炪倖娲橀悧鐘茬暦娴兼潙鍗抽柣妯诲絻閻忓﹤鈹戦悙鍙夘棞缂侇喗鍎抽埥澶愬閻樻妲版俊鐐€ら崢濂割敄閸涱垳绀婂┑鐘叉处閸嬪倹銇勯幒鎴濐仼缂佺姷鏁婚弻鐔兼倻濡崵鍙勭紓浣介哺閻熲晛顫忕紒妯诲缂佹稑顑呭▓鎰版⒑閸濄儱校妞ゃ劌锕俊鎾礃椤旂厧绐涢柡澶婄墕婢х晫绮欒箛鎾斀闁绘绮☉褎銇勯幋鐐插鐎殿噮鍋婂畷鍗炩槈濞嗘垵甯楅柣鐔哥矋缁挸鐣峰鍐ｆ瀻闊洦鎸鹃妶顐ｇ節閻㈤潧啸闁轰礁鎲￠幈銊╂偨閸涘﹤娈為梺鍛婃寙閸曨偄骞戦梻渚€鈧偛鑻晶瀛樻叏婵犲嫮甯涚紒妤冨枛瀹曟儼顦叉い顐熸櫇缁辨挻鎷呴崜鍙壭ㄩ梺杞扮椤兘鐛崘顔芥櫢闁绘ɑ褰冨畵鍡涙⒑缂佹ɑ鈷掔紒缁樼箘閼鸿鲸绻濆顓涙嫼闁荤喐鐟ョ€氼厾娆㈤幓鎺濈唵鐟滃秴霉妞嬪骸鍨濇繛鍡樻尰閸ゆ帡鏌曢崼婵囧窛闁挎稒绮撳娲焻閻愯尪瀚板褍顕埀顒冾潐濞叉﹢鏁冮姀銈冣偓浣糕枎閹炬潙浠奸柣蹇曞仩濡嫮绮诲ú顏呪拻闁稿本鐟︾粊鐗堛亜椤愩埄妲搁柣锝呭槻铻ｉ悶娑掑墲閻忓啫鈹戦悙鏉戠仧闁搞劌缍婂畷娆撴偐瀹曞洨顔曢梺鐟邦嚟閸嬬喖骞婇幇顓犵闁割偅绮庨惌娆撴煛瀹€瀣М妤犵偛娲、妤佸緞婵犲喚鍟€婵犵數濮幏鍐沪閽樺娼界紓鍌欒兌缁垶鎯勯鐐茬畺闁斥晛鍟崕鐔兼煥濠靛棙绁╅柛鐔奉儔閺岋絾鎯旈妶搴㈢秷濠电偛寮堕…鍥礆閹烘洜鐤€婵炲瓨婢樺ú顓㈢嵁濮椻偓椤㈡瑩鎮剧仦钘夋辈濠电姷鏁搁崑鐐哄垂閸洘鍤屽Δ锝呭暙閸戠姵绻涢幋鐐垫噭闁告瑥绻愰埞鎴︽偐閹绘帩浠奸梺鍛婃煥椤戝骞冨Δ鍛仺闂傚牊绋戞禒姗€姊虹€圭媭鍤欓梺甯秮閻涱喚鈧綆鍠楅崑鎰叏濡も偓閻楀繒绮旈鑺ュ弿濠电姴鍟妵婵堚偓瑙勬磸閸斿秶鎹㈠┑瀣妞ゆ巻鍋撴繛鍛灴濮婄粯鎷呴崨濠冨枑闂佸摜濮甸崝鏇⑩€﹂崶褉鏋庨柟鍨暞閺咁亪姊洪幐搴ｇ畵婵炶濡囬埀顒佺绾板秶鎹㈠☉銏犵骇闁规惌鍘奸崜鍗烆渻閵堝骸浜滅紒澶嬫尦閸╃偤骞嬮敂钘夆偓鐑芥煕濞嗗浚妯堟俊顐節濮婃椽宕ㄦ繝鍐ｆ嫻濠电偠灏欓崰搴ㄦ偩瀹勬壋鏀介悗锝庝簻缁愭盯鏌ｆ惔銏⑩姇閼裤倝鏌熼柨瀣仢婵﹤顭峰畷鎺戭潩閸楃儐鏉稿┑鐐存尰绾板秹銆冩繝鍥х畺闁绘劖鐣崑鍛存煕閹般劍娅囬柛妯块哺缁绘繈鎮介棃娴躲垽鏌涢悢鍝勵暭闁哄懎鐖奸幃鐣屽枈濡桨澹曢柣鐔哥懃鐎氼厾绮堥崘鈺冪瘈闁靛繆妲勯懓鍧楁煥濠靛牆浠滈柍瑙勫灩閳ь剨缍嗛崑鍕枔濠靛鈷戞慨鐟版搐閻忓弶绻涙担鍐插枤閸ゆ洘銇勯弮鍌涙珪缂佲檧鍋撶紓浣稿⒔婢ф鎽銈庡亜閿曨亪寮诲☉銏犖╅柍鍝勫€归柨顓㈡⒑娴兼瑧鎮奸柛蹇旓耿楠炲啯绂掔€ｎ偄浠洪梺绋跨箰閹芥粓鎮峰┑瀣拻濞达綀娅ｇ敮娑㈡煕閺冣偓椤ㄥ﹤鐣烽弴銏犖у璺侯儏娴滄鈹戦悙鏉戠仧闁搞劍妞藉畷鎰板醇閺囩喓鍘介梺褰掑亰閸欏酣宕箛娑欑厽闁圭儤鎸搁崝銈嗐亜椤撯€冲姷妞わ箑鐖奸幃妯跨疀閺冨倹鍣伴梺绯曟杹閸嬫挸顪冮妶鍡楀潑闁稿鎹囬弻锝堢疀閺冨洤顥濆銈庡幖閻忔繆鐏掗柣蹇撶箲閻楁洘绂嶉悙顒佸弿婵☆垳鍘х敮鑸电箾閸涱厾效闁哄本鐩崺鐐哄箚瑜屾竟鏇炩攽閿涘嫬浜奸柛濠冪墱閺侇噣骞掑Δ浣规珖闂佹寧鏌ㄦ晶浠嬫儗閹炬番浜滈柡鍐ㄥ€瑰▍鏇犵磼閻樺啿鍔ら棁澶愭煥濠靛棙鍣归柡鍡氶哺缁绘稓浠﹂崒姘ｅ亾濡ゅ啫鍨濋柛顐ゅ枎缁剁偤鏌熼柇锕€骞栭柍褜鍓欓悥濂稿蓟閿濆绠涙い鏂垮⒔椤︿即姊洪崷顓炲妺妞ゃ劌鎳愮槐鎺楀煛娴煎崬缍婇幃鈺侇啅椤旂厧澹夐梻浣虹帛閹搁箖宕伴弽顓炶摕闁绘柨鍚嬮悞浠嬫煥閺囨浜鹃梺璇茬箳婵炩偓闁哄矉缍佹俊鎼佹晜婵劒铏庨梻浣告惈閻ジ宕伴弽顓炵畺闁告挆鍌涘媰闁荤喐鐟辩徊楣冩倵娴煎瓨鈷掑ù锝呮啞閹牓鏌ｉ鐑嗘Ш鐎垫澘锕畷鎰版偄鏉炴壆鑳洪梻鍌氬€风粈渚€骞夐敓鐘茬闁硅揪绠戠粈澶婎熆閼搁潧濮囬柛鎴犲█閺屾稑鈽夊Ο鍏兼喖闂佺锕ら悘姘辨崲濞戙垹閱囨繝闈涚墔閾忓酣姊洪崫鍕靛剱缂佸甯℃俊鐢稿礋椤栵絾鏅ｉ梺缁樺姇瀵湹绨哄┑锛勫亼閸婃牕煤閳哄啰绀婂〒姘ｅ亾闁绘侗鍠栭～婊堝焵椤掑嫨鈧礁鈻庨幘鏉戠檮婵犮垼娉涢ˇ閬嶆儎鎼淬劍鈷掗柛灞剧懅閸斿秹鏌涙惔锛勶紞闁瑰箍鍨硅灃闁告粈鐒﹂弲顏堟⒑閸濆嫮鈻夐柛妯恒偢閹潡顢氶埀顒勭嵁閺嶎灔搴敆閳ь剚淇婃禒瀣厽闁规崘娉涢弸娑㈡煛瀹€瀣М鐎殿噮鍓熼獮鎰償閵忕姵鐎鹃梻鍌欑劍濡炲潡宕㈡總鍛婃櫇闁靛鏅涙闂佸憡娲﹂崹閬嶅疾濠靛鐓曢悘鐐插⒔閳洟姊哄▎鎯у籍婵﹦鍎ょ€电厧鈻庨幋鐐蹭还闂備胶顭堢换鎴︽晝閵忋倖鍋樻い鏃堟暜閸嬫捇鏁愭惔鈩冪亶闂佸搫鎳忕换鍫ュ蓟瀹ュ牜妾ㄩ梺鍛婃尰缁嬫捇宕氶幒鎾剁瘈婵﹩鍘煎▓銉╂⒑闂堟稓澧曟繛灞傚姂閺佸秴鈹戦崶鈺冾啎闁哄鐗嗘晶鐣岀矓椤掍降浜滈柡鍥╁枔婢х敻鏌熼鎯т沪缂佸倹甯為埀顒婄秵閸嬪棝宕㈤崡鐐╂斀妞ゆ柨顫曟禒婊堟煕鐎ｎ偅灏棁澶嬬節婵犲倸鏆熼柛鈺嬬悼閳ь剚顔栭崰鏍€﹂悜钘夋瀬闁圭増婢橀獮銏′繆椤栨碍鎯堝┑陇娅曟穱濠囨倷椤忓嫧鍋撻弽顓熷亱婵°倕鍟崹婵嬪箹濞ｎ剙鐏褝绻濆濠氬磼濮橆兘鍋撻悜鑺ュ殑闁割偅娲嶉埀顒婄畵瀹曞ジ濡烽鑺ユ珗闂備礁鎲＄粙鎺戭焽濞嗘垹涓嶉柣妯肩帛閻撴瑩姊洪銊х暠妤犵偞蓱閵囧嫰鍩￠崒婊冨绩闂佸搫鐬奸崰鏍ь潖閼姐倐鍋撻悽娈跨劸濠碘€茬矙濮婅櫣绮欏▎鎯у壄闂佸憡姊归崹鍨嚕鐠囨祴妲堟俊顖炴敱椤秴鈹戦绛嬫當闁绘妫欓幈銊モ槈閵忊檧鎷洪梺鍛婄☉椤剟鎮炴禒瀣厸濞达絿鐡旈崵娆愩亜椤愩垻绠伴悡銈嗐亜韫囨挸顏╃紒鎰⊕缁绘繈鎮介棃娴躲垽鎮楀鐓庡籍鐎规洖鐤囬ˇ鍙夈亜椤忓嫬鏆ｅ┑鈩冨劶缁犳稑鈽夊▎妯峰亾瀹ュ棛绡€闁靛繈鍨洪崵鈧┑鈽嗗亝閻熝呭垝鐎ｎ亶鍚嬪璺猴功椤旀劖绻涙潏鍓хК妞ゎ偄顦埢宥夊幢濞戞瑢鎷绘繛杈剧秬椤曟牠宕曢妷鈺傜厱闁靛ě鍐炬毉缂備礁鍊哥粔鐢稿Χ閿濆绀冮柍杞版閻㈢粯绻濋悽闈涗粶婵☆偅顨堥幑銏ゅ箛闁附鏅滃銈呯箰濡稓澹曟總鍛婄厓鐟滄粓宕滈悢椋庢殾濠靛倻顭堝敮闂佹寧姊归崕鎶界嵁閸儲鈷掑ù锝堟鐢盯鏌ㄥ杈╂／缂備降鍨归獮妤呮煛閸涱厾鍩ｉ柟顔荤矙瀹曘劍绻濋崟顐㈠闂傚倷鑳剁涵鍫曞礈濠靛枹鍝勵煥閸╄泛娲弫鍌涙叏閹邦亞鐩庢俊鐐€栭崝褏寰婇崸妤佸剹闁圭儤顨嗛悡鏇㈡煃鐟欏嫬鍔ゅù婊呭亾娣囧﹪鎮欓鍕ㄥ亾閺嶎偅鏆滈柟鐑樻煛閸嬫挸顫濋悡搴☆潾缂備緡鍠栭…宄邦嚕娴犲鏁囬柣鏂挎惈鐢箖姊绘繝搴′簻婵炲眰鍊濋、鏍川鐎涙ê鈧埖銇勯弴妤€浜鹃梺鍝勭焿缂嶄線銆佸鈧幃銏ゆ倻濡儤鐝伴梻鍌欑閹碱偊鎯夋總绋跨獥闁规崘娉涢崹婵囩箾閸℃ɑ灏柛銊ュ€归妵鍕即閻愭潙娅ｆ繝纰夌節閺€杈ㄧ┍婵犲洦鍊锋い蹇撳閸嬫捇寮撮悩鍐插簥闂佸湱鍎ら〃鍛玻濡ゅ懏鐓涚€规搩鍠栭張顒傜礊鎼淬垻绡€闁汇垽娼ф牎闂佽偐鎳撴晶鑺ョ珶閺囥垹绀傞柛蹇曞帶瀵灝鈹戦绛嬫當婵☆偅顨婂畷銏ゅ箻椤旂晫鍘遍柣搴秵閸嬪懐浜搁銏＄厽闁挎繂绻掔弧鈧Δ鐘靛仜閿曘儳鎹㈠┑瀣＜婵犲﹤鎲涢妸鈺傚€甸悷娆忓缁€鈧梺缁樼墪閵堟悂濡存担鑲濇梹鎷呴崫銉х嵁闂佽鍑界紞鍡涘磻閸涘瓨鍋熼柕濞炬櫆閳锋垿鏌ゆ慨鎰偓鏇㈠几閹寸姷纾兼い鏃囧亹閻忛亶鏌熼獮鍨仼妞ゎ偅绮撻崺鈧い鎺戝閸嬫ɑ銇勯弴妤€浜惧Δ鐘靛仜椤戝寮崘顔肩劦妞ゆ帒瀚哥紞鏍煕濞戞鎽犻柣鎾崇箻閺屾盯顢曢敐鍥╃暫闂侀潻绲肩划娆撳蓟閿濆围闁稿本鐭竟鏇炩攽閻樻鏆滅紒杈ㄦ礋瀹曟垿骞嬮敃鈧壕褰掓煛瀹ュ海浠滄繛鎴炵懄婵挳鏌ら崣澶岀畺闁烩晩鍨伴悾鐑芥偄绾拌鲸鏅㈡繛杈剧悼椤牊淇婇崘娴嬫斀闁绘ê鐏氶弳鈺佲攽椤旇姤缍戦悡銈夋煥閺囩偛鈧悂鎮￠弴銏＄厵閻庣數顭堝暩濠碘槅鍋掗崹閬嶅Φ閸曨垰绠婚悹楦挎〃濞岊亪姊洪棃鈺冪К婵炲拑缍侀獮澶愬箹娴ｅ摜顔呮繝鐢靛Т閸婅寰勯崟顖涚厓闂佸灝顑呭ù顕€鏌＄仦鍓ф创鐎殿喗鎸虫俊鎼佸Ψ閵堝洨娉垮┑锛勫亼閸婃洖霉濮樿泛鍌ㄥΔ锝呭暙閻掑灚銇勯幒鎴濇灓婵炲吋鍔栫换娑㈠矗婢舵稖鈧法鈧娲栫紞濠傜暦缁嬭鏃堝焵椤掑嫬鍑犲ù锝堛€€閸嬫捇鐛崹顔煎濡炪倧瀵岄崹宕囧垝閸懇鍋撻敐搴′簴濞存粍绮撻弻鐔煎传閸曨剦妫炴繛瀛樼矒缁犳牕顫忓ú顏勪紶闁告洟娼ч崜閬嶆⒑閹肩偛濡奸柣蹇旂箞閿濈偠绠涢幘浣规そ椤㈡棃宕熼鍡欏€為梻鍌欑閹测€趁洪敃鍌氱；闁逞屽墴閺屾稓鈧綆鍋呭畷宀勬煙缁嬪尅鏀荤€垫澘瀚板畷鍗炍熼梻瀵歌兒闂傚倸鍊风粈渚€骞栭鈷氭椽濮€閵堝懎鐎梺鑽ゅ枛濠㈠繗顦圭€殿喖顭锋俊鐑芥晜閹冩辈闂傚倷绀侀幖顐﹀疮椤愨挌娲Χ閸偅姣愰梻鍌氬€风粈渚€骞夐敓鐘冲仭闁靛／鍕簥闂佺懓顕崑鐔煎煘瀹ュ應鏀介柣妯哄级缁佲晠鏌℃担鍝バｇ紒缁樼洴楠炲鎮滈崶锔捐繑闂備焦鎮堕崐鏇㈩敄閸モ晜顫曢柟鐑樻⒒绾惧ジ鏌曡箛鏇炐㈢紒鐘侯潐缁绘繈鍩涢埀顒勫川椤旂晫褰ф繝纰樷偓鍐茬骇闁告梹鐟╅悰顕€骞掗幊铏⒐閹峰懘姊归幇顓犳綎闂傚倸鍊烽懗鍓佸垝椤栫偛鍨傞柛顐ｆ磵閳ь剨绠撳畷鍫曨敂閸屾瑦鐫忛柣鐔哥矊濞寸兘骞戦姀鐘闁靛繆鍓濆▍婊堟⒑閸涘﹣绶遍柛鐘愁殜楠炲啫鈻庨幘绮规嫼闂佸湱顭堢€涒晜寰勯崟顐€鐟邦煥閸曨厾鐣虹紓浣哥焷妞村摜鎹㈠┑瀣倞鐟滄棃宕崼鏇熲拺闁告繂瀚婵嬫煕鐎ｎ剙鏋涢挊婵嬫煛婢跺鍎ユ繛鎾愁煼閺屾洟宕煎┑鍥舵！闁诲繐绻掗弫濠氬蓟濞戞埃鍋撻敐搴′簽婵炲弶鎸抽弻宥夊Ψ閿旀儳顫ч悗鍨緲鐎氼噣鍩€椤掑﹦绉甸柛鎾寸洴閹線宕奸妷锔规嫼濠殿喚鎳撳ú銈夋倶閸欏绠惧ù锝呭暱鐎氼噣銆呴柨瀣瘈濠电姴鍊绘晶娑㈡煟閹惧鎳囬柡灞剧洴楠炲洭顢涘杈嚄缂傚倷鑳舵慨鎾€﹂悜钘夎摕闁绘梻鈷堥弫濠囨煏婵炲灝鍔滃璺哄閺屾稓鈧綆鍋勬慨澶愭煃瑜滈崜娑㈠箠閹惧鐝跺┑鐘叉搐缁愭淇婇妶鍛殶缂佲偓婵犲洤绠圭紒顔款潐椤庡棝鏌￠埀顒佺鐎ｎ偆鍘介梺褰掑亰閸撴瑧鐥閺屽秶绱掑Ο鑽ゎ槬闂傚洤顦扮换婵囩節閸屾凹浼€闂佹椿鍘奸敃銉╁Φ閸曨垰妫橀悹鎭掑妿椤斿洭姊虹€圭媭娼愰柛銊ョ仢閻ｉ攱娼忛銈囨澑闂佸搫鍟犻崑鎾绘煛婢跺鍊愭慨濠勭帛閹峰懘鎼归悷鎵偧闂佽棄鍟虫禍顒勫箟閹间礁绾ч柛顭戝枟濞堝鎮楀▓鍨灍濠电偛锕獮鍐閵堝懐顦ч梺鍏肩ゴ閺呮稑顕ｉ搹顐ょ瘈闁汇垽娼ф禒锕傛煕閵娿儳鍩ｉ柟顔惧厴閺屽棗顓奸崱妤佺暚闂備礁婀遍…鍫⑩偓娑掓櫊瀹曪綀绠涢幘顖涙杸闂佺粯蓱瑜板啴寮抽悙鐑樼厪闁搞儯鍔庣粻姗€鏌嶈閸撴繈锝炴径濞掗缚绠涘☉妯碱槷閻庡箍鍎卞ú锕€鐣烽崣澶岀瘈闂傚牊渚楅崕蹇曠磼閳ь剟宕橀埞澶哥盎闂婎偄娲﹂幐鎼侇敂閳哄倹鍙忛悷娆忓閸欌偓濠殿喖锕ら…宄扮暦閹烘垟鏋庨柟鎼幗琚﹂梻鍌欑濠€閬嶅煕閸儱纾诲┑鐘叉搐缁犳牜鎲稿澶嗏偓锕傚Ω閳轰線鍞跺┑鐘绘涧閻楁粌危閼哥數绡€闁汇垽娼ф禒锕傛煕閵娿儳鍩ｉ柟铏箖閵堬綁宕橀妸銉р偓顓炩攽閻樼粯娑фい鎴濇瀹曟劙鎮介崨濠勫弳濠电娀娼уΛ娑㈠礄瑜版帗鐓欓柡灞诲劤閻ｆ椽鏌＄仦鍓р槈妞ゎ偅绻堥、妤佸緞婵犲倸顥撻梻鍌欑閹碱偊寮甸鍕剮妞ゆ牜鍋涢弰銉╂煕閺囥劌鐏犵紒鈧崘顔界厪濠电偛鐏濋崝瀛樼箾閸忓吋灏柍瑙勫灴閹瑩鎳犻浣瑰枛缂傚倷绶￠崰鏍矓閸洖绠查柕蹇嬪€曢柋鍥ㄧ節閸偄濮堢紓宥呴閳规垿鎮欓崣澶嗘灆婵炲瓨绮嶇划宀勵敊韫囨挴鏀介柛鈥崇箲閺傗偓闂備胶绮摫鐟滄澘鍟撮崺鈧い鎺嶇劍閸婃劙鏌ㄥ┑鍫濅槐鐎规洏鍔庨埀顒佺⊕鑿ら柟閿嬫そ濮婃椽宕ㄦ繝鍕暤闁诲孩鍑归崣鍐ㄧ暦閹惰姤鎯為柛锔诲幘閿涙粓鏌ｆ惔顖滅У闁稿瀚划顓☆槾缂佽鲸甯￠、娆戝枈閸楃偛澹庢俊銈囧Х閸嬬偟鏁幒鏇犱簷濠电姷鏁告慨鎾疮椤栨埃鏋嶇€广儱顦伴埛鎺懨归敐鍫燁仩閻㈩垱鐩弻娑㈠棘鐠恒劎顔掗悗娈垮枟瑜板啴鈥﹂妸鈺侀唶婵犻潧娴傚Λ鐔兼⒒娓氣偓閳ь剛鍋涢懟顖涙櫠鐎涙ɑ鍙忓┑鐘插鐢盯鏌熷畡鐗堝殗闁圭厧缍婇幃鐑藉箥椤曞懎浠归梻鍌氬€风粈渚€骞夐垾鎰佹綎闁荤喐鍣村ú顏勫唨妞ゆ挆鍕珨闂備焦瀵х换鍌炈囬鐐寸厑闁搞儺鍓氶悡娑㈡煕閵夘垳鍒板ù婊呭亾缁绘繈濮€閿濆棛銆愬銈庡弾閸撶喖骞冮敓鐘参ㄩ柍鍝勫€婚崢鍗炩攽閻愭潙鐏ョ€规洦鍓熼幃姗€鏁愭径瀣弳濠电偞鍨堕悷褔鎯屽畝鈧槐鎺撴綇閵婏箑纾抽悗瑙勬礃鐢帡鍩㈡惔銊ョ婵犻潧妫悗鎾⒒閸屾瑧鍔嶉悗绗涘洦鍤屽Δ锝呭暙閻撴﹢鏌熸潏鎯х槣闁轰礁锕弻銈吤圭€ｎ偅娈ㄩ梺鎸庣箓椤︿即鎮¤箛娑欑厱闁靛鍨甸崰姘閸愨晝绡€婵炲牆鐏濋弸鎾绘煕鐎ｎ偅宕屾慨濠冩そ瀵剟濡烽敂鐣屽絽闂備礁鎽滄慨鐢告嚌閹规劗浜遍梻浣告啞閸旓箓鎮￠崼婵勨偓鎺撶節濮橆厾鍘梺鍓插亝缁诲倿寮冲▎鎾寸厽闊浄绲肩花鑲╃磼缂佹娲撮柟宕囧█椤㈡鍩€椤掑嫬鍑犳繛鎴炲焹閸嬫挸鈻撻崹顔界彯闂佺顑呴敃銈夋偩閻戣棄绠虫俊銈勭閳ь剛鍏橀弻鈩冨緞鐎ｎ亞浠梺璇″櫙缂嶄礁顫忛搹瑙勫珰闁肩⒈鍓涢濠囨⒑缁嬫鍎戝┑顔绢棎閻忓姊洪崨濠冨闁告鍋撶粙澶婎吋閸℃瑧顔曢悗鐟板閸犳洜鑺辨總鍛婄厓闂佸灝顑呭ù顕€鏌＄仦鍓с€掑ù鐙呯畵瀹曟粏顦抽柛锝庡灦濮婅櫣绮欑捄銊ь唶闂佹椿鍘奸崐鍨嚕婵犳碍鍋勯柛蹇撳悑閸庮亜顪冮妶鍡楀Ё缂佽尙鏅竟鏇㈠箮閼恒儮鎷洪柣鐘叉礌閳ь剝娅曢悘宥夋⒑閹稿骸鍝洪柡灞界Ф閹叉挳宕熼銈勬偅闂備線鈧偛鑻晶鍓х磽瀹ュ懏顥炵紒鍌氱Ч閹瑩宕崟顔芥啺闂備礁澹婇崑鍛崲瀹ュ懌鈧帗绻濆顓犲帾闂佸壊鍋呯换鍐夊鍐ｆ斀妞ゆ柣鍔屽ú锕傚煕閹烘鐓曢柡鍥ュ妼娴滄粍銇勮箛锝呭箻闁逞屽墲椤煤濮椻偓瀹曟繂鈻庤箛鏇熸婵犻潧鍊婚…鍫ュ础閹惰姤鐓熼柡鍌涘閹牏鈧鍠涘▍鏇犳崲濠靛鍋ㄩ梻鍫熺▓閺嬪懎鈹戦悙鏉垮皟闁稿繒鍋撶粙鎴﹀煝鎼淬倗鐤€闁挎繂鎳忓▍濠冪節閻㈤潧鈻堟繛浣冲洦鍋嬮煫鍥ㄦ嫻濞戙埄鏁嶉柣鎰嚟閸橀潧顪冮妶鍡欏ⅹ婵☆偅姊荤划鍫熸媴閸︻収娴勫銈嗘磵閸嬫捇鏌熼绛嬫當闁崇粯鎹囧畷褰掝敊閻ｅ奔閭梻鍌欒兌缁垶鎮у鍫濆偍鐟滄棃骞冩ィ鍐╁€婚柤鎭掑劚娴滄鏌熼悡搴ｆ憼閽冭鲸銇勯銏⑿㈤柍瑙勫灴閸┿儵宕卞Δ鍐ф埛闂佽崵濮崑鎾绘煥閺囩偛鈧綊宕愰悽鍛婄叄闊浄绲芥禍鐐电磼閳锯偓閸嬫捇姊绘担鍦菇闁搞劏妫勯…鍥槼缂佸倹甯￠弫鍐磼濞戞艾骞嶆俊鐐€栭悧妤€顫濋妸鈺傚€块柛婵勫劗閸嬫挾鎲撮崟顒傤槰婵犵數鍋涢敃顏勵嚕椤愶箑绠涙い鎾跺仧缁愮偞绻濋悽闈浶㈤柟鍐茬箻楠炲瀵肩€涙ǚ鎷绘繛杈剧秬濞咃絿鏁☉娆愬弿濠电姴鍋嗛悡濂告煕閳哄倻娲寸€规洖銈告俊鐑藉Ψ閿旈敮鍋撻浣虹瘈闁汇垽娼у瓭濠电偠顕滅粻鎾崇暦閹达箑鐒洪柛鎰典簽椤旀洟姊虹化鏇炲⒉妞ゃ劌鐗忕划濠氭偋閸稐绨婚棅顐㈡祫缁插ジ鏌囬鐐寸厸鐎光偓鐎ｎ剙鍩岄梺瀹狀潐閸ㄥ灝鐣烽崼鏇ㄦ晜闁告洖鐏氶ˉ銈夋⒒閸屾瑧顦﹂柣銈呮喘閿濈偞寰勯幇顒€鐎梺绉嗗嫷娈旈柡鍕╁劦閺屾洘寰勯崱妯荤彅濡ょ姷鍋戦崹鐑樼┍婵犲洦鍊烽柟缁樺坊閹稿啴姊洪悜鈺傛珔闂佸府绲介～蹇曠磼濡顎撻梺鍛婄☉閿曘儵宕伴幇鐗堚拺闂傚牃鏅濈粊鐑芥煕鎼淬劍娑ч柣锝囧厴楠炲鎮欓悽鐢垫闂備焦鎮堕崕婊堝礋椤戭偅宀稿缁樼瑹閳ь剙顭囪绡撻柍褜鍓涚槐鎺楁偐閸愯尙浠煎┑鐐叉閸ㄤ粙骞冨▎寰濆湱鈧綆鍋呴弶鎼佹⒒娴ｈ櫣甯涢柛鏃€鐟ラ…鍥槼缂佸倸绉甸妶锝夊礃閳哄啫骞楅梺纭呭亹鐞涖儱危閸涘瓨鍋傞柛蹇撳悑閸欏繐鈹戦悩鎻掓殲闁靛洦绻堥弻鐔碱敊閹冨箣濡ょ姷鍋涚粔褰掋€佸▎鎾村殟闁靛鍎查悘鍫㈢磽閸屾艾鈧悂宕愭搴㈩偨闁跨喓濮寸粣妤佷繆閵堝懏鍣圭紒鐘崇叀閺屾盯濡烽幋婵囧櫤婵炲牓绠栧娲偡閹殿喗鎲奸梺璇″灠閿曨亜顕ｉ鈧畷鐓庘攽閸℃瑧宕哄┑锛勫亼閸婃牕螞娓氣偓閿濈偞寰勭仦绋夸壕婵﹩鍋嗛惌娆愭叏婵犲嫮甯涢柟宄版噺缁楃喖顢涘鍐ㄐ梻鍌欑閹碱偊鎮у鍫濈闁绘梻鍘х粻鐘崇節闂堟稒鍌ㄩ柛瀣嚇閺屾盯骞囬埡浣割瀷闂佸憡妫戠粻鎴︹€旈崘顔嘉ч柛鈩冾殔濞兼垿姊虹粙娆惧剱闁圭懓娲璇测槈閵忕媭娼婇梺鎸庣☉鐎氼剙顕ｉ妸銉㈡斀闁绘﹩鍋勬禍鐐箾閹炬潙鐒归柛瀣尰閹便劍绻濋崘鈹夸虎濡ょ姷鍋為悧鐘诲箖濞嗘挸绠甸柟鐑樺毄閵堝鈷掑ù锝呮啞閸熺偤鏌熺喊鍗炰簽缂侇喖顭烽獮姗€骞囨担鍛婎吙婵＄偑鍊栭崝鎴﹀磹閹版澘纾婚柟鍓х帛閺呮煡骞栫划鐟板⒉闁诲繐绉瑰铏圭磼濡闉嶇紓浣割儐閹瑰洭宕洪埀顒併亜閹哄棗浜剧紓浣哄Т缁夌懓鐣烽弴鐔哥秶妞ゎ厽鍨堕鏃堟⒑瑜版帗锛熼柤瀹犳硾閻ｅ灚绗熼埀顒勫蓟閳ユ剚鍚嬮幖绮光偓宕囶啈闂備胶绮幐璇裁洪悢鐓庤摕闁哄洨鍠撶粻楣冩煕椤垵鏋涘┑顔奸叄濮婃椽鏌呴悙鑼跺濠⒀屽灦閺岀喖顢欓悡搴樺亾閹间焦鏅濋柕蹇ョ磿閻熷綊鏌嶈閸撴瑩锝炶箛娑欐優闁革富鍘鹃悡鎾绘煟閻樺厖鑸柛鏂块叄椤㈡瑩宕堕浣叉嫼闂佸憡绋戦敃銊︾珶濡眹浜滈柟瀛樼箖瀹告繃淇婇崣澶婂闁诡喗鐟╁畷顐﹀礋椤撗冩倛闂傚倷鐒︾€笛冾潖婵犳艾鐤炬い鎰剁畱閸屻劌霉閻樺樊鍎愰柍閿嬪灴濮婂宕奸悢鎭掆偓鎺戭熆瑜庡ú鏍亙闂佺粯蓱椤旀牠鎮炴ィ鍐╃厓閻犲洩灏欏瓭濡炪倖鎸搁崥瀣嚗閸曨厸鍋撻敐搴″妤犵偞鍔欏铏规嫚閼碱剛顔婇梺绋款儑閸犳牠鍨鹃敃鍌氶唶闁绘柨鎲￠悵鐑芥⒑閼测斁鎷￠柛蹇旓耿瀹曟垿骞樼拠鎻掔€銈嗗姧缁插潡宕欓敓鐘崇厽閹兼番鍨归崵顒勬煕閿濆繒绉€规洘妞介崺鈧い鎺嶉檷娴滄粓鏌熼悜妯虹厐闁告梻鍠撶槐鎺撳緞閹邦厽閿梻鍥ь槹缁绘繃绻濋崒娑樻婵炲濯存俊鍥╂閹烘纾兼繛鎴烆焽閻熻尙绱撴担鑺ャ€冪紒顔界懃閻ｇ兘濡搁敂鍓ь啎闂佺粯鍔﹂崜娆擃敂瑜版帗鈷掗柛灞剧懅椤︼箓鏌熺拠褏纾块柟骞垮灲瀹曟帒鈹冮幆褌澹曞┑顔筋焽閸樠冾嚕椤曗偓閺屸剝鎷呴崫銉愌呪偓娈垮櫘閸ｏ絽鐣烽幒鎴叆闁告粌鍟版惔濠囨⒒閸屾瑨鍏岀紒顕呭灦閳ワ箓宕煎┑鍐╃€洪梺鍝勬川閸嬬喖寮抽敂閿亾閸忓浜鹃梺閫炲苯澧撮柛鈹惧亾濡炪倖甯婄欢锟犲疮韫囨稒鐓曢柣妯虹－婢х敻鏌嶉妷顖滅暤闁诡喗绮撻幐濠冨緞鐎ｎ兘鍋撻鍕拺鐟滅増甯掓禍浼存煕濡灝浜规繛鍡愬灲閹瑥霉鐎ｎ偅鏉搁梻浣虹帛钃辩憸鏉垮暣椤㈡棃顢曢敐鍥╊啎闂佺粯鎸哥€垫帒螣閳ь剙鈹戦纭峰姛缂侇噮鍨堕獮蹇涘川椤斿墽鎳濆銈嗙墬缁本鎱ㄦ總鍛娾拻濞达絽鎲￠幆鍫ユ煟濡や胶鐭掔€规洘绮撻幃銏ゆ偂楠烆兙鍎甸弻娑㈠箛闂堟稒鐏堝Δ鐘靛亼閸ㄨ櫣鎹㈠☉銏犲耿婵°倕瀚顓熺箾鐎涙鐭岄柛瀣崌楠炲牓濡搁妷顔藉缓闂佺硶鍓濋〃鍛不鐠囨祴鏀介柣鎰綑缁插鏌ｆ幊閸旀垿鐛崼銉ノ╅柨鏂垮⒔閻﹀牓姊洪崨濠冨闁稿瀚穱濠囧传閸曘劍鏂€闂佺粯鍔楅。浠嬪箯閳ヨ秮鐟邦煥鎼存繈鍋楅梺缁樹緱閸犳牠顢橀崗鐓庣窞婵☆垳绮▍鏃堟⒒娴ｅ憡鍟炲〒姘殜瀹曨垶鍩℃笟鍥╁弳闂佸憡娲﹂崢楣冩儊閸儲鈷戞慨鐟版搐閻忓弶绻涙担鍐插椤╅攱绻濇繝鍌氼仹缂佽妫欓妵鍕冀閵娧冩殹闂傚鍓﹂崜娑㈠焵椤掑喚娼愭繛娴嬫櫇缁辩偞鎷呴崫銉︽濠殿喗銇涢崑鎾绘煃閵夘垳鐣垫鐐差儏閳规垿宕剁捄鐑樺亼婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柛娑橈攻閸欏繘鏌ｉ姀銈嗘锭闁哄鐗忛埀顒€绠嶉崕閬嵥囬鐐村仾濡わ絽鍟悡娆戠磽娴ｅ顏呯┍椤栫偞鐓熼柡鍌涘椤ャ垽鏌熼鑲╃Ш闁圭厧缍婇幃鐑藉箥椤旂瓔鍚囬梻鍌欐祰閵嗏偓闁稿鎸剧槐鎾存媴妤犮劍绮嶇粋宥咁煥閸喓鍘繝銏ｅ煐缁嬫垿銆呴鍌滅＜闁绘﹢娼ф禒锕傛煙娓氬灝濡界紒缁樼箞瀹曟﹢鍩炴径姝屾闂佽姘﹂～澶娒哄鍫濈婵せ鍋撻柕鍡曠铻ｆ繛鍡欏亾闉嬮梻鍌欑閹诧繝寮婚妸鈺佺疅闁斥晛鍟伴埞宥呪攽閻樺弶鎼愰崶鎾⒑閹肩偛鍔撮柣蹇旂箞閺佹劖寰勭€ｎ剙骞嶆俊鐐€栧濠氬储瑜旈崺鈧い鎺嶇劍閸婃劖銇勯姀鈥冲摵闁轰焦鍔栧鍕節閸曢潧鏁堕梻鍌欐祰婵倝鏁嬪銈忓閺佽鐣烽鍌涘磯閻炴稈鍓濋弬鈧梻浣虹帛椤牏浜稿▎鎰浄婵炴垯鍨洪悡鏇㈡煛閸愶絽浜鹃梺缁橆殕缁捇宕洪埀顒併亜閹烘垵顏繛鎳峰洦鐓熸い鎾跺仜閳ь剚鎮傞敐鐐剁疀閹句焦妞介、鏃堝椽娴ｈ妫冨┑鐘垫暩閸嬬偤宕归崼鏇熷殞濡わ絽鍟ㄩ埀顒€鍊哥叅妞ゅ繐鎳夐幏铏圭磼缂併垹骞栭柟鍐茬箻閹敻顢旈崼鐔哄幈闂佸搫鍟犻崑鎾淬亜閵娿儳澧︾€规洘宀搁獮鎺楀箻閸撲胶妲囨繝娈垮枟閿曗晠宕滃☉銏╂晩闁告稑鐡ㄩ埛鎴︽煕韫囨稒锛熼柤鍓蹭邯閺屾稒鎯旈姀鐘灆閻庤娲忛崹浠嬪蓟閸℃鍚嬮柛鈥崇箲鐎氳偐绱撻崒姘偓鐑芥倿閿曞倸鐓熼柕鍫濐樈閺佸倿鏌涢埄鍐炬婵☆偅绮岄—鍐Χ閸℃ê鏆楅梺鍝ュУ閸旀洟鍩㈠澶嬪亹闁绘劖鍨濈花濠氭椤愩垺澶勯柟绋款煼瀹曞啿鐣￠幊濠勬嚀椤劑宕熼鐘靛帒闂備線娼уú銈団偓姘嵆閻涱噣宕堕鈧悡娑樏归敐鍫熴€冪紒杈ㄦ礈缁辨捇宕掑▎鎺戝帯婵犳鍠栭顓熺珶閺囥埄鏁囬柣妯诲墯濞肩喖姊洪崷顓炲妺闁规瓕宕电槐鐐哄炊椤掍胶鍘繝鐢靛仜閻忔繈宕濋妶澶嬬厽闁绘梹娼欐慨鍌炴煛鐏炵喎瀚杈ㄧ箾閸℃ê鐏╃悮锕傛⒒娴ｇ瓔鍤冮柛鐘崇墱缁辩偞绻濋崒婊勬闂佽鍨堕崑濠偽ｆ總鍛娾拺鐎规洖娲ㄧ敮娑欑箾閼碱剙鏋涚€殿喛顕ч濂稿醇椤愶綆鈧洭姊绘担鍛婂暈闁规瓕顕ч悾婵嬪箹娴ｅ搫绁﹂梺纭呮彧缁犳垿鎮欐繝鍕枑婵犲﹤鍠氶悞浠嬫煛瀹ュ骸骞楅柣鎿勭秮閻擃偊宕堕妸锕€闉嶉柣鐘冲姧缁辨洟鎯€椤忓牆绠氱憸宥夊箖閹存劲搴ㄥ炊瑜濋煬顒併亜閵忥紕鈽夐柍瑙勫灴瀹曞爼寮甸棃娑欐啟濠电姷鏁搁崑娑㈩敋椤撶喐鍙忛柣銏㈩焾缁犳牠鏌涚仦鎯у毈婵炲吋鐗楃换娑橆啅椤旇崵鐩庣紓浣哄С閸楁娊鐛弽銊︾秶闁告挆鍛咃綁姊洪崫鍕紨缂佽鲸娲滃Σ鎰板箻鐎涙ê顎撻梺鎯х箳閹虫挾绮敓鐘斥拺闁绘垟鏅滃▍鎾绘煕閵娿倕宓嗛柟顕€绠栭幃婊堟嚍閵夈儰绨甸梺鍦帶閻°劏鎽梺鍝勫€甸崑鎾绘⒒閸屾瑧顦﹀鐟帮躬瀹曟垶绗熼埀顒€鐣烽崷顓熷枂闁告洦鍓欏鍧楁煛婢跺﹦澧戦柛鏂挎捣瀵囧焵椤掆偓閳规垿鎮欓懠顒€顣洪梺璇茬箲缁诲牆顕ｇ粙搴撴闁靛骏绱曢崣鍡涙⒑閸濆嫭澶勯柨姘攽閳ョ偨鍋㈤柡灞界Ф閹风娀鎳犻顐庡洦鐓涘ù锝囨嚀婵牏鈧灚婢樼€氼厾鎹㈠┑瀣妞ゆ劏鎳ｉ弴鐐╂斀闁挎稑瀚禍濂告煕婵犲啰澧遍柡渚囧櫍楠炴帒螖閳ь剛澹曢崷顓熷枑闁绘鐗嗘穱顖炴煛娴ｇ鈧潡寮婚敐澶婃闁割煈鍠楅崐顖炴⒑閹惰姤鏁遍悽顖ょ節瀵鈽夐姀鐘插祮闂侀潧顭堥崕鎵姳鐠囧樊娓婚柕鍫濇噽缁犳岸鏌￠崨顔剧煉濠碉紕鏁诲畷鐔碱敍閿濆棙娅嶉梻浣虹帛閸旀洟宕ラ埀顒勬煣韫囨捇鍙勯柡灞熷嫬顕辨繛鍡樺灦閸嬔呯磽娴ｄ粙鍝洪柟绋款煼楠炲繘宕ㄩ钘夊伎闂佽鎯岄崢铏硅姳缂佹ǜ浜滈柡鍥朵簽缁夘喚鈧娲滈…鍫ｇ亙婵炶揪绲藉鍓佲偓姘冲亹缁辨捇宕掑▎鎴М濡炪倖鍨靛Λ娑㈠焵椤掑倻鎳楅柛鎰劵閳ь剙娼￠弻鈩冨緞鐎ｉ潧鍔岄梺缁樻煥濡繈骞冨畡鎵虫瀻闊洦鎼╂禒鍓х磽娴ｆ彃浜鹃柣搴秵閸忔﹢宕戦幘鑸靛枂闁告洦鍓涢ˇ銊╂⒑閹肩偛濡兼繛纭风節閹即顢欑喊杈ㄥ缓闂侀€炲苯澧存鐐诧工椤劑宕奸悤浣诡棃婵犵數鍋為崹鍫曟偡瑜旈崺鈧い鎺戭槸瀵喗鎱ㄦ繝鍕笡闁瑰嘲鎳樺畷銊︾節閸屾稒鐣煎┑鐘垫暩閸嬬娀顢氬鍛笉闁规儳纾弳锔界節婵犲倸鏆婃俊鎻掔墦閺屽秶鎹勯悜妯诲創濠电偛妯婇崢鑺ョ┍婵犲偆娼扮€光偓婵犲唭顒傜磽娴ｅ搫浜剧€规洘锚铻為柛鏇ㄥ灡缁犳帡姊绘担鐟邦嚋缂佽鍊哥叅闁靛ň鏅涚粻娲煕瀹€鈧崑鐐烘偂閺囥垺鐓忛柛顐ｇ箖椤ョ娀鏌熼崘鍙夊枠闁哄备鈧磭鏆嗛悗锝庡墮閸╁矂鏌х紒妯煎⒌闁哄苯绉烽¨渚€鏌涢幘瀵告噯闁诲繐顑呴埞鎴︽偐缂佹ɑ閿銈嗗灥閸熸挳宕洪埀顒併亜閹达絾纭舵い锔奸檮閵囧嫰顢曢敐鍥╃杽闂佺娅曠划鎾崇暦瑜版帩鏁冮柨婵嗘搐缁€渚€姊婚崒姘偓鐑芥嚄閸撲焦鍏滈柛顐ｆ礀缁€鍫熺節闂堟侗鍎愰柛瀣у墲閵囧嫰寮介…鎴濇閻熸粎澧楃敮鎺旂矆鐎ｎ偁浜滈柡宥冨妿閵嗘帡鏌涘Ο鍦煓闁哄瞼鍠栧畷妤呭礂閻撳骸澹掗梻浣规偠閸斿秵绻涙繝鍌滄殾婵﹩鍘奸閬嶆倵濞戞瑯鐒介柛妯圭矙濡懘顢曢姀鈥愁槱闂佸搫琚崝鎴濈暦椤愶附鎯為柛蹇擃槸娴滅偓绻涢崼婵堜虎闁哄鍠栭弻锝夊箼閸曨厾鐦堥悗娈垮櫘閸嬪﹪鐛崶顒夋晣闁绘劗鏁搁悰顕€姊虹拠鎻掑毐闁诡兙鍎靛畷鐔碱敃椤愶紕妞界紓鍌氬€搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌ｉ幋锝呅撻柛濠傛健閺屻劑寮撮悙娴嬪亾瑜版帒鐤炬い蹇撳閺€浠嬫煟濡澧柛鐔风箻閺屾盯鎮╁畷鍥р拰闂佽鍟崱娆戞澑濠电偞鍨堕…鍥偪閸涘瓨鈷戠憸鐗堝笚閿涚喓绱掗埀顒佹媴閾忚锛忛悷婊勬瀵鈽夐埗鈹惧亾閿曞倸绠ｆ繝闈涙川娴滆埖绻濋悽闈涗粶闁绘绮撳畷锝夊礃椤垶缍庨梺鍛婄箓鐎氼噣寮抽敃鍌涚厱闁靛鍨哄▍鍛磼濡ゅ绡€婵﹥妞藉畷鐑筋敇閻旈攱鐣繝鐢靛仩椤曟粓姊介崟顓犵煔閺夊牄鍔庣弧鈧梺鎼炲劘閸斿苯鈻撳┑瀣拻闁稿本姘ㄦ晶娑氱磼鐎ｎ偅灏电紒顕嗙秮閹瑩鎮滃Ο鐓庡箺闂佺懓鍚嬮幆宀勫垂闁秴鐒垫い鎴炲劤閸旓妇鈧娲滈崰鏍€侀弴銏╂晝闁挎稑瀚花銉︾節閻㈤潧啸闁轰礁鎲￠幈銊╂倻閽樺鐎梺褰掓？缁€浣哄婵犳碍鐓熸俊顖涱儥閸ゆ瑧绱掗悩鑼粵闁靛洤瀚粻娑㈠箻閹碱厽顥ｇ紓鍌欐祰缂堜即宕愰弴銏＄畳闂備胶绮敋婵☆垰锕畷鏇＄疀閺囩姷锛滈梺缁橈耿濞佳囧几濞戙垺鐓涢悘鐐额嚙婵倿鏌熼鍝勭伈鐎规洦鍋婂畷鐔煎箣濞嗗繒浼堥梺鍝勭焿缂嶄焦淇婇懜鍨劅闁斥晛鍟拌ぐ搴ㄦ⒒娴ｅ憡鎯堥柣顓烆槺缁辩偞绗熼埀顒勬偘椤曗偓瀹曞爼顢楁径瀣珦闂備胶绮幐鍛婎殽閹间讲鈧牠宕卞☉娆屾嫽婵炶揪缍€濞咃絿鏁☉銏＄厵婵繂鐭堥崵娆撴偂閵堝鐓熼柡鍐ㄥ€哥敮鍫曟煢閸愵亜鏋涢柡灞诲姂閹垽宕崟鎴欏灮閻ヮ亪骞嗚閹垹绱掔紒妯肩疄婵☆偄鍟埥澶娾枎韫囨洜宕洪梻鍌欑窔濞佳兾涘Δ鍛櫇闁挎梻鍋撻～鏇㈡煙閹呮憼濠殿垱鎸抽弻娑樷攽閸℃褰呭銈嗗笒閸婄敻宕戦幘鑸靛枂闁告洦鍓涢ˇ顓㈡⒑鏉炴壆顦︽い顓炲槻閻ｅ嘲煤椤忓嫮鍔﹀銈嗗笂闂勫秵绂嶅鍫熺厵闁告繂瀚倴闁荤姵鍔掗崡鎶藉蓟濞戙垺鍋勫瀣捣椤旀帡姊洪崫鍕拱闁烩晩鍨堕獮鍐煛閸涱厾鐤€婵炶揪绲藉﹢閬嶅汲椤栫偞鈷掑〒姘ｅ亾婵炰匠鍏犳椽鏁冮崒姘鳖槷濠电偛妫楃换鎺楀汲閿旇姤鍙忔俊鐐额嚙娴滄儳顪冮妶鍐ㄧ仾闁荤啿鏅犻獮濠囧冀椤撶偟鍘告繛杈剧到閹芥粓顢樺ú顏呪拻闁稿本鑹鹃埀顒傚厴閹虫宕奸弴鐐电枃闂佸湱鍋撳鍧楀极鐎ｎ偁浜滈柟鎹愭硾閺嬫垿鏌涘▎蹇曠闁靛洤瀚伴獮姗€宕￠悙鍏告偅缂備胶鍋撻崕鎶解€﹂悜钘夎摕闁绘梻鍘х粈鍫㈡喐韫囨洘鏆滄繛鎴欏灪閻撴盯鏌涘☉鍗炴灓闁告瑢鍋撻梻浣告惈閺堫剟鎯勯鐐靛祦闁搞儺鍓欓悡娑樏归敐鍛棌闁诲孩濞婂娲传閵夈儱澹夐梺鎼炲姀濞夋盯锝炶箛鏇犵＜婵☆垵顕ч鎾寸箾閹炬潙鍤柛銊﹀▕瀹曟繄绮欏▎鐐瘜闂侀潧鐗嗗Λ妤呮倶閵夛缚绻嗘い鎰剁悼缁犵偤鏌ｅ☉鍗炴珝妤犵偛顑夐弫鍐焵椤掑嫭鍊峰┑鐘插閸犳劙鐓崶銊р槈婵鐓￠弻銊╁即閻愭祴鍋撹ぐ鎺撳亗闊洦鎸撮弨浠嬫煟閹邦厼鐏嶉柛瀣崌閺屾稓鈧綆鍋呭畷宀勬煛鐏炲墽銆掗柍褜鍓ㄧ紞鍡涘磻閸涱垯鐒婃い鎾卞灪閻撳啴鏌﹀Ο渚▓婵″弶鎮傞弻宥囨嫚閼碱儷褎銇勯姀鈽嗙劸妞わ箓浜跺娲敇瑜嶉悘锕傛煛鐏炲墽娲撮柛鈺冨仱楠炲棜顦卞瑙勬礀閳规垿鏁嶉崟顐℃澀闂佺锕ラ悧鏇㈩敊韫囨梻绡€婵﹩鍓涢敍娑㈡⒑閸︻厼浜鹃柣锝庝簼缁傚秴顭ㄩ崟鈺€绨婚梺瑙勫礃濞夋稑鐡梻浣告啞閿氭い鏇ㄥ弮閸┾偓妞ゆ帊绶￠崯蹇涙煕閻樺磭澧甸柍銉畵閺屻劎鈧絺鏅濈粻姘舵⒑閸忓吋鍊愭繛浣冲懏顐介柣鎰ゴ閺€浠嬫煟濡绲绘い蹇ｅ亰閺岋繝宕遍幇顒備紙濠殿喖锕︾划顖炲箯閸涙潙宸濆┑鐘叉噽椤㈠懘姊绘担渚劸缂併劍妞藉畷鎰攽閸℃瑦娈鹃悷婊呭鐢帡宕归崒娑栦簻闁规澘澧庨崚鎵偓娈垮枛濞尖€愁潖濞差亝顥堟繛鎴炴皑閻ゅ嫰姊虹粙鍖℃敾闁绘濞€瀹曞搫鈽夐姀鐘殿吅闂佹寧姊婚弲顐﹀储閸楃偐鏀介柣鎰綑閻忥箓鏌ㄩ弴妤佹珚闁诡噯绻濋、姗€濮€閿涘嫬骞橀梻浣虹帛椤ㄥ懘鎮ч崱娑辨晪婵犲﹤鎳愮壕鑲╃磽娴ｈ鐒介柍顖涙礋閺岀喎鐣￠悧鍫濇畻閻庤娲橀敃銏犵暦閿濆棗绶炵€光偓婵犲唭銊╂⒒閸屾瑧绐旈柍褜鍓涢崑娑㈡嚐椤栨稒娅犳い鏍ㄧ◥缁诲棙銇勯幇鈺佺仼闁哄棙鐟╅弻锝夋晲閸涱厽些濡炪値鍋呯划鎾诲春閳ь剚銇勯幒鎴濐仴闁逞屽厸缁€渚€鍩㈡惔銊ョ闁告劘灏欓崫搴♀攽閻橆喖鐏辨い鏇熺墵瀹曞綊寮跺Λ鍨そ閺佸啴宕掑☉妯圭暗闂備礁鎼ú銏ゅ垂濞差亝鍋傞柛鎰靛枟閻撳啴寮堕悙鏉戭棆妞わ讣绠撻弻锟犲幢濞嗗繑鐏堥梺鍝勭灱閸犳牕鐣峰鈧獮鎾诲箳閹炬惌鍞归梻鍌欑窔濞佳兠洪妶鍥ｅ亾濮橆偄宓嗛柕鍡曠窔瀵挳鎮㈢粙鍨Τ闂備焦鏋奸弲娑㈠疮椤愨懇鏋嶇憸蹇曟閹烘鍊锋い鎺嗗亾缂佲偓閸愨晙绻嗘い鎰╁灩椤忣厽绻濋埀顒佺瑹閳ь剙顫忓ú顏勭閹艰揪绲块悾鐢告⒑閻熸澘鏆遍梺甯秬濡垽妫呴銏″缂佸甯￠幃锟犲即閵忕姷顔愬┑鐑囩秵閸撴瑩鎮橀埡浣叉斀妞ゆ棁鍋愭晥闂佸搫鐭夌槐鏇熺閿曞倸绀堢憸搴ㄥ礆濞戞瑧绡€缁剧増锚婢ф煡鏌熼鐓庘偓瑙勭┍婵犲洤绠瑰ù锝堝€介妸鈺傜厪濠㈣泛鐗嗛崜楣冩煏婢舵稑顩紒鈾€鍋撶紓浣稿⒔婢ф鎽銈庡亜閿曨亪寮诲☉姘ｅ亾閿濆倹娅囩紒鎰⒒閳ь剝顫夊ú婵嬶綖婢跺瞼鐭夐柟鐑樻煛閸嬫捇鏁愭惔婵囨崳闂佺濮ゅú鐔奉潖濞差亜绠伴幖杈剧悼閻ｉ潧鈹戦埥鍡椾簼闁荤啿鏅涢悾鐑藉即閵忊€充簻闂佺鍋愰崑妯艰姳婵犳碍鈷戦柛婵嗗琚梺鍛婃煥闁帮綁骞嗙仦鐣屾殕闁告洦鍋勯崬銊╂⒑鐠恒劌鏋斿┑顔炬暬閸╂盯骞掗幊銊ョ秺閺佹劙宕ㄩ鍏兼畼闂備胶顢婄亸娆撯€﹂崼銉ョ厴闁硅揪瀵岄弫濠囨煟閹伴潧澧慨锝呯墦濮婃椽妫冨☉娆樻闂佸摜鍣ラ崹鎶藉箲閵忕姭鏀介悗锝庝簽閸婄偤鎮峰鍐闁诡喗鍎崇叅妞ゅ繐鎳夐幏娲煟閻樺厖鑸柛鏂跨焸瀵悂寮惔鎾存杸濡炪倖姊婚崑鎾诲汲椤掑倵鍋撶憴鍕闁搞劌鐏濋悾鐑芥偄绾拌鲸鏅┑鐐村灦閼归箖鐛崼銉︹拻濠电姴楠告禍婊勭箾鐠囇呯暤闁硅櫕绻堥幃娆擃敆閸屻倖閿ら柣鐔哥矌婢ф鏁Δ鍛亗闁哄洨鍠嶇换鍡樸亜閺嶃劎鐭岄悽顖涚洴閺屾稒绻濋崘銊ヮ潔闂侀€涚┒閸斿秶鎹㈠┑瀣窛妞ゆ洖鎳嶉崫妤冪磽閸屾瑧璐伴柛鐘愁殜閹兘鍩￠崨顖氱ウ闂佸綊鍋婇崢浠嬪磿閻斿吋鐓忛煫鍥堥崑鎾诲箛娴ｉ绀傜紓鍌氬€搁崐鎼佸磹閹间礁纾瑰瀣捣閻棗銆掑锝呬壕濡炪們鍨洪悧鐘茬暦閸楃偐妲堟俊顖濇閳ь剦鍙冨铏圭矓閸℃顏ュù鐘靛█閺屽秷顧侀柛蹇旂〒缁牊绗熼埀顒勬晲閻愭祴鏀介悗锝呯仛閺呫垺绻濋姀锝嗙【闁兼椿鍨伴锝夊箮閼恒儮鎷绘繛杈剧悼椤牓藟韫囨稒鐓曢悗锝庝簼椤ャ垻鈧娲戦崡鎶界嵁濡吋瀚氶柟缁樺笒娴滃爼姊婚崒娆戣窗闁告挻鐟х划鏃堟偨缁嬭法鍘遍梺纭呮彧闂勫嫰鎮″▎鎴犵＜閻庯綆鍋勯婊堟煙閻у摜鎮肩紒杈ㄥ浮閹晠鎼归銏紦闂備浇顕栭崰妤呫€冮崨杈剧稏婵犻潧顑嗛崑鍌炲箹濞ｎ剙鐏憸鏉块叄濮婄粯鎷呯粵瀣異闂佹悶鍔嬮崡鎶藉箠閻旂⒈鏁嶉柣鎰皺閸樻椽鏌熼崗鑲╂殬闁告柨鐬肩划缁樼節濮橆厼鈧敻鏌ｉ姀鈽嗗晱闁绘帞鍋撻〃銉╂倷閺夋垵顫掑┑顔硷龚濞咃綁骞忛悩缁樺殤妞ゆ帒鍋嗛崬娲⒒娴ｄ警鐒炬い鎴濇喘瀹曚即寮借閸ゆ洟鏌＄仦璇插姶闁轰礁鍟撮弻鏇＄疀婵犲倸鈷夐梺浼欑畱鐎涒晝鎹㈠┑鍫濇瀳濠㈣泛鐬奸敍鐔兼⒑缁嬫鍎愰柛銊ュ閳ь剟娼ч妶绋跨暦閸洦鏁嗛柍褜鍓涙竟鏇㈠礂閼测晝鐦堟繝鐢靛Т閸婂綊宕抽悾灞稿亾鐟欏嫭绀冮柣鎾偓鎰佹綎缂備焦蓱婵挳鏌涘☉姗堟敾闁稿孩鎹囧娲传閸曨厼鈷堥梺鍛婃尵閸犳牠鐛崱娑樼妞ゆ牭绲鹃弲顒勬⒑缂佹ê濮嶆繛浣冲懐鐭堥柨鏇楀亾闁宠鍨块崺銉╁幢濡ゅ啩鍝楅梻浣圭湽閸斿酣宕楀Ο铏规殾闁硅揪绠戠粻鑽ょ磽娴ｇ櫢渚涙俊顐㈠暙閳规垿鎮欓弶鎴犱桓闂佽崵鍠嗛崕闈涱嚕閹惰棄閱囬柕澶涜吂閹锋椽姊婚崒姘卞闁哄懏绻勯埀顒勬涧閵堟悂寮诲☉妯滄棃鍩€椤掑嫬鐤柛褎顨愮紞鏍叓閸ャ劎鈯曢柟顖滃仱閹嘲鈻庤箛鎿冧患闂佺粯绻€閸楀啿顫忓ú顏呭€烽柦妯侯槸婵倕鈹戦悙鍙夊櫤婵炶尙鍠栭獮鍐ㄎ熸笟顖氭倯婵犮垼娉涢鍌炲箯濞差亝鈷戦柛锔诲弾閻掔偓绻涚€电鍘撮柕鍡楀€垮濠氬Ψ閿旀儳甯鹃梻浣规偠閸庢粓宕橀崣銉х＞濠碉紕鍋戦崐鎴﹀垂閸濆娊娲偄閻撳孩鐎梺鍦濠㈡﹢鎮欐繝鍥ㄧ厓闁告繂瀚弳鐐寸節閵忥紕澧柍瑙勫灴閺佸秹宕熼鈩冩線闂備胶顭堥敃銉╁垂閸喚鏆﹂柣鐔稿櫞濞差亶鏁傞柛灞绢殔娴滈箖鏌涢幇闈涙灈妤犵偑鍨烘穱濠囧Χ閸滃啫浼愬┑鐐茬墱閸嬪﹤顫忓ú顏勫窛濠电姴鍟ˇ鈺呮⒑閸涘⊕顏堝箹椤愶箑鐓濆ù鐘差儛閺佸倿鏌涢銈呮灁闁告﹢绠栧铏规嫚閳ュ啿绠洪柣銏╁灡鐢€崇暦濠靛棭鐓ラ柛鏇ㄥ墮瀵潡姊洪幐搴ｇ畵闁瑰啿閰ｉ、鏃堫敂閸℃瑧锛滅紓鍌欑劍椤洨绮婚悙鐑樼厵妞ゆ棁宕甸惌娆愪繆椤愩垹鏆欓棁澶愭椤掍礁绲婚柛鏃€绮撻弻鏇㈠炊瑜嶉顓㈡煛娴ｇ懓濮嶇€规洖銈搁幃銈嗘媴鐠団剝顥夐梻鍌欑閹诧繝銆冩繝鍐洸闁割偅娲栭拑鐔哥箾閹寸們姘ｉ崼鐔虹闁糕剝锚婵绱掔€ｎ亝鍠樻慨濠冩そ楠炴牠鎮欓幓鎺濈€抽梻浣侯焾閿曘儵骞冮崒姘捐€垮〒姘ｅ亾婵﹨娅ｇ槐鎺懳熺拠鑼暡婵犵數鍋涢顓㈠礂濮椻偓閻涱喖螖閸涱喖浜圭紓鍌欑劍椤洭宕㈤柆宥嗙厵闁煎湱澧楄ぐ褏绱掓潏銊︾闁哄懌鍎靛缁樻媴閸涘﹥鍎撻梺娲诲幖椤﹁京妲愰悙瀵哥懝闁逞屽墴閹即顢欓柨顖氫壕闁挎繂楠搁弸鐔兼煛閸涱喚鍙€闁哄本鐩崺锕傚焵椤掑嫬鐤炬繝濠傜吇婢跺绶炵€光偓閳ь剛澹曢挊澹濆綊鏁愰崨顔藉創婵炲瓨绮撶粻鏍蓟濞戞瑦鍎熼柕蹇曞Л閺嬫瑩姊洪悙钘夊姷缂佺姵鎹囬獮鍐煥閸喎鐧勬繝銏ｆ硾鐎涒晠骞夋导瀛樷拻濞达綀娅ｇ敮娑㈡煛鐏炶濮傜€规洏鍎抽埀顒婄秵閸犳牜澹曡ぐ鎺撯拺闁割煈鍣崕宥吤瑰鍕煉闁哄瞼鍠栧畷顐﹀礋椤掑顥ｅ┑鐐茬摠缁酣宕戦幘鑸靛床婵犻潧顑呴悡鏇㈡煃瑜滈崜娑欑閿曞倹鍋╅悘鐐跺亹閺佸墽绱撻崒姘偓鐑芥嚄閸撲礁鍨濇い鏍ㄧ矌閻瑩鏌熼幆褜鍤熸い鈺傜叀閺岀喖姊荤€靛壊妲梺鍛婂姀閸嬫捇姊绘笟鈧褔鈥﹂鐔剁箚闁搞儺浜楁禍褰掓倵閿濆骸鏋熼柍閿嬪笒闇夐柨婵嗘噺閸熺偤鏌熼姘卞ⅵ闁哄矉缍侀、姗€鎮滈崱妯炪劎绱撴担鎴掑惈闁稿鍋熺划顓㈡偄閻撳海鍔﹀銈嗗笒鐎氼參宕戝Ο姹囦簻闁哄啫鍊甸幏鈩冪箾缁楀搫濮傞柡灞界Х椤т線鏌涢幘璺烘灈闁诡喕鍗抽、娆撴嚍閵夈儱濡抽梻浣瑰缁诲倸煤椤撱垹鐓橀煫鍥ㄧ⊕閻撶喖骞栭幖顓炵仭闁逞屽厵閸庨潧鐣峰Ο娆炬Щ濡炪們鍎茬喊宥囨崲濠靛棭鍤楅梺鍨儐缁舵煡鏌嶈閸撴艾煤閻旈鏆︽い鏍仜缁犮儲銇勯弮鍌楁嫛婵☆偅绮撳铏圭矙鐠恒劎顔婂銈嗗灦濞叉绮嬪鍜佺叆闁割偆鍠撻崢鐢告⒑闁稓绁锋繛鍛礀閳诲秹宕ㄧ€涙鍘介棅顐㈡祫缁茶棄鈻嶅鍥ｅ亾濞堝灝娅橀柛瀣工閻ｉ攱绺界粙璇俱劑鏌ㄩ弮鍥舵綈閻庢艾銈稿娲嚒閵堝憛銏＄箾閼碱剙鈻堥柛鈹惧亾濡炪倖甯掗崯顖炴偟椤忓牊鐓熼煫鍥ㄦ尵瀛濆銈庝簻閸熷瓨淇婇崼鏇炵闁靛鍎洪悗鎶芥⒒娴ｄ警鏀版繛鍛礋閺佸啴濮€閻樺灚娈惧┑鐐叉▕娴滄粓锝為崨瀛樼厪闁割偅绻冮崳鑺ャ亜閺冣偓鐢繝寮婚敐澶嬪亹闁告瑥顦ˇ鈺呮偡濠婂嫭绶查柛鐔告尦楠炲啫螖閸滀焦鏅ｉ梺缁樺灥濡瑧鈧潧鐭傚娲濞戞艾顣哄┑鈽嗗亝閻熲晠宕哄☉銏犵婵°倓鑳堕崢鎼佹⒑閸涘﹦鐭嗙紒鈧担鍦洸婵犻潧鐗忕壕濂告煕鐏炵偓鐨戦弫鍫濐渻閵堝啫鐏俊顐㈠暙閻ｇ兘濡搁埡濠冩櫌闂佺鏈划搴ㄥ汲娴煎瓨鈷掗柛灞剧懆閸忓瞼绱掗鍛仸鐎规洘绻堥弫鍌炴偩瀹€濠冮敜婵犵數濮撮敃銈夋偋閸℃稒鍊块柛顭戝亖娴滄粓鏌熺€电浠滈柛鏂诲€濋弻宥堫檨闁告挻姘ㄩ幑銏ゅ磼閻愭潙浠奸梺缁樺灱濡嫰锝為崨鏉戠闁规儼妫勭壕濠氭煕濞戞鎽犻柣鎾存礋閺岀喖骞嶉搹顐ｇ彅婵犵鍓濈€笛囧Φ閸曨喚鍞╅梺鍛婂姧缁查箖寮埀顒佷繆閻愵亜鈧牠寮婚妸鈺嬬稏闁哄洨鍠撻々閿嬬節婵犲倻澧涢柣鎾存礋閹鏁愭惔鈥茬凹閻庤娲栭惌鍌炲蓟閳ュ磭鏆嗛悗锝庡墮閸╁矂姊虹€圭姵顥夋い锕傛涧閻ｇ兘鏁撻悩鍐测偓鐑芥倵閻㈡鐒剧紓宥呮濮婅櫣鎷犻崣澶婃敪濡炪値鍋勯ˇ顖滃弲濠殿喗銇涢崑鎾绘煛娴ｇ鏆ｅ┑顔瑰亾闂侀潧鐗嗛崯鐘诲磻閹惧瓨濯寸紒顖涙礃閻庡姊洪悷鎵憼缂佽鍟撮幃妤呭箮閼恒儳鍘告繝銏ｆ硾閿曪附鏅堕幋锔界厱闁哄倹瀵ч幑锝囩磼椤旇姤顥堢€规洩绻濋幃娆撳煛閸屾稒婢戦梻鍌欑劍鐎笛呯矙閹寸姭鍋撳鐓庡缂佸倸绉电缓浠嬪川婵犲嫬骞堝┑鐘垫暩閸嬫盯鎮樺┑瀣劦妞ゆ帊鑳剁粻鎾绘煟閿濆洤鍘存鐐差儔閺佸啴鍩€椤掑倻涓嶉柧蹇ｅ亗缁诲棝鏌曢崼婵嗏偓鍛婄妤ｅ啯鈷戠紒瀣劵椤箓鏌涢弬璺ㄧ劮闁诲繐鍟村娲箰鎼达絿鐣甸梺鐟板暱闁帮絽顕ｉ崘娴嬫瀻闁瑰濮烽敍婊堟⒑闁偛鑻晶瀵糕偓瑙勬礀閵堝憡淇婇悜鑺ユ櫆闁诡垎鍐洭濠电姷鏁告慨鐑藉极閸涘﹥鍙忛悗闈涙啞瀹曟煡鏌熼幏灞界劷闁逞屽墮閹虫﹢銆佸Δ鍛＜婵犲﹤鎷戠槐鏌ユ⒒娴ｈ櫣甯涙い顓炵墕閻ｇ兘鎮介崨濠備患闂佹儳绻愰崢婊堝磻閹炬枼鏋旈柛顭戝枟閻忓牓姊虹紒妯煎⒈闁告鍥ㄥ仼闁绘垼濮ら崑鍌炲箹鐎涙〞鎴﹀棘閳ь剟姊绘担铏广€婇柍褜鍓欑壕顓犵不閺屻儲鍊甸柛顭戝亞婢у灚鎱ㄦ繝鍕笡闁瑰嘲鎳樺畷銊︾節閸屾稒鐣奸梻浣圭湽閸╁嫰宕归幎钘夌；闁绘劕鎼悿楣冩偣鏉炴壆绉块柡鍐ㄧ墕缁€鍐煃鏉炴壆顦︾紒銊ｅ劜缁绘繈鎮介棃娑掓瀰濠电偘鍖犻崶鑸垫櫈闂佸憡绋戦悺銊╁磹閸ф鐓曟い顓熷灥娴滄牕霉濠婂嫮鐭掗柡宀€鍠栧畷顐﹀礋椤撳鍊濋弻娑㈠棘鐠恒剱銈囩磼鏉堛劍灏い鎾炽偢瀹曘劑顢欓崗鑲╀悍闂傚倷绀侀幖顐﹀嫉椤掆偓鐓ゆ俊顖氬悑瀹曞弶绻涢幋鐐电煠婵℃彃顭峰娲传閸曨厼鈷堥梺鍛婃尵閸犳牕顕?
				File diskFile = new File(diskFilePath);
				if (diskFile.exists()) {
					imageBytes = Files.readAllBytes(diskFile.toPath());
				}
			}
			byte[] composedPic = logo != null ? TaoImage.composePic(image, imageBytes) : null;

			media.setContent(composedPic != null ? composedPic : tContent);
			media.setFilesize(media.getContent().length);
		} catch (Exception e) {
			TaoDebug.error("got exception when resizing the image! e:{}", e);
		}
		media.setPerson(person);
		mediaUploadService.persist(media);
	}

	// ===========alive check======================
	@RequestMapping(value = "stgocheck/{keyStr}", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<String> stgocheck(@PathVariable("keyStr") String keyStr, HttpServletRequest request) {
		if (!keyStr.equals("stgo"))
			return null;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");

		Person person = personService.findPerson(1l);
		Customize customize = customizeService.findCustomizeByKeyAndPerson(keyStr, person);
		// for first time, put in how long it has run.
		if (customize == null) {
			customize = new Customize();
			customize.setPerson(person);
			customize.setCusKey("stgo");
			customize.setCusValue("s");
			customizeService.persist(customize);
		}
		return new ResponseEntity<String>(customizeService.toJson(customize), headers, HttpStatus.OK);
	}

	// ===========person db bk======================
	@RequestMapping(value = "persondbbk/{accountInfo}", headers = "Accept=application/json")
	@ResponseBody
	public ResponseEntity<String> personDBBKout(@PathVariable("accountInfo") String accountInfo,
			HttpServletRequest request) {
		Person person = TaoDbUtil.verifyAccountInfo(accountInfo);
		if (person == null)
			return null;
		// verify passed! ------------------------
		// header
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json; charset=utf-8");

		// prepare the json String.
		Collection<String> collection = new ArrayList<String>();

		// useraccounts
		List<UserAccount> userAccounts = userAccountService.findAllUserAccountsByPerson(person);
		if (userAccounts == null)
			userAccounts = new ArrayList<UserAccount>();
		String tUserAccountJsonAryStr = UserAccount.toJsonArray(userAccounts);
		collection.add(tUserAccountJsonAryStr);

		// customize
		List<Customize> customizes = customizeService.findAllCustomizesByPerson(person);
		if (customizes == null)
			customizes = new ArrayList<Customize>();
		String tCustomizesJsonAryStr = customizeService.toJsonArray(customizes);
		collection.add(tCustomizesJsonAryStr);

		// feature
		List<Feature> features = featureService.findAllFeaturesByPerson(person);
		if (features == null)
			features = new ArrayList<Feature>();
		String tFeaturesJsonAryStr = new JSONSerializer().exclude("*.class").serialize(features);
		collection.add(tFeaturesJsonAryStr);

		// mainOrder
		List<MainOrder> mainOrders = mainOrderService.findMainOrdersByStatusAndPerson(-1, person, "DESC");
		if (mainOrders == null)
			mainOrders = new ArrayList<MainOrder>();
		String tMainOrdersJsonAryStr = new JSONSerializer().exclude("*.class").serialize(mainOrders);
		collection.add(tMainOrdersJsonAryStr);

		// material
		// taxonomyMaterial
		List<Material> materials = new ArrayList<Material>();
		List<TaxonomyMaterial> taxonomyMaterials = new ArrayList<TaxonomyMaterial>();
		for (MainOrder mainOrder : mainOrders) {
			List<Material> tMaterials = materialService.findAllMaterialsByMainOrder(mainOrder);
			List<TaxonomyMaterial> tTaxonomyMaterials = taxonomyMaterialService.findAllTaxonomyMaterialsByMainOrder(mainOrder);
			if (tMaterials != null) {
				for (Material material : tMaterials) {
					materials.add(material);
				}
			}
			if (tTaxonomyMaterials != null) {
				for (TaxonomyMaterial taxonomyMaterial : tTaxonomyMaterials) {
					taxonomyMaterials.add(taxonomyMaterial);
				}
			}
		}
		String tMaterialsJsonAryStr = new JSONSerializer().exclude("*.class").serialize(materials);
		collection.add(tMaterialsJsonAryStr);
		String tTaxonomyMaterialsJsonAryStr = new JSONSerializer().exclude("*.class").serialize(taxonomyMaterials);
		collection.add(tTaxonomyMaterialsJsonAryStr);

		// service
		List<Service> services = serviceService.findServiceByPerson(person);
		if (services == null)
			services = new ArrayList<Service>();
		String tServicesJsonAryStr = Service.toJsonArray(services);
		collection.add(tServicesJsonAryStr);

		// textContent
		List<TextContent> textContents = textContentService.findAllMatchedTextContents("%", null, person);
		if (textContents == null)
			textContents = new ArrayList<TextContent>();
		String tTextContentJsonAryStr = TextContent.toJsonArray(textContents);
		collection.add(tTextContentJsonAryStr);

		// mediaUploads
		List<MediaUpload> mediaUploads = mediaUploadService.findAllMediaUploadByPerson(person);
		if (mediaUploads == null)
			mediaUploads = new ArrayList<MediaUpload>();
		String tMediaUploadJsonAryStr = mediaUploadService.toJsonArray(mediaUploads);
		collection.add(tMediaUploadJsonAryStr);

		// return
		String jsonStr = new JSONSerializer().exclude("*.class").serialize(collection);
		return new ResponseEntity<String>(jsonStr, headers, HttpStatus.OK);
	}

	/*
	 * seems like when no image set for generalServicePage, it will send out request
	 * like getImage/{id} (id is empty). in that case, it will be considered as a
	 * client, and go into systemCommandCheck.
	 */
	private String systemCommandCheck(Model model, HttpServletRequest request, String commandStr) {
		if ("login".equals(commandStr) || "signup".equals(commandStr) || "printer_debug".equals(commandStr) || "ui_debug".equals(commandStr)) {
			return commandStr;
		} else if ("customizes".equals(commandStr)) {
			CustomizeController customizeController = SpringApplicationContext.getApplicationContext()
					.getBean("customizeController", CustomizeController.class);
			return customizeController.list(null, null, "cusKey", "ASC", model, request);
		} else if ("style2".equals(commandStr)) {
			String tUserName = userContextService.getCurrentUserName();
			Person person = taoUtil.getCurPerson(request);
			if (person.getName().equalsIgnoreCase(tUserName)) {
				styleUtil.initializeToStyle2(request, person);
			}
		} else if ("style3".equals(commandStr)) {
			String tUserName = userContextService.getCurrentUserName();
			Person person = taoUtil.getCurPerson(request);
			if (person.getName().equalsIgnoreCase(tUserName)) {
				styleUtil.initializeToStyle3(request, person);
			}
		} else if ("style4".equals(commandStr)) {
			String tUserName = userContextService.getCurrentUserName();
			Person person = taoUtil.getCurPerson(request);
			if (person.getName().equalsIgnoreCase(tUserName)) {
				styleUtil.initializeToStyle4(request, person);
			}
		}  else if ("stylek5".equals(commandStr)) {
			String tUserName = userContextService.getCurrentUserName();
			Person person = taoUtil.getCurPerson(request);
			if (person.getName().equalsIgnoreCase(tUserName)) {
				styleUtil.initializeToStyleK5(request, person);
			}
		} else if (commandStr.startsWith("bkDB:")) {
			String tUserName = userContextService.getCurrentUserName();
			Person person = taoUtil.getCurPerson(request);
			if (tUserName == null || person == null || !tUserName.equalsIgnoreCase(person.getName())) {
				return null;
			}
			// if login with some person's name and password, then input a command like:
			// "bkDB:www.shaerthegoodones.com", it means to download all the records of the
			// current user from www.sharethegoodones.com to this host. it includs all
			// records in table:
			// userAccounts, customizes, features, mainOrders, materials, mediaupload,
			// person, service,
			// taxonomymaterial, textcontents.
			commandStr = commandStr.endsWith(".com/") ? commandStr
					: (commandStr.endsWith(".com") ? commandStr + "/" : commandStr + ".com/");
			commandStr = commandStr.endsWith("localhost:91.com/") ? "bkDB:localhost:91/taostyle/" : commandStr;
			String url = new StringBuilder("https://").append(commandStr.substring(5)).append("persondbbk/")
					.append(person.getName()).append(":")
					.append(TaoEncrypt.getURLFriendlyPassword(person.getPassword())).toString();
			// the code following is just send out a request.

			ClientConfig config = new DefaultClientConfig();
			Client client = Client.create(config); // if we need a ssh, can go on work in this part.

			WebResource webResource = client.resource(url);
			webResource.accept("application/json"); // other methods in controller.

			ClientResponse response = webResource.type("application/json").get(ClientResponse.class);
			if (200 == response.getStatus()) {
				List<String> tList = new JSONDeserializer<List<String>>().use(null, ArrayList.class)
						.use("values", String.class).deserialize(response.getEntity(String.class));

				// the following code is to update into local db.
				int result = TaoDbUtil.saveToLocalDB(person, tList, "http://" + commandStr.substring(5), person);
				System.out.println("saving to localDB result is :" + result);
			}
		} else if (commandStr.startsWith("copyDB:")) {
			commandStr = commandStr.substring(7);
			String[] account = commandStr.split(":");
			
			String tUserName = userContextService.getCurrentUserName();
			Person person = taoUtil.getCurPerson(request);
			if (tUserName == null || person == null || !tUserName.equalsIgnoreCase(person.getName())) {
				return null;
			}
			// if login with some person's name and password, then input a command like:
			// "copyDB:targetaccount,password", it means to download all the records of the
			// targetaccount from www.sharethegoodones.com to this host. it includs all
			// records in table:
			// userAccounts, customizes, features, mainOrders, materials, mediaupload,
			// person, service,
			// taxonomymaterial, textcontents.
			String url = new StringBuilder("https://www.shareTheGoodOnes.com/").append("persondbbk/")
					.append(account[0]).append(":").append(TaoEncrypt.getURLFriendlyPassword(TaoEncrypt.encryptPassword(account[1]))).toString();
			// the code following is just send out a request.

			ClientConfig config = new DefaultClientConfig();
			Client client = Client.create(config); // if we need a ssh, can go on work in this part.

			WebResource webResource = client.resource(url);
			webResource.accept("application/json"); // other methods in controller.

			ClientResponse response = webResource.type("application/json").get(ClientResponse.class);
			if (200 == response.getStatus()) {
				List<String> tList = new JSONDeserializer<List<String>>().use(null, ArrayList.class)
						.use("values", String.class).deserialize(response.getEntity(String.class));

				// the following code is to update into local db.
				int result = TaoDbUtil.saveToLocalDB(personService.findPersonByName(account[0]), tList, "https://www.shareTheGoodOnes.com/", person);
				System.out.println("saving to localDB result is :" + result);
			}
		} else if ("cleanDebugInfo".equals(commandStr)) {
			TaoDebug.resetSB(request.getSession());
		}else if(commandStr.startsWith("mediaMoving")){
			mediaUploadService.mediaMoving(Integer.valueOf(commandStr.substring("mediaMoving".length())));
		} else if(commandStr.startsWith("dbBatchDance_")) {
			//prepare a new map, for check is there's existing record with this position.
		    HashMap<String, String> map = new HashMap<String, String>();
		    //for recognize the parameters, so to tell what the command want to do
		    Person person = taoUtil.getCurPerson(request);
		    String sourceStr = commandStr.substring("dbBatchDance_".length());
		    String targetStr = "";
		    int p = sourceStr.indexOf("--");
		    if(p > 0) {
		    	targetStr = sourceStr.substring(p + 2);
		    	sourceStr = sourceStr.substring(0, p);
		    }
		    sourceStr = sourceStr.replace('-', '%');
	    	p = sourceStr.indexOf('%');
	    	String prefix = sourceStr.substring(0, p);   //find prefix闂?闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧綊鏌熼梻瀵割槮缁炬儳缍婇弻鐔兼⒒鐎靛壊妲紒鐐劤缂嶅﹪寮婚悢鍏尖拻閻庨潧澹婂Σ顔剧磼閻愵剙鍔ょ紓宥咃躬瀵鎮㈤崗灏栨嫽闁诲酣娼ф竟濠偽ｉ鍓х＜闁绘劦鍓欓崝銈囩磽瀹ュ拑韬€殿喖顭烽幃銏ゅ礂鐏忔牗瀚介梺璇查叄濞佳勭珶婵犲伣锝夘敊閸撗咃紲闂佺粯鍔﹂崜娆撳礉閵堝洨纾界€广儱鎷戦煬顒傗偓娈垮枛椤兘骞冮姀銈呯閻忓繑鐗楃€氫粙姊虹拠鏌ュ弰婵炰匠鍕彾濠电姴浼ｉ敐澶樻晩闁告挆鍜冪床闂備胶绮崝锕傚礈濞嗘挸绀夐柕鍫濇川绾剧晫鈧箍鍎遍幏鎴︾叕椤掑倵鍋撳▓鍨灈妞ゎ厾鍏樺顐﹀箛椤撶偟绐炴繝鐢靛Т鐎氱兘宕ラ崨瀛樷拻濞达絿鎳撻婊呯磼鐠囨彃鈧潡鐛径濞炬闁靛繒濮烽鎺旂磽閸屾瑧鍔嶆い顓炴喘瀹曘垽鏌嗗鍡忔嫼闂傚倸鐗婄粙鎾存櫠閺囥垺鐓欓柛鎰叀閸欏嫭銇勯姀鈩冾棃妞ゃ垺锕㈡慨鈧柨娑樺楠炴劙姊虹拠鑼闁稿绋掗弲鍫曟寠婢规繆娅ｉ埀顒佺⊕鑿уù婊勭矒閺屾洝绠涙繝鍌氣拤缂備讲鍋撻悗锝庡枟閻撴稑霉閿濆洦鍤€濠殿喖绉堕埀顒冾潐濞叉牕鐣烽鍕厺閹肩补鍨鹃悢鐓庣畳鐎广儱妫欓惃鎴犵磼鏉堛劍宕岀€规洘甯掗埢搴ㄥ箳閹存繂鑵愰梻鍌欒兌椤牓顢栭崱娑樼闁搞儜鍛闂佸搫娲㈤崹鍦不椤曗偓閹嘲鈻庡▎鎴濈煗闂佺顑嗛幑鍥极閸愵喖纾兼繛鎴炃氶崑鎾寸節濮橆厾鍙冨┑鈽嗗灟鐠€锕€危婵傚憡鐓欓柤鎭掑劜缁€瀣叏婵犲啯銇濈€规洦鍋婂畷鐔碱敆閳ь剛绮ｅ☉銏♀拺闁告縿鍎辨牎闂佸湱顭堟晶浠嬪礆閹烘梻纾兼俊顖滎焾娴滄繈姊洪崨濠傚闁哄懏绻堝畷銏＄鐎ｎ偀鎷洪梻渚囧亝缁嬫垵鐣甸崱妯肩濞达絽鍟跨€氼厼鈻嶉悩鐐戒簻闁哄稁鍋勬禒锕傛煟閹捐泛鏋戠紒缁樼箞濡啫鈽夊▎蹇ｆП闂備焦濞婇弨杈╂暜閻愬灚顫曢柟鐑樻尰缂嶅洭鏌曟繛鍨姕閻犲洨鍋ゅ铏规嫚閳ヨ櫕鐏嶉梺鑽ゅ暱閺呯娀濡存担鑲濇棃宕ㄩ鐘插Е婵＄偑鍊栫敮鎺斺偓姘煎墰缁牊寰勯幇顓犲帾闂佸壊鍋呯换鍐夐悩鐢电＜闁绘ɑ褰冮埛鏃堟煟閵夘喕閭い銏★耿婵偓闁冲搫鍋嗗Λ鏍磽閸屾瑨顔夋俊鐙欏洤纾婚柟鍓х帛閻撶喖骞栧ǎ顒€鐒洪柛鐔风箻閺屾盯鎮╁畷鍥р拰闂佺偨鍎荤粻鎾诲蓟閵娧€鍋撻敐鍌涙珖缂佺姵宀稿娲礈閹绘帊绨煎┑鐐插级閻楃娀骞冨▎鎰瘈闁告洦鍓欓弸鎴︽煙閸忚偐鏆橀柛鏂跨Ч瀹曟劙鎮介崨濠勫幍闂佽鍨卞妯款暱闂備焦濞婇弨閬嶅垂閸ф钃熸繛鎴炃氬Σ鍫熸叏濡も偓濡盯宕ｉ弴鐐╂斀闁绘劘灏欐晶娑㈡煥閺囨ê鐏茬€殿喛顕ч埥澶婎潩閿濆懍澹曢梺鎸庣箓妤犲憡绂嶉悙瀵哥闁告瑥顦扮亸锕傛煛鐏炶濡奸柍瑙勫灴瀹曠厧鈹戦崨顕呮綗濠碉紕鍋戦崐鎴﹀垂閸濆娊娲偄闂€鎰畾闂佸壊鍋呭ú鏍倷婵犲洦鐓冮柛婵嗗閸庢垿姊婚崒銈呯仭缂佺粯绻堥幃浠嬫濞戞鎽嬮梻浣筋潐濡炴寧绂嶉悙宸殫濠电姴鍟伴々鐑芥倵閿濆簼绨芥い鏃€鍔曢—鍐Χ閸℃衼缂備礁寮剁€笛囧疾閸洖鐏抽柡鍌樺劜閺傗偓闂備胶绮摫妞ゆ梹鐗犲鎶筋敍濞戞绠氶梺鍦帛鐢宕甸崶鈹惧亾鐟欏嫭绀冮柛銊ョ仢閻ｇ兘鎮㈢喊杈ㄦ櫖濠殿喗顭堟ご鎼佹倿妤ｅ啯鈷掑ù锝呮啞閹牊銇勯敂鍨祮鐎规洘妞藉畷濂稿Ψ閵夈儲鍎梻浣告惈濞层劑宕戦幇鏉跨哗濞寸姴顑嗛悡鐔镐繆椤栨繃顏犻柨娑樼У閵囧嫰鏁愰崱娆忓绩闂佸搫鏈粙鎾诲焵椤掑﹦绉靛ù婊勭矒椤㈡棃顢橀姀锛勫幐闁诲繒鍋涙晶浠嬧€栭懖鈺冪＜闁绘ê纾ú瀵糕偓娈垮枟閹告娊骞冨▎鎾跺彄妞ゆ挾濮寸粭鈩冪節閻㈤潧啸闁轰焦鎮傚畷鎴濃槈閵忊€充罕闂佸憡娲﹂崹鎵閸ф鈷掗柛顐ゅ枍缁惰鲸绻涢崗鑲╁缂佺粯绋戦蹇涱敊閼姐倕鍤俊鐐€戦崕閬嶆偋婵犲嫭宕叉繛鎴欏灩缁狅綁鏌ｉ幇顖涚【妞ゃ儲绻傞—鍐Χ韫囨洜蓱濠碉紕鍋樼划娆忣嚕婵犳碍鏅查柛鈩兠崝鍛存⒑闂堟稓绠氶柟鍐叉喘瀵悂寮崼鐔叉嫼闂佸憡绻傜€氼參宕抽挊澶嗘斀妞ゆ棁濮ょ亸锔姐亜閵忊€冲摵闁轰焦鍔栧鍕熺紒妯荤彇闂佽绻愮换妯肩不閺嶃劋绻嗛柟闂寸閻撴盯鏌涚仦鍓х叝闁告牗鐗犲缁樻媴閻戞ê娈岄梺鍛婅壘椤戝鐣?+ key + "_闂?
	    	String affix = sourceStr.substring(p + 1);
	    	
		    
		    if(targetStr.length() < 1) {				//case1: to get rid of the duplicated records.
			    //get all the records first.
			    List<TextContent> list = textContentService.findAllMatchedTextContents(sourceStr, null, person);//langPrf + key + "_-_description" e.g. en_service_2_0_0_-_description
		    	for (TextContent textContent : list) {
			        String posInPage = textContent.getPosInPage();
			        //find tail, "_description"
			        p = posInPage.lastIndexOf('_');
			        String tail = posInPage.substring(p);
		            posInPage = posInPage.substring(0, p); //---------posInPage become "langPrf + key + "_%"
		            //find keykey, whichi is the '%' in parameter.
		            p = posInPage.lastIndexOf('_');
		            String keykey = posInPage.substring(p + 1);
		            
		            if(map.containsKey(posInPage + tail)) {	
		            	for(int i = 0; i < 10; i++) {
		            		String newKey = prefix + keykey + i % 10 + tail;
		            		if(!map.containsKey(newKey)) {
		            			textContent.setPosInPage(newKey);
		            			textContentService.persist(textContent);
				            	map.put(newKey, "");
		            			break;
		            		}
		            	}
		            	TaoDebug.error("can not find a new key by adding 0~9 at the end of existing key", posInPage + tail);
		            }else {
		            	map.put(posInPage + tail, "");
		            }
			    }
		    }else if(affix.length() > 0){
			    //get all the records first.
			    List<TextContent> list = textContentService.findAllMatchedTextContents(sourceStr, null, person);//langPrf + key + "_-_description" e.g. en_service_2_0_0_-_description
		    	for (TextContent textContent : list) {
			        String posInPage = textContent.getPosInPage();
			        if(posInPage.startsWith(prefix) && posInPage.endsWith(affix)) {
			        	String newPrefix = prefix.substring(0, prefix.length() - targetStr.length()) + targetStr;
			        	posInPage = newPrefix + posInPage.substring(newPrefix.length());
            			textContent.setPosInPage(posInPage);
            			textContentService.persist(textContent);
			        }
			    }
		    }else {
			    //get all the records first.
//			    List<Service> list = serviceService.findServiceByCatalogAndPerson(sourceStr, person);  //for now, I can use replace all to achive this goal.
		    }
		}
		return null;
	}
}




