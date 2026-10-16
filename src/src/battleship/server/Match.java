package src.battleship.server;

public final class Match {
    private final String id;
    private final String player1;
    private final String player2;
    private volatile MatchStatus status = MatchStatus.WAITING;
    private volatile boolean player1Ready;
    private volatile boolean player2Ready;

    public Match(String id, String player1, String player2) {
        this.id = id;
        this.player1 = player1;
        this.player2 = player2;
    }
    public String getId() { return id; }
    public String getPlayer1() { return player1; }
    public String getPlayer2() { return player2; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public boolean isPlayer1Ready() { return player1Ready; }
    public boolean isPlayer2Ready() { return player2Ready; }

    public synchronized boolean setReady(String username) {
        if (player1.equalsIgnoreCase(username)) player1Ready = true;
        else if (player2.equalsIgnoreCase(username)) player2Ready = true;
        else return false;
        return true;
    }
    public boolean bothReady() { return player1Ready && player2Ready; }
    public String opponentOf(String username) {
        if (player1.equalsIgnoreCase(username)) return player2;
        if (player2.equalsIgnoreCase(username)) return player1;
        return null;
    }
    public boolean contains(String username) {
        return player1.equalsIgnoreCase(username) || player2.equalsIgnoreCase(username);
    }
}
