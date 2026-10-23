// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.ProxyAppListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyAppListSelectionTest {

    @Test
    fun editing_installed_apps_keeps_packages_that_are_only_present_in_the_config() {
        val configuredApps = listOf(
            "ru.yota.android",
            "ru.tinkoff.mobile",
            "0:com.example.installed",
        )
        val installedApp = proxyAppListItem("0:com.example.installed")
        val newlySelectedApp = proxyAppListItem("0:com.example.custom")

        val afterAddingApp = updateProxyAppListSelection(
            selectedApps = configuredApps,
            item = newlySelectedApp,
            isChecked = true,
        )
        val afterRemovingInstalledApp = updateProxyAppListSelection(
            selectedApps = afterAddingApp,
            item = installedApp,
            isChecked = false,
        )

        assertEquals(
            listOf(
                "ru.yota.android",
                "ru.tinkoff.mobile",
                "0:com.example.custom",
            ),
            afterRemovingInstalledApp,
        )
    }

    @Test
    fun toggling_shared_uid_updates_every_key_and_keeps_unknown_legacy_ids() {
        val sharedUidItem = ProxyAppListItem(
            key = "0:com.example.first",
            app = AppPackageEntry(
                packageName = "com.example.first",
                userId = 0,
                uid = 42,
            ),
            sharedUid = true,
            selectionKeys = listOf(
                "0:com.example.first",
                "0:com.example.second",
            ),
        )
        val configuredApps = listOf(
            "ru.yota.android",
            "com.example.legacy",
            "0:com.example.first",
        )

        val afterCheckingSharedUid = updateProxyAppListSelection(
            selectedApps = configuredApps,
            item = sharedUidItem,
            isChecked = true,
        )
        val afterUncheckingSharedUid = updateProxyAppListSelection(
            selectedApps = afterCheckingSharedUid,
            item = sharedUidItem,
            isChecked = false,
        )

        assertEquals(
            listOf(
                "ru.yota.android",
                "com.example.legacy",
                "0:com.example.first",
                "0:com.example.second",
            ),
            afterCheckingSharedUid,
        )
        assertEquals(
            listOf("ru.yota.android", "com.example.legacy"),
            afterUncheckingSharedUid,
        )
    }

    private fun proxyAppListItem(selectionKey: String): ProxyAppListItem {
        val packageName = selectionKey.substringAfter(':')
        return ProxyAppListItem(
            key = selectionKey,
            app = AppPackageEntry(packageName = packageName, userId = 0),
            sharedUid = false,
            selectionKeys = listOf(selectionKey),
        )
    }
}
