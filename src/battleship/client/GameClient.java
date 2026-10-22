package battleship.client;

import battleship.common.Protocol;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class GameClient implements Closeable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final ConcurrentMap<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<Map<String, Object>>> pushListeners = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    GameClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        reader.submit(this::readLoop);
    }

    void onPush(Consumer<Map<String, Object>> c) {
        pushListeners.clear();
        pushListeners.add(c);
    }

    void addPushListener(Consumer<Map<String, Object>> c) {
        pushListeners.add(c);
    }

    CompletableFuture<Map<String, Object>> request(String type, Map<String, Object> payload) {
        String id = UUID.randomUUID().toString();
        CompletableFuture<Map<String, Object>> f = new CompletableFuture<>();
        pending.put(id, f);
        synchronized (out) {
            out.println(Protocol.encode(Protocol.message(type, id, payload)));
        }
        return f.orTimeout(8, TimeUnit.SECONDS).whenComplete((v, e) -> pending.remove(id));
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                Map<String, Object> m = Protocol.decode(line);
                Object id = m.get("requestId");
                if (id != null && pending.containsKey(String.valueOf(id)))
                    pending.get(String.valueOf(id)).complete(m);
                else
                    for (Consumer<Map<String, Object>> listener : pushListeners) listener.accept(m);
            }
        } catch (Exception e) {
            if (!closed)
                for (Consumer<Map<String, Object>> listener : pushListeners)
                    listener.accept(Map.of("type", "CONNECTION_LOST"));
        } finally {
            pending.values().forEach(f -> f.completeExceptionally(new IOException("Connection closed")));
        }
    }

    public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) {}
        reader.shutdownNow();
    }
}
