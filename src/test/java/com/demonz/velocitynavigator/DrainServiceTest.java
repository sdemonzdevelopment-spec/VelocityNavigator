/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrainServiceTest {

    @Test
    void drainExcludesServerFromRouting() {
        DrainService service = new DrainService();

        service.drain("lobby-1");
        assertTrue(service.isDrained("lobby-1"),
                "Drained server should be reported as drained");
        assertFalse(service.isDrained("lobby-2"),
                "Non-drained server should not be reported as drained");
    }

    @Test
    void undrainRestoresServer() {
        DrainService service = new DrainService();

        service.drain("lobby-1");
        assertTrue(service.isDrained("lobby-1"));

        service.undrain("lobby-1");
        assertFalse(service.isDrained("lobby-1"),
                "Server should no longer be drained after undrain");
    }

    @Test
    void drainStatusListsDrainedServers() {
        DrainService service = new DrainService();

        service.drain("lobby-1");
        service.drain("lobby-2");

        Map<String, Boolean> status = service.drainState();
        assertEquals(2, status.size());
        assertTrue(status.containsKey("lobby-1"));
        assertTrue(status.containsKey("lobby-2"));
    }

    @Test
    void drainStateClearedOnReset() {
        DrainService service = new DrainService();

        service.drain("lobby-1");
        service.drain("lobby-2");
        assertTrue(service.isDrained("lobby-1"));
        assertTrue(service.isDrained("lobby-2"));

        service.clear();

        assertFalse(service.isDrained("lobby-1"), "Drain state should be cleared after reset");
        assertFalse(service.isDrained("lobby-2"), "Drain state should be cleared after reset");
        assertTrue(service.drainState().isEmpty(), "Drain state map should be empty after reset");
    }
}
