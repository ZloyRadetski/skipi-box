// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayGeneratedConfigTest {
    @Test
    fun assemblesOptionalCoreSectionsAndTrafficCounters() {
        val config = GeneratedXrayConfig(
            log = buildJsonObject {},
            dns = buildJsonObject {},
            inbounds = buildJsonArray {},
            outbounds = buildJsonArray {},
            routing = buildJsonObject {},
            fakeDns = buildJsonObject {},
            observatory = buildJsonObject {},
            burstObservatory = buildJsonObject {},
        ).toJsonObject()

        assertTrue(config.keys.containsAll(setOf(
            "log",
            "dns",
            "inbounds",
            "outbounds",
            "routing",
            "fakeDns",
            "observatory",
            "burstObservatory",
            "stats",
            "policy",
        )))
        assertTrue(GeneratedXrayConfig(
            log = buildJsonObject {},
            inbounds = buildJsonArray {},
            outbounds = buildJsonArray {},
            collectTrafficStats = false,
        ).toJsonObject().let { "stats" !in it && "policy" !in it })
    }
}
