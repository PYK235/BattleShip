package battleship.server;

public final class User {
    public enum Status { OFFLINE, IDLE, PLAYING }
    private final String username; private volatile Status status = Status.OFFLINE;
    private volatile int totalScore, wins, losses, matches;
    public User(String username,int score,int wins,int losses,int matches){this.username=username;this.totalScore=score;this.wins=wins;this.losses=losses;this.matches=matches;}
    public String getUsername(){return username;} public Status getStatus(){return status;} void setStatus(Status s){status=s;}
    public int getTotalScore(){return totalScore;} public int getWins(){return wins;} public int getLosses(){return losses;} public int getMatches(){return matches;}
    void refreshStats(User source) {
        if (source == null) return;
        totalScore = source.getTotalScore();
        wins = source.getWins();
        losses = source.getLosses();
        matches = source.getMatches();
    }
}
