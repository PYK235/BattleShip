package battleship.server;

import battleship.common.Protocol;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final BattleShipServer server;
    private BufferedReader in;
    private PrintWriter out;
    private volatile User user;

    ClientHandler(Socket socket, BattleShipServer server) {
        this.socket = socket;
        this.server = server;
    }
    public User getUser() { return user; }
    void setUser(User u) { user = u; }

    public void run() {
        try (Socket s = socket) {
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            String line;
            while ((line = in.readLine()) != null) {
                try { handle(Protocol.decode(line)); }
                catch (RuntimeException e) { send("ERROR", null, Map.of("message", "Thông điệp không hợp lệ.")); }
            }
        } catch (IOException ignored) {
        } finally {
            if (user != null && server.getMatches() != null)
                server.getMatches().playerLeft(user.getUsername(), "DISCONNECTED");
            server.getUsers().logout(this);
            server.broadcastLobby();
        }
    }

    @SuppressWarnings("unchecked")
    private void handle(Map<String, Object> msg) {
        String type = String.valueOf(msg.getOrDefault("type", ""));
        String id = msg.get("requestId") == null ? null : String.valueOf(msg.get("requestId"));
        Map<String, Object> p = msg.get("payload") instanceof Map ? (Map<String, Object>) msg.get("payload") : Map.of();
        switch (type) {
            case "LOGIN": {
                String err = server.getUsers().login(str(p, "username"), str(p, "password"), this);
                if (err == null) {
                    send("LOGIN_RESULT", id, Map.of("ok", true, "username", user.getUsername()));
                    server.broadcastLobby();
                } else send("LOGIN_RESULT", id, Map.of("ok", false, "message", err));
                break;
            }
            case "REGISTER": {
                boolean ok = server.getDao().create(str(p, "username"), str(p, "password"));
                send("REGISTER_RESULT", id, Map.of("ok", ok, "message",
                        ok ? "Tạo tài khoản thành công." : "Tên phải gồm 3–20 ký tự chữ/số/_ và mật khẩu 6–64 ký tự; tên có thể đã tồn tại."));
                break;
            }
            case "GET_LOBBY":
                if (user == null) send("ERROR", id, Map.of("message", "Vui lòng đăng nhập."));
                else send("LOBBY_RESULT", id, Map.of("users", server.getUsers().lobby()));
                break;
            case "LOGOUT":
                if (server.getMatches() != null && user != null)
                    server.getMatches().playerLeft(user.getUsername(), "LOGOUT");
                server.getUsers().logout(this);
                send("LOGOUT_RESULT", id, Map.of("ok", true));
                server.broadcastLobby();
                break;
            default:
                if (user == null) send("ERROR", id, Map.of("code", "AUTH_REQUIRED", "message", "Vui lòng đăng nhập."));
                else if (!server.dispatch(type, this, id, p))
                    send("ERROR", id, Map.of("code", "UNKNOWN_MESSAGE", "message", "Loại thông điệp không được hỗ trợ: " + type));
        }
    }

    private static String str(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v == null ? "" : String.valueOf(v);
    }
    public synchronized void send(String type, String id, Map<String, Object> payload) {
        if (out != null) out.println(Protocol.encode(Protocol.message(type, id, payload)));
    }
}
