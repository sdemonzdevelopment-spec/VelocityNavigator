package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyAdminsOnJoinConfigTest {

    @Test
    void defaultsHaveNotifyAdminsOnJoinEnabled() {
        Config defaults = Config.defaults();
        assertTrue(defaults.notifyAdminsOnJoin(),
                "notifyAdminsOnJoin should default to true");
    }

    @Test
    void notifyAdminsOnJoinCanBeDisabled() {
        Config d = Config.defaults();
        Config disabled = new Config(
                d.configVersion(),
                d.commands(),
                d.routing(),
                d.healthChecks(),
                d.messages(),
                d.updateChecker(),
                d.metrics(),
                d.debug(),
                d.circuitBreaker(),
                d.degradation(),
                d.geoRouting(),
                d.notifyOnStartup(),
                false
        );
        assertFalse(disabled.notifyAdminsOnJoin(),
                "notifyAdminsOnJoin should be false when explicitly set");
        assertTrue(disabled.notifyOnStartup(),
                "notifyOnStartup should remain unchanged");
    }

    @Test
    void notifyAdminsOnJoinIndependentOfNotifyOnStartup() {
        Config d = Config.defaults();
        // Both disabled
        Config bothOff = new Config(
                d.configVersion(),
                d.commands(),
                d.routing(),
                d.healthChecks(),
                d.messages(),
                d.updateChecker(),
                d.metrics(),
                d.debug(),
                d.circuitBreaker(),
                d.degradation(),
                d.geoRouting(),
                false,
                false
        );
        assertFalse(bothOff.notifyOnStartup());
        assertFalse(bothOff.notifyAdminsOnJoin());

        // Only admin join enabled
        Config onlyAdmin = new Config(
                d.configVersion(),
                d.commands(),
                d.routing(),
                d.healthChecks(),
                d.messages(),
                d.updateChecker(),
                d.metrics(),
                d.debug(),
                d.circuitBreaker(),
                d.degradation(),
                d.geoRouting(),
                false,
                true
        );
        assertFalse(onlyAdmin.notifyOnStartup());
        assertTrue(onlyAdmin.notifyAdminsOnJoin());
    }
}
