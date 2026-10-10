package utills;

import exception.HardwareException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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
 */
public class HardwareClient {

    // ==================================================
    // CONFIGURATION
    // ==================================================

    private final String baseUrl;
    private static final int TIMEOUT_MS = 3000;

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

        return callAndCheckOk(
                "/switch/off?gpio=" + gpio
        );
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
     * "OFF"
     *
     * hoặc null nếu không lấy được.
     */
    public String getStatus(int gpio) throws HardwareException {

        String body = callAndGetBody(
                "/switch/status?gpio=" + gpio
        );


        if (body == null) {
            return null;
        }


        body = body.toLowerCase();


        if (body.contains("\"status\":\"on\"")) {
            return "ON";
        }


        if (body.contains("\"status\":\"off\"")) {
            return "OFF";
        }


        return null;
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

        return callAndCheckOk(
                "/device/status"
        );
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

        return callAndGetBody(
                "/device/info"
        );
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


            return code ==
                    HttpURLConnection.HTTP_OK;


        } catch (IOException e) {
            throw new HardwareException(e.getMessage());
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

                return null;
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


            return response.toString();


        } catch (IOException e) {
            throw new HardwareException(e.getMessage());
        } finally {

            if (conn != null) {

                conn.disconnect();
            }
        }
    }
}