package battleship.part3.server;

public final class TurnManager {
    private static final long TURN_DURATION_NANOS = 15_000_000_000L;

    private int current;
    private long deadline;

    public TurnManager(int first) {
        current = first;
        reset();
    }

    public int current() {
        return current;
    }

    public int secondsLeft() {
        long remaining = deadline - System.nanoTime();
        return (int) Math.max(0, (remaining + 999_999_999L) / 1_000_000_000L);
    }

    public void hit() {
        reset();
    }

    public void miss() {
        current = 1 - current;
        reset();
    }

    public boolean expired() {
        return System.nanoTime() >= deadline;
    }

    private void reset() {
        deadline = System.nanoTime() + TURN_DURATION_NANOS;
    }
}
