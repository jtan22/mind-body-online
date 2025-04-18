package com.bw.mindbodyonline;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;

public interface MindBodyOnlineConstants {

    String BASE_URL					    = "https://cart.mindbodyonline.com/sites/80603/";
    String URL_GET_NEW_SESSION			= BASE_URL + "session/new";
    String URL_POST_LOGIN				= BASE_URL + "session";
    String URL_GET_CART_AREA			= BASE_URL + "cart_area";
    String URL_GET_CLIENT_EDIT			= BASE_URL + "client/edit";
    String URL_GET_ADD_BOOKING			= BASE_URL + "cart/add_booking";
    String URL_GET_CART				    = BASE_URL + "cart";
    String URL_GET_PROCEED_TO_CHECKOUT	= BASE_URL + "cart/proceed_to_checkout";
    String URL_GET_SELECT_SERVICE		= BASE_URL + "cart/select_service";
    String URL_GET_ADD_ITEM			    = BASE_URL + "cart/add_item";
    String URL_GET_SELECT_PAYMENT		= BASE_URL + "cart/select_payment";
    String URL_GET_SCHEDULES			= BASE_URL + "client/schedules";
    String URL_POST_LOGOUT				= BASE_URL + "session";

    String FORM_KEY_UTF8				= "utf8";
    String FORM_KEY_TOKEN				= "authenticity_token";
    String FORM_KEY_REDIRECT			= "redirect";
    String FORM_KEY_USERNAME			= "mb_client_session[username]";
    String FORM_KEY_PASSWORD			= "mb_client_session[password]";
    String FORM_KEY_METHOD				= "_method";
    String FORM_KEY_MBO_ITEM			= "mbo_item";

    String FORM_VALUE_UTF8				= "✓";
    String FORM_VALUE_REDIRECT			= "https://brandedweb-next.mindbodyonline.com/";
    String FORM_VALUE_METHOD			= "delete";

    String PARAM_UTF8					= "utf8=%E2%9C%93";

    Map<String, String> SESSION_TYPES	= Map.of(
            "90", "75",
            "60", "76",
            "30", "77");
    Map<String, String> COURT_NUMBERS	= Map.of(
            "1", "100000040",
            "2", "100000041",
            "3", "100000042",
            "4", "100000043",
            "5", "100000044",
            "6", "100000045");
    Map<String, String> MBO_ITEMS	= Map.of(
            "90", "pricing_option-100503",
            "60", "pricing_option-100194",
            "30", "pricing_option-100502");

    DateFormat INPUT_DATE_FORMAT		= new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    DateFormat CONFIRM_DATE_FORMAT		= new SimpleDateFormat("EEEE M/d/yyyy");
    DateFormat CONFIRM_TIME_FORMAT		= new SimpleDateFormat("h:mm a");

    String CHECKOUT_COMPLETE			= "checkout_complete";

}
