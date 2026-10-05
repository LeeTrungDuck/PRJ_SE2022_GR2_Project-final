/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utills;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author ltrun
 */
public class URLMap {

    private static final String LOGIN_PAGE = "web/login.jsp";
    private static final String ERROR_PAGE = "error.jsp"; // sau nay them
    private final Map<String, String> urlMap = new HashMap<>();


    public URLMap() {
        urlMap.put("LOGIN", "LoginController");
        urlMap.put("DEVICE_CONTROL","web/DeviceControl.jsp" ); // sua thanh controller sau
    }
    
    public String getUrl(String action){
        return urlMap.get(action);
    }

    public static String getLOGIN_PAGE() {
        return LOGIN_PAGE;
    }

    public static String getERROR_PAGE() {
        return ERROR_PAGE;
    }
    

}
