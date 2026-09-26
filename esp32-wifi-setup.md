# Smart Switch — Ghi chú kết nối ESP32 & WiFi

Tài liệu tham chiếu nhanh khi lập trình phần cứng cho dự án Smart Switch (ESP32 + LED, giao tiếp với Java Web qua HTTP).

---

## 1. Phần cứng đang dùng

| Thành phần | Model / Ghi chú |
|---|---|
| Vi điều khiển | ESP32 NodeMCU 38 chân, cổng Type-C, UART CP2102 |
| Thiết bị điều khiển | LED đơn (thay cho công tắc thật) |
| Điện trở | 220Ω – 330Ω (hạn dòng cho LED) |
| Breadboard | SYB-170 (170 lỗ) — chỉ dùng để cắm LED + điện trở + nút bấm, **không** cắm ESP32 lên breadboard |
| Kết nối | Dây jumper đực-đực (GPIO → điện trở → LED → GND, và GPIO → nút bấm → GND) |
| Nút bấm vật lý | Nút nhấn 4 chân (tactile push button) — điều khiển trực tiếp tại chỗ, độc lập với lệnh từ Web (đúng theo Mục II.2.1 của đề tài) |

**Sơ đồ đấu nối LED:**
```
ESP32 GPIO2 ──dây──> [Điện trở 220Ω] ──(breadboard)──> [Chân dài LED (Anode +)]
                                                          [Chân ngắn LED (Cathode -)] ──(breadboard)──> dây ──> ESP32 GND
```

**Sơ đồ đấu nối nút bấm:**
```
ESP32 GPIO4 ──dây──> [1 chân nút bấm] ──(breadboard)──> [chân đối diện của nút bấm] ──dây──> ESP32 GND
```
Dùng chế độ `INPUT_PULLUP` trong code (không cần điện trở ngoài) — khi không nhấn, chân đọc giá trị `HIGH`; khi nhấn, chân bị kéo xuống `LOW`.

⚠️ Nút bấm 4 chân thường có 2 cặp chân **luôn nối sẵn với nhau bên trong** (2 chân trái nối nhau, 2 chân phải nối nhau). Chỉ cần bắt 1 chân bất kỳ ở cặp trái và 1 chân bất kỳ ở cặp phải — không cần dùng cả 4 chân.

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

Bản có tích hợp **nút bấm vật lý** (đọc trạng thái + chống dội phím/debounce) song song với điều khiển qua Web — cả 2 cách đều tác động lên cùng 1 biến trạng thái `ledState`, đảm bảo đồng bộ:

```cpp
#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>

const char* ssid = "TEN_HOTSPOT_DIEN_THOAI";
const char* password = "MAT_KHAU_HOTSPOT";
const char* mdnsName = "esp32-switch";   // truy cập qua http://esp32-switch.local

WebServer server(80);
const int LED_PIN = 2;
const int BUTTON_PIN = 4;

bool ledState = false;          // trạng thái LED hiện tại, dùng chung cho cả Web và nút bấm
int lastButtonReading = HIGH;   // giá trị đọc thô lần trước (dùng để phát hiện cạnh xuống)
unsigned long lastDebounceTime = 0;
const unsigned long DEBOUNCE_DELAY = 50; // ms, chống dội phím

void applyLedState() {
  digitalWrite(LED_PIN, ledState ? HIGH : LOW);
}

void handleOn() {
  ledState = true;
  applyLedState();
  server.send(200, "application/json", "{\"status\":\"on\"}");
}

void handleOff() {
  ledState = false;
  applyLedState();
  server.send(200, "application/json", "{\"status\":\"off\"}");
}

void handleStatus() {
  String json = "{\"status\":\"" + String(ledState ? "on" : "off") + "\"}";
  server.send(200, "application/json", json);
}

void checkButton() {
  int reading = digitalRead(BUTTON_PIN);

  // Phát hiện cạnh xuống (nhấn nút): HIGH -> LOW, có chống dội bằng thời gian
  if (reading != lastButtonReading) {
    lastDebounceTime = millis();
  }

  if ((millis() - lastDebounceTime) > DEBOUNCE_DELAY) {
    if (reading == LOW && lastButtonReading == HIGH) {
      // Vừa nhấn nút -> đảo trạng thái LED
      ledState = !ledState;
      applyLedState();
      Serial.println(ledState ? "Nut bam: BAT" : "Nut bam: TAT");
    }
  }

  lastButtonReading = reading;
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT_PULLUP); // không cần điện trở ngoài

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
  checkButton();   // liên tục kiểm tra nút bấm để phản hồi tức thời, không chặn server
}
```

**Endpoint sau khi chạy:**
- `http://esp32-switch.local/led/on` — bật LED (từ Web)
- `http://esp32-switch.local/led/off` — tắt LED (từ Web)
- `http://esp32-switch.local/led/status` — kiểm tra trạng thái hiện tại (phản ánh đúng dù bật bằng Web hay bằng nút bấm)

**Vì sao dùng biến `ledState` thay vì `digitalRead(LED_PIN)` như bản trước:** để `/led/status` luôn trả đúng trạng thái logic ngay cả khi có nhiễu điện áp trên chân LED — đồng thời đây cũng là nơi duy nhất cần sửa nếu sau này bạn muốn ghi thêm vào `Control_History` mỗi khi trạng thái đổi do nút bấm (hiện tại nút bấm chỉ đổi trạng thái tại chỗ, chưa báo về Java — xem lưu ý bên dưới).

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

## 7. Lưu ý quan trọng: đồng bộ trạng thái khi có nút bấm vật lý

Khi thêm nút bấm, có 1 vấn đề kiến trúc cần hiểu rõ trước khi code phần Java:

- ESP32 **không tự gửi** thông báo lên Java khi ai đó bấm nút — nó chỉ đổi `ledState` cục bộ và chờ được hỏi.
- Vì vậy, nếu người dùng đứng bấm nút trực tiếp tại thiết bị, **trang Web sẽ không tự cập nhật ngay lập tức** trừ khi Java chủ động gọi lại `/led/status`.

**Cách xử lý (chọn 1 trong 2, tuỳ mức độ đồ án):**
1. **Đơn giản (đủ dùng)**: Trang Web dùng JavaScript `setInterval` gọi lại Servlet mỗi 2-3 giây để lấy trạng thái mới nhất từ ESP32, cập nhật lại giao diện (polling).
2. **Nâng cao hơn**: ESP32 tự gửi 1 HTTP request báo về server Java mỗi khi trạng thái đổi do nút bấm (ESP32 đóng vai trò client gọi ngược lại) — phức tạp hơn, không bắt buộc cho đồ án môn học.

→ Khuyến nghị dùng cách 1 (polling định kỳ), đã đủ để chứng minh "đồng bộ trạng thái thiết bị" như yêu cầu ở Mục I.3 của đề tài.

---

## 8. Các lỗi thường gặp & cách xử lý nhanh

| Hiện tượng | Nguyên nhân khả dĩ | Cách xử lý |
|---|---|---|
| Serial Monitor hiện `.....` mãi, không kết nối WiFi | Hotspot phát ở 5GHz | Bật "Maximum Compatibility"/chọn band 2.4GHz trên điện thoại |
| Gõ đúng `http://esp32-switch.local/...` nhưng không vào được | mDNS bị chặn hoặc chưa khởi tạo đúng | Dùng tạm IP từ Serial Monitor để kiểm tra, kiểm tra lại `MDNS.begin()` có trả `true` không |
| Điện thoại gọi được nhưng laptop thì không | Laptop đang ở mạng WiFi khác, hoặc Hotspot bật chế độ cô lập thiết bị (AP Isolation) | Kiểm tra lại laptop đã join đúng Hotspot; tắt tính năng giới hạn kết nối trong cài đặt Hotspot |
| Java `HttpRequest` bị timeout | ESP32 mất nguồn/mất WiFi, hoặc sai baseUrl trong properties | Kiểm tra Serial Monitor xem ESP32 còn sống không; kiểm tra lại file `hardware.properties` |
| LED không sáng dù response trả 200 | Sai chân GPIO, đấu ngược cực LED, thiếu điện trở | Kiểm tra lại `LED_PIN` khớp với chân đã đấu dây; LED có phân cực, chân dài (+) phải nối qua điện trở tới GPIO |
| Nhấn nút không thấy LED đổi trạng thái | Chưa đặt `INPUT_PULLUP`, hoặc đấu nhầm cặp chân không thông nhau của nút 4 chân | Kiểm tra lại `pinMode(BUTTON_PIN, INPUT_PULLUP)`; dùng đồng hồ đo thông mạch để xác định đúng 2 chân đối diện nhau trên nút bấm |
| Nhấn 1 lần nhưng LED nhấp nháy đổi trạng thái nhiều lần | Bị dội phím (bounce) do `DEBOUNCE_DELAY` quá ngắn hoặc dây tiếp xúc lỏng | Tăng `DEBOUNCE_DELAY` lên 100-150ms; kiểm tra lại dây jumper cắm chắc vào breadboard |
| Web không biết được trạng thái đổi khi bấm nút vật lý | `/led/status` chỉ trả lời khi Java **chủ động hỏi**, ESP32 không tự báo (push) khi có người bấm nút | Cần polling định kỳ từ Java (gọi `/led/status` mỗi vài giây) để đồng bộ hiển thị, xem Mục 8 |
