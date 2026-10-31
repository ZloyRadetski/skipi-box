// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import app.ProxyServerState
import features.backup.AppBackupProxyServer
import features.proxy.server.model.VLESS
import features.proxy.server.model.toPersistedProxyServer
import features.subscription.DefaultSubscriptionGroupId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * olcRTC servers no longer exist. A backup that still carries one must restore everything else
 * instead of failing as a whole.
 */
class RemovedProtocolBackupTest {
    @Test
    fun aBackupWithAnOlcRtcServerRestoresTheOtherServers() {
        val vless = ProxyServerState(
            id = 1,
            groupId = DefaultSubscriptionGroupId,
            server = VLESS(remarks = "Keep", id = "u", server = "keep.example", port = "443"),
        )
        val backup = AppState(proxyServers = listOf(vless), selectedProxyServerId = 1)
            .toAppBackupFile(createdAtMillis = 0L, appVersionName = "1.0", appVersionCode = 1)
        val withRemoved = backup.copy(
            data = backup.data.copy(
                proxyServers = backup.data.proxyServers + AppBackupProxyServer(
                    id = 2,
                    groupId = DefaultSubscriptionGroupId,
                    protocol = "olcrtc",
                    payload = JsonObject(mapOf("remarks" to JsonPrimitive("Gone"))),
                ),
            ),
        )

        val restored = withRemoved.toRestorePreview().restoredState

        assertEquals(listOf(1), restored.proxyServers.map { it.id })
        assertEquals("Keep", restored.proxyServers.single().server.getInfo().remarks)
        assertEquals(1, restored.selectedProxyServerId)
    }
}
