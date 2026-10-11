package com.stgo.taostyle.web;

public interface CC {
    String ADAM = "sharethegoodones";
    String EVE = "for_demo";

    String SEP = ";";
    String limit_same_source = "limit_same_source";
    String auto_print = "auto_print";
    String auto_combine_print = "auto_combine_print";
    String auto_combine_rec = "auto_combine_rec";
    int UNLIMITED_UPLOD_SIZE = 100;
    String UNIQ_SEP = "_S_T_G_O_";
    String SB = "debugInfo";
    String MaxInactiveInterval = "MaxInactiveInterval";
    String DOMAIN_NAME = "sharethegoodones.";
    String TextContentKey = "posInPage";
    String MediaUploadKey = "filepath";
    String user_role = "user_role";
    String ROLE_CLIENT = "ROLE_CLIENT";
    String ROLE_PARTNER = "ROLE_PARTNER";
    String ROLE_EMPLOYEE = "ROLE_EMPLOYEE";
    String ROLE_ADMIN = "ROLE_ADMIN";
    String ROLE_MANAGER = "ROLE_MANAGER";
    String ROLE_PRINTER = "ROLE_PRINTER";

    // --------------------------------------
    String HTML = "HTML";

    String GALLERY = "GALLERY";
    String CATALOG = "CATALOG";
    String FEATURE = "FEATURE";
    String PRODUCT = "PRODUCT";
    String SERVICE = "SERVICE";
    String RESEARCH = "RESEARCH";
    String LOCATION = "LOCATION";
	String HTML_SERVICE = "HTML_SERVICE";
	String FORM = "FORM";

    String ADMIN = "sharethegoodones"; // used only for openning a new account (person)
    String CLIENT = "client";
    String debugFlag = "debugFlag";
    String debugInfo = "debugInfo";
    String currentUser = "currentUser";
    String selectedItems = "selectedItems";
	String selectedNotes = "selectedNotes";
	String noteOfSelectedItems = "noteOfSelectedItems";
    String notes = "notes";
    String itemNumber = "itemNumber";
    String totalPrice = "totalPrice";
    String endPrice = "endPrice";
    String latitude = "latitude";
    String longitude = "longitude";
    String hideTitleInSelection = "hideTitleInSelection";

    String app_name = "app_name";
    String app_ContentManager = "app_ContentManager";
    String app_ManagerEmail = "app_ManagerEmail";
    String app_WebsiteAddress = "app_WebsiteAddress";
    String DEFAULT_LANG = "DEFAULT_LANG";
    String default_feature_menu = "default_feature_menu";
    String show_status_message1 = "show_status_message1";
    String show_status_message2 = "show_status_message2";
    String show_status_category1 = "show_status_category1";
    String show_status_category2 = "show_status_category2";

    String flash_1_URL = "flash_1_URL";
    String LANG = "lang";
    String menuIdx = "menuIdx";
    String position = "position";
    String W = "w";
    String H = "h";

    String CALCULAT_EXIST_NEEDED_ = "CALCULAT_EXIST_NEEDED_";
    String LANGUAGE_PRF_NEEDED_ = "LANGUAGE_PRF_NEEDED_";
    String THUMNEEDED_ = "THUMNEEDED_";

    String TEMP_SEP_PROD_FIELD_INFO = "^";

    String DEFAULT_IMAGE_TYPE = ".jpg";

    String STATUS_NEW = "new submit";
    String STATUS_WAITING = "waiting for review";
    String STATUS_COMMENTED = "reviewed";
    String STATUS_FULL = "FULL";
    String STATUS_MINE_ARE_FULL = "next";
    String STATUS_DELETED = "deleted";
    int STATUS_CHANGED_PLACE = 1;//when employee open a new empty order, should be this status, so waiter logged in dashboard can now some order with old table
    //number should be merged into this order. it's not so useful when there's a pos system which automatically download every 20 secs. but useful when waiter 
    //use only web print to manage restaurant.
    int STATUS_TO_PRINT = 10;
    int STATUS_PRINTED = 20;
    int STATUS_PAID = 50;
    int LEVEL_FULL = 120;

    String APPEND = "append";
    String OVERWRITE = "overwrite";
    String APPEND_SAME = "append_same";
    String APD = "APD_";

    String singleUploadUri = "mediauploads";
    String multiUploadUri = "gellaryUploads";
    String resetQRCode = "resetQRCode";

    String MAP_POS = "MAP_POS";
    String MAP_POS_X = "MAP_POS_X";
    String MAP_POS_Y = "MAP_POS_Y";

    String showDescriptionsOnBottom = "showDescriptionsOnBottom";
    String is_depanneur_site = "is_depanneur_site";
    String show_lucky_wheel = "show_lucky_wheel";
    String lucky_wheel_prizes = "lucky_wheel_prizes";
    String lucky_pending_prize = "lucky_pending_prize";
    String lucky_pending_prize_at = "lucky_pending_prize_at";
    String lucky_pending_prize_cookie = "stgo_lucky_prize";
    String lucky_pending_prize_cookie_time = "stgo_lucky_prize_at";
    String lucky_status_won = "WON";
    String lucky_status_claimed = "CLAIMED";
    String lucky_status_expired = "EXPIRED";

    // --------------------------------
    String MinDiffForOrentationCheck = "minDiff_Orentation";
    String rotateDirection = "rotateDirection";

    String cloak = "cloak";
    String delete = "delete";
    String defaultservice = "defaultservice";
    String SAVED_NAME_STR = "*";
    String app_Website_map = "app_Website_map";
    String known_robot_names = "known_robot_names";

    public enum customizes {
        app_ContentManager("tao"),
        app_ManagerEmail("info@ShareTheGoodOnes.com"),
        background_app("E5E5E5"),
        background_foot("666666"),
        background_footArea("888888"),
        flashpage_h("528"),
        flashpage_w("845"),
        foreground("fff"),
        foreground_app("000"),
        foreground_foot("fff"),
        foreground_footArea("fff"),
        indicate_language_with_img(""),
        IsTextPositionAbsolute("commont-absolute"),
        link_log1(""),
        link_log2("https://teams.sharethegoodones.com"),
        link_log3(""),
        margin_left("10"),
        margin_right("10"),
        show_1_imgObject(""),
        show_1_TextObject(""),
        show_3swicher(""),
        show_3_Object("10"),
        show_3_Object_inSmallerRow(""),
        show_3_Object_text("10"),
        show_4_Object(""),
        show_MainSlider("50"),
        show_Menu("0"),
        show_myDocs_toClient("false"),
        show_SmallSlider(""),
        showFacebookOnTop(""),
        showGooglePlusOnTop(""),
        showGoogleSearchOnTop(""),
        showSearchOnHomePage(""),
        show_SearchBox(""),
        showTextOnSlider("620"),
        showTwitterOnTop(""),
        background_menu("888888"),
        foreground_menu("fff"),
        flash_1_URL(""),
        background_leftbar("999999"),
        background_pop("999999"),
        background_subpage("999999"),
        footer_h("90"),
        forground_subpage("444444"),
        MAP_h("270"),
        Center_img_w("60%"),
        show_registerForm("true"),
        stgo("s"),
        MAP_w("285"),
        MAP_zoom("16"),
        MAP_POS_X3("31.2360716"),
        MAP_POS_Y3("121.511461"),
        margin_top_logo("0"),
        showDescriptionsOnBottom("true"),
        is_depanneur_site("false"),
        show_lucky_wheel("false"),
        lucky_wheel_prizes(""),
        support_subMenu("false"),
        service_number_lg("3"),
        service_number_md("3"),
        service_number_sm("6"),
        service_number_xs("6"),
        show_QRCode("true"),
        app_WebsiteAddress("https://www.shareTheGoodOnes.com"),
        app_name("ShareTheGoodOnes"),
        CONTENTTYPE_1("MAINPAGE"),
        CONTENTTYPE_2("SERVICE"),
        CONTENTTYPE_3("FEATURE"),
        CONTENTTYPE_10("LOCATION"),
        MAP_POS_X2("45.497743"),
        MAP_POS_Y2("-73.554106");

        private String cusValue;

        private customizes(String cusValue) {
            this.cusValue = cusValue;
        }

        public String getValue() {
            return cusValue;
        }
    };

    public enum money {
        en("$"), fr("$"), zh("$"), it("$");

        private String moneyLetter;

        private money(String moneyLetter) {
            this.moneyLetter = moneyLetter;
        }

        public String getValue() {
            return moneyLetter;
        }
    };

    // -------------------------------
    String tableID = "tableID";
	String TABLESELECTION = "TABLESELECTION";
	String ShowTableOnBill = "ShowTableOnBill";
	String GST = "GST";
	String QST = "QST";
	String PrinterSN = "PrinterSN";
	String PrinterWidth = "PrinterWidth";
	String PrintTimes = "PrintTimes";
	String IgnoreFlag1 = "IgnoreFlag1";
	String IgnoreFlag2 = "IgnoreFlag2";
	String OrderNumber = "OrderNumber";
	String ShowAboveMenu = "show_AboveMenu";
	String TRUE = "true";
	String ContentToPrint = "ContentToPrint";
	String MainOrder = "MainOrder";
	String client_id = "client_id";
	String client_secret = "client_secret";
	String profileID = "profileID";
	String maxMainImageWidth = "maxMainImageWidth";
	String maxMainImageHeight = "maxMainImageHeight";
	String paypalOrderId = "paypalOrderId";
	String LabelPrinter = "labelPrinter";
	String service_height = "service_height";
	String show_service_cBox = "show_service_cBox";
	String SMSList = "SMSList";
	String TIMEZONE = "timezone";
}
