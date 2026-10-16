# Battle Ship Online — Thành viên 1

Bộ khung Java 17 cho networking, đăng nhập và lobby. Hợp đồng tích hợp chung cho cả nhóm nằm trong [INTEGRATION.md](INTEGRATION.md).

## Chạy

Mở terminal tại thư mục `battleship-member1`:

```powershell
mvn compile
mvn exec:java '-Dexec.mainClass=battleship.server.ServerMain'
```

Mở terminal khác để chạy một hoặc nhiều client:

```powershell
mvn exec:java '-Dexec.mainClass=battleship.client.ClientMain'
```

Server mặc định lắng nghe cổng `5050`. Tài khoản demo: `alice/123456`, `bob/123456`, `charlie/123456`. Có thể đăng ký tài khoản mới từ màn hình đăng nhập; dữ liệu chỉ tồn tại đến khi server dừng.

## Thiết kế

- `ServerSocket` chấp nhận nhiều kết nối; mỗi kết nối được phục vụ bởi một `ClientHandler`.
- Giao thức một JSON object trên mỗi dòng UTF-8 (`type`, `requestId`, `payload`). Server gửi phản hồi cùng `requestId`; các thông báo chủ động như `LOBBY_UPDATE` không có request id.
- `UserManager` giữ trạng thái `OFFLINE`, `IDLE`, `PLAYING`, điểm và số trận trong bộ nhớ. Login chuyển sang `IDLE`; logout/mất kết nối chuyển về `OFFLINE`.
- `UserDAO` là interface lưu tài khoản; `InMemoryUserDAO` là triển khai demo và điểm nối để thay bằng JDBC khi nhóm tích hợp database.
- Client Swing gồm Login/Register và Lobby, hiển thị người chơi online, trạng thái và điểm. Danh sách được cập nhật theo sự kiện server.

## Giao thức

Client gửi `LOGIN {username,password}`, `REGISTER {username,password}`, `LOGOUT {}` hoặc `GET_LOBBY {}`. Server trả `LOGIN_RESULT`, `REGISTER_RESULT`, `LOBBY_RESULT`, `ERROR`; cập nhật lobby được phát dưới dạng `LOBBY_UPDATE` với `payload.users`.

Mật khẩu trong bản demo được lưu dưới dạng SHA-256 trong bộ nhớ; đây không phải thiết kế production (nên dùng BCrypt/Argon2 và DAO JDBC). Các handler đồng bộ phát thông báo trạng thái lên mọi client đang đăng nhập.
