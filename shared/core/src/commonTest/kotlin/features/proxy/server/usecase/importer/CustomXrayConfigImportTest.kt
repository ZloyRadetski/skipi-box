// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CustomXrayConfigImportTest {
    @Test
    fun parsesOneNamedCustomXrayConfigWithoutPlatformDependencies() {
        val result = parseCustomXrayConfigPayload(
            """
                {
                  "remarks": "My server",
                  "outbounds": [
                    { "tag": "proxy", "protocol": "vless" }
                  ]
                }
            """.trimIndent(),
        )

        val imported = assertIs<CustomXrayConfigImportResult.Imported>(result)
        assertEquals(1, imported.configCount)
        assertEquals(0, imported.rejectedConfigCount)
        assertEquals("My server", imported.servers.single().remarks)
    }

    @Test
    fun retainsValidArrayEntriesAndReportsRejectedConfigs() {
        val result = parseCustomXrayConfigPayload(
            """
                [
                  { "outbounds": [{ "tag": "proxy", "protocol": "vless" }] },
                  { "outbounds": [] }
                ]
            """.trimIndent(),
        )

        val imported = assertIs<CustomXrayConfigImportResult.Imported>(result)
        assertEquals(2, imported.configCount)
        assertEquals(1, imported.rejectedConfigCount)
        assertEquals("JSON (VLESS) 1", imported.servers.single().remarks)
    }

    @Test
    fun distinguishesNonJsonMalformedJsonAndArraysWithoutObjects() {
        assertIs<CustomXrayConfigImportResult.NotJson>(parseCustomXrayConfigPayload("vless://example"))
        assertIs<CustomXrayConfigImportResult.InvalidJson>(parseCustomXrayConfigPayload("{not valid json"))
        assertIs<CustomXrayConfigImportResult.NoConfigObjects>(parseCustomXrayConfigPayload("[null, 1]"))
    }
}
