package src.battleship.server;

import java.util.Map;

/** Extension point for request types owned by other feature modules. */
@FunctionalInterface
public interface MessageRoute {
    void handle(ClientHandler client, String requestId, Map<String,Object> payload);
}
