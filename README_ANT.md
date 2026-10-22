# BattleShip Online — Java Ant + MySQL (single project)

Một bộ mã nguồn duy nhất chứa Client, Server, Common và logic game được tích hợp vào server chung.

## Yêu cầu
- JDK 17 trở lên
- Apache Ant (NetBeans có thể chạy build.xml)
- MySQL Server 8.0+
- MySQL Connector/J 9.4.0 (JAR)

## 1. Thêm thư viện MySQL
Tải MySQL Connector/J tại https://dev.mysql.com/downloads/connector/j/ rồi giải nén.
Chép file `mysql-connector-j-9.4.0.jar` vào thư mục `lib/` của project.
Trong NetBeans, nếu Libraries chưa nhận JAR: nhấp phải project > Properties > Libraries > Add JAR/Folder > chọn JAR trong `lib/`.

## 2. Mở project trong NetBeans
Thử File > Open Project và chọn thư mục `BattleShip_Ant`.
Nếu NetBeans không nhận thư mục là project Java Ant, tạo project bằng File > New Project > Java with Ant > Java Project with Existing Sources; chọn `src` làm Source Package Folder, sau đó thêm JAR trong `lib/` ở Project Properties > Libraries. Có thể bỏ qua cấu hình `nbproject` nếu tạo project mới theo cách này.

## 3. Tạo database
Mở MySQL Workbench, kết nối MySQL và chạy `database/01_create_database_mysql.sql`.

## 4. Cấu hình kết nối MySQL (máy chạy Server)
Code đọc các biến môi trường:
- `BATTLESHIP_DB_URL` (mặc định `jdbc:mysql://localhost:3306/battleship?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh`)
- `BATTLESHIP_DB_USER` (mặc định `root`)
- `BATTLESHIP_DB_PASSWORD` (mặc định rỗng)

PowerShell ví dụ:
```powershell
$env:BATTLESHIP_DB_URL="jdbc:mysql://localhost:3306/battleship?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"
$env:BATTLESHIP_DB_USER="root"
$env:BATTLESHIP_DB_PASSWORD="MAT_KHAU_MYSQL"
```
Thay mật khẩu bằng mật khẩu thật. Đặt biến trong terminal đang dùng để chạy server.

## 5. Build và chạy
Trong terminal tại thư mục có `build.xml`:
```powershell
ant clean jar
ant run-server
```
Mở terminal khác:
```powershell
ant run-client
```
Hoặc chạy `run-server.bat` / `run-client.bat` trên Windows sau khi đã build. Trong NetBeans, có thể nhấp phải `ServerMain.java` hoặc `ClientMain.java` > Run File sau khi đã thêm Connector/J vào Libraries.

## 6. Client ở máy khác
Trên máy client đặt IP của máy chạy Java Server (không phải IP MySQL):
```powershell
$env:BATTLESHIP_SERVER_HOST="192.168.1.10"
$env:BATTLESHIP_SERVER_PORT="5051"
```
Thay IP ví dụ bằng IPv4 thật. Cho phép TCP 5050 qua Windows Firewall trên máy server. Client không kết nối trực tiếp MySQL; chỉ server Java kết nối database.

## Ghi chú
- Logic bàn cờ và lượt chơi chạy trong bộ nhớ server; database lưu tài khoản, trận và thống kê theo script SQL.
- MySQL Connector/J không được nhúng trong ZIP; tải và đặt vào `lib/` như hướng dẫn.
- Đã kiểm tra biên dịch Java nguồn với JDK 17. Chưa kiểm thử kết nối MySQL thật vì cần thông tin server/database của môi trường chạy.

code MySql

-- ==========================================
-- BATTLESHIP ONLINE - MYSQL 8.0
-- Schema khớp với MySqlUserDAO.java,
-- MySqlMatchDAO.java trong project
-- LƯU Ý: XÓA TOÀN BỘ DATABASE CŨ
-- ==========================================

DROP DATABASE IF EXISTS battleship;

CREATE DATABASE battleship
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE battleship;

-- ==========================================
-- 1. USERS: TÀI KHOẢN NGƯỜI CHƠI
-- ==========================================
CREATE TABLE users (
    username VARCHAR(20) NOT NULL,
    password_hash CHAR(64) NOT NULL,
    total_score INT NOT NULL DEFAULT 0,
    wins INT UNSIGNED NOT NULL DEFAULT 0,
    losses INT UNSIGNED NOT NULL DEFAULT 0,
    matches_played INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (username)
) ENGINE=InnoDB;

-- ==========================================
-- 2. MATCHES: THÔNG TIN TRẬN ĐẤU
-- ==========================================
CREATE TABLE matches (
    match_id VARCHAR(36) NOT NULL,
    player1 VARCHAR(20) NOT NULL,
    player2 VARCHAR(20) NOT NULL,

    status ENUM(
        'WAITING',
        'PREPARATION',
        'PLAYING',
        'FINISHED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'WAITING',

    winner_username VARCHAR(20) NULL,
    end_reason VARCHAR(40) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (match_id),

    INDEX idx_matches_status_created (status, created_at),
    INDEX idx_matches_winner (winner_username),

    CONSTRAINT fk_matches_player1
        FOREIGN KEY (player1)
        REFERENCES users(username),

    CONSTRAINT fk_matches_player2
        FOREIGN KEY (player2)
        REFERENCES users(username),

    CONSTRAINT fk_matches_winner
        FOREIGN KEY (winner_username)
        REFERENCES users(username)
) ENGINE=InnoDB;

-- ==========================================
-- 3. MATCH_PLAYERS: NGƯỜI CHƠI TRONG TRẬN
-- ==========================================
CREATE TABLE match_players (
    match_id VARCHAR(36) NOT NULL,
    username VARCHAR(20) NOT NULL,
    player_no TINYINT UNSIGNED NOT NULL,

    result ENUM(
        'WIN',
        'LOSS',
        'DRAW',
        'ABANDONED'
    ) NULL,

    score_delta INT NOT NULL DEFAULT 0,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (match_id, username),

    UNIQUE KEY uq_match_player_no (match_id, player_no),
    INDEX idx_match_players_user (username, joined_at),

    CONSTRAINT fk_match_players_match
        FOREIGN KEY (match_id)
        REFERENCES matches(match_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_match_players_user
        FOREIGN KEY (username)
        REFERENCES users(username),

    CONSTRAINT chk_match_player_no
        CHECK (player_no IN (1, 2))
) ENGINE=InnoDB;

-- ==========================================
-- 4. VIEW XẾP HẠNG
-- ==========================================
CREATE VIEW v_ranking AS
SELECT
    username,
    total_score,
    wins,
    losses,
    matches_played,
    CASE
        WHEN matches_played = 0 THEN 0
        ELSE ROUND(wins * 100.0 / matches_played, 2)
    END AS win_rate_percent
FROM users;

-- ==========================================
-- 5. KIỂM TRA CẤU TRÚC
-- ==========================================
SHOW TABLES;

DESCRIBE users;
DESCRIBE matches;
DESCRIBE match_players;

SELECT * FROM v_ranking;


## Giao diện chủ đề World War II

Các ảnh nền giao diện nằm trong `src/battleship/client/assets/`:
- `battle-bg.png`: màn hình chiến đấu và xếp tàu.
- `lobby-bg.png`: sảnh chờ.
- `login-bg.png`: đăng nhập/đăng ký.

Màu giao diện dùng xanh thép/navy, viền đồng và nút đỏ chiến thuật. Ant sẽ sao chép tài nguyên vào `build/classes` và đóng gói vào JAR khi chạy `ant clean jar`.

`.env.example` là mẫu cấu hình; Java không tự nạp file `.env`. Hãy đặt các biến môi trường tương ứng trên máy của bạn hoặc cấu hình theo hướng dẫn trong README.

## Điều hướng giao diện WWII

Các màn hình Lobby, Trang cá nhân, Bảng xếp hạng, Xếp tàu và Trận đấu dùng chung một `JFrame` với `CardLayout`; chúng chuyển trang trong cửa sổ chính thay vì mở `JDialog`. Từ Lobby, dùng nút **Trang cá nhân** để xem hồ sơ của mình, nút **Bảng xếp hạng** để xem thứ hạng; nhấp đúp vào người chơi hoặc một dòng trong bảng xếp hạng để mở hồ sơ tương ứng. Dùng **Quay lại Lobby** để trở về.

## Lobby: danh sách và trạng thái người chơi

- Lobby hiển thị toàn bộ tài khoản đã đăng ký trong MySQL, kể cả người đang Offline.
- Nhấp đúp vào tên bất kỳ để xem trang cá nhân và lịch sử đấu.
- Chỉ người có trạng thái `Online` (đã đăng nhập và chưa trong trận) mới có thể được thách đấu. Server cũng kiểm tra lại trạng thái trước khi tạo lời mời/chấp nhận lời mời.
- Màu trạng thái: Offline xám, Online xanh lá, Trong trận đỏ; các màu được chọn để nổi bật trên nền xanh navy của giao diện WWII.
