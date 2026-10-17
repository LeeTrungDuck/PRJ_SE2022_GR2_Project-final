# Kiểm tra ADMIN A1–A10 — 15/10/2026

Mã ứng dụng: `1c9200c`. Chỉ thêm bộ kiểm tra và báo cáo, không sửa mã sản phẩm.

## Phạm vi

- 37 file Java sản phẩm biên dịch thành công bằng JDK 8.
- 53 kiểm tra HTTP trên Tomcat 9 localhost: **47 PASS, 6 FAIL**.
- Các request đi qua MainController hoặc URL Servlet trực tiếp, controller, DAO và JSP thật. `DBConnection` trong app kiểm thử được thay bằng JDBC trong bộ nhớ. ESP32 là HTTP server loopback.
- Dữ liệu thử có Admin gốc U1, Viewer U2, Operator U3, Admin khác U4, ba switch hoạt động trên ESP32 active, switch inactive và ESP32 inactive.
- Dùng SELECT đọc metadata SQL Server thật, xác nhận UNIQUE `host_name`, `(device_id,gpio_pin)`, username/email/phone và `(user_id,switch_id)`. Các lần thử ghi/vi phạm ràng buộc chạy trên fixture phản ánh metadata này. Không thực hiện INSERT/UPDATE/DELETE trên SQL Server thật.
- Chưa thử relay thật. Không có bằng chứng về transaction/khóa/rollback hay lần ghi trên SQL Server thật từ các fixture này.
- Kiểm tra bổ sung: **91 ManagementControllerTest**, **32 ManagementErrorTest**, **8 HardwareClientTest** đều đạt. Chúng không thay thế sáu lỗi acceptance đã tái hiện.
- Đã mở màn hình ADMIN bằng Chrome; các URL A8 được đối chiếu với title/heading của từng JSP.
- Đã mở file kết quả thành công bằng Microsoft Excel ẩn, read-only. Đã kiểm tra A1/A10, số sheet và xuất PDF xem trước từ Excel. File có sáu cột đúng mẫu, không có kết quả sau sửa giả định.
- Tomcat kiểm thử đã dừng.

## Kết luận

| Mục | Kết luận | Kiểm tra con | Kết quả thực tế |
|---|---|---|---|
| A1 | ĐẠT | 1/1 | ADMIN xem cả ba switch hoạt động trên ESP32 active. Không hiển thị switch/ESP32 inactive theo hợp đồng DAO hiện tại. |
| A2 | ĐẠT | 1/1 | U1 thấy SW2 dù chỉ có quyền riêng trên SW1. Phạm vi xem ADMIN không bị giới hạn bởi quyền riêng. |
| A3 | ĐẠT MỘT PHẦN | 2/3 | OFF → ON → OFF cập nhật ESP32/database/lịch sử mô phỏng. Nhưng HTTP 200 với `ok:false` và relay không đổi vẫn lưu thành công, làm database lệch phần cứng. |
| A4 | ĐẠT | 1/1 | Menu User Management, trang danh sách và Add User hiển thị, HTTP 200. |
| A5 | ĐẠT | 6/6 | Tạo đủ ADMIN/OPERATOR/VIEWER, password hash P1$, active, từng tài khoản mới đăng nhập được qua LoginController thật. |
| A6 | KHÔNG ĐẠT | 7/10 | CRUD tài khoản thường, xóa mềm, thu hồi quyền và chặn tự thay đổi đều hoạt động. Nhưng U4 có thể xóa mềm/khóa/đổi role U1 khi có hai ADMIN active. Chưa có bảo vệ ADMIN gốc độc lập với người đang đăng nhập/ADMIN cuối cùng. |
| A7 | ĐẠT | 5/5 | Cấp/lưu SW2, bỏ SW1, thu hồi sau Save, lưu inactive và cấp lại dùng dòng cũ đều hoạt động. |
| A8 | ĐẠT | 10/10 | Năm Servlet User/Permissions/Device/Schedule/Account trực tiếp và năm route MainController tương ứng đều trả đúng trang HTTP 200 cho ADMIN. |
| A9 | ĐẠT MỘT PHẦN | 9/10 | Thêm/đổi tên/xóa mềm ESP32/switch, chặn GPIO trùng trong cùng ESP và hostname giống hệt hoạt động với fixture constraints. GPIO giống nhau trên hai ESP khác nhau được phép. Tuy nhiên cùng IP viết `192.0.2.10` và `http://192.0.2.10` vẫn thêm được hai thiết bị. |
| A10 | ĐẠT MỘT PHẦN | 5/6 | Tạo/sửa/bật/tắt bằng Enabled rồi Save và xóa mềm hoạt động. Không có thao tác Run now trên UI/controller. POST thử `runSchedule` trả lỗi Invalid schedule operation và không gửi lệnh. |

Theo tính năng: **6 đạt, 3 đạt một phần, 1 không đạt**. Sáu FAIL là các tình huống con, không phải sáu tính năng khác nhau.

## Cách sửa đề xuất

1. **A6:** xác định và bảo vệ ADMIN gốc bằng thuộc tính hoặc ID cấu hình tại server. Từ chối xóa/khóa/đổi role dù actor là ADMIN khác. Giữ kiểm tra self/last Admin hiện có. Không mặc định mọi database đều dùng U1 làm gốc; U1 chỉ là tài khoản được xác định là gốc trong fixture.
2. **A3:** xác minh phản hồi theo hợp đồng firmware và đọc lại trạng thái sau lệnh, chỉ ghi thành công khi thiết bị xác nhận đúng. Phản hồi lỗi `ok:false` được chủ động tạo, không khẳng định firmware thật đang trả nó.
3. **A9:** chuẩn hóa hostname/IP/URL thành địa chỉ thống nhất trước khi lưu và kiểm tra trùng. Giữ ràng buộc UNIQUE; thông báo lỗi GPIO hiện chưa nêu đúng tên GPIO. Không gửi request tới IP tài liệu `192.0.2.10` trong các ca này.
4. **A10:** thêm Run now trên UI/controller bằng POST + CSRF và kiểm tra quyền, switch/device active. Gửi lệnh, xác minh kết quả và ghi lịch sử. Thử này không chứng minh hoặc kiểm tra việc scheduler tự chạy theo giờ.

Không sửa logic ứng dụng trong lượt này. Cột “Kết quả sau khi sửa” là **chưa sửa/chưa kiểm tra lại** cho các mục còn lỗi, hoặc **không cần sửa tiêu chí đã đạt** cho các mục đạt trong phạm vi kiểm tra.

## Chạy lại

Từ thư mục dự án:

```powershell
& ([scriptblock]::Create([IO.File]::ReadAllText((Join-Path (Get-Location).Path 'tests/admin-audit/run.ps1'))))
```

Kết quả:

```text
ADMIN AUDIT: 47 passed, 6 failed
PASS: 8 HardwareClient exception checks.
AUDIT_EXIT=1
```

Exit 1 phản ánh các lỗi acceptance hiện còn tồn tại. Runner mặc định dừng đúng Tomcat riêng của nó sau kiểm tra. Bộ test chỉ dành cho localhost, không đưa fixture hoặc `DBConnection` test vào ứng dụng thật.

## Bằng chứng và file

- `tests/admin-audit/README.md`: mô hình kiểm tra và giới hạn.
- `tests/admin-audit/adminaudit/AdminHttpAudit.java`: các request và kỳ vọng thực thi.
- `build/admin-audit/audit.log`, `evidence/results.tsv`: toàn bộ 53 kết quả.
- `build/admin-audit/unique-indexes.tsv`: metadata SQL Server thật, chỉ đọc.
- `build/admin-audit/management-controller.log`, `management-errors.log`, `hardware-client.log`: kiểm tra bổ sung.
- `build/admin-audit/evidence/*.html`: phản hồi JSP và lỗi được chủ động tạo.
- `build/admin-audit/evidence/admin-user-manager.png`: ảnh Chrome màn hình quản lý người dùng.
- `build/admin-audit/native-excel-validation.log`: xác nhận mở file bằng Excel.
- `build/admin-audit/admin-native-excel.pdf`: bản xem trước do Excel xuất, chỉ dùng QA.
- `outputs/admin-test-cases-20261015/Test_case_ADMIN.xlsx`: bảng kết quả theo mẫu.
- `outputs/admin-test-cases-20261015/Test_case_ADMIN.csv`: bản nội dung dự phòng, UTF-8 BOM.
