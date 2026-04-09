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

    private static NavigatorAPI instance;

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
