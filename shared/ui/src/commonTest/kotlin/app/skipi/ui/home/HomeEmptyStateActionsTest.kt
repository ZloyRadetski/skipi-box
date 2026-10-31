// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeImportSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeEmptyStateActionsTest {
    private val allSources = setOf(
        ProxyHomeImportSource.QrCode,
        ProxyHomeImportSource.Clipboard,
        ProxyHomeImportSource.File,
        ProxyHomeImportSource.ManualInput,
    )

    private fun actions(
        hasAnyServers: Boolean = false,
        hasSearch: Boolean = false,
        canAddSubscription: Boolean = true,
        canImport: Boolean = true,
        importSources: Set<ProxyHomeImportSource> = allSources,
        canAddServer: Boolean = true,
    ) = proxyHomeEmptyStateActions(
        hasAnyServers = hasAnyServers,
        hasSearch = hasSearch,
        canAddSubscription = canAddSubscription,
        canImport = canImport,
        importSources = importSources,
        canAddServer = canAddServer,
    )

    @Test
    fun anEmptyHomeOffersPasteAddAndQr() {
        assertEquals(ProxyHomeEmptyStateActions(paste = true, add = true, scanQr = true), actions())
    }

    @Test
    fun nothingIsOfferedOnceAnyServerExists() {
        assertEquals(ProxyHomeEmptyStateActions(false, false, false), actions(hasAnyServers = true))
    }

    @Test
    fun anEmptySearchResultIsNotTheFirstRunState() {
        assertEquals(ProxyHomeEmptyStateActions(false, false, false), actions(hasSearch = true))
    }

    @Test
    fun pasteAndQrFollowTheImportSourcesAndPermission() {
        val noQr = actions(importSources = setOf(ProxyHomeImportSource.Clipboard))
        assertTrue(noQr.paste)
        assertFalse(noQr.scanQr)

        val noClipboard = actions(importSources = setOf(ProxyHomeImportSource.QrCode))
        assertFalse(noClipboard.paste)
        assertTrue(noClipboard.scanQr)

        val importForbidden = actions(canImport = false)
        assertFalse(importForbidden.paste)
        assertFalse(importForbidden.scanQr)
        assertTrue(importForbidden.add)
    }

    @Test
    fun addIsOfferedForASubscriptionOrAServerKindAndHiddenWhenNeitherIsAllowed() {
        assertTrue(actions(canAddSubscription = false).add)
        assertTrue(actions(canAddServer = false).add)
        assertFalse(actions(canAddSubscription = false, canAddServer = false).add)
    }

    @Test
    fun theAddButtonListsTheSameNonImportEntriesAsThePlusMenu() {
        assertEquals(
            listOf("add-subscription", "add-server"),
            proxyHomeEmptyStateAddMenuIds(canAddSubscription = true, canAddServer = true),
        )
        assertEquals(listOf("add-server"), proxyHomeEmptyStateAddMenuIds(canAddSubscription = false, canAddServer = true))
        assertEquals(emptyList(), proxyHomeEmptyStateAddMenuIds(canAddSubscription = false, canAddServer = false))
    }
}
