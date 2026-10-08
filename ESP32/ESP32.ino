#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>

// ==================================================
// WIFI CONFIGURATION
// ==================================================

const char* ssid = "WINDOWS 9012";
const char* password = "07-039fC";

// Tên mDNS của ESP32.
// Mỗi ESP32 phải có tên KHÁC nhau.
const char* mdnsName = "esp32-switch1";


// ==================================================
// GPIO CONFIGURATION
// ==================================================

// GPIO dùng cho nút nhấn vật lý
const int BUTTON_PIN = 4;


// Các GPIO được phép điều khiển từ Java
//
// GPIO 4 KHÔNG nằm ở đây vì đang dùng cho BUTTON.

const int CONTROL_PINS[] = {
    2,
    5,
    18,
    19
};

// Số lượng GPIO điều khiển
const int CONTROL_PIN_COUNT =
    sizeof(CONTROL_PINS) / sizeof(CONTROL_PINS[0]);


// Nút nhấn vật lý sẽ điều khiển GPIO này
const int BUTTON_CONTROL_GPIO = 2;


// ==================================================
// WEB SERVER
// ==================================================

WebServer server(80);


// ==================================================
// SWITCH STATE
// ==================================================
//
// ESP32 có GPIO từ 0 -> 39.
// switchState[2]  = trạng thái GPIO 2
// switchState[5]  = trạng thái GPIO 5
// switchState[18] = trạng thái GPIO 18
// switchState[19] = trạng thái GPIO 19
//
bool switchState[40] = {false};


// ==================================================
// BUTTON DEBOUNCE
// ==================================================

bool lastButtonState = HIGH;

unsigned long lastDebounceTime = 0;

const unsigned long debounceDelay = 50;


// ==================================================
// CHECK GPIO
// ==================================================
//
// Kiểm tra GPIO có nằm trong danh sách được phép
// điều khiển hay không.
//
bool isValidGPIO(int gpio) {

    for (int i = 0; i < CONTROL_PIN_COUNT; i++) {

        if (CONTROL_PINS[i] == gpio) {
            return true;
        }
    }

    return false;
}


// ==================================================
// TURN GPIO ON
// ==================================================

void turnOnGPIO(int gpio) {

    digitalWrite(gpio, HIGH);

    switchState[gpio] = true;
}


// ==================================================
// TURN GPIO OFF
// ==================================================

void turnOffGPIO(int gpio) {

    digitalWrite(gpio, LOW);

    switchState[gpio] = false;
}


// ==================================================
// API
// GET /switch/on?gpio=2
// ==================================================

void handleSwitchOn() {

    // Kiểm tra tham số gpio
    if (!server.hasArg("gpio")) {

        server.send(
            400,
            "application/json",
            "{\"error\":\"Missing gpio parameter\"}"
        );

        return;
    }


    // Lấy GPIO từ URL
    int gpio = server.arg("gpio").toInt();


    // Kiểm tra GPIO hợp lệ
    if (!isValidGPIO(gpio)) {

        server.send(
            400,
            "application/json",
            "{\"error\":\"Invalid gpio\"}"
        );

        return;
    }


    // Bật GPIO
    turnOnGPIO(gpio);


    // Tạo JSON response
    String response =
        "{\"gpio\":" +
        String(gpio) +
        ",\"status\":\"on\"}";


    // Trả response
    server.send(
        200,
        "application/json",
        response
    );


    // Serial debug
    Serial.print("HTTP -> GPIO ");
    Serial.print(gpio);
    Serial.println(" ON");
}


// ==================================================
// API
// GET /switch/off?gpio=2
// ==================================================

void handleSwitchOff() {

    // Kiểm tra tham số gpio
    if (!server.hasArg("gpio")) {

        server.send(
            400,
            "application/json",
            "{\"error\":\"Missing gpio parameter\"}"
        );

        return;
    }


    // Lấy GPIO
    int gpio = server.arg("gpio").toInt();


    // Kiểm tra GPIO
    if (!isValidGPIO(gpio)) {

        server.send(
            400,
            "application/json",
            "{\"error\":\"Invalid gpio\"}"
        );

        return;
    }


    // Tắt GPIO
    turnOffGPIO(gpio);


    // JSON response
    String response =
        "{\"gpio\":" +
        String(gpio) +
        ",\"status\":\"off\"}";


    server.send(
        200,
        "application/json",
        response
    );


    // Serial debug
    Serial.print("HTTP -> GPIO ");
    Serial.print(gpio);
    Serial.println(" OFF");
}


// ==================================================
// API
// GET /switch/status?gpio=2
// ==================================================

void handleSwitchStatus() {

    // Kiểm tra tham số gpio
    if (!server.hasArg("gpio")) {

        server.send(
            400,
            "application/json",
            "{\"error\":\"Missing gpio parameter\"}"
        );

        return;
    }


    // Lấy GPIO
    int gpio = server.arg("gpio").toInt();


    // Kiểm tra GPIO
    if (!isValidGPIO(gpio)) {

        server.send(
            400,
            "application/json",
            "{\"error\":\"Invalid gpio\"}"
        );

        return;
    }


    // Lấy trạng thái
    String status;

    if (switchState[gpio]) {
        status = "on";
    } else {
        status = "off";
    }


    // JSON response
    String response =
        "{\"gpio\":" +
        String(gpio) +
        ",\"status\":\"" +
        status +
        "\"}";


    server.send(
        200,
        "application/json",
        response
    );


    // Serial debug
    Serial.print("STATUS -> GPIO ");
    Serial.print(gpio);
    Serial.print(" = ");
    Serial.println(status);
}


// ==================================================
// API
// GET /device/status
// ==================================================
//
// Java dùng API này để kiểm tra ESP32 còn online không.
//
void handleDeviceStatus() {

    String response =
        "{"
        "\"device\":\"" +
        String(mdnsName) +
        "\","
        "\"status\":\"online\""
        "}";


    server.send(
        200,
        "application/json",
        response
    );
}


// ==================================================
// API
// GET /device/info
// ==================================================
//
// Trả về thông tin ESP32 và danh sách GPIO.
//
void handleDeviceInfo() {

    String response = "{";

    response += "\"device\":\"";
    response += mdnsName;
    response += "\",";

    response += "\"status\":\"online\",";

    response += "\"switches\":[";


    for (int i = 0; i < CONTROL_PIN_COUNT; i++) {

        int gpio = CONTROL_PINS[i];

        response += "{";

        response += "\"gpio\":";
        response += String(gpio);
        response += ",";

        response += "\"status\":\"";

        if (switchState[gpio]) {
            response += "on";
        } else {
            response += "off";
        }

        response += "\"}";


        if (i < CONTROL_PIN_COUNT - 1) {
            response += ",";
        }
    }


    response += "]";

    response += "}";


    server.send(
        200,
        "application/json",
        response
    );
}


// ==================================================
// BUTTON
// ==================================================

void checkButton() {

    bool currentButtonState =
        digitalRead(BUTTON_PIN);


    // Phát hiện cạnh HIGH -> LOW
    // tức là nút vừa được nhấn.
    if (
        lastButtonState == HIGH &&
        currentButtonState == LOW
    ) {

        // Debounce
        if (
            millis() - lastDebounceTime
            > debounceDelay
        ) {

            // Nếu đang ON -> OFF
            if (
                switchState[BUTTON_CONTROL_GPIO]
            ) {

                turnOffGPIO(
                    BUTTON_CONTROL_GPIO
                );

                Serial.print(
                    "BUTTON -> GPIO "
                );

                Serial.print(
                    BUTTON_CONTROL_GPIO
                );

                Serial.println(
                    " OFF"
                );

            }

            // Nếu đang OFF -> ON
            else {

                turnOnGPIO(
                    BUTTON_CONTROL_GPIO
                );

                Serial.print(
                    "BUTTON -> GPIO "
                );

                Serial.print(
                    BUTTON_CONTROL_GPIO
                );

                Serial.println(
                    " ON"
                );
            }


            // Lưu thời gian debounce
            lastDebounceTime = millis();
        }
    }


    // Lưu trạng thái nút
    lastButtonState =
        currentButtonState;
}


// ==================================================
// SETUP
// ==================================================

void setup() {

    // ==================================================
    // SERIAL
    // ==================================================

    Serial.begin(115200);

    delay(500);

    Serial.println();
    Serial.println("==============================");
    Serial.println("ESP32 MULTI SWITCH SERVER");
    Serial.println("==============================");


    // ==================================================
    // INITIALIZE CONTROL GPIO
    // ==================================================

    Serial.println();
    Serial.println("Initializing GPIO...");


    for (
        int i = 0;
        i < CONTROL_PIN_COUNT;
        i++
    ) {

        int gpio = CONTROL_PINS[i];


        // GPIO output
        pinMode(
            gpio,
            OUTPUT
        );


        // Trạng thái ban đầu OFF
        digitalWrite(
            gpio,
            LOW
        );


        switchState[gpio] =
            false;


        Serial.print(
            "GPIO "
        );

        Serial.print(
            gpio
        );

        Serial.println(
            " -> OFF"
        );
    }


    // ==================================================
    // BUTTON
    // ==================================================

    // Sử dụng điện trở kéo lên bên trong ESP32
    //
    // Button:
    // GPIO 4 ---- Button ---- GND
    //
    pinMode(
        BUTTON_PIN,
        INPUT_PULLUP
    );


    Serial.print(
        "BUTTON GPIO: "
    );

    Serial.println(
        BUTTON_PIN
    );


    // ==================================================
    // WIFI
    // ==================================================

    Serial.println();
    Serial.println("Connecting to WiFi...");

    Serial.print(
        "SSID: "
    );

    Serial.println(
        ssid
    );


    WiFi.begin(
        ssid,
        password
    );


    while (
        WiFi.status() != WL_CONNECTED
    ) {

        delay(500);

        Serial.print(".");
    }


    Serial.println();

    Serial.println(
        "WiFi connected!"
    );


    // ==================================================
    // IP ADDRESS
    // ==================================================

    Serial.print(
        "IP address: "
    );

    Serial.println(
        WiFi.localIP()
    );


    // ==================================================
    // mDNS
    // ==================================================

    Serial.println();
    Serial.println("Starting mDNS...");


    if (
        MDNS.begin(mdnsName)
    ) {

        Serial.println(
            "mDNS started successfully!"
        );


        Serial.print(
            "Device address: http://"
        );

        Serial.print(
            mdnsName
        );

        Serial.println(
            ".local"
        );

    }

    else {

        Serial.println(
            "mDNS failed!"
        );
    }


    // ==================================================
    // REGISTER API
    // ==================================================

    // --------------------------
    // SWITCH ON
    // --------------------------

    server.on(
        "/switch/on",
        HTTP_GET,
        handleSwitchOn
    );


    // --------------------------
    // SWITCH OFF
    // --------------------------

    server.on(
        "/switch/off",
        HTTP_GET,
        handleSwitchOff
    );


    // --------------------------
    // SWITCH STATUS
    // --------------------------

    server.on(
        "/switch/status",
        HTTP_GET,
        handleSwitchStatus
    );


    // --------------------------
    // DEVICE STATUS
    // --------------------------

    server.on(
        "/device/status",
        HTTP_GET,
        handleDeviceStatus
    );


    // --------------------------
    // DEVICE INFO
    // --------------------------

    server.on(
        "/device/info",
        HTTP_GET,
        handleDeviceInfo
    );


    // ==================================================
    // START SERVER
    // ==================================================

    server.begin();


    Serial.println();
    Serial.println(
        "HTTP server started!"
    );


    Serial.println();
    Serial.println("==============================");
    Serial.println("AVAILABLE API");
    Serial.println("==============================");

    Serial.println(
        "GET /switch/on?gpio=2"
    );

    Serial.println(
        "GET /switch/off?gpio=2"
    );

    Serial.println(
        "GET /switch/status?gpio=2"
    );

    Serial.println(
        "GET /device/status"
    );

    Serial.println(
        "GET /device/info"
    );

    Serial.println("==============================");
}


// ==================================================
// LOOP
// ==================================================

void loop() {

    // Xử lý HTTP request
    server.handleClient();


    // Xử lý nút nhấn
    checkButton();
}