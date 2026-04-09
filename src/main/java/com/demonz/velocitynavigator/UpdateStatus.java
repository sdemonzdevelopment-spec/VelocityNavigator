package com.demonz.velocitynavigator;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class UpdateStatus {

    /**
     * Immutable snapshot of the latest update check state. All fields are read
     * together atomically via the enclosing AtomicReference, eliminating torn
     * reads where version and error could be from different check cycles.
     */
    public record Snapshot(
            Instant lastCheckedAt,
            String latestKnownVersion,
            boolean updateAvailable,
            String lastError
    ) {
        public static final Snapshot INITIAL = new Snapshot(null, "unknown", false, "");
    }

    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.INITIAL);

    public Snapshot snapshot() {
        return current.get();
    }

    // Convenience delegators for backward compatibility with existing callers
    public Instant lastCheckedAt() {
        return current.get().lastCheckedAt();
    }

    public String latestKnownVersion() {
        return current.get().latestKnownVersion();
    }

    public boolean updateAvailable() {
        return current.get().updateAvailable();
    }

    public String lastError() {
        return current.get().lastError();
    }

    public void recordSuccess(String latestKnownVersion, boolean updateAvailable) {
        current.set(new Snapshot(Instant.now(), latestKnownVersion, updateAvailable, ""));
    }

    public void recordFailure(String lastError) {
        current.set(new Snapshot(Instant.now(), current.get().latestKnownVersion(), false, lastError == null ? "Unknown error" : lastError));
    }
}
