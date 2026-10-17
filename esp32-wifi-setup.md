# Smart Switch — Tham chiếu API firmware ESP32

Tài liệu API dành cho phía Java Web, tổng hợp từ firmware `ESP32.ino`. Mọi endpoint đều dùng phương thức `GET` và trả về JSON.

**Base URL:** `http://esp32-switch1.local` (nếu mDNS lỗi, dùng IP in ra ở Serial Monitor)

---

## 1. Quy ước chung

| Mục | Giá trị |
|---|---|
| GPIO hợp lệ (công tắc) | `2`, `5`, `18`, `19` |
| GPIO nút bấm vật lý | `4`, `13`, `14`, `27` (tương ứng LED 2, 5, 18, 19) |
| Múi giờ | `ICT-7` (UTC+7, không có giờ mùa hè) |
| Định dạng giờ | `HH:mm` (24h, bắt buộc 2 chữ số, ví dụ `07:30`) |
| Giá trị `action` | `ON` hoặc `OFF` (không phân biệt hoa thường) |
| Giới hạn lịch | 10 lịch / GPIO, tối đa 40 lịch |
| Lỗi | `{"error":"<thông báo>"}` kèm mã HTTP tương ứng |

---

## 2. Điều khiển công tắc

### `GET /switch/on?gpio={gpio}`
Bật công tắc.
```json
{"gpio":2,"status":"on"}
```

### `GET /switch/off?gpio={gpio}`
Tắt công tắc.
```json
{"gpio":2,"status":"off"}
```

### `GET /switch/status?gpio={gpio}`
Trạng thái logic hiện tại của 1 công tắc (đã phản ánh cả thay đổi do nút bấm và hẹn giờ).
```json
{"gpio":2,"status":"on"}
```

**Lỗi:** `400 Missing gpio parameter`, `400 Invalid gpio`.

---

## 3. Thông tin thiết bị

### `GET /device/status`
Kiểm tra ESP32 còn online (dùng làm health check).
```json
{"device":"esp32-switch1","status":"online"}
```

### `GET /device/info`
Trạng thái cả 4 công tắc trong 1 request — phù hợp cho polling.
```json
{
  "device":"esp32-switch1",
  "status":"online",
  "switches":[
    {"gpio":2,"status":"on"},
    {"gpio":5,"status":"off"},
    {"gpio":18,"status":"off"},
    {"gpio":19,"status":"off"}
  ]
}
```

### `GET /time/status`
Kiểm tra ESP32 đã đồng bộ giờ NTP chưa (hẹn giờ chỉ chạy khi đã đồng bộ).

| Mã | Phản hồi |
|---|---|
| `200` | `{"status":"synchronized","timezone":"UTC+7","time":"2026-10-16 07:30:00"}` |
| `503` | `{"status":"unsynchronized","timezone":"UTC+7"}` |

---

## 4. Hẹn giờ (Schedule)

### `GET /schedule/add`
Thêm lịch mới, hoặc **cập nhật** nếu `id` đã tồn tại.

| Tham số | Bắt buộc | Mô tả |
|---|---|---|
| `id` | Có | Số nguyên dương (lấy từ database của Java) |
| `gpio` | Có | `2`, `5`, `18` hoặc `19` |
| `time` | Có | `HH:mm` |
| `action` | Có | `ON` hoặc `OFF` |
| `enabled` | Không | `true`/`1`/`on` là bật; giá trị khác là tắt. Mặc định `true` |

Ví dụ:
```
/schedule/add?id=1&gpio=2&time=07:30&action=ON&enabled=true
```
Phản hồi:
```json
{"result":"ok","id":1,"gpio":2,"time":"07:30","action":"ON","enabled":true}
```

**Lỗi:**

| Mã | Thông báo |
|---|---|
| `400` | `Required parameters: id, gpio, time, action` |
| `400` | `Invalid schedule id` / `Schedule id must be positive` |
| `400` | `Invalid gpio` |
| `400` | `Time must use HH:mm format` |
| `400` | `Action must be ON or OFF` |
| `409` | `This GPIO already has 10 schedules` |
| `409` | `Schedule storage is full` |

### `GET /schedule/delete?id={id}`
```json
{"result":"ok","deletedId":1}
```
**Lỗi:** `400 Missing id parameter`, `400 Invalid schedule id`, `404 Schedule not found`.

### `GET /schedule/list`
```json
{
  "device":"esp32-switch1",
  "schedules":[
    {"id":1,"gpio":2,"time":"07:30","action":"ON","enabled":true}
  ]
}
```

### Quy tắc chạy lịch
- Mỗi lịch lặp lại hằng ngày; mỗi ngày chạy tối đa 1 lần vào đúng phút đã hẹn.
- Lịch được lưu trong flash (NVS), còn nguyên sau khi ESP32 khởi động lại.
- Sau khi khởi động lại, lịch chỉ chạy tiếp khi ESP32 đồng bộ lại NTP (cần Internet).
- Sửa giờ, `action`, `gpio` hoặc `enabled` của một lịch sẽ xoá đánh dấu "đã chạy hôm nay", nên lịch có thể chạy lại trong cùng ngày nếu chưa qua giờ hẹn.
- Khi lịch chạy, ESP32 đổi trạng thái công tắc nhưng **không báo về Java** — Java cần polling để biết.

---

## 5. Endpoint không tồn tại

Mọi đường dẫn khác trả về:
```json
{"error":"Endpoint not found"}
```
kèm mã `404`.

---

## 6. Ví dụ gọi nhanh

**Trình duyệt / curl:**
```bash
curl "http://esp32-switch1.local/switch/on?gpio=2"
curl "http://esp32-switch1.local/device/info"
curl "http://esp32-switch1.local/schedule/add?id=1&gpio=2&time=07:30&action=ON"
curl "http://esp32-switch1.local/schedule/list"
curl "http://esp32-switch1.local/time/status"
```

**Java (`java.net.http`):**
```java
HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://esp32-switch1.local/switch/status?gpio=2"))
        .timeout(Duration.ofSeconds(3))
        .GET()
        .build();
HttpResponse<String> res = HttpClient.newHttpClient()
        .send(req, HttpResponse.BodyHandlers.ofString());

// ESP32 luôn trả 200 khi hợp lệ -> phải đọc nội dung JSON để biết bật hay tắt
boolean isOn = res.statusCode() == 200 && res.body().contains("\"status\":\"on\"");
```

---

## 7. Gợi ý tích hợp

- **Polling:** gọi `/device/info` mỗi 2–3 giây để cập nhật giao diện (bắt được thay đổi do nút bấm và hẹn giờ).
- **Health check:** gọi `/device/status` trước các thao tác quan trọng, timeout khoảng 3 giây.
- **Đồng bộ lịch:** Java lưu lịch trong database làm nguồn chính; khi tạo, sửa hoặc xoá lịch thì gọi `/schedule/add` hoặc `/schedule/delete` với cùng `id`. Nếu nghi ngờ lệch dữ liệu, đối chiếu với `/schedule/list`.
- **Kiểm tra giờ:** gọi `/time/status` trước khi tin rằng hẹn giờ đang hoạt động.
