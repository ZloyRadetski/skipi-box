// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.stripLeadingCountryFlag

/**
 * Persists [updated] profiles after a subscription refresh imported an embedded config, then reconciles
 * proxy groups when the profile set changed. [onActiveProfileChanged] runs once the library is saved and
 * before reconciliation, so a failing reconcile still leaves the caller's flag accurate.
 */
internal suspend fun commitRefreshedConfigLibrary(
    original: DesktopConfigLibrary,
    updated: DesktopConfigLibrary,
    saveConfigs: (DesktopConfigLibrary) -> Result<Unit>,
    publishConfigs: (DesktopConfigLibrary) -> Unit,
    readConfigs: () -> DesktopConfigLibrary,
    proxyServerRepository: DesktopProxyServerRepository,
    onActiveProfileChanged: (Boolean) -> Unit,
) {
    val configLibraryChanged = original.configs != updated.configs ||
        original.selectedConfigId != updated.selectedConfigId
    saveConfigs(updated).getOrThrow()
    publishConfigs(updated)
    onActiveProfileChanged(updated.activeProfileChangedFrom(original))
    if (configLibraryChanged) {
        DesktopTrafficConfigProxyGroupReconciler(
            serverRepository = proxyServerRepository,
            readConfigLibrary = readConfigs,
            stripLeadingCountryFlag = { remarks -> remarks.stripLeadingCountryFlag() },
        ).reconcile()
    }
}
