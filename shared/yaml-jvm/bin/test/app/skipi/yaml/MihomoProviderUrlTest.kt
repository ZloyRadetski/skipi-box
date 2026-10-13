// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MihomoProviderUrlTest {
    @Test
    fun trimsAndPreservesValidHttpUrisAsTheAndroidImporterDoes() {
        assertEquals(
            "https://provider.example.test/path?a=1",
            normalizeMihomoProviderUrl("  https://provider.example.test/path?a=1  "),
        )
        assertEquals(
            "HTTPS://provider.example.test/nodes.yaml",
            normalizeMihomoProviderUrl("HTTPS://provider.example.test/nodes.yaml"),
        )
        assertEquals(
            "http://user:pass@provider.example.test:8080/nodes.yaml",
            normalizeMihomoProviderUrl("http://user:pass@provider.example.test:8080/nodes.yaml"),
        )
    }

    @Test
    fun rejectsBlankMalformedNonHttpAndHostlessUris() {
        assertNull(normalizeMihomoProviderUrl(null))
        assertNull(normalizeMihomoProviderUrl("   "))
        assertNull(normalizeMihomoProviderUrl("ftp://provider.example.test/nodes.yaml"))
        assertNull(normalizeMihomoProviderUrl("https:///nodes.yaml"))
        assertNull(normalizeMihomoProviderUrl("https://[invalid"))
    }

    @Test
    fun extractsOnlyTheHostForProviderFailureLogging() {
        assertEquals(
            "provider.example.test",
            mihomoProviderUrlLogHost("https://user:pass@provider.example.test/nodes.yaml?token=secret"),
        )
        assertEquals("<unknown>", mihomoProviderUrlLogHost("not a URI"))
    }
}
