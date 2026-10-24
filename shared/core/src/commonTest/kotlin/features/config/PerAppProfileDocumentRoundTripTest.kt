// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerAppProfileDocumentRoundTripTest {
    @Test
    fun profileDocumentRoundTripCanonicalizesOpaquePackageIdsAndKeepsMode() {
        val source = TrafficConfigState(
            id = 7,
            name = "Work",
            rawConfig = """
                [General]
                dns-server = system

                [SKIPI]
                per-app-mode = whitelist
                per-app-package =  legacy.unknown  
                per-app-package =
                per-app-package = 10:com.example.other-user
                per-app-package = legacy.unknown
                per-app-package = 0:com.example.present
            """.trimIndent(),
        )
        val expectedIds = listOf(
            "legacy.unknown",
            "10:com.example.other-user",
            "0:com.example.present",
        )

        val parsed = source.withSkipiSettingsReadFromRawConfig()
        assertEquals(SkipiPerAppModeWhitelist, parsed.proxyAppListMode)
        assertEquals(expectedIds, parsed.proxyAppListSelectedApps)

        val serialized = parsed.withSkipiSettingsInRawConfig().rawConfig
        val reparsed = parsed.copy(rawConfig = serialized).withSkipiSettingsReadFromRawConfig()
        val serializedValues = serialized.skipiSectionValues()

        assertEquals(SkipiPerAppModeWhitelist, reparsed.proxyAppListMode)
        assertEquals(expectedIds, reparsed.proxyAppListSelectedApps)
        assertEquals(expectedIds, serializedValues[SkipiPerAppPackage])
        assertEquals(listOf("whitelist"), serializedValues[SkipiPerAppMode])
        assertTrue(serialized.contains("[General]"))
        assertTrue(serialized.contains("dns-server = system"))
    }
}
