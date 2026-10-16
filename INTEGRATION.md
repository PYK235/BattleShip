# Hợp đồng tích hợp Battle Ship Online

Tài liệu này là điểm thống nhất cho cả nhóm. Mọi thay đổi message, enum, payload hoặc quyền sở hữu dữ liệu phải được ghi tại đây trước khi merge.

## 1. Nền tảng và cách chạy

- Java 17, Maven, Swing, TCP Socket; một dòng UTF-8 tương ứng một JSON object (JSON Lines).
- Từ thư mục dự án: `mvn compile`; server: `mvn exec:java -Dexec.mainClass=battleship.server.ServerMain`; client: `mvn exec:java -Dexec.mainClass=battleship.client.ClientMain`.
- Server mặc định cổng `5050`; có thể truyền cổng bằng đối số server.
- Package hiện tại: `battleship.common`, `battleship.server`, `battleship.client`. Thành viên khác thêm package `battleship.match`, `battleship.game`, `battleship.result` tương ứng, không đặt lớp cùng tên vào package người khác.

## 2. Envelope bắt buộc

Request và response:

```json
{"type":"LOGIN","requestId":"uuid","payload":{"username":"alice","password":"123456"}}
```

```json
{"type":"LOGIN_RESULT","requestId":"uuid","payload":{"ok":true,"username":"alice"}}
```

Server push không cần `requestId`. Payload lỗi chuẩn: `{"code":"NOT_READY","message":"..."}`; response thông thường giữ `requestId` và có `ok` nếu thao tác có thể thất bại. `Protocol` đã encode/decode JSON cơ bản; không nối chuỗi JSON thủ công, hãy dùng `Protocol.message(...)` và `Protocol.encode(...)`.

## 3. Danh mục message

| Nhóm | Request Client → Server | Response Server → Client | Push Server → Client |
|---|---|---|---|
| Tài khoản/lobby | `REGISTER`, `LOGIN`, `LOGOUT`, `GET_LOBBY` | `REGISTER_RESULT`, `LOGIN_RESULT`, `LOGOUT_RESULT`, `LOBBY_RESULT` | `LOBBY_UPDATE` |
| Thách đấu | `INVITE`, `ACCEPT_INVITE`, `REJECT_INVITE`, `READY` | `INVITE_RESULT`, `ACCEPT_RESULT`, `REJECT_RESULT`, `READY_RESULT` | `INVITE_RECEIVED`, `MATCH_CREATED`, `MATCH_START`, `OPPONENT_LEFT` |
| Bàn cờ/trận | `PLACE_SHIPS`, `ATTACK`, `PLAY_AGAIN`, `EXIT_MATCH` | `PLACE_SHIPS_RESULT`, `ATTACK_RESULT`, `PLAY_AGAIN_RESULT`, `EXIT_MATCH_RESULT` | `TURN_CHANGED`, `ATTACK_RESOLVED`, `MATCH_RESULT`, `OPPONENT_DISCONNECTED`, `REMATCH_REQUEST` |
| Kết quả | `GET_RANKING`, `GET_HISTORY` | `RANKING_RESULT`, `HISTORY_RESULT` | — |

Mỗi request chỉ được trả lời một lần. Kết quả nhiều người nhận (ví dụ `ATTACK_RESOLVED`) là push tới hai người chơi; không dùng chung requestId ở các push.

## 4. Payloads đã chốt

- Lobby item: `{"username":"alice","status":"IDLE","totalScore":0}`. Status chỉ nhận `OFFLINE`, `IDLE`, `PLAYING`; lobby chỉ liệt kê tài khoản online.
- `INVITE`: `{"target":"bob"}`; `INVITE_RECEIVED`: `{"matchId":"...","from":"alice"}`.
- `ACCEPT_INVITE` / `REJECT_INVITE` / `READY` / `PLAY_AGAIN` / `EXIT_MATCH`: `{"matchId":"..."}`.
- `MATCH_CREATED` và mọi event trận: luôn có `matchId`; không chấp nhận thao tác trận nếu user không thuộc trận đó.
- Tọa độ ô: zero-based `{ "row":0, "col":0 }`; bàn 6×6 nên miền hợp lệ là 0–5. `PLACE_SHIPS` dùng `{"matchId":"...","ships":[{"size":2,"row":0,"col":0,"orientation":"H"}]}`; orientation `H` hoặc `V`.
- `ATTACK`: `{"matchId":"...","row":2,"col":4}`. `ATTACK_RESOLVED`: `{"matchId":"...","by":"alice","row":2,"col":4,"result":"MISS|HIT|SUNK","nextTurn":"bob"}`. Không gửi vị trí tàu của đối thủ cho client.
- `MATCH_RESULT`: `{"matchId":"...","winner":"alice","loser":"bob","reason":"ALL_SHIPS_SUNK|DISCONNECTED|TIMEOUT|EXIT"}`.
- `GET_RANKING`: `{"limit":50}`; ranking item gồm `rank`, `username`, `totalScore`, `wins`, `losses`, `matches`, sắp `totalScore DESC`, rồi `wins DESC`.
- `GET_HISTORY`: `{"page":1,"pageSize":20}`; item gồm `matchId`, `opponent`, `result`, `scoreChange`, `endedAt` (ISO-8601 UTC).
- Điểm số, lượt đi, thời gian lượt và trạng thái trận do server quyết định; Client chỉ gửi ý định, không gửi kết quả mình tự tính.

## 5. Quyền sở hữu và điểm nối

- Thành viên 1: socket, session đăng nhập, `UserManager`, lobby, `UserDAO`; chỉ `UserManager` thay đổi trạng thái online/offline. Trạng thái `PLAYING` cần được cập nhật qua phương thức dịch vụ công khai do nhóm bổ sung, không sửa trực tiếp field User.
- Thành viên 2: `MatchManager` sở hữu invite/match lifecycle và membership. Chỉ tạo match sau khi kiểm tra cả hai user đang `IDLE`; phát event qua API gửi message của server.
- Thành viên 3: `GameManager` sở hữu board, tàu, lượt, timeout và attack resolution. `MatchManager` gọi GameManager khi match bắt đầu; mỗi action phải kiểm tra người chơi, lượt, tọa độ và deadline ở server.
- Thành viên 4: kết quả/ranking/history và transaction cập nhật kết quả một lần khi match kết thúc. Không tự cộng điểm ở client.
- `ClientHandler` xử lý account/lobby và chuyển message chưa biết qua `BattleShipServer.registerRoute(type, handler)`. Mỗi module đăng ký các route của mình trước `server.start(port)`; handler nhận `(ClientHandler, requestId, payload)`, rồi phản hồi bằng `client.send(responseType, requestId, payload)`. Gửi push riêng bằng `server.broadcast(username, eventType, payload)`; đổi trạng thái qua `server.getUsers().setStatus(username, User.Status.PLAYING|IDLE)`. Không thêm toàn bộ logic game vào `ClientHandler`.
- Các DAO dùng chung cùng một database và cùng khóa `username`, `matchId`; không tạo database riêng theo thành viên. Nếu đổi schema, cập nhật tài liệu migration/schema tại đây.

## 6. Quy tắc xử lý sự cố

- Client ngắt kết nối: đóng session, chuyển user sang `OFFLINE`, thông báo lobby; nếu user thuộc trận thì MatchManager/GameManager quyết định kết quả và push sự kiện đối thủ rời trận.
- Timeout lượt 15 giây được tính bằng server, không tin đồng hồ client.
- Logout khi đang trong match phải được router chuyển thành `EXIT_MATCH` trước khi session đóng.
- Request chưa đăng nhập hoặc không có quyền trên match trả lỗi chuẩn, không làm thay đổi state.
- Không để exception làm dừng accept loop; log lỗi phía server, trả lỗi tổng quát phía client.

## 7. Cách mỗi thành viên bàn giao

Mỗi người tạo nhánh riêng, chỉ commit file thuộc package/phần mình và gửi: các file mới, message/payload đã dùng, API cần phần khác gọi, cách chạy thủ công và các trường hợp lỗi đã xử lý. Trước khi merge, rà xem có đổi giao thức/schema không; nếu có phải sửa tài liệu và báo cả nhóm. Tích hợp theo thứ tự: protocol/router → match/invite → game → result/history/ranking → UI liên kết.
