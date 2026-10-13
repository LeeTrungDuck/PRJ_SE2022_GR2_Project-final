#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>

// ==================================================
// WIFI CONFIGURATION
// ==================================================
const char* ssid = "WINDOWS 9012";
const char* password = "07-039fC";
const char* mdnsName = "esp32-switch1";

// ==================================================
// GPIO CONFIGURATION
// ==================================================
// GPIO outputs controlled by Java / HTTP
const int CONTROL_PINS[] = { 2, 5, 18, 19 };
const int CONTROL_PIN_COUNT = sizeof(CONTROL_PINS) / sizeof(CONTROL_PINS[0]);

// Four physical buttons: each button turns OFF its corresponding output.
// Button wiring: GPIO pin <-> push button <-> GND.
// INPUT_PULLUP means pressed = LOW.
const int BUTTON_PINS[] = { 4, 13, 14, 27 };
const int BUTTON_CONTROL_PINS[] = { 2, 5, 18, 19 };
const int BUTTON_COUNT = sizeof(BUTTON_PINS) / sizeof(BUTTON_PINS[0]);

const unsigned long debounceDelay = 50;
bool lastButtonReading[BUTTON_COUNT];
bool stableButtonState[BUTTON_COUNT];
unsigned long lastDebounceTime[BUTTON_COUNT] = { 0 };

// ==================================================
// WEB SERVER AND SWITCH STATES
// ==================================================
WebServer server(80);
bool switchState[40] = { false };

// ==================================================
// GPIO HELPERS
// ==================================================
bool isValidGPIO(int gpio) {
  for (int i = 0; i < CONTROL_PIN_COUNT; i++) {
    if (CONTROL_PINS[i] == gpio) return true;
  }
  return false;
}

void turnOnGPIO(int gpio) {
  digitalWrite(gpio, HIGH);
  switchState[gpio] = true;
}

void turnOffGPIO(int gpio) {
  digitalWrite(gpio, LOW);
  switchState[gpio] = false;
}

void sendError(int statusCode, const char* message) {
  String response = "{\"error\":\"";
  response += message;
  response += "\"}";
  server.send(statusCode, "application/json", response);
}

bool getValidGPIOArgument(int& gpio) {
  if (!server.hasArg("gpio")) {
    sendError(400, "Missing gpio parameter");
    return false;
  }

  String value = server.arg("gpio");
  if (value.length() == 0) {
    sendError(400, "Invalid gpio");
    return false;
  }

  gpio = value.toInt();
  if (!isValidGPIO(gpio)) {
    sendError(400, "Invalid gpio");
    return false;
  }
  return true;
}

// ==================================================
// API: GET /switch/on?gpio=2
// ==================================================
void handleSwitchOn() {
  int gpio;
  if (!getValidGPIOArgument(gpio)) return;

  turnOnGPIO(gpio);
  String response = "{\"gpio\":" + String(gpio) + ",\"status\":\"on\"}";
  server.send(200, "application/json", response);

  Serial.print("HTTP -> GPIO ");
  Serial.print(gpio);
  Serial.println(" ON");
}

// ==================================================
// API: GET /switch/off?gpio=2
// ==================================================
void handleSwitchOff() {
  int gpio;
  if (!getValidGPIOArgument(gpio)) return;

  turnOffGPIO(gpio);
  String response = "{\"gpio\":" + String(gpio) + ",\"status\":\"off\"}";
  server.send(200, "application/json", response);

  Serial.print("HTTP -> GPIO ");
  Serial.print(gpio);
  Serial.println(" OFF");
}

// ==================================================
// API: GET /switch/status?gpio=2
// ==================================================
void handleSwitchStatus() {
  int gpio;
  if (!getValidGPIOArgument(gpio)) return;

  String status = switchState[gpio] ? "on" : "off";
  String response = "{\"gpio\":" + String(gpio) + ",\"status\":\"" + status + "\"}";
  server.send(200, "application/json", response);
}

// ==================================================
// API: GET /device/status
// ==================================================
void handleDeviceStatus() {
  String response = "{\"device\":\"" + String(mdnsName) + "\",\"status\":\"online\"}";
  server.send(200, "application/json", response);
}

// ==================================================
// API: GET /device/info
// ==================================================
void handleDeviceInfo() {
  String response = "{\"device\":\"" + String(mdnsName) + "\",\"status\":\"online\",\"switches\":[";

  for (int i = 0; i < CONTROL_PIN_COUNT; i++) {
    int gpio = CONTROL_PINS[i];
    if (i > 0) response += ",";
    response += "{\"gpio\":" + String(gpio) + ",\"status\":\"";
    response += switchState[gpio] ? "on" : "off";
    response += "\"}";
  }

  response += "]}";
  server.send(200, "application/json", response);
}

// ==================================================
// PHYSICAL BUTTON HANDLING
// A press turns OFF its mapped output; it does not toggle it ON.
// ==================================================
void checkButton() {
  for (int i = 0; i < BUTTON_COUNT; i++) {
    bool reading = digitalRead(BUTTON_PINS[i]);

    if (reading != lastButtonReading[i]) {
      lastDebounceTime[i] = millis();
      lastButtonReading[i] = reading;
    }

    if (millis() - lastDebounceTime[i] >= debounceDelay) {
      if (reading != stableButtonState[i]) {
        stableButtonState[i] = reading;

        // INPUT_PULLUP: LOW means the button has been pressed.

        if (stableButtonState[i] == LOW) {
          int gpio = BUTTON_CONTROL_PINS[i];

          if (switchState[gpio]) {
            turnOffGPIO(gpio);
            Serial.print("BUTTON GPIO ");
            Serial.print(BUTTON_PINS[i]);
            Serial.print(" pressed -> GPIO ");
            Serial.print(gpio);
            Serial.println(" OFF");
          } else {
            turnOnGPIO(gpio);
            Serial.print("BUTTON GPIO ");
            Serial.print(BUTTON_PINS[i]);
            Serial.print(" pressed -> GPIO ");
            Serial.print(gpio);
            Serial.println(" ON");
          }
        }
      }
    }
  }
}

// ==================================================
// SETUP
// ==================================================
void setup() {
  Serial.begin(115200);
  delay(500);

  Serial.println();
  Serial.println("==============================");
  Serial.println("ESP32 MULTI SWITCH SERVER");
  Serial.println("==============================");

  // Initialize output pins in OFF state.
  for (int i = 0; i < CONTROL_PIN_COUNT; i++) {
    int gpio = CONTROL_PINS[i];
    pinMode(gpio, OUTPUT);
    digitalWrite(gpio, LOW);
    switchState[gpio] = false;

    Serial.print("GPIO ");
    Serial.print(gpio);
    Serial.println(" -> OFF");
  }

  // Initialize buttons and debounce state.
  for (int i = 0; i < BUTTON_COUNT; i++) {
    pinMode(BUTTON_PINS[i], INPUT_PULLUP);
    bool initialState = digitalRead(BUTTON_PINS[i]);
    lastButtonReading[i] = initialState;
    stableButtonState[i] = initialState;

    Serial.print("BUTTON GPIO ");
    Serial.println(BUTTON_PINS[i]);
  }

  // Connect to Wi-Fi.
  Serial.println();
  Serial.println("Connecting to WiFi...");
  Serial.print("SSID: ");
  Serial.println(ssid);
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();
  Serial.println("WiFi connected!");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());

  // Start mDNS.
  Serial.println("Starting mDNS...");
  if (MDNS.begin(mdnsName)) {
    Serial.println("mDNS started successfully!");
    Serial.print("Device address: http://");
    Serial.print(mdnsName);
    Serial.println(".local");
  } else {
    Serial.println("mDNS failed!");
  }

  // Register HTTP API endpoints.
  server.on("/switch/on", HTTP_GET, handleSwitchOn);
  server.on("/switch/off", HTTP_GET, handleSwitchOff);
  server.on("/switch/status", HTTP_GET, handleSwitchStatus);
  server.on("/device/status", HTTP_GET, handleDeviceStatus);
  server.on("/device/info", HTTP_GET, handleDeviceInfo);

  server.onNotFound([]() {
    sendError(404, "Endpoint not found");
  });

  server.begin();
  Serial.println("HTTP server started!");
  Serial.println("Available APIs:");
  Serial.println("GET /switch/on?gpio=2");
  Serial.println("GET /switch/off?gpio=2");
  Serial.println("GET /switch/status?gpio=2");
  Serial.println("GET /device/status");
  Serial.println("GET /device/info");
}

// ==================================================
// LOOP
// ==================================================
void loop() {
  server.handleClient();
  checkButton();
}
