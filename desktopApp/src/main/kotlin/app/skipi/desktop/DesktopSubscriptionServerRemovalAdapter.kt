// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Removes every persisted row owned by a subscription while preserving unrelated raw rows.
 * If the selected row belongs to that subscription, the host first stops the running proxy while
 * the row still exists. The destructive library update then rebases under the proxy repository's
 * lock so rows added while stopping are preserved.
 */
internal class DesktopSubscriptionServerRemovalAdapter(
    private val readLibrary: () -> DesktopServerLibrary,
    private val proxyServerRepository: DesktopProxyServerRepository,
    private val stopSelectedServer: suspend () -> Unit,
) {
    suspend fun remove(subscriptionId: Int) {
        currentCoroutineContext().ensureActive()
        val beforeStop = readLibrary()
        val selectedServerWillBeRemoved = beforeStop.selectedServerId?.let { selectedId ->
            beforeStop.servers.any { stored ->
                stored.id == selectedId && stored.subscriptionId == subscriptionId
            }
        } == true

        if (selectedServerWillBeRemoved) {
            currentCoroutineContext().ensureActive()
            stopSelectedServer()
        }

        currentCoroutineContext().ensureActive()
        proxyServerRepository.updateLibrary { latest ->
            DesktopServerLibraries.removeSubscriptionServers(latest, subscriptionId)
        }
    }
}
