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
    private static final String ERROR_PAGE = "web/errorPage.jsp";
    private final Map<String, String> urlMap = new HashMap<>();

    public URLMap() {
        urlMap.put("LOGIN", "LoginController");
        urlMap.put("LOGOUT", "LogOutController");
        urlMap.put("DEVICE_CONTROL", "/DeviceControlController");
        urlMap.put("USER_MANAGER", "UserManager.jsp");
        urlMap.put("PERMISSION_MANAGER", "PermissionManager.jsp");
        urlMap.put("ACCOUNT_MANAGER", "AccountManager.jsp");
        urlMap.put("DEVICE_MANAGER", "DeviceManager.jsp");
        urlMap.put("SCHEDULE_MANAGER", "ScheduleManager.jsp");
        urlMap.put("CONTROL_HISTORY", "ControlHistory.jsp");
        
        

    }

    public String getUrl(String action) {
        return urlMap.get(action);
    }

    public static String getLOGIN_PAGE() {
        return LOGIN_PAGE;
    }

    public static String getERROR_PAGE() {
        return ERROR_PAGE;
    }

}
