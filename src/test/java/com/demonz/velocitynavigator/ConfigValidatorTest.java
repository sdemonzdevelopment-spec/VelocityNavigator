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

import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {

    @Test
    void validatesCorrectConfigWithoutWarnings() {
        Toml toml = new Toml().read("""
                [routing]
                selection_mode = "latency"
                [update_checker]
                channel = "release"
                [degradation]
                mode = "random"
                [lobby]
                no_server_strategy = "disconnect"
                [messages]
                formatting = "auto"
                """);

        List<String> warnings = ConfigValidator.validate(Config.defaults(), toml);
        assertTrue(warnings.isEmpty(), "Should have no warnings for a fully valid config");
    }

    @Test
    void suggestsTyposUsingLevenshtein() {
        Toml toml = new Toml().read("""
                [routing]
                selection_mode = "leadt_players"
                """);

        List<String> warnings = ConfigValidator.validate(Config.defaults(), toml);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Unknown value 'leadt_players' for key 'routing.selection_mode'"));
        assertTrue(warnings.get(0).contains("Did you mean 'least_players'?"));
    }

    @Test
    void listsAllValidOptionsIfDistanceTooLarge() {
        Toml toml = new Toml().read("""
                [routing]
                selection_mode = "completely_broken_value"
                """);

        List<String> warnings = ConfigValidator.validate(Config.defaults(), toml);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Valid options are: least_players, random, round_robin"));
    }

    @Test
    void detectsNonStringValues() {
        Toml toml = new Toml().read("""
                [routing]
                selection_mode = 123
                """);

        List<String> warnings = ConfigValidator.validate(Config.defaults(), toml);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Value at 'routing.selection_mode' is not a string."));
    }

    @Test
    void LevenshteinCalculatesCorrectly() {
        assertEquals(0, ConfigValidator.levenshtein("cat", "cat"));
        assertEquals(1, ConfigValidator.levenshtein("cat", "bat"));
        assertEquals(1, ConfigValidator.levenshtein("leadt_players", "least_players"));
        assertEquals(7, ConfigValidator.levenshtein("random", "round_robin"));
    }

    @Test
    void LevenshteinHandlesEdgeCases() {
        assertEquals(0, ConfigValidator.levenshtein("", ""));
        assertEquals(3, ConfigValidator.levenshtein("abc", ""));
        assertEquals(3, ConfigValidator.levenshtein("", "abc"));
        assertEquals(Integer.MAX_VALUE, ConfigValidator.levenshtein(null, null));
        assertEquals(Integer.MAX_VALUE, ConfigValidator.levenshtein("abc", null));
        assertEquals(Integer.MAX_VALUE, ConfigValidator.levenshtein(null, "abc"));
        assertEquals(1, ConfigValidator.levenshtein("a-b", "a_b"));
    }
}
