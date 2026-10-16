package src.battleship.client;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletionException;

public final class ClientMain {
    private final JFrame frame = new JFrame("Battle Ship Online");
    private GameClient client;
    private ChallengeController challenges;
    private String username;
    private String currentMatchId;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JTextField userField = new JTextField(18);
    private final JPasswordField passField = new JPasswordField(18);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Người chơi", "Trạng thái", "Tổng điểm"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable playerTable = new JTable(model);
    private final JLabel lobbyTitle = new JLabel("Lobby");

    private ClientMain() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(700, 460);
        frame.setLocationRelativeTo(null);
        root.add(loginPanel(), "login");
        root.add(lobbyPanel(), "lobby");
        frame.setContentPane(root);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                disconnect();
                frame.dispose();
            }
        });
        frame.setVisible(true);
    }

    private JPanel loginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 10));
        form.setBorder(BorderFactory.createTitledBorder("Đăng nhập / Đăng ký"));
        form.add(new JLabel("Tên tài khoản"));
        form.add(userField);
        form.add(new JLabel("Mật khẩu"));
        form.add(passField);
        JButton login = new JButton("Đăng nhập");
        JButton register = new JButton("Đăng ký");
        form.add(login);
        form.add(register);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        panel.add(form, g);
        login.addActionListener(e -> connectAndLogin(false));
        register.addActionListener(e -> connectAndLogin(true));
        passField.addActionListener(e -> connectAndLogin(false));
        return panel;
    }

    private JPanel lobbyPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        lobbyTitle.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 0));
        p.add(lobbyTitle, BorderLayout.NORTH);
        playerTable.setFillsViewportHeight(true);
        p.add(new JScrollPane(playerTable), BorderLayout.CENTER);

        JButton challenge = new JButton("Thách đấu");
        JButton refresh = new JButton("Làm mới");
        JButton logout = new JButton("Đăng xuất");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(challenge);
        bottom.add(refresh);
        bottom.add(logout);
        p.add(bottom, BorderLayout.SOUTH);

        challenge.addActionListener(e -> sendChallenge());
        playerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) sendChallenge();
            }
        });
        refresh.addActionListener(e -> requestLobby());
        logout.addActionListener(e -> logout());
        return p;
    }

    private void connectAndLogin(boolean register) {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword());
        if (u.isBlank() || p.isBlank()) {
            warn("Nhập tên tài khoản và mật khẩu.");
            return;
        }
        setBusy(true);
        new SwingWorker<Map<String, Object>, Void>() {
            protected Map<String, Object> doInBackground() throws Exception {
                if (client == null) client = new GameClient("localhost", 5050);
                return await(client.request(register ? "REGISTER" : "LOGIN",
                        Map.of("username", u, "password", p)));
            }

            protected void done() {
                setBusy(false);
                try {
                    Map<String, Object> response = get();
                    Map<String, Object> body = payload(response);
                    boolean ok = Boolean.TRUE.equals(body.get("ok"));
                    if (register) {
                        info(String.valueOf(body.get("message")));
                        return;
                    }
                    if (ok) {
                        username = String.valueOf(body.get("username"));
                        lobbyTitle.setText("Lobby — Xin chào " + username);
                        challenges = new ChallengeController(client);
                        challenges.onInvite(ClientMain.this::handleInvite);
                        challenges.onMatch(ClientMain.this::handleMatch);
                        client.addPushListener(m -> {
                            if ("LOBBY_UPDATE".equals(m.get("type")))
                                SwingUtilities.invokeLater(() -> showUsers(payload(m)));
                            else if ("CONNECTION_LOST".equals(m.get("type")))
                                SwingUtilities.invokeLater(() -> {
                                    warn("Mất kết nối tới server.");
                                    showLogin();
                                });
                        });
                        cards.show(root, "lobby");
                        requestLobby();
                    } else warn(String.valueOf(body.get("message")));
                } catch (Exception ex) {
                    warn("Không thể kết nối hoặc đăng nhập: " + rootMessage(ex));
                }
            }
        }.execute();
    }

    private void sendChallenge() {
        int row = playerTable.getSelectedRow();
        if (row < 0) {
            warn("Chọn một người chơi trong Lobby.");
            return;
        }
        String target = String.valueOf(model.getValueAt(row, 0));
        String status = String.valueOf(model.getValueAt(row, 1));
        if ("Offline".equals(status) || target.equalsIgnoreCase(username)) {
            warn("Chỉ có thể thách đấu người chơi đang Rỗi.");
            return;
        }
        if (!"Rỗi".equals(status)) {
            warn("Đối thủ đang chơi.");
            return;
        }
        challenges.invite(target).thenAccept(m -> SwingUtilities.invokeLater(() -> {
            Map<String, Object> p = payload(m);
            if (Boolean.TRUE.equals(p.get("ok"))) info("Đã gửi lời mời đến " + target + ".");
            else warn(String.valueOf(p.getOrDefault("message", "Không thể gửi lời mời.")));
        }));
    }

    private void handleInvite(Map<String, Object> m) {
        String type = String.valueOf(m.get("type"));
        Map<String, Object> p = payload(m);
        SwingUtilities.invokeLater(() -> {
            if ("INVITE_RECEIVED".equals(type)) {
                String id = String.valueOf(p.get("challengeId"));
                String inviter = String.valueOf(p.get("inviter"));
                int choice = JOptionPane.showConfirmDialog(frame,
                        inviter + " muốn thách đấu với bạn.\nBạn có chấp nhận không?",
                        "Lời mời thi đấu", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION)
                    challenges.accept(id);
                else
                    challenges.reject(id);
            } else if ("INVITE_REJECTED".equals(type)) {
                info("Đối thủ đã từ chối lời mời.");
            } else if ("INVITE_CANCELLED".equals(type)) {
                info("Lời mời đã bị hủy.");
            }
        });
    }

    private void handleMatch(Map<String, Object> m) {
        String type = String.valueOf(m.get("type"));
        Map<String, Object> p = payload(m);
        SwingUtilities.invokeLater(() -> {
            if ("MATCH_START".equals(type)) {
                currentMatchId = String.valueOf(p.get("matchId"));
                showPreparation(String.valueOf(p.get("opponent")));
            } else if ("MATCH_PLAYING".equals(type)) {
                info("Cả hai đã Ready. Chuyển sang giao diện Battle Ship.");
            } else if ("OPPONENT_READY".equals(type)) {
                info("Đối thủ đã Ready.");
            } else if ("OPPONENT_LEFT".equals(type)) {
                currentMatchId = null;
                info("Đối thủ đã thoát hoặc mất kết nối.");
                requestLobby();
            } else if ("MATCH_END".equals(type)) {
                currentMatchId = null;
                info("Trận đấu kết thúc: " + p.get("reason"));
                requestLobby();
            }
        });
    }

    private void showPreparation(String opponent) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel("Đối thủ: " + opponent, SwingConstants.CENTER), BorderLayout.NORTH);
        JLabel status = new JLabel("Chuẩn bị trận đấu. TV3 sẽ tích hợp bàn cờ đặt tàu tại đây.",
                SwingConstants.CENTER);
        panel.add(status, BorderLayout.CENTER);
        JButton ready = new JButton("Ready");
        JButton exit = new JButton("Thoát trận");
        JPanel buttons = new JPanel();
        buttons.add(ready);
        buttons.add(exit);
        panel.add(buttons, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(frame, "Battle Ship - Chuẩn bị", true);
        dialog.setContentPane(panel);
        dialog.setSize(500, 190);
        dialog.setLocationRelativeTo(frame);

        ready.addActionListener(e -> challenges.ready(currentMatchId)
                .thenAccept(m -> SwingUtilities.invokeLater(() -> {
                    Map<String, Object> p = payload(m);
                    if (Boolean.FALSE.equals(p.get("ok")))
                        warn(String.valueOf(p.get("message")));
                    else
                        status.setText("Đã Ready. Đang chờ đối thủ...");
                })));

        exit.addActionListener(e -> {
            challenges.exitMatch();
            currentMatchId = null;
            dialog.dispose();
            requestLobby();
        });
        dialog.setVisible(true);
    }

    private void requestLobby() {
        if (client == null) return;
        client.request("GET_LOBBY", Map.of())
                .thenAccept(m -> SwingUtilities.invokeLater(() -> showUsers(payload(m))))
                .exceptionally(e -> {
                    SwingUtilities.invokeLater(() -> warn("Không lấy được danh sách người chơi."));
                    return null;
                });
    }

    private void showUsers(Map<String, Object> body) {
        Object obj = body.get("users");
        if (!(obj instanceof List)) return;
        model.setRowCount(0);
        for (Object x : (List<?>) obj) if (x instanceof Map) {
            Map<?, ?> u = (Map<?, ?>) x;
            model.addRow(new Object[]{u.get("username"), statusLabel(String.valueOf(u.get("status"))), u.get("totalScore")});
        }
    }

    private String statusLabel(String s) {
        return switch (s) {
            case "IDLE" -> "Rỗi";
            case "PLAYING" -> "Đang chơi";
            default -> "Offline";
        };
    }

    private void logout() {
        if (client == null) {
            showLogin();
            return;
        }
        client.request("LOGOUT", Map.of()).whenComplete((m, e) ->
                SwingUtilities.invokeLater(() -> {
                    username = null;
                    currentMatchId = null;
                    showLogin();
                }));
    }

    private void showLogin() {
        cards.show(root, "login");
        model.setRowCount(0);
    }

    private void disconnect() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private void setBusy(boolean b) {
        userField.setEnabled(!b);
        passField.setEnabled(!b);
    }

    private void info(String s) {
        JOptionPane.showMessageDialog(frame, s, "Battle Ship", JOptionPane.INFORMATION_MESSAGE);
    }

    private void warn(String s) {
        JOptionPane.showMessageDialog(frame, s, "Thông báo", JOptionPane.WARNING_MESSAGE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> m) {
        return m.get("payload") instanceof Map ? (Map<String, Object>) m.get("payload") : Map.of();
    }

    private static Map<String, Object> await(java.util.concurrent.CompletableFuture<Map<String, Object>> f)
            throws Exception {
        try { return f.join(); }
        catch (CompletionException e) { throw new Exception(e.getCause()); }
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return String.valueOf(t.getMessage());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientMain::new);
    }
}
