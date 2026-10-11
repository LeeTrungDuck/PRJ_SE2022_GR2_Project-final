# Chuyển UI sang JSP/Servlet, không JavaScript

Status: complete

Scope ban đầu: giữ giao diện hiện có của Login, User, Permission, Account,
Device Control, Device/Switch, Schedule và Control History. Chuyển dữ liệu minh
họa và các thao tác đang nằm trong JavaScript sang Java; không thêm kết nối
database hoặc ESP32. Cập nhật sau: Device Control đồng bộ trạng thái phần cứng
thực theo mô tả bên dưới.

1. Tạo state minh họa riêng cho từng HTTP session, controller nhận form, kiểm tra
   đầu vào/CSRF và chuyển dữ liệu hiển thị sang JSP. POST thành công redirect GET.
2. Render danh sách và trạng thái bằng JSTL. Dùng form GET cho lọc/tìm kiếm,
   POST cho thay đổi. Hộp thoại mở/đóng bằng link và response server.
3. Giữ các chức năng quản lý; thay sao chép JSON tự động bằng tải JSON/chọn và
   Ctrl+C. Giờ chạy kế được tính khi tải trang theo Asia/Ho_Chi_Minh. Trang
   Device Control đọc trạng thái thực từ ESP32 khi tải trang; người dùng tải
   lại thủ công để cập nhật trạng thái công tắc vật lý.
4. Bỏ cả 8 file JS, script include và các phụ thuộc jQuery/Bootstrap JS còn lại.
5. Kiểm tra biên dịch Java, JSP trên Tomcat, thao tác bằng HTTP session và các
   trường hợp đầu vào không hợp lệ, bảo vệ root, CSRF, escaping, cách ly session.

Acceptance: mọi trang hoạt động với JavaScript tắt; không có script, inline event
handler hoặc javascript: URL trong source giao diện; CSS và sidebar giữ bên trái.
Dữ liệu giữ qua lần tải lại trong session và có nút đặt lại. Không tự chạy lịch,
gửi lệnh ESP32, xác thực OTP thật hoặc ghi database trong bản minh họa.

Implemented:

- `DashboardController` nhận GET/POST; `ui/PreviewState`, `PreviewActions`,
  `PreviewViews`, `UiSupport` và `PreviewSeeds` thay xử lý JavaScript.
- Bảy trang quản lý dùng JSTL và form HTML. Login giữ form gửi lên server.
  Sidebar, màu chữ sáng và CSS hiện có được giữ. Không còn file `.js`, thẻ
  `<script>`, inline event handler hoặc URL `javascript:` trong `web`.
- Đổi trạng thái hoặc lưu form sẽ tải lại trang. JSON có thể tải xuống;
  lịch chạy kế tính khi tải trang. Device Control đồng bộ trạng thái switch từ
  ESP32 mỗi lần tải; người dùng chủ động tải lại trang để cập nhật. Lỗi đồng bộ
  hiển thị riêng trong thẻ switch bị ảnh hưởng; switch khác vẫn tiếp tục cập nhật.
- Mỗi session giữ dữ liệu riêng; nút “Đặt lại dữ liệu mẫu” phục hồi dữ liệu ban đầu.
  OTP `246810` chỉ minh họa; mật khẩu nhập vào không được lưu. Luồng xác thực
  hiện có không được thay đổi để bổ sung database hay kiểm tra tài khoản thật.

Validation:

- Biên dịch toàn bộ Java bằng JDK 8, Servlet API của Tomcat 9.0.113.
- Chạy trên Tomcat riêng tại `127.0.0.1:18080`, dùng bản sao trong thư mục temp.
- `tests/NoJavaScriptUiTest.java`: 229 kiểm tra HTTP/JSP, không chạy script:
  render các trang và dialog, thêm/sửa/xóa, lọc, CSRF, bảo vệ SuperAdmin,
  OTP/mật khẩu, trùng GPIO/IP, thiết bị offline, lịch tuần/một lần, cộng giờ
  qua nửa đêm, trace JSON, escaping, cách ly session và đặt lại dữ liệu.
- Không có trình duyệt được kết nối trong phiên công cụ, nên chưa kiểm tra ảnh
  chụp giao diện. CSS gốc và các breakpoint được giữ; bổ sung CSS cho form native.

Chạy lại dự án bằng **Clean and Build → Run** trong NetBeans để các class/mapping
mới được triển khai. Không mở JSP bằng `file://`; truy cập qua `MainController`.
Nếu muốn chạy kiểm tra tự động, biên dịch `tests/NoJavaScriptUiTest.java` vào thư
mục temp rồi chạy class `NoJavaScriptUiTest` với đối số URL ứng dụng, ví dụ
`http://localhost:8080/SmartSwitchSystem`. Bộ kiểm tra tạo session riêng, không
ghi database và không gửi lệnh phần cứng.
