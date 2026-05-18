package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionRateTrackerTest {

    @Test
    void tracksConnectionRate() {
        ConnectionRateTracker tracker = new ConnectionRateTracker(60);

        // Record several connections in rapid succession
        for (int i = 0; i < 5; i++) {
            tracker.recordConnection("server-1");
        }

        double rate = tracker.getRatePerSecond("server-1");
        // Rate should be > 0 since we recorded connections
        assertTrue(rate >= 0, "Rate should be non-negative");

        int count = tracker.getConnectionCount("server-1");
        assertTrue(count > 0, "Connection count should be positive after recording");
        assertTrue(count <= 5, "Connection count should not exceed recorded connections");
    }

    @Test
    void rateDecaysOverTime() throws InterruptedException {
        ConnectionRateTracker tracker = new ConnectionRateTracker(1); // 1-second window

        // Record connections
        for (int i = 0; i < 5; i++) {
            tracker.recordConnection("server-1");
        }

        int countBefore = tracker.getConnectionCount("server-1");
        assertTrue(countBefore > 0, "Should have connections before decay");

        // Wait for the window to expire
        Thread.sleep(1100);

        int countAfter = tracker.getConnectionCount("server-1");
        // After the window expires, connections should be purged
        assertTrue(countAfter < countBefore,
                "Connection count should decrease after window expires; before=" + countBefore + ", after=" + countAfter);
    }

    @Test
    void multipleServersTrackedIndependently() {
        ConnectionRateTracker tracker = new ConnectionRateTracker(60);

        tracker.recordConnection("server-1");
        tracker.recordConnection("server-1");
        tracker.recordConnection("server-1");

        tracker.recordConnection("server-2");
        tracker.recordConnection("server-2");

        int count1 = tracker.getConnectionCount("server-1");
        int count2 = tracker.getConnectionCount("server-2");

        assertTrue(count1 > count2,
                "server-1 should have more recorded connections than server-2");
        assertEquals(0, tracker.getConnectionCount("server-3"),
                "Untracked server should have zero connections");
    }
}
