# Kết quả kiểm tra Device Control — 15/10/2026

Phiên bản mã ứng dụng: Git `1c9200c`. Không chỉnh sửa mã ứng dụng trong lượt kiểm tra này.

## Phạm vi và bằng chứng

- Biên dịch thành công **37 file Java sản phẩm** bằng JDK 8. Có cảnh báo unchecked hiện hữu ở `DeviceControlController`.
- Chạy **50 kiểm tra HTTP** trên Tomcat 9 riêng: **41 PASS, 9 FAIL**. Các request đi qua controller, DAO và JSP thật. Chỉ kết nối database được thay bằng JDBC fixture trong bộ nhớ; ESP32 được thay bằng HTTP server localhost.
- Bộ dữ liệu gồm **45 switch hoạt động** thuộc hai ESP32; thêm một switch bị vô hiệu hóa và một switch thuộc ESP32 bị vô hiệu hóa. Có tài khoản Admin, Operator, Viewer và quyền Viewer đã thu hồi.
- Chạy **8 kiểm tra HardwareClient**: đều PASS, gồm lệnh thành công, HTTP lỗi, GPIO không khớp, dữ liệu trạng thái, GPIO không hợp lệ và lỗi kết nối.
- Mở UI trong Chrome và kiểm tra ảnh `build/device-control-audit/evidence/device-control-desktop.png`. Sidebar, tên switch, GPIO, ESP32, trạng thái và bộ lọc hiển thị được. Các thao tác và nội dung động được kiểm tra thêm qua HTTP; ảnh đơn lẻ không được dùng để kết luận mọi tương tác đều đúng.
- Đọc **SQL Server thật bằng SELECT**: DAO lấy được 15 switch hoạt động, 15 ID khác nhau, đều lưu OFF; 15 quyền, đều active; truy vấn 50 bản ghi lịch sử gần nhất chạy được; một Viewer hoạt động có 7 switch được cấp quyền. Đây là ảnh chụp dữ liệu tại thời điểm kiểm tra.
- **Chưa bật/tắt ESP32 thật**, chưa kiểm tra relay thực tế hoặc ghi vào database thật. Kết quả ghi trạng thái/lịch sử bên dưới là tích hợp với fixture. Không suy ra trạng thái phần cứng thật từ giá trị OFF trong database.
- Tomcat kiểm thử đã được dừng.

## Kết quả theo 15 mục

| STT | Tính năng | Kết luận | Đã thực hiện và kết quả |
|---|---|---|---|
| 1 | Đăng nhập | **ĐẠT** — 2/2 | Xóa session rồi truy cập cả `MainController?action=DEVICE_CONTROL` và `/DeviceControlController`. Cả hai hiển thị Login, không có danh sách switch. Hiện dùng **forward, HTTP 200**, không phải redirect HTTP 302. |
| 2 | Hiển thị danh sách switch | **ĐẠT trong phạm vi kiểm tra** — 3/3 | Tên, GPIO, mã/tên ESP32 và ON/OFF hiển thị theo dữ liệu DAO. Admin/Operator thấy 45 switch hoạt động, 20 trên trang đầu; switch inactive và ESP32 inactive bị loại. Viewer chỉ thấy hai switch có quyền active, không thấy switch có quyền đã thu hồi. Truy vấn và mapping trên SQL Server thật cũng chạy được. |
| 3 | Đồng bộ trạng thái ESP32 khi tải lại | **KHÔNG ĐẠT** — 0/1 | Cho database SW1 = ON nhưng ESP32 mô phỏng = OFF. Tải trang hai lần, UI vẫn ON, database vẫn ON và **không có request đọc phần cứng**. Trang hiện chỉ đọc trạng thái database. |
| 4 | Lọc theo ESP32 | **ĐẠT theo database** — 2/2 | Chọn ESP1: tổng 23 switch; chọn ESP2: tổng 22 switch. Các card trên mỗi trang thuộc đúng ESP32 đã chọn. |
| 5 | Lọc theo trạng thái | **ĐẠT theo database** — 3/3 | ON: 23 switch, các card đều ON. OFF: 22 switch, các card đều OFF. All: 45 switch. Đây là trạng thái lưu trong database, chưa bảo đảm trùng phần cứng vì mục 3 chưa đạt. |
| 6 | Kết hợp bộ lọc | **ĐẠT theo database** — 2/2 | ESP2 + ON: 11 switch; ESP2 + OFF: 11 switch. Mỗi kết quả khớp cả ESP32 và trạng thái. |
| 7 | Xóa bộ lọc | **ĐẠT** — 1/1 | Kiểm tra liên kết Clear filters rồi truy cập URL của liên kết. Trang trở lại trang 1, không lọc, hiển thị 20/45 switch. Clear filters chỉ xóa lựa chọn lọc; không khôi phục trạng thái ON/OFF đã điều khiển. |
| 8 | Bật/tắt switch | **ĐẠT MỘT PHẦN** — 4/5 | ON → OFF và OFF → ON đều đổi ESP32 mô phỏng, cập nhật trạng thái database mô phỏng và UI sau redirect. Viewer/Operator có quyền active cũng điều khiển được. Tuy nhiên, giả lập ESP32 trả **HTTP 200 với `ok:false` và không đổi relay**, hệ thống vẫn lưu OFF và ghi lịch sử thành công dù phần cứng còn ON. Chưa kiểm tra bật/tắt thiết bị thật. |
| 9 | Hiển thị lỗi đồng bộ | **ĐẠT MỘT PHẦN** — 2/4 | Khi nhấn điều khiển, ESP32 trả HTTP 503 hoặc trạng thái `UNKNOWN` đều làm UI hiện lỗi ở switch, không ghi trạng thái mới, không gửi lệnh bật/tắt tiếp và có lịch sử ERROR. Nhưng **chỉ tải lại trang** thì không phát hiện hai tình huống này vì không đọc ESP32; không có thông báo lỗi đồng bộ khi reload. |
| 10 | Lưu lịch sử điều khiển | **ĐẠT với fixture** — 2/2 | Sau lần điều khiển thành công đầu tiên có một bản ghi và mở trang Control History thấy SW1/OFF. Điều khiển lần hai thêm bản ghi thứ hai. Truy vấn SELECT lịch sử trên SQL Server thật thành công; chưa chạy INSERT trên database thật. Kết quả thành công có thể sai trong tình huống xác nhận lỗi ở mục 8. |
| 11 | Phân trang | **ĐẠT với fixture** — 3/3 | 45 switch chia 20/20/5. Hiển thị `Showing 1–20 of 45`, `21–40`, `41–45`; Previous/Next và trang đầu/cuối đúng. Đi theo URL Next rồi Previous thật giữ được ESP/status. Trang vượt số trang được kẹp về trang cuối; sau lọc ít kết quả được đưa về trang hợp lệ. |
| 12 | Dữ liệu rỗng | **ĐẠT về không lỗi** — 2/2 | Cả database không có switch và bộ lọc không có kết quả đều trả HTTP 200, không có card, không phát sinh trang lỗi. UI hiện chưa có thông báo riêng “No switches found”, nên vùng danh sách để trống. |
| 13 | Tham số không hợp lệ | **ĐẠT về xử lý an toàn** — 9/9 | `page=abc`, `-1`, `0`, số vượt int về trang 1; `page=999` về trang cuối. Status sai hiện `Invalid status filter.` và dùng All; `status=on` được chuẩn hóa thành ON. ESP không tồn tại hoặc chuỗi sai định dạng trả danh sách rỗng, không lỗi 500; chưa có lời giải thích “ESP32 không tồn tại”. |
| 14 | Bảo mật request | **KHÔNG ĐẠT** — 6 PASS, 4 FAIL | Chặn được anonymous POST, quyền đã thu hồi, switch không tồn tại và thiếu ID; giá trị ESP thử chèn script được escape trong HTML. Nhưng **GET trực tiếp `/SwitchStageController?id=SW1` và GET qua MainController vẫn gửi lệnh, ghi trạng thái và lịch sử**. POST thiếu CSRF hoặc CSRF sai cũng thực hiện được. Không thể kết luận an toàn chỉ vì nút trên UI dùng POST. |
| 15 | Double-click | **KHÔNG ĐẠT** — 0/1 | Cho hai request của cùng session đọc cùng trạng thái OFF trước khi gửi lệnh. Kết quả **2 lệnh ON, 2 cập nhật trạng thái và 2 lịch sử**. Chưa có cơ chế gộp/chặn request trùng ở server. Đây là tái hiện hai lần submit nhanh qua HTTP; chưa thử thao tác vật lý trên ESP32. |

Tổng theo tính năng: **10 đạt trong phạm vi nêu trên, 2 đạt một phần, 3 không đạt**. Chín kiểm tra FAIL là chín tình huống con, không phải chín tính năng khác nhau.

## Các lỗi cần xử lý

### 1. Request GET vẫn có thể điều khiển thiết bị; thiếu CSRF

**Ưu tiên cao.** `SwitchStageController.doGet()` gọi cùng luồng điều khiển với POST. Trong luồng không có xác minh CSRF. `MainController` cũng chuyển tiếp GET tới action điều khiển. Form POST trong JSP không đủ bảo vệ các URL này.

Vị trí: `src/java/controller/SwitchStageController.java:227`, `src/java/controller/MainController.java:40`, `web/web/DeviceControl.jsp:88`.

Tái hiện trong app kiểm thử: đăng nhập fixture Admin, gọi GET `/SwitchStageController?id=SW1`. Kết quả HTTP 302, phần cứng mô phỏng ON → OFF; số lệnh/ghi trạng thái/lịch sử đều bằng 1. Thực hiện POST không có token hoặc `csrf=invalid` cũng cho kết quả đó.

Hướng sửa: từ chối GET điều khiển bằng 405; kiểm tra CSRF ở controller đích trước side effect, kể cả request chuyển tiếp qua MainController. Giữ kiểm tra quyền active và switch/device active.

### 2. Request trùng tạo nhiều lệnh và lịch sử

**Ưu tiên cao.** Cả hai request đều đọc OFF rồi cùng gửi ON. Không có khóa/idempotency để giới hạn lần thực hiện của một thao tác người dùng.

Vị trí: `src/java/controller/SwitchStageController.java:102`.

Hướng sửa: cơ chế nhận diện và xử lý một lần cho cùng thao tác ở server; phối hợp kiểm soát đồng thời theo switch. Chỉ redirect sau POST không ngăn hai request ban đầu. Có thể làm bằng form HTML và Java, không bắt buộc JavaScript.

### 3. Reload không đồng bộ với ESP32

**Không đáp ứng mục 3 và phần reload của mục 9.** Controller chỉ lấy danh sách từ DAO, lọc rồi phân trang. JSP cũng ghi rõ trạng thái được lấy từ database.

Vị trí: `src/java/controller/DeviceControlController.java:75`, `web/web/DeviceControl.jsp:39`.

Hướng sửa theo checklist: có bước đọc trạng thái ESP32 khi tải lại, xử lý timeout/dữ liệu sai và cập nhật trạng thái trước khi tính kết quả lọc. Nếu chỉ đồng bộ sau phân trang thì bộ lọc trạng thái vẫn có thể dựa trên dữ liệu cũ.

### 4. HTTP 200 chưa chứng minh relay đã đổi trạng thái

**Có thể làm UI/database/lịch sử lệch phần cứng.** `HardwareClient.callAndCheckOk()` chỉ kiểm tra mã HTTP rồi trả true; không đọc body xác nhận. Controller không đọc lại trạng thái sau lệnh trước khi lưu kết quả thành công.

Vị trí: `src/java/utills/HardwareClient.java:268`, `src/java/controller/SwitchStageController.java:102`.

Tái hiện: server trả HTTP 200 + `{"ok":false}` nhưng không thay đổi trạng thái. Database bị ghi OFF, phần cứng vẫn ON và có lịch sử thành công. Đây là phản hồi lỗi được chủ động đưa vào fixture, **không phải khẳng định firmware thật đang trả phản hồi này**.

Hướng sửa: thống nhất hợp đồng xác nhận với firmware và xác minh trạng thái sau điều khiển. Chỉ ghi thành công khi có bằng chứng phù hợp từ thiết bị.

## Lưu ý bổ sung

- `SwitchStageController` redirect sau điều khiển chỉ giữ `page`; không giữ `esp` và `status` dù form gửi hai giá trị đó. Đây là quan sát từ mã tại dòng 162, chưa đưa vào số lỗi của checklist vì không có tiêu chí riêng yêu cầu giữ bộ lọc sau điều khiển.
- `SwitchDAO.findAll()` chưa có ORDER BY. Phân trang trên fixture ổn định; chưa có bằng chứng thứ tự trên SQL Server được bảo đảm khi dữ liệu thay đổi. Nên quy định thứ tự trước khi coi phân trang ổn định giữa các lần truy cập.
- Kết quả thử chuỗi script trong bộ lọc không thay thế một cuộc kiểm tra bảo mật toàn ứng dụng.
- Các tên “ĐẠT” bên trên chỉ áp dụng cho dữ liệu/luồng đã nêu, không bao hàm transaction SQL Server, lỗi ghi database thật hoặc firmware chưa được chạy thử.

## Chạy lại và xem log

Bộ kiểm tra và cách chạy: `tests/device-control-audit/README.md`.

Đã chạy từ thư mục dự án:

```powershell
& ([scriptblock]::Create([IO.File]::ReadAllText((Join-Path (Get-Location).Path 'tests/device-control-audit/run.ps1'))))
```

Kết quả chính:

```text
AUDIT SUMMARY: 41 passed, 9 failed. Isolated DB / HTTP hardware fixtures; no live database or ESP32 writes.
PASS: 8 HardwareClient exception checks.
AUDIT_EXIT=1
```

Exit 1 là kết quả mong đợi của bộ kiểm tra khi các lỗi sản phẩm còn tồn tại. Không sửa kỳ vọng để biến các lỗi này thành PASS. Trường hợp ESP không tồn tại được coi là xử lý an toàn khi trả danh sách rỗng; thông báo cụ thể là đề xuất UX, không tự đặt thành yêu cầu bắt buộc.

Log và bằng chứng trong workspace:

- `build/device-control-audit/audit.log`: toàn bộ 50 kết quả.
- `build/device-control-audit/evidence/results.tsv`: mỗi kiểm tra một dòng, mở được bằng Excel.
- `build/device-control-audit/evidence/*.html`: nội dung HTML thật của Login, danh sách, trạng thái cũ, lỗi, lịch sử và trang cuối.
- `build/device-control-audit/evidence/device-control-desktop.png`: ảnh Chrome của trang danh sách.
- `build/device-control-audit/hardware-client.log`: kết quả 8 kiểm tra HardwareClient.
- `build/device-control-audit/live-read-only.log`: kết quả SELECT SQL Server thật.
- `build/device-control-audit/server.err.log`: log server; các HardwareException do ca 503/UNKNOWN là lỗi được chủ động tạo trong fixture.

Probe SQL thật được biên dịch vào `build/device-control-audit/production-classes` và chạy bằng classpath **production-classes + lib/sqljdbc4.jar**, không phải lớp DBConnection của fixture:

```powershell
$java = 'C:\Program Files\Java\jdk1.8.0_172\bin'
$classes = Join-Path (Get-Location).Path 'build\device-control-audit\production-classes'
& "$java\javac.exe" -encoding UTF-8 -cp $classes -d $classes 'tests\device-control-audit\audit\ReadOnlyDeviceControlProbe.java'
& "$java\java.exe" -cp "$classes;lib\sqljdbc4.jar" audit.ReadOnlyDeviceControlProbe
```

## Còn cần thử trên thiết bị thật

Chọn chính xác ESP32, Switch ID/GPIO và tải có thể bật/tắt thử. Sau đó kiểm tra ON → OFF → ON bằng trạng thái phần cứng, giá trị database và lịch sử; thay đổi trạng thái trực tiếp trên ESP32 rồi reload; ngắt kết nối và thử lại; kiểm tra request trùng. Các bước này hiện **UNVERIFIED** trên phần cứng thật.
