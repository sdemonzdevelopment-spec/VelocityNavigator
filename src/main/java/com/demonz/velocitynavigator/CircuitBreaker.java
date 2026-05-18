package com.demonz.velocitynavigator;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private record BreakerState(State state, int failureCount, Instant openSince, int halfOpenTests) {}

    private final ConcurrentMap<String, BreakerState> states = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final int cooldownSeconds;
    private final int halfOpenMaxTests;

    public CircuitBreaker(int failureThreshold, int cooldownSeconds, int halfOpenMaxTests) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownSeconds = Math.max(1, cooldownSeconds);
        this.halfOpenMaxTests = Math.max(1, halfOpenMaxTests);
    }

    /**
     * Atomically checks availability and increments HALF_OPEN test counter.
     * Uses compute() to prevent check-then-act race conditions under concurrent access.
     */
    public boolean isAvailable(String serverName) {
        BreakerState state = states.get(serverName);
        if (state == null) {
            return true;
        }
        return switch (state.state) {
            case CLOSED -> true;
            case OPEN -> {
                // Atomically transition OPEN → HALF_OPEN if cooldown has elapsed
                AtomicBoolean available = new AtomicBoolean(false);
                states.compute(serverName, (key, current) -> {
                    if (current != null && current.state == State.OPEN
                            && Instant.now().isAfter(current.openSince.plusSeconds(cooldownSeconds))) {
                        available.set(true);
                        return new BreakerState(State.HALF_OPEN, current.failureCount, current.openSince, 0);
                    }
                    return current;
                });
                yield available.get();
            }
            case HALF_OPEN -> {
                // Atomically increment halfOpenTests — enforces the halfOpenMaxTests limit
                AtomicBoolean available = new AtomicBoolean(false);
                states.compute(serverName, (key, current) -> {
                    if (current != null && current.state == State.HALF_OPEN
                            && current.halfOpenTests < halfOpenMaxTests) {
                        available.set(true);
                        return new BreakerState(State.HALF_OPEN, current.failureCount,
                                current.openSince, current.halfOpenTests + 1);
                    }
                    return current;
                });
                yield available.get();
            }
        };
    }

    /**
     * Records a successful connection. Only resets state if not already CLOSED,
     * to avoid resetting failure counts on periodic health checks in CLOSED state.
     */
    public void recordSuccess(String serverName) {
        states.compute(serverName, (key, current) -> {
            if (current != null && current.state != State.CLOSED) {
                return new BreakerState(State.CLOSED, 0, null, 0);
            }
            return current; // In CLOSED state, don't touch the failure count
        });
    }

    public void recordFailure(String serverName) {
        states.compute(serverName, (key, current) -> {
            if (current == null) {
                current = new BreakerState(State.CLOSED, 0, null, 0);
            }
            return switch (current.state) {
                case CLOSED -> {
                    int newCount = current.failureCount + 1;
                    if (newCount >= failureThreshold) {
                        yield new BreakerState(State.OPEN, newCount, Instant.now(), 0);
                    }
                    yield new BreakerState(State.CLOSED, newCount, current.openSince, current.halfOpenTests);
                }
                case OPEN -> new BreakerState(State.OPEN, current.failureCount + 1, current.openSince, 0);
                case HALF_OPEN -> new BreakerState(State.OPEN, current.failureCount + 1, Instant.now(), 0);
            };
        });
    }

    /**
     * Atomically transitions OPEN → HALF_OPEN if cooldown has elapsed.
     * Uses compute() to prevent race conditions.
     */
    public State getState(String serverName) {
        BreakerState state = states.get(serverName);
        if (state == null) {
            return State.CLOSED;
        }
        if (state.state == State.OPEN && Instant.now().isAfter(state.openSince.plusSeconds(cooldownSeconds))) {
            states.compute(serverName, (key, current) -> {
                if (current != null && current.state == State.OPEN
                        && Instant.now().isAfter(current.openSince.plusSeconds(cooldownSeconds))) {
                    return new BreakerState(State.HALF_OPEN, current.failureCount, current.openSince, 0);
                }
                return current;
            });
        }
        // FIX-7: Always read the current state after compute() to avoid returning stale snapshot
        BreakerState current = states.get(serverName);
        return current == null ? State.CLOSED : current.state;
    }

    public void reset(String serverName) {
        states.remove(serverName);
    }

    public void resetAll() {
        states.clear();
    }
}
