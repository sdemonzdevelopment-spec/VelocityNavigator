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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ServerLoadTracker {

    private final double smoothingFactor;
    private final ConcurrentMap<String, Double> emaValues = new ConcurrentHashMap<>();

    public ServerLoadTracker(double smoothingFactor) {
        this.smoothingFactor = Math.max(0.01, Math.min(1.0, smoothingFactor));
    }

    public void update(String serverName, int currentPlayers) {
        emaValues.compute(serverName, (key, previous) -> {
            if (previous == null) {
                return (double) currentPlayers;
            }
            return smoothingFactor * currentPlayers + (1.0 - smoothingFactor) * previous;
        });
    }

    public double getEma(String serverName) {
        return emaValues.getOrDefault(serverName, 0.0);
    }

    public void remove(String serverName) {
        emaValues.remove(serverName);
    }

    public void clear() {
        emaValues.clear();
    }
}
