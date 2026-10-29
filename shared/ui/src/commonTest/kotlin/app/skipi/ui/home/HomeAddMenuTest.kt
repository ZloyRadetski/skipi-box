// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeImportSource
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAddMenuTest {
    private val allSources = setOf(
        ProxyHomeImportSource.QrCode,
        ProxyHomeImportSource.Clipboard,
        ProxyHomeImportSource.File,
        ProxyHomeImportSource.ManualInput,
    )

    @Test
    fun plusMenuIsSubscriptionClipboardQrThenServer() {
        assertEquals(
            listOf("add-subscription", "import:Clipboard", "import:QrCode", "add-server"),
            proxyHomeAddMenuIds(
                canAddSubscription = true,
                canImport = true,
                importSources = allSources,
                canAddServer = true,
            ),
        )
    }

    @Test
    fun missingCapabilitiesDropTheirEntriesAndThereIsNoSeparateGroupEntry() {
        assertEquals(
            listOf("add-subscription", "import:Clipboard", "add-server"),
            proxyHomeAddMenuIds(true, true, setOf(ProxyHomeImportSource.Clipboard, ProxyHomeImportSource.File), true),
        )
        assertEquals(
            listOf("add-server"),
            proxyHomeAddMenuIds(false, false, allSources, true),
        )
        assertEquals(emptyList(), proxyHomeAddMenuIds(false, true, emptySet(), false))
    }
}
