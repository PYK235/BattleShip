package src.battleship.server;

import java.time.Instant;

public final class Challenge {
    private final String id;
    private final String inviter;
    private final String invitee;
    private final Instant createdAt = Instant.now();

    public Challenge(String id, String inviter, String invitee) {
        this.id = id;
        this.inviter = inviter;
        this.invitee = invitee;
    }
    public String getId() { return id; }
    public String getInviter() { return inviter; }
    public String getInvitee() { return invitee; }
    public Instant getCreatedAt() { return createdAt; }
}
