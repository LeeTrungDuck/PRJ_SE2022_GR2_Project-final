# Smart Switch — Ghi chú kết nối ESP32 & WiFi

Tài liệu tham chiếu nhanh khi lập trình phần cứng cho dự án Smart Switch (ESP32 + LED, giao tiếp với Java Web qua HTTP).

---

## 1. Phần cứng đang dùng

| Thành phần | Model / Ghi chú |
|---|---|
| Vi điều khiển | ESP32 NodeMCU 38 chân, cổng Type-C, UART CP2102 |
| Thiết bị điều khiển | LED đơn (thay cho công tắc thật) |
| Điện trở | 220Ω – 330Ω (hạn dòng cho LED) |
| Breadboard | SYB-170 (170 lỗ) — chỉ dùng để cắm LED + điện trở, **không** cắm ESP32 lên breadboard |
| Kết nối | 2 dây jumper đực-đực (GPIO → điện trở → LED → GND) |

**Sơ đồ đấu nối:**
```
ESP32 GPIO2 ──dây──> [Điện trở 220Ω] ──(breadboard)──> [Chân dài LED (Anode +)]
                                                          [Chân ngắn LED (Cathode -)] ──(breadboard)──> dây ──> ESP32 GND
```

---

## 2. Mạng WiFi

- **Nguồn phát WiFi**: Hotspot từ điện thoại (không dùng router).
- **Chuẩn bảo mật**: `WPA2-Personal` — giữ nguyên, không đổi sang WPA3 hay chế độ Mixed (ESP32 tương thích tốt nhất với WPA2 thuần).
- **Băng tần bắt buộc**: `2.4GHz`. ESP32 (bản thường) **không hỗ trợ 5GHz**.
  - Nếu điện thoại có mục "Maximum Compatibility" / "Band" trong cài đặt Hotspot → bật lên để đảm bảo phát 2.4GHz.
  - Nếu ESP32 kết nối mãi không được (Serial Monitor hiện `.....` liên tục) → nghi ngờ đầu tiên là do băng tần 5GHz, không phải do sai mật khẩu.
- **Điều kiện bắt buộc**: Laptop chạy Java Web và ESP32 phải cùng kết nối vào **cùng một Hotspot**.
- **Lưu ý pin**: Cắm sạc điện thoại khi demo lâu để tránh mất kết nối giữa chừng.

---

## 3. Vì sao dùng mDNS (ESPmDNS) thay vì IP tĩnh

Hotspot điện thoại **không có giao diện quản trị router** nên không thể đặt DHCP Reservation (IP tĩnh) như router thường. Mỗi lần bật lại Hotspot, ESP32 có thể nhận IP khác.

→ Giải pháp: dùng thư viện `ESPmDNS`, đặt cho ESP32 một **tên miền cố định trong mạng nội bộ**, ví dụ `esp32-switch.local`. Java sẽ gọi qua tên này thay vì IP, không lo IP đổi.

⚠️ Lưu ý: mDNS hoạt động dựa trên UDP multicast — một số hệ điều hành/driver hiếm khi chặn, nếu gọi bằng tên `.local` mà lỗi, thử lấy IP qua Serial Monitor để gọi tạm bằng IP trong lúc debug.

---

## 4. Firmware ESP32 (Arduino IDE)

```cpp
#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>

const char* ssid = "TEN_HOTSPOT_DIEN_THOAI";
const char* password = "MAT_KHAU_HOTSPOT";
const char* mdnsName = "esp32-switch";   // truy cập qua http://esp32-switch.local

WebServer server(80);
const int LED_PIN = 2;

void handleOn() {
  digitalWrite(LED_PIN, HIGH);
  server.send(200, "application/json", "{\"status\":\"on\"}");
}

void handleOff() {
  digitalWrite(LED_PIN, LOW);
  server.send(200, "application/json", "{\"status\":\"off\"}");
}

void handleStatus() {
  bool state = digitalRead(LED_PIN);
  String json = "{\"status\":\"" + String(state ? "on" : "off") + "\"}";
  server.send(200, "application/json", json);
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);

  WiFi.begin(ssid, password);
  Serial.print("Dang ket noi WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected!");
  Serial.print("IP address (du phong khi mDNS loi): ");
  Serial.println(WiFi.localIP());

  // Khoi tao mDNS
  if (MDNS.begin(mdnsName)) {
    Serial.println("mDNS OK -> truy cap qua: http://" + String(mdnsName) + ".local");
  } else {
    Serial.println("Loi khoi tao mDNS, dung tam IP o tren de goi thu");
  }

  server.on("/led/on", handleOn);
  server.on("/led/off", handleOff);
  server.on("/led/status", handleStatus);
  server.begin();
}

void loop() {
  server.handleClient();
}
```

**Endpoint sau khi chạy:**
- `http://esp32-switch.local/led/on` — bật LED
- `http://esp32-switch.local/led/off` — tắt LED
- `http://esp32-switch.local/led/status` — kiểm tra trạng thái (trả JSON)

---

## 5. Phía Java — gọi HTTP tới ESP32

```java
package hardware;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RealHardwareClient implements HardwareClient {

    private final String esp32BaseUrl; // "http://esp32-switch.local"
    private final HttpClient client;

    public RealHardwareClient(String esp32BaseUrl) {
        this.esp32BaseUrl = esp32BaseUrl;
        this.client = HttpClient.newHttpClient();
    }

    @Override
    public boolean turnOn(String deviceId) {
        return sendCommand("/led/on");
    }

    @Override
    public boolean turnOff(String deviceId) {
        return sendCommand("/led/off");
    }

    @Override
    public boolean getStatus(String deviceId) {
        return sendCommand("/led/status");
    }

    private boolean sendCommand(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(esp32BaseUrl + path))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            e.printStackTrace(); // log lỗi: ESP32 offline, sai tên mDNS, timeout...
            return false;
        }
    }
}
```

Cấu hình địa chỉ trong file `hardware.properties` (không hard-code):
```properties
hardware.mode=REAL
hardware.esp32.baseUrl=http://esp32-switch.local
```

---

## 6. Thứ tự test — luôn làm đúng trình tự này để dễ debug

1. **Nạp firmware** lên ESP32, mở Serial Monitor (baud 115200), xác nhận thấy `WiFi connected!` và `mDNS OK`.
2. **Test ESP32 độc lập** bằng trình duyệt trên laptop (cùng Hotspot):
   `http://esp32-switch.local/led/on` → LED phải sáng.
   - Nếu không được, thử bằng IP tạm thời in ra ở Serial Monitor.
3. **Test Java thuần** (chưa đụng Servlet): viết 1 class có `main()` gọi `RealHardwareClient.turnOn()`, in kết quả console.
4. **Tích hợp Servlet + JSP** sau khi bước 3 chạy ổn định.

---

## 7. Các lỗi thường gặp & cách xử lý nhanh

| Hiện tượng | Nguyên nhân khả dĩ | Cách xử lý |
|---|---|---|
| Serial Monitor hiện `.....` mãi, không kết nối WiFi | Hotspot phát ở 5GHz | Bật "Maximum Compatibility"/chọn band 2.4GHz trên điện thoại |
| Gõ đúng `http://esp32-switch.local/...` nhưng không vào được | mDNS bị chặn hoặc chưa khởi tạo đúng | Dùng tạm IP từ Serial Monitor để kiểm tra, kiểm tra lại `MDNS.begin()` có trả `true` không |
| Điện thoại gọi được nhưng laptop thì không | Laptop đang ở mạng WiFi khác, hoặc Hotspot bật chế độ cô lập thiết bị (AP Isolation) | Kiểm tra lại laptop đã join đúng Hotspot; tắt tính năng giới hạn kết nối trong cài đặt Hotspot |
| Java `HttpRequest` bị timeout | ESP32 mất nguồn/mất WiFi, hoặc sai baseUrl trong properties | Kiểm tra Serial Monitor xem ESP32 còn sống không; kiểm tra lại file `hardware.properties` |
| LED không sáng dù response trả 200 | Sai chân GPIO, đấu ngược cực LED, thiếu điện trở | Kiểm tra lại `LED_PIN` khớp với chân đã đấu dây; LED có phân cực, chân dài (+) phải nối qua điện trở tới GPIO |
