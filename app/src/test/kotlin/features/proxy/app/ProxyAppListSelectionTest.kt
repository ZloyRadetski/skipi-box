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
