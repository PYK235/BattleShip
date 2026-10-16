package src.battleship.client;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ChallengeController {
    private final GameClient client;
    private Consumer<Map<String, Object>> inviteHandler = m -> {};
    private Consumer<Map<String, Object>> matchHandler = m -> {};

    public ChallengeController(GameClient client) {
        this.client = client;
        client.addPushListener(this::handlePush);
    }

    public CompletableFuture<Map<String, Object>> invite(String targetUsername) {
        return client.request("INVITE", Map.of("targetUsername", targetUsername));
    }

    public CompletableFuture<Map<String, Object>> accept(String challengeId) {
        return client.request("ACCEPT", Map.of("challengeId", challengeId));
    }

    public CompletableFuture<Map<String, Object>> reject(String challengeId) {
        return client.request("REJECT", Map.of("challengeId", challengeId));
    }

    public CompletableFuture<Map<String, Object>> ready(String matchId) {
        return client.request("READY", Map.of("matchId", matchId));
    }

    public CompletableFuture<Map<String, Object>> exitMatch() {
        return client.request("MATCH_EXIT", Map.of());
    }

    public void onInvite(Consumer<Map<String, Object>> handler) { inviteHandler = handler; }
    public void onMatch(Consumer<Map<String, Object>> handler) { matchHandler = handler; }

    private void handlePush(Map<String, Object> m) {
        String type = String.valueOf(m.get("type"));
        if (type.startsWith("INVITE_")) inviteHandler.accept(m);
        else if (type.equals("MATCH_START") || type.equals("MATCH_PLAYING") ||
                type.equals("OPPONENT_READY") || type.equals("OPPONENT_LEFT") ||
                type.equals("MATCH_END")) matchHandler.accept(m);
    }
}
