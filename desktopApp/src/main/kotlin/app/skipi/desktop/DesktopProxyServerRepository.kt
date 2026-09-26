// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.repository.ProxyServerRepository
import app.skipi.desktop.DesktopServerLibraries.remove
import app.skipi.desktop.DesktopServerLibraries.select
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridges the shared proxy catalog to the existing servers.json library. */
class DesktopProxyServerRepository(
    initialLibrary: DesktopServerLibrary,
    private val readLibrary: () -> DesktopServerLibrary,
    private val saveLibrary: (DesktopServerLibrary) -> Result<Unit>,
    private val publishLibrary: (DesktopServerLibrary) -> Unit,
) : ProxyServerRepository {
    private val _servers = MutableStateFlow(initialLibrary.toRecords())
    override val servers: StateFlow<List<ProxyServerRecord>> = _servers.asStateFlow()

    override suspend fun select(serverId: Int) {
        selectAndCommit(serverId)
    }

    /** Synchronous host entry point for the existing Desktop selection callback. */
    fun selectAndCommit(serverId: Int): DesktopServerLibrary {
        val updated = DesktopServerLibraries.select(readLibrary(), serverId)
        commit(updated)
        return updated
    }

    override suspend fun upsert(server: ProxyServerRecord) {
        val library = readLibrary()
        val replacement = DesktopStoredProxyServer(
            id = server.id,
            serverJson = server.server.encodePersistedProxyServer(),
            subscriptionId = server.sourceSubscriptionId,
        )
        val existing = library.servers.any { it.id == server.id }
        val existingSelection = library.selectedServerId?.takeIf { id -> library.servers.any { it.id == id } }
        val updated = library.copy(
            selectedServerId = existingSelection ?: server.id,
            servers = if (existing) {
                library.servers.map { if (it.id == server.id) replacement else it }
            } else {
                library.servers + replacement
            },
        )
        commit(updated)
    }

    override suspend fun remove(serverId: Int) {
        commit(DesktopServerLibraries.remove(readLibrary(), serverId))
    }

    /** Keeps this repository's observable view aligned with subscription/import updates. */
    fun refresh(library: DesktopServerLibrary) {
        _servers.value = library.toRecords()
    }

    /** Compatibility bridge while Main's existing selection result handling is migrated. */
    fun persistIfChanged(library: DesktopServerLibrary): Result<Unit> {
        if (readLibrary() == library) return Result.success(Unit)
        return saveLibrary(library).onSuccess {
            publishLibrary(library)
            refresh(library)
        }
    }

    private fun commit(library: DesktopServerLibrary) {
        saveLibrary(library).getOrThrow()
        publishLibrary(library)
        refresh(library)
    }
}

private fun DesktopServerLibrary.toRecords(): List<ProxyServerRecord> = servers.mapNotNull { stored ->
    stored.decode().getOrNull()?.let { server ->
        ProxyServerRecord(
            id = stored.id,
            server = server,
            sourceSubscriptionId = stored.subscriptionId,
        )
    }
}
