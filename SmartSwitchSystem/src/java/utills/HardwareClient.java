package utills;

import exception.HardwareException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HardwareClient
 *
 * Dùng để Java Web giao tiếp với ESP32
 * thông qua HTTP.
 *
 * Tương thích JDK 8.
 *
 * ESP32 API:
 *
 * GET /switch/on?gpio=2
 * GET /switch/off?gpio=2
 * GET /switch/status?gpio=2
 *
 * GET /device/status
 * GET /device/info
 *
 * GET /schedule/add?id=1&gpio=2&time=07%3A30&action=ON&enabled=true
 * GET /schedule/delete?id=1
 */
public class HardwareClient {

    // ==================================================
    // CONFIGURATION
    // ==================================================

    private final String baseUrl;
    private static final int TIMEOUT_MS = 3000;
    private static final Pattern DEVICE_ONLINE_PATTERN = Pattern.compile(
            "\"status\"\\s*:\\s*\"online\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SWITCH_STATUS_PATTERN = Pattern.compile(
            "\\{\\s*\"gpio\"\\s*:\\s*(\\d+)\\s*,\\s*\"status\"\\s*:\\s*\"(on|off)\"\\s*\\}",
            Pattern.CASE_INSENSITIVE
    );

    public HardwareClient(String hostName)
            throws HardwareException {

        if (hostName == null
                || hostName.trim().isEmpty()) {

            throw new HardwareException(
                    "Host name khong duoc de trong"
            );
        }


        hostName = hostName.trim();


        // Nếu người dùng đã truyền http://
        // thì không thêm lần nữa.

        if (hostName.startsWith("http://")
                || hostName.startsWith("https://")) {

            this.baseUrl = hostName;

        } else {

            this.baseUrl =
                    "http://" + hostName;
        }
    }


    // ==================================================
    // SWITCH ON
    // ==================================================

    /**
     * Bật switch tại GPIO.
     *
     * Ví dụ:
     *
     * HardwareClient client =
     *      new HardwareClient(
     *          "esp32-switch1.local"
     *      );
     *
     * client.turnOn(2);
     *
     * Java gửi:
     *
     * GET
     * http://esp32-switch1.local/
     * switch/on?gpio=2
     */
    public boolean turnOn(int gpio) throws HardwareException{
        validateGpio(gpio);
        return callAndCheckOk(
                "/switch/on?gpio=" + gpio
        );
    }


    // ==================================================
    // SWITCH OFF
    // ==================================================

    /**
     * Tắt switch tại GPIO.
     */
    public boolean turnOff(int gpio) throws HardwareException{
        validateGpio(gpio);
        return callAndCheckOk(
                "/switch/off?gpio=" + gpio
        );
    }

    public boolean addOrUpdateSchedule(int scheduleId, int gpio, LocalTime runTime,
            String action, boolean enabled) throws HardwareException {
        if (scheduleId <= 0) {
            throw new HardwareException("Schedule ID must be positive.");
        }
        validateGpio(gpio);
        if (runTime == null) {
            throw new HardwareException("Schedule time must not be empty.");
        }
        if (!"ON".equals(action) && !"OFF".equals(action)) {
            throw new HardwareException("Schedule action must be ON or OFF.");
        }
        String path = "/schedule/add?id=" + scheduleId
                + "&gpio=" + gpio
                + "&time=" + encodeQueryValue(runTime.toString())
                + "&action=" + encodeQueryValue(action)
                + "&enabled=" + enabled;
        return callAndCheckOk(path);
    }

    public boolean deleteSchedule(int scheduleId) throws HardwareException {
        if (scheduleId <= 0) {
            throw new HardwareException("Schedule ID must be positive.");
        }
        return callAndCheckOk("/schedule/delete?id=" + scheduleId);
    }


    // ==================================================
    // SWITCH STATUS
    // ==================================================

    /**
     * Lấy trạng thái switch tại GPIO.
     *
     * ESP32 trả:
     *
     * {"gpio":2,"status":"on"}
     *
     * hoặc:
     *
     * {"gpio":2,"status":"off"}
     *
     * Java trả:
     *
     * "ON"
     * "OFF". Invalid or unavailable responses throw HardwareException.
     */
    public String getStatus(int gpio) throws HardwareException {
        validateGpio(gpio);
        String body = callAndGetBody(
                "/switch/status?gpio=" + gpio
        );
        Matcher matcher = SWITCH_STATUS_PATTERN.matcher(body);
        while (matcher.find()) {
            try {
                if (Integer.parseInt(matcher.group(1)) == gpio) {
                    return matcher.group(2).toUpperCase();
                }
            } catch (NumberFormatException e) {
                throw new HardwareException("ESP32 returned an invalid GPIO value.", e);
            }
        }
        throw new HardwareException("ESP32 returned no valid status for GPIO " + gpio + ".");
    }


    // ==================================================
    // DEVICE STATUS
    // ==================================================

    /**
     * Kiểm tra ESP32 có đang online hay không.
     *
     * ESP32:
     *
     * GET /device/status
     *
     * Trả:
     *
     * {
     *   "device":"esp32-switch1",
     *   "status":"online"
     * }
     *
     * @return true nếu ESP32 trả HTTP 200
     */
    public boolean isOnline() throws HardwareException {
        String body = callAndGetBody("/device/status");
        if (!DEVICE_ONLINE_PATTERN.matcher(body).find()) {
            throw new HardwareException("ESP32 returned an invalid or offline device status.");
        }
        return true;
    }


    // ==================================================
    // DEVICE INFO
    // ==================================================

    /**
     * Lấy thông tin ESP32.
     *
     * ESP32:
     *
     * GET /device/info
     *
     * Ví dụ response:
     *
     * {
     *   "device":"esp32-switch1",
     *   "status":"online",
     *   "switches":[
     *      {"gpio":2,"status":"on"},
     *      {"gpio":5,"status":"off"},
     *      {"gpio":18,"status":"off"},
     *      {"gpio":19,"status":"on"}
     *   ]
     * }
     *
     * Method này trả nguyên JSON dưới dạng String.
     */
    public String getDeviceInfo() throws HardwareException {
        String body = callAndGetBody("/device/info");
        if (!SWITCH_STATUS_PATTERN.matcher(body).find()) {
            throw new HardwareException("ESP32 returned invalid device information.");
        }
        return body;
    }

    /**
     * Lấy trạng thái của tất cả GPIO từ một lần gọi /device/info.
     */
    public Map<Integer, String> getSwitchStatuses() throws HardwareException {
        Map<Integer, String> statuses = new LinkedHashMap<>();
        String body = getDeviceInfo();
        Matcher matcher = SWITCH_STATUS_PATTERN.matcher(body);
        while (matcher.find()) {
            try {
                statuses.put(Integer.valueOf(matcher.group(1)), matcher.group(2).toUpperCase());
            } catch (NumberFormatException e) {
                throw new HardwareException("ESP32 returned an invalid GPIO value.", e);
            }
        }
        if (statuses.isEmpty()) {
            throw new HardwareException("ESP32 returned no valid switch status data.");
        }
        return statuses;
    }

    private void validateGpio(int gpio) throws HardwareException {
        if (gpio < 0 || gpio > 255) {
            throw new HardwareException("GPIO must be between 0 and 255.");
        }
    }

    private String encodeQueryValue(String value) throws HardwareException {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new HardwareException("Could not encode ESP32 request parameters.", e);
        }
    }


    // ==================================================
    // HTTP GET - CHECK STATUS CODE
    // ==================================================

    /**
     * Gửi HTTP GET request.
     *
     * @param path
     *
     * Ví dụ:
     *
     * /switch/on?gpio=2
     *
     * @return true nếu ESP32 trả HTTP 200
     */
    private boolean callAndCheckOk(String path) throws HardwareException{

        HttpURLConnection conn = null;


        try {

            URL url =
                    new URL(baseUrl + path);


            conn =
                    (HttpURLConnection)
                    url.openConnection();


            conn.setRequestMethod("GET");


            conn.setConnectTimeout(
                    TIMEOUT_MS
            );

            conn.setReadTimeout(
                    TIMEOUT_MS
            );


            int code =
                    conn.getResponseCode();


            if (code != HttpURLConnection.HTTP_OK) {
                throw new HardwareException("ESP32 returned HTTP " + code + " for " + path + ".");
            }
            return true;


        } catch (IOException e) {
            throw new HardwareException("Could not communicate with ESP32 for " + path + ".", e);
        } finally {

            if (conn != null) {

                conn.disconnect();
            }
        }
    }


    // ==================================================
    // HTTP GET - READ RESPONSE
    // ==================================================

    /**
     * Gửi HTTP GET request
     * và đọc response body.
     *
     * Dùng cho:
     *
     * /switch/status
     * /device/info
     */
    private String callAndGetBody(String path) throws HardwareException {

        HttpURLConnection conn = null;


        try {

            URL url =
                    new URL(baseUrl + path);


            conn =
                    (HttpURLConnection)
                    url.openConnection();


            conn.setRequestMethod("GET");


            conn.setConnectTimeout(
                    TIMEOUT_MS
            );

            conn.setReadTimeout(
                    TIMEOUT_MS
            );


            int code =
                    conn.getResponseCode();


            // ESP32 không trả 200
            if (code != HttpURLConnection.HTTP_OK) {
                throw new HardwareException("ESP32 returned HTTP " + code + " for " + path + ".");
            }


            StringBuilder response =
                    new StringBuilder();


            try (
                BufferedReader reader =
                    new BufferedReader(
                        new InputStreamReader(
                            conn.getInputStream()
                        )
                    )
            ) {

                String line;


                while (
                    (line = reader.readLine())
                    != null
                ) {

                    response.append(line);
                }
            }


            String body = response.toString().trim();
            if (body.isEmpty()) {
                throw new HardwareException("ESP32 returned an empty response for " + path + ".");
            }
            return body;


        } catch (IOException e) {
            throw new HardwareException("Could not communicate with ESP32 for " + path + ".", e);
        } finally {

            if (conn != null) {

                conn.disconnect();
            }
        }
    }
}