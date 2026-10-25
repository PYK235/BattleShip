package battleship.client;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import battleship.part3.server.Board;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.net.URL;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;

public final class ClientMain {
    private static final Color NAVY = new Color(8, 22, 34);
    private static final Color PANEL = new Color(9, 25, 39);
    private static final Color BRASS = new Color(202, 164, 91);
    private static final Color CREAM = new Color(239, 226, 197);
    private static final Color STEEL = new Color(44, 70, 86);
    private static final Color WAR_RED = new Color(145, 43, 35);
    private final JFrame frame = new JFrame("Battle Ship Online");
    private GameClient client;
    private ChallengeController challenges;
    private String username;
    private String currentMatchId;
    private JPanel gamePanel;
    private JPanel preparationPanel;
    private JPanel profilePanel;
    private JPanel rankingPanel;
    private String currentPage = "login";
    private JLabel preparationStatus;
    private JButton[][] ownCells;
    private JButton[][] enemyCells;
    private JLabel gameStatus;
    private JLabel turnCountdown;
    private javax.swing.Timer countdownTimer;
    private int secondsLeft;
    private boolean myTurn;
    private boolean gameEnded;
    private final boolean[][] enemyShots = new boolean[6][6];
    private final Map<Integer, BufferedImage> shipImages = new HashMap<>();

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
        installTheme();
        loadShipImages();
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(1100, 720);
        frame.setMinimumSize(new Dimension(900, 620));
        frame.setLocationRelativeTo(null);
        JPanel login = loginPanel(); login.setName("login");
        JPanel lobby = lobbyPanel(); lobby.setName("lobby");
        root.add(login, "login");
        root.add(lobby, "lobby");
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
        JPanel panel = new BackgroundPanel(new GridBagLayout(), "login-bg.png");
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 10));
        form.setOpaque(true);
        form.setBackground(new Color(7, 20, 31, 235));
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BRASS, 2),
                BorderFactory.createTitledBorder(
                        BorderFactory.createEmptyBorder(10, 12, 10, 12),
                        "ĐĂNG NHẬP / ĐĂNG KÝ", 0, 0,
                        form.getFont().deriveFont(Font.BOLD, 15f), BRASS)));
        form.add(new JLabel("Tên tài khoản"));
        styleField(userField);
        form.add(userField);
        form.add(new JLabel("Mật khẩu"));
        styleField(passField);
        form.add(passField);
        JButton login = new JButton("Đăng nhập");
        JButton register = new JButton("Đăng ký");
        styleButton(login, true);
        styleButton(register, false);
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
        JPanel p = new BackgroundPanel(new BorderLayout(10, 10), "lobby-bg.png");
        lobbyTitle.setForeground(BRASS);
        lobbyTitle.setFont(lobbyTitle.getFont().deriveFont(Font.BOLD, 20f));
        lobbyTitle.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 0));
        p.add(lobbyTitle, BorderLayout.NORTH);
        playerTable.setFillsViewportHeight(true);
        playerTable.setToolTipText("Nhấp đúp vào bất kỳ người chơi nào để xem hồ sơ; chỉ người Online mới có thể bị thách đấu.");
        styleTable(playerTable);
        JScrollPane playerScroll = new JScrollPane(playerTable);
        makeTransparent(playerScroll);
        p.add(playerScroll, BorderLayout.CENTER);

        JButton challenge = new JButton("Thách đấu");
        JButton ranking = new JButton("Bảng xếp hạng");
        JButton profile = new JButton("Trang cá nhân");
        JButton refresh = new JButton("Làm mới");
        JButton logout = new JButton("Đăng xuất");
        styleButton(challenge, true);
        styleButton(ranking, false);
        styleButton(profile, false);
        styleButton(refresh, false);
        styleButton(logout, false);
        JPanel bottom = new JPanel(new GridLayout(2, 1));
        bottom.setOpaque(false);
        JPanel social = new JPanel(new FlowLayout(FlowLayout.CENTER));
        social.setOpaque(false);
        social.add(challenge);
        social.add(ranking);
        social.add(profile);
        JPanel session = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        session.setOpaque(false);
        session.add(refresh);
        session.add(logout);
        bottom.add(social);
        bottom.add(session);
        p.add(bottom, BorderLayout.SOUTH);

        challenge.addActionListener(e -> sendChallenge());
        ranking.addActionListener(e -> showRanking());
        profile.addActionListener(e -> showProfile(username));
        playerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = playerTable.rowAtPoint(e.getPoint());
                if (row < 0) return;
                playerTable.setRowSelectionInterval(row, row);
                if (e.getClickCount() == 2) {
                    String account = String.valueOf(model.getValueAt(row, 0));
                    showProfile(account);
                }
            }
        });
        refresh.addActionListener(e -> requestLobby());
        logout.addActionListener(e -> logout());
        return p;
    }

    private static String serverHost() {
        String fromEnv = System.getenv("BATTLESHIP_SERVER_HOST");
        return fromEnv == null || fromEnv.isBlank() ? "localhost" : fromEnv.trim();
    }

    private static int serverPort() {
        String fromEnv = System.getenv("BATTLESHIP_SERVER_PORT");
        if (fromEnv == null || fromEnv.isBlank()) return 5051;
        try { return Integer.parseInt(fromEnv.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("BATTLESHIP_SERVER_PORT phải là số.", e); }
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
                if (client == null) client = new GameClient(serverHost(), serverPort());
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
                        client.addPushListener(m -> SwingUtilities.invokeLater(() -> {
                            String type = String.valueOf(m.get("type"));
                            if ("LOBBY_UPDATE".equals(type)) showUsers(payload(m));
                            else if ("CONNECTION_LOST".equals(type)) {
                                boolean wasInGame = "game".equals(currentPage);
                                closeGameDialog();
                                if (!wasInGame) warn("Mất kết nối tới server.");
                                showLogin();
                            } else handleGamePush(m);
                        }));
                        showCard("lobby");
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
        if (target.equalsIgnoreCase(username)) {
            warn("Bạn không thể tự thách đấu chính mình.");
            return;
        }
        if (!"Online".equals(status)) {
            warn("Chỉ có thể thách đấu người chơi đang Online và chưa tham gia trận đấu.");
            return;
        }
        challenges.invite(target).thenAccept(m -> SwingUtilities.invokeLater(() -> {
            Map<String, Object> p = payload(m);
            if (Boolean.TRUE.equals(p.get("ok"))) info("Đã gửi lời mời đến " + target + ".");
            else warn(String.valueOf(p.getOrDefault("message", "Không thể gửi lời mời.")));
        }));
    }

    private void showRanking() {
        client.request("GET_RANKING", Map.of()).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        warn("Không tải được bảng xếp hạng: " + rootMessage(error));
                        return;
                    }
                    if (!"RANKING_RESULT".equals(String.valueOf(response.get("type")))) {
                        warn(String.valueOf(payload(response).getOrDefault("message", "Server chưa trả được bảng xếp hạng.")));
                        return;
                    }
                    Object values = payload(response).get("users");
                    DefaultTableModel rankingModel = readOnlyModel("Hạng", "Người chơi", "Điểm", "Thắng", "Thua", "Số trận");
                    if (values instanceof List<?>) {
                        int rank = 1;
                        for (Object value : (List<?>) values) if (value instanceof Map<?, ?>) {
                            Map<?, ?> user = (Map<?, ?>) value;
                            rankingModel.addRow(new Object[]{rank++, user.get("username"), user.get("totalScore"),
                                    user.get("wins"), user.get("losses"), user.get("matches")});
                        }
                    }
                    JTable table = new JTable(rankingModel);
                    table.setFillsViewportHeight(true);
                    styleTable(table);
                    table.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                            if (e.getClickCount() == 2 && table.getSelectedRow() >= 0)
                                showProfile(String.valueOf(rankingModel.getValueAt(table.getSelectedRow(), 1)));
                        }
                    });
                    BackgroundPanel page = new BackgroundPanel(new BorderLayout(10, 10), "lobby-bg.png");
                    page.add(pageHeader("BẢNG XẾP HẠNG", "lobby"), BorderLayout.NORTH);
                    JScrollPane scroll = new JScrollPane(table);
                    makeTransparent(scroll);
                    page.add(scroll, BorderLayout.CENTER);
                    JButton refresh = new JButton("Làm mới");
                    styleButton(refresh, false);
                    refresh.addActionListener(e -> showRanking());
                    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                    footer.setOpaque(false);
                    footer.add(refresh);
                    footer.add(backButton("lobby"));
                    page.add(footer, BorderLayout.SOUTH);
                    showPage("ranking", page);
                }));
    }

    private void showProfile(String targetUsername) {
        if (targetUsername == null || targetUsername.isBlank() || client == null) return;
        client.request("GET_PROFILE", Map.of("username", targetUsername)).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        warn("Không tải được hồ sơ: " + rootMessage(error));
                        return;
                    }
                    if (!"PROFILE_RESULT".equals(String.valueOf(response.get("type")))) {
                        warn(String.valueOf(payload(response).getOrDefault("message", "Server chưa trả được hồ sơ.")));
                        return;
                    }
                    Map<String, Object> profile = payload(response);
                    if (!Boolean.TRUE.equals(profile.get("ok"))) {
                        warn(String.valueOf(profile.getOrDefault("message", "Không tải được hồ sơ.")));
                        return;
                    }
                    BackgroundPanel content = new BackgroundPanel(new BorderLayout(10, 10), "lobby-bg.png");
                    content.add(pageHeader("HỒ SƠ NGƯỜI CHƠI", "lobby"), BorderLayout.NORTH);
                    JLabel summary = new JLabel(String.format(
                            "<html><div style='padding:10px'><b style='font-size:18px'>%s</b><br><br>Điểm: %s　 |　 Thắng: %s　 |　 Thua: %s　 |　 Số trận: %s</div></html>",
                            profile.get("username"), profile.get("totalScore"), profile.get("wins"),
                            profile.get("losses"), profile.get("matches")));
                    summary.setOpaque(true);
                    summary.setBackground(new Color(7, 20, 31, 225));
                    summary.setForeground(CREAM);
                    summary.setBorder(BorderFactory.createLineBorder(BRASS, 1));
                    DefaultTableModel historyModel = readOnlyModel("Thời gian", "Đối thủ", "Kết quả", "Người thắng", "Lý do", "Điểm");
                    Object history = profile.get("history");
                    if (history instanceof List<?>) for (Object value : (List<?>) history) {
                        if (!(value instanceof Map<?, ?>)) continue;
                        Map<?, ?> match = (Map<?, ?>) value;
                        historyModel.addRow(new Object[]{match.get("playedAt"), match.get("opponent"),
                                resultLabel(String.valueOf(match.get("result"))), emptyDash(match.get("winner")),
                                formatEndReason(match.get("reason")), match.get("scoreDelta")});
                    }
                    JTable table = new JTable(historyModel);
                    table.setFillsViewportHeight(true);
                    styleTable(table);
                    JScrollPane historyScroll = new JScrollPane(table);
                    makeTransparent(historyScroll);
                    JPanel center = new JPanel(new BorderLayout(8, 8));
                    center.setOpaque(false);
                    center.add(summary, BorderLayout.NORTH);
                    center.add(historyScroll, BorderLayout.CENTER);
                    content.add(center, BorderLayout.CENTER);
                    JButton back = backButton("lobby");
                    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                    footer.setOpaque(false);
                    footer.add(back);
                    content.add(footer, BorderLayout.SOUTH);
                    table.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                            if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                                String opponent = String.valueOf(historyModel.getValueAt(table.getSelectedRow(), 1));
                                showProfile(opponent);
                            }
                        }
                    });
                    showPage("profile", content);
                }));
    }

    private JPanel pageHeader(String title, String backCard) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setForeground(BRASS);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 24f));
        label.setBorder(BorderFactory.createEmptyBorder(8, 8, 14, 8));
        header.add(label, BorderLayout.CENTER);
        return header;
    }

    private JButton backButton(String card) {
        JButton back = new JButton("← Quay lại Lobby");
        styleButton(back, false);
        back.addActionListener(e -> showCard(card));
        return back;
    }

    private void showPage(String name, JPanel panel) {
        panel.setName(name);
        Component old = findCard(name);
        if (old != null) root.remove(old);
        root.add(panel, name);
        root.revalidate();
        root.repaint();
        showCard(name);
    }

    private Component findCard(String name) {
        for (Component component : root.getComponents()) {
            if (component instanceof JComponent jc && name.equals(jc.getName())) return component;
        }
        return null;
    }

    private void showCard(String name) {
        currentPage = name;
        cards.show(root, name);
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static Image loadBackgroundImage(String imageName) {
        URL resource = ClientMain.class.getResource("/battleship/client/assets/" + imageName);
        return resource == null ? null : new ImageIcon(resource).getImage();
    }

    private static void installTheme() {
        UIManager.put("Panel.background", NAVY);
        UIManager.put("Label.foreground", CREAM);
        UIManager.put("Button.background", STEEL);
        UIManager.put("Button.foreground", CREAM);
        UIManager.put("Button.focus", BRASS);
        UIManager.put("TextField.background", new Color(14, 29, 41));
        UIManager.put("TextField.foreground", CREAM);
        UIManager.put("TextField.caretForeground", CREAM);
        UIManager.put("PasswordField.background", new Color(14, 29, 41));
        UIManager.put("PasswordField.foreground", CREAM);
        UIManager.put("PasswordField.caretForeground", CREAM);
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.foreground", CREAM);
        UIManager.put("Table.selectionBackground", new Color(104, 77, 39));
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("Table.gridColor", new Color(119, 139, 147));
        UIManager.put("OptionPane.background", NAVY);
        UIManager.put("OptionPane.messageForeground", CREAM);
        UIManager.put("TitledBorder.titleColor", BRASS);
    }

    private static void styleButton(JButton button, boolean primary) {
        button.setForeground(CREAM);
        button.setBackground(primary ? WAR_RED : STEEL);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? BRASS : new Color(103, 124, 133), 1),
                BorderFactory.createEmptyBorder(7, 13, 7, 13)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void styleField(JTextField field) {
        field.setBackground(new Color(9, 24, 36));
        field.setForeground(CREAM);
        field.setCaretColor(CREAM);
        field.setSelectionColor(new Color(104, 77, 39));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(135, 111, 71)),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)));
    }

    private static final class BackgroundPanel extends JPanel {
        private final Image backgroundImage;

        BackgroundPanel(LayoutManager layout, String imageName) {
            super(layout);
            this.backgroundImage = loadBackgroundImage(imageName);
            setOpaque(true);
            setBackground(NAVY);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                if (backgroundImage != null) {
                    int imageWidth = backgroundImage.getWidth(this);
                    int imageHeight = backgroundImage.getHeight(this);
                    if (imageWidth > 0 && imageHeight > 0) {
                        double scale = Math.max((double) getWidth() / imageWidth, (double) getHeight() / imageHeight);
                        int width = (int) Math.ceil(imageWidth * scale);
                        int height = (int) Math.ceil(imageHeight * scale);
                        g.drawImage(backgroundImage, (getWidth() - width) / 2, (getHeight() - height) / 2,
                                width, height, this);
                    }
                }
                g.setColor(new Color(3, 12, 21, 112));
                g.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g.dispose();
            }
        }
    }

    private static void styleBoardCell(JButton cell, boolean interactive) {
        cell.setForeground(CREAM);
        cell.setBackground(new Color(12, 42, 57));
        cell.setOpaque(true);
        cell.setFocusPainted(false);
        cell.setBorder(BorderFactory.createLineBorder(new Color(115, 139, 144), 1));
        cell.setFont(cell.getFont().deriveFont(Font.BOLD, 13f));
        if (interactive) cell.setToolTipText("Chọn ô để bắn");
    }

    private static void makeTransparent(JScrollPane scroll) {
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().setBackground(new Color(7, 21, 32, 215));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(202, 164, 91, 150)));
    }

    private static void styleTable(JTable table) {
        table.setOpaque(false);
        table.setForeground(Color.WHITE);
        table.setGridColor(new Color(125, 185, 210, 85));
        table.setSelectionBackground(new Color(104, 77, 39, 220));
        table.setSelectionForeground(Color.WHITE);
        table.setRowHeight(25);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value,
                    boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                setOpaque(true);
                String text = value == null ? "" : value.toString();
                Color statusColor = switch (text) {
                    case "Offline" -> new Color(166, 174, 181);
                    case "Online" -> new Color(105, 210, 133);
                    case "Trong trận" -> new Color(255, 105, 91);
                    default -> CREAM;
                };
                setForeground(column == 1 ? statusColor : CREAM);
                setBackground(selected ? new Color(104, 77, 39, 225) : new Color(7, 21, 32, 230));
                setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                setFont(source.getFont().deriveFont(column == 1 ? Font.BOLD : Font.PLAIN));
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(Number.class, renderer);
        if (table.getTableHeader() != null) {
            table.getTableHeader().setOpaque(true);
            table.getTableHeader().setForeground(Color.WHITE);
            table.getTableHeader().setBackground(new Color(35, 45, 48));
            table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
            table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BRASS));
        }
    }

    private static String resultLabel(String result) {
        return switch (result) {
            case "WIN" -> "Thắng";
            case "LOSS" -> "Thua";
            case "DRAW" -> "Hòa";
            case "ABANDONED" -> "Kết thúc sớm";
            default -> "Đang cập nhật";
        };
    }

    private static String emptyDash(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text.isBlank() ? "—" : text;
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
                // The game window is opened by GAME_STARTED; no popup is needed here.
            } else if ("OPPONENT_READY".equals(type)) {
                if (preparationStatus != null && "preparation".equals(currentPage))
                    preparationStatus.setText("Đối thủ đã sẵn sàng.");
            } else if ("OPPONENT_LEFT".equals(type)) {
                // MATCH_END follows with the final result and a single result popup.
            } else if ("MATCH_END".equals(type)) {
                closeGameDialog();
                preparationPanel = null;
                preparationStatus = null;
                currentMatchId = null;
                String result = String.valueOf(p.getOrDefault("result", ""));
                String winner = String.valueOf(p.getOrDefault("winner", ""));
                String outcome = "WIN".equalsIgnoreCase(result) ? "Bạn đã thắng!"
                        : "LOSS".equalsIgnoreCase(result) ? "Bạn đã thua."
                        : "Trận đấu kết thúc hòa.";
                String winnerText = winner.isBlank() ? "Không có người thắng."
                        : "Người thắng: " + winner + ".";
                showCard("lobby");
                info(outcome + "\n" + winnerText + "\nLý do: " + formatEndReason(p.get("reason")));
                requestLobby();
            }
        });
    }

    private void showPreparation(String opponent) {
        final Board[] placementBoard = {new Board()};
        final List<String> setup = new ArrayList<>();
        final boolean[] horizontal = {true};
        final JButton[][] cells = new JButton[Board.SIZE][Board.SIZE];

        JPanel panel = new BackgroundPanel(new BorderLayout(8, 8), "battle-bg.png");
        JLabel heading = new JLabel("Đối thủ: " + opponent, SwingConstants.CENTER);
        heading.setForeground(CREAM);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(heading, BorderLayout.NORTH);

        JLabel status = new JLabel("Chọn ô bắt đầu để đặt tàu dài 2 ô.", SwingConstants.CENTER);
        status.setForeground(CREAM);
        preparationStatus = status;
        JButton ready = new JButton("SẴN SÀNG");
        styleButton(ready, true);
        ready.setEnabled(false);
        JPanel boardPanel = new JPanel(new GridLayout(Board.SIZE, Board.SIZE, 3, 3));
        boardPanel.setOpaque(false);
        boardPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                final int row = r, col = c;
                JButton cell = new JButton("·");
                styleBoardCell(cell, false);
                cell.setPreferredSize(new Dimension(48, 42));
                cell.setFont(cell.getFont().deriveFont(Font.BOLD, 18f));
                cells[r][c] = cell;
                cell.addActionListener(e -> {
                    try {
                        int length = placementBoard[0].nextLength();
                        placementBoard[0].place(row, col, horizontal[0]);
                        setup.add(row + "," + col + "," + (horizontal[0] ? "H" : "V"));
                        refreshPlacementCells(cells, placementBoard[0]);
                        status.setText(placementBoard[0].ready()
                                ? "Đã đặt đủ 4 tàu. Nhấn Ready để bắt đầu."
                                : "Đã đặt tàu dài " + length + ". Tiếp theo đặt tàu dài "
                                    + placementBoard[0].nextLength() + " ô.");
                        ready.setEnabled(placementBoard[0].ready());
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        warn(ex.getMessage());
                    }
                });
                boardPanel.add(cell);
            }
        }

        JButton rotate = new JButton("Hướng: Ngang");
        styleButton(rotate, false);
        rotate.addActionListener(e -> {
            horizontal[0] = !horizontal[0];
            rotate.setText("Hướng: " + (horizontal[0] ? "Ngang" : "Dọc"));
        });
        JButton reset = new JButton("Xếp lại từ đầu");
        styleButton(reset, false);
        reset.addActionListener(e -> {
            placementBoard[0] = new Board();
            setup.clear();
            refreshPlacementCells(cells, placementBoard[0]);
            status.setText("Chọn ô bắt đầu để đặt tàu dài 2 ô.");
            ready.setEnabled(false);
        });

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(new JLabel("Chọn ô đầu tàu; đặt lần lượt tàu dài 2, 3, 4, 5 ô."), BorderLayout.CENTER);
        top.add(rotate, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        panel.add(boardPanel, BorderLayout.CENTER);

        JButton exit = new JButton("Thoát trận");
        styleButton(exit, true);
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(reset);
        buttons.add(ready);
        buttons.add(exit);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);

        preparationPanel = panel;
        showPage("preparation", panel);
        refreshPlacementCells(cells, placementBoard[0]);

        ready.addActionListener(e -> {
            if (!placementBoard[0].ready() || setup.size() != 4) {
                warn("Bạn phải đặt đủ 4 tàu trước khi Ready.");
                return;
            }
            ready.setEnabled(false);
            String encodedSetup = String.join(";", setup);
            client.request("PLACE_BOARD", Map.of("matchId", currentMatchId, "setup", encodedSetup))
                    .thenCompose(placed -> {
                        Map<String, Object> body = payload(placed);
                        if (!Boolean.TRUE.equals(body.get("ok"))) {
                            throw new CompletionException(new IllegalStateException(
                                    String.valueOf(body.getOrDefault("message", "Không đặt được tàu."))));
                        }
                        return challenges.ready(currentMatchId);
                    }).whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
                        if (error != null) {
                            ready.setEnabled(true);
                            warn("Không thể Ready: " + rootMessage(error));
                            return;
                        }
                        Map<String, Object> body = payload(response);
                        if (Boolean.FALSE.equals(body.get("ok"))) {
                            ready.setEnabled(true);
                            warn(String.valueOf(body.get("message")));
                        } else {
                            status.setText("Đã xếp tàu và Ready. Đang chờ đối thủ...");
                            for (JButton[] row : cells) for (JButton cell : row) cell.setEnabled(false);
                            rotate.setEnabled(false);
                            reset.setEnabled(false);
                        }
                    }));
        });

        exit.addActionListener(e -> {
            challenges.exitMatch();
            currentMatchId = null;
            preparationPanel = null;
            preparationStatus = null;
            requestLobby();
            showCard("lobby");
        });
    }

    private void refreshPlacementCells(JButton[][] cells, Board board) {
        String encoded = board.encode(true);
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                JButton cell = cells[r][c];
                char value = encoded.charAt(r * Board.SIZE + c);
                cell.setText(value == '1' ? "■" : "·");
                cell.setEnabled(!board.ready());
                cell.setToolTipText("Hàng " + (r + 1) + ", cột " + (c + 1));
            }
        }
    }

    private void handleGamePush(Map<String, Object> message) {
        String type = String.valueOf(message.get("type"));
        Map<String, Object> p = payload(message);
        if ("GAME_STARTED".equals(type)) {
            currentMatchId = String.valueOf(p.get("matchId"));
            myTurn = Boolean.TRUE.equals(p.get("yourTurn"));
            secondsLeft = number(p.get("secondsPerTurn"));
            preparationPanel = null;
            preparationStatus = null;
            showGame(p);
        } else if ("OPPONENT_SHOT".equals(type)) {
            if (!"game".equals(currentPage) || gamePanel == null) return;
            int row = number(p.get("row")), col = number(p.get("col"));
            if (inside(row, col)) {
                String result = String.valueOf(p.get("result"));
                ownCells[row][col].setText(cellLabel(result, true));
                ownCells[row][col].setEnabled(false);
                if ("SUNK".equals(result)) revealSunkShip(p.get("sunkCells"), number(p.get("shipLength")), true);
            }
            myTurn = Boolean.TRUE.equals(p.get("yourTurn"));
            updateGameStatus();
        } else if ("TURN_CHANGED".equals(type)) {
            myTurn = username != null && username.equalsIgnoreCase(String.valueOf(p.get("currentPlayer")));
            secondsLeft = number(p.get("secondsLeft"));
            updateGameStatus();
        } else if ("GAME_OVER".equals(type)) {
            gameEnded = true;
            myTurn = false;
            if (countdownTimer != null) countdownTimer.stop();
            updateGameStatus();
        }
    }

    private void showGame(Map<String, Object> data) {
        closeGameDialog();
        gameEnded = false;
        secondsLeft = number(data.get("secondsPerTurn"));
        for (int r = 0; r < 6; r++) java.util.Arrays.fill(enemyShots[r], false);
        ownCells = new JButton[6][6];
        enemyCells = new JButton[6][6];
        gameStatus = new JLabel("", SwingConstants.CENTER);
        gameStatus.setForeground(CREAM);
        gameStatus.setFont(gameStatus.getFont().deriveFont(Font.BOLD, 15f));
        turnCountdown = new JLabel("", SwingConstants.CENTER);
        turnCountdown.setForeground(BRASS);

        JPanel boards = new JPanel(new GridLayout(1, 2, 18, 8));
        boards.setOpaque(false);
        boards.add(createBoardPanel("Bàn cờ của bạn", ownCells, String.valueOf(data.get("board")), false));
        boards.add(createBoardPanel("Bàn cờ đối thủ", enemyCells, String.valueOf(data.get("opponentBoard")), true));

        JButton exit = new JButton("Thoát trận");
        styleButton(exit, true);
        exit.addActionListener(e -> {
            if (currentMatchId != null) challenges.exitMatch();
            closeGameDialog();
            currentMatchId = null;
            showCard("lobby");
            requestLobby();
        });
        BackgroundPanel gameContent = new BackgroundPanel(new BorderLayout(8, 8), "battle-bg.png");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        JPanel statusPanel = new JPanel(new GridLayout(2, 1));
        statusPanel.setOpaque(false);
        statusPanel.add(gameStatus);
        statusPanel.add(turnCountdown);
        bottom.add(statusPanel, BorderLayout.CENTER);
        bottom.add(exit, BorderLayout.EAST);
        gameContent.add(boards, BorderLayout.CENTER);
        gameContent.add(bottom, BorderLayout.SOUTH);
        gamePanel = gameContent;
        showPage("game", gameContent);
        countdownTimer = new javax.swing.Timer(1000, e -> {
            if (secondsLeft > 0) secondsLeft--;
            updateGameStatus();
        });
        countdownTimer.start();
        updateGameStatus();
    }

    private JPanel createBoardPanel(String title, JButton[][] cells, String encoded, boolean attackBoard) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel(title, SwingConstants.CENTER), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(6, 6, 2, 2));
        panel.setOpaque(false);
        grid.setOpaque(false);
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                final int row = r, col = c;
                JButton cell = new JButton();
                styleBoardCell(cell, attackBoard);
                cell.setMargin(new Insets(2, 2, 2, 2));
                char value = encoded.length() == 36 ? encoded.charAt(r * 6 + c) : '0';
                cell.setText(attackBoard ? "" : ownCellLabel(value));
                cell.setEnabled(attackBoard && myTurn && !gameEnded);
                if (attackBoard) {
                    cell.addActionListener(e -> fireShot(row, col));
                } else {
                    cell.setEnabled(false);
                }
                cells[r][c] = cell;
                grid.add(cell);
            }
        }
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private void fireShot(int row, int col) {
        if (!myTurn || gameEnded || currentMatchId == null || enemyShots[row][col]) return;
        myTurn = false;
        updateGameStatus();
        for (JButton[] line : enemyCells) for (JButton b : line) b.setEnabled(false);
        client.request("SHOT", Map.of("matchId", currentMatchId, "row", row, "col", col))
                .whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        showGameMessage("Không gửi được nước bắn: " + rootMessage(error));
                        myTurn = true;
                        updateGameStatus();
                        return;
                    }
                    Map<String, Object> p = payload(response);
                    if (!Boolean.TRUE.equals(p.get("ok"))) {
                        showGameMessage(String.valueOf(p.getOrDefault("message", "Nước bắn không hợp lệ.")));
                        myTurn = username != null && username.equalsIgnoreCase(String.valueOf(p.get("currentPlayer")));
                        if (p.get("currentPlayer") == null) myTurn = true;
                        updateGameStatus();
                        return;
                    }
                    enemyShots[row][col] = true;
                    String shotResult = String.valueOf(p.get("result"));
                    enemyCells[row][col].setText(cellLabel(shotResult, false));
                    enemyCells[row][col].setEnabled(false);
                    if ("SUNK".equals(shotResult)) revealSunkShip(p.get("sunkCells"), number(p.get("shipLength")), false);
                    myTurn = Boolean.TRUE.equals(p.get("yourTurn"));
                    secondsLeft = number(p.get("secondsLeft"));
                    updateGameStatus();
                }));
    }

    private void updateGameStatus() {
        if (gameStatus != null) gameStatus.setText(gameEnded ? "Trận đấu đã kết thúc."
                : myTurn ? "Đến lượt bạn — tối đa 15 giây." : "Đang chờ lượt đối thủ...");
        if (turnCountdown != null) {
            turnCountdown.setText(gameEnded ? "" : "Thời gian còn lại: " + Math.max(0, secondsLeft) + " giây");
            turnCountdown.setForeground(secondsLeft <= 5 ? new Color(224, 87, 62) : new Color(211, 181, 116));
        }
        if (enemyCells != null) {
            for (int r = 0; r < 6; r++) for (int c = 0; c < 6; c++)
                enemyCells[r][c].setEnabled(myTurn && !gameEnded && !enemyShots[r][c]);
        }
    }

    private void showGameMessage(String message) {
        if (gameStatus != null) gameStatus.setText(message);
    }

    private String formatEndReason(Object reasonValue) {
        String reason = String.valueOf(reasonValue == null ? "" : reasonValue);
        return switch (reason) {
            case "ALL_SHIPS_SUNK" -> "Toàn bộ tàu của đối thủ đã bị đánh chìm.";
            case "PLAYER_EXIT" -> "Đối thủ đã rời trận.";
            case "DISCONNECTED" -> "Đối thủ mất kết nối khỏi trận.";
            case "LOGOUT" -> "Đối thủ đã đăng xuất.";
            default -> "Trận đấu đã kết thúc theo diễn biến của ván đấu.";
        };
    }

    private String ownCellLabel(char value) {
        return switch (value) {
            case '2' -> "MISS";
            case '3' -> "HIT";
            default -> ""; // Tàu không lộ diện cho đến khi bị đánh chìm
        };
    }

    private String cellLabel(String result, boolean ownBoard) {
        return switch (result) {
            case "MISS" -> "MISS";
            case "HIT", "SUNK" -> "HIT";
            default -> "";
        };
    }

    private void loadShipImages() {
        loadShipImage(2, "/battleship/client/assets/ships/destroyer.png");
        loadShipImage(3, "/battleship/client/assets/ships/cruiser.png");
        loadShipImage(4, "/battleship/client/assets/ships/battleship.png");
        loadShipImage(5, "/battleship/client/assets/ships/carrier.png");
    }

    private void loadShipImage(int length, String path) {
        try (var in = ClientMain.class.getResourceAsStream(path)) {
            if (in != null) shipImages.put(length, ImageIO.read(in));
            else System.err.println("Không tìm thấy ảnh tàu: " + path);
        } catch (IOException ex) {
            System.err.println("Không đọc được ảnh tàu: " + path + ": " + ex.getMessage());
        }
    }

    private void revealSunkShip(Object cellsValue, int lengthValue, boolean ownBoardSunk) {
        if (cellsValue == null || String.valueOf(cellsValue).isBlank()) return;
        BufferedImage source = shipImages.get(lengthValue);
        if (source == null) return;
        String[] entries = String.valueOf(cellsValue).split(";");
        java.util.List<Point> points = new ArrayList<>();
        for (String entry : entries) {
            String[] rc = entry.split(",");
            if (rc.length != 2) continue;
            try {
                int r = Integer.parseInt(rc[0]), c = Integer.parseInt(rc[1]);
                if (inside(r, c)) points.add(new Point(r, c));
            } catch (NumberFormatException ignored) {}
        }
        if (points.size() != lengthValue) return;
        points.sort((a, b) -> a.x == b.x ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
        boolean horizontal = points.stream().allMatch(pt -> pt.x == points.get(0).x);
        BufferedImage oriented = horizontal ? rotateShipImage(source) : source;
        int targetLength = horizontal ? points.stream().mapToInt(pt -> pt.y).max().orElse(0)
                - points.stream().mapToInt(pt -> pt.y).min().orElse(0) + 1
                : points.size();
        int cellW = Math.max(1, (int) (enemyCells != null ? enemyCells[0][0].getWidth() : 48));
        int cellH = Math.max(1, (int) (enemyCells != null ? enemyCells[0][0].getHeight() : 48));
        for (Point pt : points) {
            int index = horizontal ? pt.y - points.stream().mapToInt(v -> v.y).min().orElse(0)
                    : pt.x - points.stream().mapToInt(v -> v.x).min().orElse(0);
            BufferedImage segment = cropShipSegment(oriented, index, lengthValue, horizontal);
            Image scaled = segment.getScaledInstance(cellW - 4, cellH - 4, Image.SCALE_SMOOTH);
            JButton cell = ownBoardSunk
                    ? (ownCells != null ? ownCells[pt.x][pt.y] : null)
                    : (enemyCells != null ? enemyCells[pt.x][pt.y] : null);
            if (cell != null) {
                cell.setIcon(new ImageIcon(scaled));
                if ("HIT".equals(cell.getText())) {
                    cell.setHorizontalTextPosition(SwingConstants.CENTER);
                    cell.setVerticalTextPosition(SwingConstants.CENTER);
                    cell.setForeground(new Color(255, 96, 73));
                    cell.setFont(cell.getFont().deriveFont(Font.BOLD, 10f));
                }
                cell.setDisabledIcon(new ImageIcon(scaled));
                cell.repaint();
            }
        }
    }

    private BufferedImage rotateShipImage(BufferedImage source) {
        BufferedImage rotated = new BufferedImage(source.getHeight(), source.getWidth(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = rotated.createGraphics();
        g.translate(rotated.getWidth(), 0);
        g.rotate(Math.PI / 2);
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rotated;
    }

    private BufferedImage cropShipSegment(BufferedImage source, int index, int length, boolean horizontal) {
        if (horizontal) {
            int segmentWidth = Math.max(1, source.getWidth() / length);
            int x = Math.min(source.getWidth() - segmentWidth, index * segmentWidth);
            return source.getSubimage(x, 0, segmentWidth, source.getHeight());
        }
        int segmentHeight = Math.max(1, source.getHeight() / length);
        int y = Math.min(source.getHeight() - segmentHeight, index * segmentHeight);
        return source.getSubimage(0, y, source.getWidth(), segmentHeight);
    }

    private static int number(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return -1; }
    }

    private static boolean inside(int row, int col) { return row >= 0 && row < 6 && col >= 0 && col < 6; }

    private void closeGameDialog() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
        if (gamePanel != null) {
            root.remove(gamePanel);
            gamePanel = null;
            root.revalidate();
            root.repaint();
        }
        ownCells = null;
        enemyCells = null;
        gameStatus = null;
        turnCountdown = null;
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
            case "IDLE" -> "Online";
            case "PLAYING" -> "Trong trận";
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
        showCard("login");
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
