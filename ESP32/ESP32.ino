#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>

// =======================
// WIFI
// =======================
const char* ssid = "HALLO HOLLA";
const char* password = "hihihihi";

const char* mdnsName = "esp32-switch";

// =======================
// PIN
// =======================
const int LED_PIN = 2;
const int BUTTON_PIN = 4;

// =======================
// SERVER
// =======================
WebServer server(80);

// =======================
// LED STATE
// =======================
bool ledState = false;

// =======================
// BUTTON DEBOUNCE
// =======================
bool lastButtonState = HIGH;
unsigned long lastDebounceTime = 0;
const unsigned long debounceDelay = 50;


// =======================
// APPLY LED STATE
// =======================
void applyLedState() {
    digitalWrite(LED_PIN, ledState ? HIGH : LOW);
}


// =======================
// /led/on
// =======================
void handleLedOn() {

    ledState = true;

    applyLedState();

    server.send(
        200,
        "application/json",
        "{\"status\":\"on\"}"
    );
}


// =======================
// /led/off
// =======================
void handleLedOff() {

    ledState = false;

    applyLedState();

    server.send(
        200,
        "application/json",
        "{\"status\":\"off\"}"
    );
}


// =======================
// /led/status
// =======================
void handleLedStatus() {

    String status = ledState ? "on" : "off";

    server.send(
        200,
        "application/json",
        "{\"status\":\"" + status + "\"}"
    );
}


// =======================
// BUTTON
// =======================
void checkButton() {

    bool currentButtonState = digitalRead(BUTTON_PIN);

    // Phát hiện lúc nút vừa được nhấn
    if (lastButtonState == HIGH && currentButtonState == LOW) {

        // Debounce
        if (millis() - lastDebounceTime > debounceDelay) {

            ledState = !ledState;

            applyLedState();

            Serial.print("Nut bam: ");

            if (ledState) {
                Serial.println("BAT");
            } else {
                Serial.println("TAT");
            }

            lastDebounceTime = millis();
        }
    }

    lastButtonState = currentButtonState;
}


// =======================
// SETUP
// =======================
void setup() {

    Serial.begin(115200);

    // LED
    pinMode(LED_PIN, OUTPUT);
    applyLedState();

    // Button
    // Không cần điện trở ngoài
    pinMode(BUTTON_PIN, INPUT_PULLUP);

    // =======================
    // WIFI CONNECT
    // =======================

    Serial.println();
    Serial.print("Connecting to WiFi");

    WiFi.begin(ssid, password);

    while (WiFi.status() != WL_CONNECTED) {

        delay(500);

        Serial.print(".");
    }

    Serial.println();
    Serial.println("WiFi connected!");

    // In IP của ESP32
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());


    // =======================
    // mDNS
    // =======================

    if (MDNS.begin(mdnsName)) {

        Serial.println("mDNS OK");

        Serial.print("Address: http://");
        Serial.print(mdnsName);
        Serial.println(".local");
    }
    else {

        Serial.println("mDNS failed");
    }


    // =======================
    // API
    // =======================

    server.on("/led/on", HTTP_GET, handleLedOn);

    server.on("/led/off", HTTP_GET, handleLedOff);

    server.on("/led/status", HTTP_GET, handleLedStatus);


    // =======================
    // START SERVER
    // =======================

    server.begin();

    Serial.println("HTTP server started");
}


// =======================
// LOOP
// =======================
void loop() {

    // Xử lý request từ Java/browser
    server.handleClient();

    // Kiểm tra nút nhấn
    checkButton();
}