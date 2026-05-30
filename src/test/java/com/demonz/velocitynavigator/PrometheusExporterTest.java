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

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrometheusExporterTest {

    @Test
    void escapesPrometheusLabelValues() {
        assertEquals("lobby\\\\\\\"one\\n", PrometheusExporter.escapeLabelValue("lobby\\\"one\n"));
        assertEquals("", PrometheusExporter.escapeLabelValue(null));
    }

    @Test
    void classifiesLoopbackBindHosts() {
        assertEquals(true, PrometheusExporter.isLoopbackBindHost("127.0.0.1"));
        assertEquals(true, PrometheusExporter.isLoopbackBindHost("localhost"));
        assertEquals(true, PrometheusExporter.isLoopbackBindHost("::1"));
        assertEquals(false, PrometheusExporter.isLoopbackBindHost("0.0.0.0"));
    }

    @Test
    void reportsOnlineOnlyForTrackedServers() {
        assertEquals(true, PrometheusExporter.shouldReportServerOnline(true, true, false));
        assertEquals(true, PrometheusExporter.shouldReportServerOnline(false, true, true));
        assertEquals(false, PrometheusExporter.shouldReportServerOnline(false, true, false));
        assertEquals(false, PrometheusExporter.shouldReportServerOnline(true, false, true));
    }
}
