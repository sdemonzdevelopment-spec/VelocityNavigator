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

/**
 * Static holder for the {@link NavigatorAPI} instance.
 * <p>
 * Third-party plugins should access the API like this:
 * <pre>{@code
 * NavigatorAPI api = NavigatorAPIProvider.get();
 * if (api != null) {
 *     // VelocityNavigator is loaded and ready
 * }
 * }</pre>
 */
public final class NavigatorAPIProvider {

    private static volatile NavigatorAPI instance;

    private NavigatorAPIProvider() {
    }

    /**
     * Get the current API instance, or {@code null} if VelocityNavigator
     * has not been initialized yet.
     *
     * @return the API instance, or null
     */
    public static NavigatorAPI get() {
        return instance;
    }

    /**
     * Internal use only. Called by VelocityNavigator during initialization.
     */
    static void set(NavigatorAPI api) {
        instance = api;
    }

    /**
     * Internal use only. Called by VelocityNavigator during shutdown.
     */
    static void clear() {
        instance = null;
    }
}
