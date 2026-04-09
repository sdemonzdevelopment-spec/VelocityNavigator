package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {

    @Test
    void comparesDoubleDigitVersionsCorrectly() {
        SemanticVersion older = SemanticVersion.parse("2.2.0-STABLE");
        SemanticVersion newer = SemanticVersion.parse("2.10.0-STABLE");

        assertTrue(newer.compareTo(older) > 0);
    }

    @Test
    void ordersReleaseTypesCorrectly() {
        SemanticVersion alpha = SemanticVersion.parse("3.0.0-ALPHA");
        SemanticVersion beta = SemanticVersion.parse("3.0.0-BETA");
        SemanticVersion stable = SemanticVersion.parse("3.0.0-STABLE");
        SemanticVersion release = SemanticVersion.parse("3.0.0");

        assertTrue(beta.compareTo(alpha) > 0);
        assertTrue(stable.compareTo(beta) > 0);
        assertTrue(release.compareTo(stable) > 0);
    }
}

