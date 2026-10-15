#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>
#include <Preferences.h>
#include <time.h>

// ==================================================
// WIFI CONFIGURATION
// ==================================================
const char* ssid = "WINDOWS 9012";
const char* password = "07-039fC";
const char* mdnsName = "esp32-switch1";

// Viet Nam timezone: UTC+7, no daylight saving time.
const char* ntpServer1 = "pool.ntp.org";
const char* ntpServer2 = "time.google.com";
const char* timezone = "ICT-7";

// ==================================================
// GPIO CONFIGURATION
// ==================================================
const int CONTROL_PINS[] = {2, 5, 18, 19};
const int CONTROL_PIN_COUNT =
    sizeof(CONTROL_PINS) / sizeof(CONTROL_PINS[0]);

// Button wiring: GPIO -> push button -> GND.
// INPUT_PULLUP: pressed = LOW.
const int BUTTON_PINS[] = {4, 13, 14, 27};
const int BUTTON_CONTROL_PINS[] = {2, 5, 18, 19};
const int BUTTON_COUNT =
    sizeof(BUTTON_PINS) / sizeof(BUTTON_PINS[0]);

const unsigned long debounceDelay = 50;
bool lastButtonReading[BUTTON_COUNT];
bool stableButtonState[BUTTON_COUNT];
unsigned long lastDebounceTime[BUTTON_COUNT] = {0};

// ==================================================
// SCHEDULE CONFIGURATION
// 4 switches x max 10 schedules = 40 schedule slots.
// ==================================================
const int MAX_SCHEDULES_PER_GPIO = 10;
const int MAX_SCHEDULES =
    CONTROL_PIN_COUNT * MAX_SCHEDULES_PER_GPIO;

struct Schedule {
  bool used;
  int id;                 // ID from Java/database
  int gpio;               // 2, 5, 18, or 19
  int minuteOfDay;        // 0..1439
  bool turnOn;             // true = ON, false = OFF
  bool enabled;
  int lastRunDate;         // YYYYMMDD; prevents duplicate run each day
};

Schedule schedules[MAX_SCHEDULES];
Preferences preferences;
WebServer server(80);
bool switchState[40] = {false};

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
// SCHEDULE STORAGE (ESP32 NVS / Preferences)
// ==================================================
String keyFor(const char* prefix, int slot) {
  return String(prefix) + String(slot);
}

void saveScheduleSlot(int slot) {
  String k;

  k = keyFor("used", slot);
  preferences.putBool(k.c_str(), schedules[slot].used);

  k = keyFor("id", slot);
  preferences.putInt(k.c_str(), schedules[slot].id);

  k = keyFor("gpio", slot);
  preferences.putInt(k.c_str(), schedules[slot].gpio);

  k = keyFor("min", slot);
  preferences.putInt(k.c_str(), schedules[slot].minuteOfDay);

  k = keyFor("on", slot);
  preferences.putBool(k.c_str(), schedules[slot].turnOn);

  k = keyFor("en", slot);
  preferences.putBool(k.c_str(), schedules[slot].enabled);

  k = keyFor("day", slot);
  preferences.putInt(k.c_str(), schedules[slot].lastRunDate);
}

void loadSchedules() {
  for (int i = 0; i < MAX_SCHEDULES; i++) {
    String k;

    k = keyFor("used", i);
    schedules[i].used = preferences.getBool(k.c_str(), false);

    k = keyFor("id", i);
    schedules[i].id = preferences.getInt(k.c_str(), 0);

    k = keyFor("gpio", i);
    schedules[i].gpio = preferences.getInt(k.c_str(), 0);

    k = keyFor("min", i);
    schedules[i].minuteOfDay = preferences.getInt(k.c_str(), 0);

    k = keyFor("on", i);
    schedules[i].turnOn = preferences.getBool(k.c_str(), false);

    k = keyFor("en", i);
    schedules[i].enabled = preferences.getBool(k.c_str(), false);

    k = keyFor("day", i);
    schedules[i].lastRunDate = preferences.getInt(k.c_str(), 0);

    // Ignore corrupted or incompatible saved entries.
    if (schedules[i].used &&
        (schedules[i].id <= 0 ||
         !isValidGPIO(schedules[i].gpio) ||
         schedules[i].minuteOfDay < 0 ||
         schedules[i].minuteOfDay > 1439)) {
      schedules[i].used = false;
      saveScheduleSlot(i);
    }
  }
}

int findScheduleById(int id) {
  for (int i = 0; i < MAX_SCHEDULES; i++) {
    if (schedules[i].used && schedules[i].id == id) return i;
  }
  return -1;
}

int countSchedulesForGPIO(int gpio, int ignoreSlot = -1) {
  int count = 0;
  for (int i = 0; i < MAX_SCHEDULES; i++) {
    if (i != ignoreSlot &&
        schedules[i].used &&
        schedules[i].gpio == gpio) {
      count++;
    }
  }
  return count;
}

bool parseTime(const String& value, int& minuteOfDay) {
  // Require exact HH:mm format.
  if (value.length() != 5 || value.charAt(2) != ':') return false;
  if (!isDigit(value.charAt(0)) ||
      !isDigit(value.charAt(1)) ||
      !isDigit(value.charAt(3)) ||
      !isDigit(value.charAt(4))) {
    return false;
  }

  int hour = value.substring(0, 2).toInt();
  int minute = value.substring(3, 5).toInt();

  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
    return false;
  }

  minuteOfDay = hour * 60 + minute;
  return true;
}

bool getTimeNow(struct tm& timeInfo) {
  time_t now = time(nullptr);
  // NTP has not synchronized yet.
  if (now < 1700000000) return false;
  localtime_r(&now, &timeInfo);
  return true;
}

int dateNumber(const struct tm& timeInfo) {
  return (timeInfo.tm_year + 1900) * 10000
       + (timeInfo.tm_mon + 1) * 100
       + timeInfo.tm_mday;
}

// ==================================================
// SCHEDULE API
//
// Add/update:
// /schedule/add?id=1&gpio=2&time=07:30&action=ON&enabled=true
//
// Delete:
// /schedule/delete?id=1
//
// List:
// /schedule/list
// ==================================================
void handleScheduleAdd() {
  if (!server.hasArg("id") ||
      !server.hasArg("gpio") ||
      !server.hasArg("time") ||
      !server.hasArg("action")) {
    sendError(400, "Required parameters: id, gpio, time, action");
    return;
  }

  String idValue = server.arg("id");
  for (unsigned int i = 0; i < idValue.length(); i++) {
    if (!isDigit(idValue.charAt(i))) {
      sendError(400, "Invalid schedule id");
      return;
    }
  }

  int id = idValue.toInt();
  int gpio = server.arg("gpio").toInt();
  int minuteOfDay;

  if (id <= 0) {
    sendError(400, "Schedule id must be positive");
    return;
  }
  if (!isValidGPIO(gpio)) {
    sendError(400, "Invalid gpio");
    return;
  }
  if (!parseTime(server.arg("time"), minuteOfDay)) {
    sendError(400, "Time must use HH:mm format");
    return;
  }

  String action = server.arg("action");
  action.toUpperCase();
  if (action != "ON" && action != "OFF") {
    sendError(400, "Action must be ON or OFF");
    return;
  }

  bool enabled = true;
  if (server.hasArg("enabled")) {
    String enabledValue = server.arg("enabled");
    enabledValue.toLowerCase();
    enabled = (enabledValue == "true" ||
               enabledValue == "1" ||
               enabledValue == "on");
  }

  // Same ID updates the existing schedule.
  int slot = findScheduleById(id);

  // Enforce a maximum of 10 schedules per GPIO.
  if (countSchedulesForGPIO(gpio, slot) >= MAX_SCHEDULES_PER_GPIO) {
    sendError(409, "This GPIO already has 10 schedules");
    return;
  }

  // If this is a new schedule, find a free slot.
  if (slot < 0) {
    for (int i = 0; i < MAX_SCHEDULES; i++) {
      if (!schedules[i].used) {
        slot = i;
        break;
      }
    }
  }

  if (slot < 0) {
    sendError(409, "Schedule storage is full");
    return;
  }

  bool wasUsed = schedules[slot].used;
  int oldGPIO = schedules[slot].gpio;
  int oldMinute = schedules[slot].minuteOfDay;
  bool oldAction = schedules[slot].turnOn;
  bool oldEnabled = schedules[slot].enabled;

  schedules[slot].used = true;
  schedules[slot].id = id;
  schedules[slot].gpio = gpio;
  schedules[slot].minuteOfDay = minuteOfDay;
  schedules[slot].turnOn = (action == "ON");
  schedules[slot].enabled = enabled;

  // Reset today's execution marker if schedule settings changed.
  if (!wasUsed || oldGPIO != gpio ||
      oldMinute != minuteOfDay ||
      oldAction != schedules[slot].turnOn ||
      oldEnabled != enabled) {
    schedules[slot].lastRunDate = 0;
  }

  saveScheduleSlot(slot);

  String response = "{\"result\":\"ok\",\"id\":" + String(id)
      + ",\"gpio\":" + String(gpio)
      + ",\"time\":\"" + server.arg("time")
      + "\",\"action\":\"" + action
      + "\",\"enabled\":" + String(enabled ? "true" : "false") + "}";
  server.send(200, "application/json", response);

  Serial.printf("Schedule saved: id=%d GPIO=%d time=%s action=%s enabled=%s\n",
                id, gpio, server.arg("time").c_str(), action.c_str(),
                enabled ? "true" : "false");
}

void handleScheduleDelete() {
  if (!server.hasArg("id")) {
    sendError(400, "Missing id parameter");
    return;
  }

  String idValue = server.arg("id");
  for (unsigned int i = 0; i < idValue.length(); i++) {
    if (!isDigit(idValue.charAt(i))) {
      sendError(400, "Invalid schedule id");
      return;
    }
  }

  int id = idValue.toInt();
  int slot = findScheduleById(id);

  if (id <= 0 || slot < 0) {
    sendError(404, "Schedule not found");
    return;
  }

  schedules[slot].used = false;
  schedules[slot].enabled = false;
  saveScheduleSlot(slot);

  server.send(200, "application/json",
              "{\"result\":\"ok\",\"deletedId\":" + String(id) + "}");
  Serial.printf("Schedule deleted: id=%d\n", id);
}

void handleScheduleList() {
  String response = "{\"device\":\"" + String(mdnsName) + "\",\"schedules\":[";
  bool first = true;

  for (int i = 0; i < MAX_SCHEDULES; i++) {
    if (!schedules[i].used) continue;

    if (!first) response += ",";
    first = false;

    int hour = schedules[i].minuteOfDay / 60;
    int minute = schedules[i].minuteOfDay % 60;
    char timeText[6];
    snprintf(timeText, sizeof(timeText), "%02d:%02d", hour, minute);

    response += "{\"id\":" + String(schedules[i].id)
        + ",\"gpio\":" + String(schedules[i].gpio)
        + ",\"time\":\"" + String(timeText)
        + "\",\"action\":\"" + (schedules[i].turnOn ? "ON" : "OFF")
        + "\",\"enabled\":" + String(schedules[i].enabled ? "true" : "false")
        + "}";
  }

  response += "]}";
  server.send(200, "application/json", response);
}

void handleTimeStatus() {
  struct tm timeInfo;
  if (!getTimeNow(timeInfo)) {
    server.send(503, "application/json",
                "{\"status\":\"unsynchronized\",\"timezone\":\"UTC+7\"}");
    return;
  }

  char textTime[25];
  strftime(textTime, sizeof(textTime), "%Y-%m-%d %H:%M:%S", &timeInfo);

  String response = "{\"status\":\"synchronized\",\"timezone\":\"UTC+7\",\"time\":\"";
  response += textTime;
  response += "\"}";
  server.send(200, "application/json", response);
}

// ==================================================
// EXISTING SWITCH APIs
// ==================================================
void handleSwitchOn() {
  int gpio;
  if (!getValidGPIOArgument(gpio)) return;

  turnOnGPIO(gpio);
  server.send(200, "application/json",
              "{\"gpio\":" + String(gpio) + ",\"status\":\"on\"}");
  Serial.printf("HTTP -> GPIO %d ON\n", gpio);
}

void handleSwitchOff() {
  int gpio;
  if (!getValidGPIOArgument(gpio)) return;

  turnOffGPIO(gpio);
  server.send(200, "application/json",
              "{\"gpio\":" + String(gpio) + ",\"status\":\"off\"}");
  Serial.printf("HTTP -> GPIO %d OFF\n", gpio);
}

void handleSwitchStatus() {
  int gpio;
  if (!getValidGPIOArgument(gpio)) return;

  String status = switchState[gpio] ? "on" : "off";
  server.send(200, "application/json",
              "{\"gpio\":" + String(gpio) + ",\"status\":\"" + status + "\"}");
}

void handleDeviceStatus() {
  server.send(200, "application/json",
              "{\"device\":\"" + String(mdnsName) + "\",\"status\":\"online\"}");
}

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
// PHYSICAL BUTTONS: toggle corresponding GPIO
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

        // Trigger only on the press edge (HIGH -> LOW).
        if (stableButtonState[i] == LOW) {
          int gpio = BUTTON_CONTROL_PINS[i];

          if (switchState[gpio]) {
            turnOffGPIO(gpio);
          } else {
            turnOnGPIO(gpio);
          }

          Serial.printf("BUTTON GPIO %d -> GPIO %d %s\n",
                        BUTTON_PINS[i], gpio,
                        switchState[gpio] ? "ON" : "OFF");
        }
      }
    }
  }
}

// ==================================================
// AUTOMATIC SCHEDULE EXECUTION
// Each enabled schedule runs once per local calendar day.
// ==================================================
void checkSchedules() {
  struct tm timeInfo;
  if (!getTimeNow(timeInfo)) return;

  int currentMinute = timeInfo.tm_hour * 60 + timeInfo.tm_min;
  int today = dateNumber(timeInfo);

  for (int i = 0; i < MAX_SCHEDULES; i++) {
    if (!schedules[i].used || !schedules[i].enabled) continue;
    if (schedules[i].minuteOfDay != currentMinute) continue;
    if (schedules[i].lastRunDate == today) continue;

    int gpio = schedules[i].gpio;

    if (schedules[i].turnOn) {
      turnOnGPIO(gpio);
    } else {
      turnOffGPIO(gpio);
    }

    // Persist the date to avoid a duplicate execution after reboot
    // during the same scheduled minute.
    schedules[i].lastRunDate = today;
    saveScheduleSlot(i);

    Serial.printf("SCHEDULE id=%d -> GPIO %d %s at %02d:%02d\n",
                  schedules[i].id, gpio,
                  schedules[i].turnOn ? "ON" : "OFF",
                  timeInfo.tm_hour, timeInfo.tm_min);
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
  Serial.println("ESP32 MULTI SWITCH + SCHEDULE");
  Serial.println("==============================");

  for (int i = 0; i < CONTROL_PIN_COUNT; i++) {
    int gpio = CONTROL_PINS[i];
    pinMode(gpio, OUTPUT);
    digitalWrite(gpio, LOW);
    switchState[gpio] = false;
  }

  for (int i = 0; i < BUTTON_COUNT; i++) {
    pinMode(BUTTON_PINS[i], INPUT_PULLUP);
    bool initialState = digitalRead(BUTTON_PINS[i]);
    lastButtonReading[i] = initialState;
    stableButtonState[i] = initialState;
  }

  // Open persistent schedule storage and restore saved schedules.
  preferences.begin("schedules", false);
  loadSchedules();

  Serial.println("Connecting to WiFi...");
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();
  Serial.println("WiFi connected!");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());

  if (MDNS.begin(mdnsName)) {
    Serial.print("mDNS address: http://");
    Serial.print(mdnsName);
    Serial.println(".local");
  } else {
    Serial.println("mDNS failed!");
  }

  // Start NTP synchronization using Vietnam time (UTC+7).
  configTzTime(timezone, ntpServer1, ntpServer2);

  // Existing switch APIs.
  server.on("/switch/on", HTTP_GET, handleSwitchOn);
  server.on("/switch/off", HTTP_GET, handleSwitchOff);
  server.on("/switch/status", HTTP_GET, handleSwitchStatus);
  server.on("/device/status", HTTP_GET, handleDeviceStatus);
  server.on("/device/info", HTTP_GET, handleDeviceInfo);

  // Schedule APIs.
  server.on("/schedule/add", HTTP_GET, handleScheduleAdd);
  server.on("/schedule/delete", HTTP_GET, handleScheduleDelete);
  server.on("/schedule/list", HTTP_GET, handleScheduleList);
  server.on("/time/status", HTTP_GET, handleTimeStatus);

  server.onNotFound([]() {
    sendError(404, "Endpoint not found");
  });

  server.begin();
  Serial.println("HTTP server started!");
  Serial.println("Schedule capacity: max 10 schedules per GPIO.");
}

// ==================================================
// LOOP
// ==================================================
void loop() {
  server.handleClient();
  checkButton();
  checkSchedules();
}
