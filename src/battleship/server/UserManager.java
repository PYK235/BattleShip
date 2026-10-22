package battleship.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class UserManager {
    private final UserDAO dao;
    private final Map<String, ClientHandler> sessions = new ConcurrentHashMap<>();

    public UserManager(UserDAO dao) { this.dao = dao; }

    public synchronized String login(String username, String password, ClientHandler handler) {
        User u = dao.find(username);
        if (!dao.authenticate(username, password) || u == null)
            return "Tên đăng nhập hoặc mật khẩu không đúng.";
        String key = key(u.getUsername());
        if (sessions.containsKey(key)) return "Tài khoản đang được đăng nhập ở nơi khác.";
        sessions.put(key, handler);
        u.setStatus(User.Status.IDLE);
        handler.setUser(u);
        return null;
    }

    public synchronized void logout(ClientHandler handler) {
        User u = handler.getUser();
        if (u == null) return;
        sessions.remove(key(u.getUsername()), handler);
        u.setStatus(User.Status.OFFLINE);
        handler.setUser(null);
    }

    public synchronized boolean setStatus(String username, User.Status status) {
        ClientHandler session = session(username);
        if (session == null || session.getUser() == null) return false;
        session.getUser().setStatus(status);
        return true;
    }

    public synchronized User currentUser(String username) {
        ClientHandler session = session(username);
        return session != null ? session.getUser() : dao.find(username);
    }

    public synchronized ClientHandler session(String username) {
        return sessions.get(key(username));
    }

    public synchronized List<Map<String, Object>> lobby() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User stored : dao.all()) {
            ClientHandler session = sessions.get(key(stored.getUsername()));
            User live = session == null || session.getUser() == null ? stored : session.getUser();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", stored.getUsername());
            item.put("status", session == null || session.getUser() == null
                    ? User.Status.OFFLINE.name() : live.getStatus().name());
            item.put("totalScore", live.getTotalScore());
            item.put("wins", live.getWins());
            item.put("losses", live.getLosses());
            item.put("matches", live.getMatches());
            out.add(item);
        }
        out.sort(Comparator.comparing(x -> x.get("username").toString(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public synchronized void refreshStats(String username) {
        ClientHandler session = session(username);
        if (session == null || session.getUser() == null) return;
        session.getUser().refreshStats(dao.find(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    public synchronized List<ClientHandler> clients() { return new ArrayList<>(sessions.values()); }
}
