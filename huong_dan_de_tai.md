# HƯỚNG DẪN ĐỀ TÀI — SMART SWITCH

**Hệ thống điều khiển công tắc thông minh qua Web và tự giám sát trạng thái theo thời gian**
_(Web-Based Smart Switch Control and Real-Time Status Monitoring System)_

- Môn: Lập trình ứng dụng web (PRJ301) — Trường Đại học FPT
- Công nghệ: Java, JSP, Servlet — NetBeans (Java web application) — Tomcat 9 — SQL Server
- Phần cứng: ESP32 NodeMCU 38 chân + LED + nút bấm vật lý
- Tài liệu này được cập nhật theo tiến độ trao đổi của nhóm. Các mục đánh dấu **[Đề xuất]** là phần chưa được nhóm chốt chính thức, các mục **[Đã có]** là phần đã có trong script/thiết kế hiện tại.

---

## 1. Mục tiêu và phạm vi

### 1.1 Vấn đề cần giải quyết

Việc bật/tắt thiết bị điện hiện phụ thuộc công tắc vật lý, người dùng phải thao tác trực tiếp tại thiết bị, khó theo dõi trạng thái và lịch sử sử dụng. Đề tài xây dựng hệ thống cho phép **điều khiển, giám sát và quản lý thiết bị từ xa qua trang Web**, kết hợp phần mềm Web với phần cứng ESP32.

### 1.2 Điểm mới nhóm phải làm rõ trong báo cáo

Chuỗi hoạt động hoàn chỉnh: **Web → WiFi → ESP32 → GPIO → LED/thiết bị → Response → Web**.

1. Điều khiển thiết bị thông qua Web (không cần công tắc vật lý).
2. Kết hợp phần mềm và phần cứng thật (LED mô phỏng thiết bị điện).
3. Giao tiếp không dây qua WiFi (hotspot điện thoại).
4. Đồng bộ trạng thái thiết bị giữa ESP32 và Web.
5. Dùng tên miền nội bộ ổn định bằng mDNS (`esp32-switch.local`) thay cho IP.
6. Mô hình prototype có khả năng mở rộng sang thiết bị khác qua phần tử đóng/ngắt phù hợp.

### 1.3 Điểm phải chứng minh khi demo và báo cáo

1. Web gửi được lệnh điều khiển đến ESP32.
2. ESP32 nhận và xử lý đúng lệnh.
3. GPIO thay đổi trạng thái và điều khiển LED.
4. Trạng thái được phản hồi và cập nhật lại trên Web.
5. Mỗi lần điều khiển được ghi nhận thành một phiên dữ liệu.
6. Xử lý được trường hợp giao tiếp thất bại và trả về nhãn `ERROR`.

---

## 2. Phiên dữ liệu và nhãn kết quả

**Phiên dữ liệu (Data Session)** là một chuỗi tương tác hoàn chỉnh từ lúc người dùng gửi yêu cầu điều khiển đến lúc Web nhận phản hồi và cập nhật trạng thái. Trong CSDL, **một phiên dữ liệu = một dòng trong `tblControl_History`**.

```
User Action → Web Request → WiFi → ESP32 → GPIO/LED → Response → Web Status Update
```

| Nhãn      | Ý nghĩa                                                                                             |
| --------- | --------------------------------------------------------------------------------------------------- |
| **ON**    | Thiết bị đang bật (LED sáng)                                                                        |
| **OFF**   | Thiết bị đang tắt (LED tắt)                                                                         |
| **ERROR** | Lệnh không thực hiện được hoặc lỗi giao tiếp với ESP32 (ESP32 không phản hồi, yêu cầu không hợp lệ) |

---

## 3. Mô hình phần cứng

### 3.1 Linh kiện

| Linh kiện             | Thông số                   | SL           | Chức năng                                            |
| --------------------- | -------------------------- | ------------ | ---------------------------------------------------- |
| ESP32 NodeMCU 38 chân | USB Type-C, UART CP2102    | 1            | Bộ điều khiển trung tâm, WiFi                        |
| LED đơn               | LED thường 5mm             | 1            | Mô phỏng thiết bị điện                               |
| Điện trở              | 220Ω – 330Ω                | 1            | Hạn dòng LED                                         |
| Nút bấm               | Tactile push button 4 chân | 1            | Điều khiển vật lý tại chỗ                            |
| Breadboard            | SYB-170                    | 1            | Lắp LED, điện trở, nút bấm (**không** cắm ESP32 lên) |
| Dây jumper            | Đực – đực                  | theo nhu cầu | Nối GPIO, GND                                        |
| Điện thoại            | Hotspot WiFi 2.4GHz        | 1            | Cấp mạng cho ESP32 và laptop                         |
| Laptop/PC             | Chạy Tomcat                | 1            | Chạy Web Application                                 |

### 3.2 Đấu nối

```
ESP32 GPIO2  ──> [Điện trở 220Ω] ──> [LED Anode +]   [LED Cathode -] ──> ESP32 GND
ESP32 GPIO4  ──> [Nút bấm] ──> ESP32 GND              (INPUT_PULLUP, không cần điện trở ngoài)
```

- Nút 4 chân: mỗi cặp chân cùng phía đã nối sẵn với nhau, chỉ cần dùng 1 chân mỗi bên.
- Khi không nhấn: GPIO4 đọc `HIGH`; khi nhấn: `LOW`. Firmware có chống dội phím (debounce 50ms).

### 3.3 Mạng và giao tiếp

- ESP32 và laptop chạy Java Web **phải cùng một hotspot điện thoại**.
- Bảo mật hotspot: **WPA2-Personal**, băng tần **2.4GHz** (ESP32 bản thường không hỗ trợ 5GHz).
- Hotspot không có giao diện quản trị router nên không đặt được IP tĩnh → dùng **ESPmDNS**, tên cố định `esp32-switch.local`. Nếu mDNS lỗi thì dùng tạm IP in ở Serial Monitor.
- Sau khi nạp firmware, ESP32 chạy độc lập, chỉ cần nguồn USB và WiFi; máy tính/Arduino IDE chỉ cần khi nạp lại code hoặc xem Serial Monitor.

### 3.4 Endpoint firmware hiện có

| Endpoint          | Chức năng                                                   | Response                                  |
| ----------------- | ----------------------------------------------------------- | ----------------------------------------- |
| `GET /led/on`     | Bật LED                                                     | `{"status":"on"}`                         |
| `GET /led/off`    | Tắt LED                                                     | `{"status":"off"}`                        |
| `GET /led/status` | Đọc trạng thái hiện tại (phản ánh cả thao tác bằng nút bấm) | `{"status":"on"}` hoặc `{"status":"off"}` |

Nút bấm và lệnh Web cùng tác động lên một biến trạng thái `ledState` trong firmware để đảm bảo đồng bộ.

### 3.5 Hai phương thức điều khiển

```
Công tắc vật lý ──> ESP32 ──> LED
Web ──> WiFi ─────────┘
```

ESP32 không tự báo lên Java khi có người bấm nút tại chỗ → Web phải **polling** định kỳ (gọi `/led/status` mỗi 2–3 giây qua Servlet) để cập nhật trạng thái.

> Đầy đủ code firmware, code Java gọi HTTP và bảng lỗi thường gặp nằm trong file `esp32-wifi-setup.md`.

---

## 4. Vai trò và phân quyền

Ba vai trò: **Admin**, **Operator**, **Viewer**. Quyền của Viewer được xác định bởi **Role + Device Permission** (bảng do Operator/Admin cấp), không chỉ dựa vào Role.

### 4.1 Quyền CRUD theo đối tượng

| Đối tượng         | Admin   | Operator | Viewer              |
| ----------------- | ------- | -------- | ------------------- |
| User              | C/R/U/D | —        | —                   |
| ESP32 Device      | C/R/U/D | C/R/U    | R\*                 |
| Switch            | C/R/U/D | C/R/U    | R/U\* (U = bật/tắt) |
| Device Permission | C/R/U/D | C/R/U/D  | —                   |
| Control History   | C/R/D   | C/R      | R\*                 |
| Device Status     | R       | R        | R\*                 |

`*` = chỉ áp dụng cho Switch/Device đã được cấp quyền.

### 4.2 Công việc từng vai trò

- **Admin**: quản lý User, ESP32/Switch, phân quyền, điều khiển, xem và xóa lịch sử.
- **Operator**: quản lý ESP32/Switch (không xóa), điều khiển, xem lịch sử, xem danh sách Viewer, cấp/sửa/thu hồi quyền Viewer. Không quản lý User.
- **Viewer**: xem và bật/tắt các Switch được cấp quyền, xem lịch sử liên quan. **Không được**: thêm/xóa/sửa Device, tự cấp quyền, cấp quyền cho người khác, quản lý User.

### 4.3 Luồng kiểm tra quyền của Viewer

```
Viewer → Kiểm tra Device Permission → Có quyền? → Cho phép View / ON / OFF
                                            └─ Không → Từ chối (403), ghi nhận sự kiện
```

Việc kiểm tra quyền phải thực hiện **ở phía máy chủ** (Filter + Service), không chỉ ẩn nút trên JSP.

---

## 5. Các trang Web phải làm

| #   | Trang                      | Admin | Operator | Viewer | Chức năng chính                                                            |
| --- | -------------------------- | ----- | -------- | ------ | -------------------------------------------------------------------------- |
| 1   | Login                      | ✓     | ✓        | ✓      | Đăng nhập, xác thực, tạo session                                           |
| 2   | Dashboard                  | ✓     | ✓        | ✓      | Tổng quan ESP32/Switch, số ON/OFF, trạng thái Online (giới hạn theo quyền) |
| 3   | User Management            | ✓     | —        | —      | CRUD tài khoản và Role                                                     |
| 4   | Device & Switch Management | ✓     | ✓        | R\*    | Quản lý ESP32 (hostname, trạng thái) và Switch (GPIO, gán vào ESP32)       |
| 5   | Device Control             | ✓     | ✓        | ✓\*    | Điều khiển ON/OFF                                                          |
| 6   | Permission Management      | ✓     | ✓        | —      | Cấp, sửa, thu hồi quyền Viewer theo Switch                                 |
| 7   | Control History            | ✓     | ✓        | R\*    | Xem lịch sử điều khiển                                                     |
| 8   | Profile / Account          | ✓     | ✓        | ✓      | Xem/cập nhật thông tin cá nhân                                             |
| 9   | Access Denied / 403        | ✓     | ✓        | ✓      | Thông báo không đủ quyền                                                   |
| 10  | Schedule **[Đề xuất]**     | ✓     | ✓        | ✓\*    | Hẹn giờ bật/tắt Switch                                                     |
| 11  | Alert **[Đề xuất]**        | ✓     | ✓        | R\*    | Xem cảnh báo                                                               |

Menu theo vai trò: Admin thấy đủ; Operator không có User Management; Viewer chỉ có Dashboard, Device & Switch (chỉ xem), Device Control, Control History, Profile.

---

## 6. Luật cảnh báo và thống kê

### 6.1 Sáu luật cảnh báo

| #   | Luật                          | Điều kiện                                          | Nội dung                 |
| --- | ----------------------------- | -------------------------------------------------- | ------------------------ |
| 1   | Thiết bị mất kết nối          | ESP32 không phản hồi trong thời gian quy định      | Device Offline           |
| 2   | Điều khiển thất bại           | ESP32 trả lỗi hoặc không phản hồi khi gửi ON/OFF   | Control Failed           |
| 3   | Trạng thái không đồng bộ      | Trạng thái trên Web khác trạng thái thật của ESP32 | Status Mismatch          |
| 4   | Nhiều lần thất bại            | Một Switch thất bại liên tiếp nhiều lần            | Repeated Control Failure |
| 5   | Truy cập không được cấp quyền | Viewer điều khiển Switch chưa được cấp quyền       | Access Denied            |
| 6   | Thiết bị bất thường           | ESP32 liên tục Online/Offline trong thời gian ngắn | Unstable Device          |

Luồng xử lý chung:
`User Action → Web Server → Check Rule → Execute Command → Receive ESP32 Response → Check Result → Create Alert if Necessary → Update Control History`

### 6.2 Các con số bắt buộc có trong báo cáo

- Trạng thái từng ESP32/Switch theo thời gian (Online/Offline, ON/OFF).
- Số lần điều khiển thành công/thất bại và tỷ lệ thành công.
- Số cảnh báo theo từng loại và tỷ lệ đã xử lý hoặc bị từ chối.
- Số lần điều khiển theo thời gian (theo ngày/giờ).
- Bảng đối chiếu trạng thái thiết bị và lịch sử điều khiển.

Đề đã cho 5 truy vấn thống kê mẫu (theo kết quả, theo Switch, theo giờ, theo loại cảnh báo, trạng thái ESP32). Tên bảng/cột trong đề dùng `Control_History`, `Alert`, `ESP32_Device`, cần đổi theo tên thật (`tblControl_History`, `tblAlert`, `tblESP32_Device`).

---

## 7. Ràng buộc hiện có

### 7.1 User

- `user_id` **phải bắt đầu bằng `U`** (ví dụ `U001`).
- Vai trò (`role`) **phải là 1 trong 3**: `ADMIN`, `OPERATOR`, `VIEWER` (mặc định `VIEWER`).

### 7.2 ESP32

- `device_id` **phải bắt đầu bằng `ESP`** (ví dụ `ESP001`).
- Trạng thái (`status`) **phải là 1 trong 3**: `ONLINE`, `OFFLINE`, `ERROR` (mặc định `OFFLINE`).

### 7.3 Switch

- `switch_id` **phải bắt đầu bằng `SW`** (ví dụ `SW001`).
- Trạng thái (`status`) **phải là 1 trong 2**: `ON`, `OFF` (mặc định `OFF`).

### 7.4 Ràng buộc nghiệp vụ theo đề

- Viewer chỉ xem/điều khiển Switch có bản ghi trong Device Permission với `canView`/`canControl` tương ứng.
- Viewer không được tự cấp quyền cho mình hoặc cho Viewer khác.
- Operator không xóa ESP32/Switch, không quản lý User.
- Chỉ Admin được xóa lịch sử điều khiển.
- Control History và Device Status do **hệ thống** ghi, không ai sửa tay.
- Mỗi lần ON/OFF phải ghi một dòng Control History (User → Switch → Command → Result → Time).
- Mật khẩu phải được mã hóa (hash) khi lưu.
- Kết quả điều khiển chỉ có 3 nhãn: `ON`, `OFF`, `ERROR`.

### 7.5 Ràng buộc bổ sung (nhóm điền thêm bên dưới)

- ...

---

## 8. Thiết kế cơ sở dữ liệu

Tên CSDL: **SmartSwitchSystem** (SQL Server).

### 8.1 Sơ đồ quan hệ

```
tblUser ──< tblDevice_Permission >── tblSwitch >── tblESP32_Device
   │                                     │
   └────────< tblControl_History >───────┘

tblAlert  ── tham chiếu ESP32 / Switch / User   [Đề xuất]
tblSchedule ── tham chiếu Switch / User          [Đề xuất]
```

### 8.2 Các bảng đã có trong script

**tblUser** — tài khoản

| Cột       | Kiểu         | Ràng buộc                                                  |
| --------- | ------------ | ---------------------------------------------------------- |
| user_id   | varchar(20)  | PK, CHECK `LIKE 'U%'`                                      |
| user_name | nvarchar(50) | NOT NULL                                                   |
| password  | varchar(50)  | NOT NULL                                                   |
| full_name | nvarchar(50) | NOT NULL                                                   |
| role      | varchar(10)  | DEFAULT `'VIEWER'`, CHECK IN (`ADMIN`,`OPERATOR`,`VIEWER`) |
| is_active | bit          | DEFAULT 1                                                  |

**tblESP32_Device** — ESP32 vật lý

| Cột       | Kiểu         | Ràng buộc                                                  |
| --------- | ------------ | ---------------------------------------------------------- |
| device_id | varchar(20)  | PK, CHECK `LIKE 'ESP%'`                                    |
| name      | nvarchar(50) | NOT NULL                                                   |
| host_name | varchar(255) | NOT NULL (ví dụ `esp32-switch.local`)                      |
| status    | varchar(10)  | DEFAULT `'OFFLINE'`, CHECK IN (`ONLINE`,`OFFLINE`,`ERROR`) |
| last_seen | datetime     | lần cuối phản hồi                                          |

**tblSwitch** — công tắc logic gắn với một GPIO

| Cột         | Kiểu         | Ràng buộc                              |
| ----------- | ------------ | -------------------------------------- |
| switch_id   | varchar(20)  | PK, CHECK `LIKE 'SW%'`                 |
| device_id   | varchar(20)  | FK → tblESP32_Device, NOT NULL         |
| switch_name | nvarchar(50) | NOT NULL                               |
| gpio_pin    | tinyint      | NOT NULL                               |
| status      | varchar(6)   | DEFAULT `'OFF'`, CHECK IN (`ON`,`OFF`) |

**tblDevice_Permission** — quyền Viewer trên từng Switch

| Cột           | Kiểu        | Ràng buộc                |
| ------------- | ----------- | ------------------------ |
| permission_id | varchar(20) | PK                       |
| user_id       | varchar(20) | FK → tblUser, NOT NULL   |
| switch_id     | varchar(20) | FK → tblSwitch, NOT NULL |
| canView       | tinyint     | DEFAULT 1                |
| canControl    | tinyint     | DEFAULT 1                |

**tblControl_History** — lịch sử điều khiển (mỗi dòng = một phiên dữ liệu)

| Cột          | Kiểu        | Ràng buộc                      |
| ------------ | ----------- | ------------------------------ |
| history_id   | varchar(20) | PK                             |
| user_id      | varchar(20) | FK → tblUser (cho phép NULL)   |
| switch_id    | varchar(20) | FK → tblSwitch (cho phép NULL) |
| command      | varchar(10) | NOT NULL                       |
| result       | varchar(10) | NOT NULL                       |
| control_time | datetime    |                                |

### 8.3 Đề xuất chỉnh sửa bảng hiện có **[Đề xuất]**

| Bảng                 | Đề xuất                                                                 | Lý do                                                                    |
| -------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| tblUser              | Tăng `password` lên `varchar(255)`                                      | Chuỗi hash (SHA-256 hex = 64 ký tự, BCrypt = 60) không vừa `varchar(50)` |
| tblUser              | Thêm `email nvarchar(100)`                                              | Trang Profile của đề hiển thị email                                      |
| tblSwitch            | `UNIQUE (device_id, gpio_pin)`                                          | Tránh 2 Switch dùng cùng chân GPIO của một ESP32                         |
| tblDevice_Permission | Thêm `granted_by` (FK tblUser), `granted_at datetime DEFAULT GETDATE()` | Đề (mục VI.4) yêu cầu lưu ai cấp quyền, khi nào                          |
| tblDevice_Permission | `UNIQUE (user_id, switch_id)`; đổi `canView`/`canControl` sang `bit`    | Chống cấp quyền trùng; đúng ngữ nghĩa cờ đúng/sai                        |
| tblControl_History   | `control_time DEFAULT GETDATE()`                                        | Tránh NULL khi code quên gán                                             |
| tblControl_History   | `CHECK command IN ('ON','OFF')`, `CHECK result IN ('ON','OFF','ERROR')` | Đúng 3 nhãn kết quả của đề                                               |
| tblControl_History   | Thêm `source varchar(10) DEFAULT 'WEB'` (`WEB`/`SCHEDULE`/`BUTTON`)     | Phân biệt lệnh do người dùng, hẹn giờ, nút bấm                           |

### 8.4 Bảng bổ sung **[Đề xuất]**

**tblSchedule** — hẹn giờ bật/tắt

```sql
create table tblSchedule(
    schedule_id int identity(1,1) primary key,
    switch_id varchar(20) not null references tblSwitch(switch_id),
    created_by varchar(20) not null references tblUser(user_id),
    action varchar(3) not null check (action in ('ON','OFF')),
    run_time time not null,               -- giờ chạy, ví dụ 18:30
    repeat_days varchar(20) null,         -- 'MON,TUE,FRI'; null = chạy 1 lần
    run_date date null,                   -- dùng khi hẹn 1 lần
    is_enabled bit default 1
)
```

Quy tắc sinh mã: `U001`, `ESP001`, `SW001`… (tầng ứng dụng sinh mã theo prefix). Các bảng phát sinh nhiều dòng (`tblAlert`, `tblSchedule`) dùng `identity`.

---

## 9. Cấu trúc class Java

Đề xuất các package (kiến trúc MVC + tầng Service/DAO):

### 9.1 `model` — POJO tương ứng bảng

| Class                    | Bảng                                                                    |
| ------------------------ | ----------------------------------------------------------------------- |
| `User`                   | tblUser                                                                 |
| `Esp32Device`            | tblESP32_Device                                                         |
| `SwitchDevice`           | tblSwitch _(không đặt tên `Switch` để tránh nhầm với từ khóa `switch`)_ |
| `DevicePermission`       | tblDevice_Permission                                                    |
| `ControlHistory`         | tblControl_History                                                      |
| `Schedule` **[Đề xuất]** | tblSchedule                                                             |

### 9.2 `dao` — truy cập dữ liệu (bằng `PreparedStatement`, chống SQL Injection)

| Class                       | Chức năng chính                                                                 |
| --------------------------- | ------------------------------------------------------------------------------- |
| `DBContext` / `DBUtil`      | Mở kết nối SQL Server (chuỗi kết nối là thứ duy nhất cần sửa khi chạy máy khác) |
| `UserDAO`                   | CRUD User, tìm theo username, kiểm tra đăng nhập                                |
| `Esp32DeviceDAO`            | CRUD ESP32, cập nhật `status`, `last_seen`                                      |
| `SwitchDAO`                 | CRUD Switch, cập nhật trạng thái ON/OFF, lấy Switch theo ESP32                  |
| `DevicePermissionDAO`       | Cấp/sửa/thu hồi quyền, lấy danh sách Switch mà Viewer được phép                 |
| `ControlHistoryDAO`         | Ghi lịch sử, tìm kiếm/lọc, xóa (Admin), các truy vấn thống kê                   |
| `ScheduleDAO` **[Đề xuất]** | CRUD lịch, `findDueSchedules(now)`, `updateLastRun(...)`                        |

### 9.3 `hardware` — giao tiếp ESP32

| Class                        | Vai trò                                                                               |
| ---------------------------- | ------------------------------------------------------------------------------------- |
| `HardwareClient` (interface) | `turnOn`, `turnOff`, `getStatus` — tách để dễ thay cách giao tiếp                     |
| `RealHardwareClient`         | Gọi HTTP (`java.net.http.HttpClient`, timeout 3 giây) tới `http://esp32-switch.local` |

Địa chỉ ESP32 đọc từ `hardware.properties` (`hardware.esp32.baseUrl`), không hard-code.

### 9.4 `service` — nghiệp vụ

| Class                           | Chức năng                                                                                                                     |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `AuthService`                   | Đăng nhập, kiểm tra tài khoản active, xác định Role                                                                           |
| `UserService`                   | Quản lý User (Admin)                                                                                                          |
| `DeviceService`                 | Quản lý ESP32/Switch                                                                                                          |
| `PermissionService`             | Cấp/sửa/thu hồi quyền, `canView(user, switch)`, `canControl(user, switch)`                                                    |
| `SwitchControlService`          | **Lõi hệ thống**: kiểm tra quyền → gọi `HardwareClient` → cập nhật `tblSwitch` → ghi `tblControl_History` → tạo Alert nếu lỗi |
| `ScheduleService` **[Đề xuất]** | Kiểm tra quyền và dữ liệu lịch, xác định lịch đến hạn                                                                         |
| `StatisticsService`             | Tính số liệu cho Dashboard và báo cáo                                                                                         |

### 9.5 `controller` — Servlet

| Servlet                         | Trang / chức năng                                         |
| ------------------------------- | --------------------------------------------------------- |
| `LoginServlet`, `LogoutServlet` | Đăng nhập / đăng xuất                                     |
| `DashboardServlet`              | Dashboard                                                 |
| `UserServlet`                   | User Management (Admin)                                   |
| `DeviceServlet`                 | Device & Switch Management                                |
| `SwitchControlServlet`          | Device Control (nhận lệnh ON/OFF, ví dụ `/device/toggle`) |
| `StatusApiServlet`              | Trả JSON trạng thái cho polling từ trang Web              |
| `PermissionServlet`             | Permission Management                                     |
| `HistoryServlet`                | Control History                                           |
| `ProfileServlet`                | Profile / Account                                         |
| `ScheduleServlet` **[Đề xuất]** | Hẹn giờ                                                   |

### 9.6 `filter` — xác thực và phân quyền phía máy chủ

| Class                  | Vai trò                                                       |
| ---------------------- | ------------------------------------------------------------- |
| `AuthenticationFilter` | Chưa đăng nhập → chuyển về Login                              |
| `AuthorizationFilter`  | Kiểm tra Role theo URL; sai quyền → trang 403 (Access Denied) |

### 9.7 `listener` / `scheduler` — tiến trình nền **[Đề xuất]**

| Class               | Vai trò                                                                                          |
| ------------------- | ------------------------------------------------------------------------------------------------ |
| `SchedulerListener` | `ServletContextListener`, tạo `ScheduledExecutorService`, hủy khi Tomcat dừng                    |
| `ScheduleJob`       | Mỗi 30–60 giây lấy lịch đến hạn, gọi `SwitchControlService`, ghi history với `source = SCHEDULE` |
| `DeviceMonitorJob`  | Định kỳ ping ESP32 để cập nhật `status`/`last_seen`, làm cơ sở cho luật 1, 3, 6                  |

### 9.8 `util`

| Class          | Vai trò                              |
| -------------- | ------------------------------------ |
| `PasswordUtil` | Mã hóa và kiểm tra mật khẩu          |
| `ConfigUtil`   | Đọc `hardware.properties`            |
| `IdGenerator`  | Sinh mã theo prefix `U`, `ESP`, `SW` |

### 9.9 Trang JSP (Web Pages)

`login.jsp`, `dashboard.jsp`, `userManagement.jsp`, `deviceSwitchManagement.jsp`, `deviceControl.jsp`, `permissionManagement.jsp`, `controlHistory.jsp`, `profile.jsp`, `403.jsp`, `schedule.jsp` **[Đề xuất]**

---

## 10. Tính năng bổ sung ngoài đề gốc

### 10.1 Nút bấm vật lý — **[Đã có]**

Đã thiết kế trong firmware và sơ đồ đấu nối (mục 3). Cần polling phía Web để đồng bộ trạng thái.

### 10.2 Hẹn giờ — **[Đề xuất]**

- Chạy hẹn giờ **trên server Java** (không cần thêm phần cứng): `SchedulerListener` + `ScheduleJob` gọi ESP32 khi đến giờ.
- Cần: bảng `tblSchedule`, các class ở mục 9.2 / 9.4 / 9.5 / 9.7, trang `schedule.jsp`.
- Lưu ý: chống chạy trùng bằng `last_run`; đặt múi giờ `Asia/Ho_Chi_Minh`; ESP32 offline lúc đến giờ → ghi `ERROR` và tạo cảnh báo; Viewer chỉ hẹn giờ Switch có `canControl`; laptop chạy Tomcat phải bật lúc đến giờ.

### 10.3 Tích hợp AI — **[Chưa chốt hướng]**

Nhóm muốn có tính năng AI nhưng đề gốc không nêu. Các hướng khả thi (chọn 1):

- Gợi ý lịch bật/tắt từ thói quen trong `tblControl_History`.
- Phát hiện bất thường (bật quá lâu, lỗi lặp lại).
- Chatbot điều khiển bằng ngôn ngữ tự nhiên (gọi API AI từ Servlet).

Khi chốt hướng sẽ bổ sung class (ví dụ `AiAdvisorService`, `AiClient`) và trang/chức năng tương ứng.

---

## 11. Danh mục nộp và điều kiện chấm

| Mục nộp                          | Yêu cầu                                                                                              |
| -------------------------------- | ---------------------------------------------------------------------------------------------------- |
| Mã nguồn ứng dụng web            | Thư mục dự án NetBeans nén lại, mở và chạy được trên Tomcat 9, không phải sửa gì ngoài chuỗi kết nối |
| Bản sao lưu CSDL                 | Tệp backup chứa **dữ liệu thật**                                                                     |
| Script tạo bảng                  | Tệp riêng                                                                                            |
| Mã nguồn cho bo mạch             | Tệp firmware nạp vào ESP32                                                                           |
| Tệp số liệu                      | File CSV xuất từ hệ thống                                                                            |
| Video                            | Một tệp video demo (đề nêu "7 cảnh" nhưng phần kịch bản chưa có nội dung trong file đề)              |
| Báo cáo                          | Mô tả đề, mô hình thiết bị, thiết kế CSDL, các con số ở mục 6.2, phần điểm mới, phân công công việc  |
| Bản đánh dấu danh sách công việc | Bảng công việc đã điền người làm và dấu xong                                                         |

**Điều kiện được chấm:** thiếu bản sao lưu CSDL hoặc thiếu video thì **không đủ điều kiện chấm**. Nếu chỉ chạy được trên dữ liệu mẫu, vẫn nộp và vẫn được chấm phần ứng dụng nhưng phải nói rõ trong báo cáo.

---

## 12. Điểm cần lưu ý và cần quyết định

1. **Phần đề còn là khung trống hoặc thuộc mẫu chung**: các mục III, IV, V, VIII, X trong file đề chỉ có tiêu đề; danh sách công việc và danh mục nộp còn nhắc đến `AirDB`, `is_sample`, 5 vai trò, cảm biến, hiệu chuẩn, 880 phiên, biểu đồ canvas — các nội dung này có vẻ thuộc đề khác cùng môn, cần đối chiếu với giảng viên xem áp dụng thế nào cho đề Smart Switch.
2. **Xung đột GPIO**: đề lấy ví dụ LED 2 ở GPIO4, LED 3 ở GPIO5, trong khi nút bấm hiện đang dùng GPIO4. Nếu làm nhiều Switch, nên chuyển nút bấm sang chân khác (ví dụ GPIO27) và cập nhật firmware.
3. **Firmware mới hỗ trợ 1 LED** (`/led/on|off|status`). Khi có nhiều Switch với `gpio_pin` khác nhau trong DB, cần mở rộng endpoint để chỉ định chân/Switch.
4. **Ghi nhận Access Denied**: đề (luật 5) yêu cầu ghi vào Control History, nhưng nhãn kết quả chỉ có `ON/OFF/ERROR`. Cần quyết định: thêm nhãn `DENIED`, hoặc chỉ ghi vào `tblAlert`.
5. **Độ dài cột `password`** trong script hiện tại (50 ký tự) không đủ cho chuỗi hash — cần sửa (mục 8.3).
6. **Trạng thái ESP32 `ERROR`** đã có trong ràng buộc nhưng đề chưa định nghĩa khi nào xảy ra; nhóm cần thống nhất (ví dụ: ESP32 online nhưng trả lỗi/trạng thái không hợp lệ).
7. Cần chốt hướng **AI** (mục 10.3) trước khi thiết kế thêm class và bảng.
