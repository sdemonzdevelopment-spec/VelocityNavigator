package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    @Test
    void startsInClosedState() {
        CircuitBreaker breaker = new CircuitBreaker(3, 30, 1);

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));
        assertTrue(breaker.isAvailable("server-1"));
    }

    @Test
    void transitionsToOpenAfterThresholdFailures() {
        CircuitBreaker breaker = new CircuitBreaker(3, 30, 1);

        breaker.recordFailure("server-1");  // count = 1
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));

        breaker.recordFailure("server-1");  // count = 2
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));

        breaker.recordFailure("server-1");  // count = 3 >= threshold → OPEN
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState("server-1"));
        assertFalse(breaker.isAvailable("server-1"));
    }

    @Test
    void halfOpenAfterCooldown() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, 1, 1); // 1-second cooldown

        // Drive to OPEN state
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState("server-1"));

        // Wait for cooldown to elapse
        Thread.sleep(1100);

        // Should transition to HALF_OPEN
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState("server-1"));
    }

    @Test
    void closesAfterSuccessInHalfOpen() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, 1, 1);

        // Drive to OPEN
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");

        // Wait for cooldown → HALF_OPEN
        Thread.sleep(1100);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState("server-1"));

        // Record success → CLOSED
        breaker.recordSuccess("server-1");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));
        assertTrue(breaker.isAvailable("server-1"));
    }

    @Test
    void staysOpenAfterFailureInHalfOpen() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, 1, 1);

        // Drive to OPEN
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");

        // Wait for cooldown → HALF_OPEN
        Thread.sleep(1100);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState("server-1"));

        // Record failure in HALF_OPEN → back to OPEN
        breaker.recordFailure("server-1");
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState("server-1"));
        assertFalse(breaker.isAvailable("server-1"));
    }

    @Test
    void isAvailableReturnsTrueForClosedAndHalfOpen() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, 1, 1);

        // CLOSED → available
        assertTrue(breaker.isAvailable("server-1"));

        // Drive to OPEN
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        assertFalse(breaker.isAvailable("server-1"));

        // Wait for cooldown → HALF_OPEN → available (can test)
        Thread.sleep(1100);
        assertTrue(breaker.isAvailable("server-1"),
                "HALF_OPEN state should report available for test requests");
    }
}
