// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.repository.ProxyServerRepository
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges the shared proxy catalog to the existing servers.json library.
 *
 * [saveLibrary] owns persistence and its durability semantics. [publishLibrary]
 * updates host memory after a successful save and must not throw; a later
 * publication failure cannot roll back a completed save.
 */
class DesktopProxyServerRepository(
    initialLibrary: DesktopServerLibrary,
    private val readLibrary: () -> DesktopServerLibrary,
    private val saveLibrary: (DesktopServerLibrary) -> Result<Unit>,
    private val publishLibrary: (DesktopServerLibrary) -> Unit,
) : ProxyServerRepository {
    private val lock = Any()
    private val _servers = MutableStateFlow(initialLibrary.toRecords())
    override val servers: StateFlow<List<ProxyServerRecord>> = _servers.asStateFlow()

    override suspend fun updateCatalog(transform: (ProxyServerCatalog) -> ProxyServerCatalog) {
        synchronized(lock) {
            val current = readLibrary()
            commitCatalog(current, transform(current.toCatalog()))
        }
    }

    /**
     * Applies a host-library transform to the latest persisted snapshot under
     * the same lock as shared catalog actions. It commits the exact transformed
     * raw library after validation and high-water normalization, saving before
     * publishing the updated library.
     */
    fun updateLibrary(transform: (DesktopServerLibrary) -> DesktopServerLibrary): DesktopServerLibrary =
        synchronized(lock) {
            val current = readLibrary()
            val transformed = transform(current)
            validateLibrary(transformed)
            val updated = transformed.copy(
                nextServerId = maxOf(
                    current.effectiveNextServerId,
                    transformed.nextServerId,
                    nextIdAfter(transformed.servers),
                ),
            )
            if (updated == current) {
                _servers.value = updated.toRecords()
            } else {
                saveAndPublish(updated)
            }
            updated
        }

    override suspend fun select(serverId: Int) {
        selectAndCommit(serverId)
    }

    /** Synchronous host entry point for the existing Desktop selection callback. */
    fun selectAndCommit(serverId: Int): DesktopServerLibrary = synchronized(lock) {
        val current = readLibrary()
        require(current.servers.any { it.id == serverId }) { "Unknown desktop server ID: $serverId" }
        commitCatalog(
            current,
            current.toCatalog().copy(selectedServerId = serverId),
        )
    }

    override suspend fun upsert(server: ProxyServerRecord) {
        require(server.enabled) { "Disabled proxy servers are not supported by Desktop storage" }
        synchronized(lock) {
            val current = readLibrary()
            val records = current.toRecords()
            val existing = records.any { it.id == server.id }
            val selected = current.selectedServerId?.takeIf { selectedId ->
                current.servers.any { it.id == selectedId }
            }
            val updatedRecords = if (existing) {
                records.map { if (it.id == server.id) server else it }
            } else {
                records + server
            }
            commitCatalog(
                current,
                current.toCatalog().copy(
                    servers = updatedRecords,
                    selectedServerId = selected ?: server.id,
                ),
            )
        }
    }

    override suspend fun remove(serverId: Int) {
        synchronized(lock) {
            val current = readLibrary()
            val records = current.toRecords()
            val remaining = records.filterNot { it.id == serverId }
            val selected = if (current.selectedServerId == serverId) {
                remaining.firstOrNull()?.id ?: NO_SELECTION
            } else {
                current.selectedServerId ?: NO_SELECTION
            }
            commitCatalog(
                current,
                current.toCatalog().copy(servers = remaining, selectedServerId = selected),
            )
        }
    }

    /** Keeps this repository's observable view aligned with subscription/import updates. */
    fun refresh(library: DesktopServerLibrary) {
        synchronized(lock) {
            _servers.value = library.toRecords()
        }
    }

    /** Compatibility bridge while Main's existing selection result handling is migrated. */
    fun persistIfChanged(library: DesktopServerLibrary): Result<Unit> = synchronized(lock) {
        runCatching {
            val current = readLibrary()
            if (current == library) {
                _servers.value = current.toRecords()
            } else {
                commitCatalog(
                    current,
                    ProxyServerCatalog(
                        servers = library.toRecords(),
                        nextServerId = library.nextServerId,
                        selectedServerId = library.selectedServerId ?: NO_SELECTION,
                    ),
                )
            }
        }.map { Unit }
    }

    /**
     * Validates and materializes one complete catalog before writing it. The
     * repository lock serializes its own reads and commits; host-side writers
     * remain responsible for coordinating changes made outside this adapter.
     */
    private fun commitCatalog(
        current: DesktopServerLibrary,
        catalog: ProxyServerCatalog,
    ): DesktopServerLibrary {
        require(catalog.nextServerId > 0) { "Next proxy server ID must be positive" }
        require(catalog.servers.all { it.id > 0 }) { "Proxy server IDs must be positive" }
        require(catalog.servers.map { it.id }.distinct().size == catalog.servers.size) {
            "Proxy server IDs must be unique"
        }
        require(catalog.servers.all(ProxyServerRecord::enabled)) {
            "Disabled proxy servers are not supported by Desktop storage"
        }

        val oldRaw = current.servers.map { stored -> stored to stored.toRecordOrNull() }
        val opaque = oldRaw.filter { (_, record) -> record == null }.map { (stored, _) -> stored }
        val opaqueIds = opaque.mapTo(hashSetOf(), DesktopStoredProxyServer::id)
        require(catalog.servers.none { it.id in opaqueIds }) {
            "Proxy server IDs cannot replace undecodable Desktop records"
        }

        val oldVisibleById = oldRaw.mapNotNull { (stored, record) ->
            record?.let { decoded -> stored.id to (stored to decoded) }
        }.toMap()
        val encodedVisible = catalog.servers.map { record ->
            oldVisibleById[record.id]?.takeIf { (_, oldRecord) -> oldRecord == record }?.first
                ?: DesktopStoredProxyServer(
                    id = record.id,
                    serverJson = record.server.encodePersistedProxyServer(),
                    subscriptionId = record.sourceSubscriptionId,
                )
        }
        var visibleIndex = 0
        val mergedServers = buildList {
            oldRaw.forEach { (stored, oldRecord) ->
                if (oldRecord == null) {
                    add(stored)
                } else if (visibleIndex < encodedVisible.size) {
                    add(encodedVisible[visibleIndex++])
                }
            }
            while (visibleIndex < encodedVisible.size) {
                add(encodedVisible[visibleIndex++])
            }
        }

        val visibleIds = catalog.servers.mapTo(hashSetOf(), ProxyServerRecord::id)
        val rawIds = mergedServers.mapTo(hashSetOf(), DesktopStoredProxyServer::id)
        val selectedServerId = when {
            catalog.selectedServerId == NO_SELECTION -> null
            catalog.selectedServerId in visibleIds -> catalog.selectedServerId
            catalog.selectedServerId in rawIds -> catalog.selectedServerId
            else -> catalog.servers.firstOrNull()?.id
        }
        val updated = current.copy(
            selectedServerId = selectedServerId,
            servers = mergedServers,
            nextServerId = maxOf(
                current.effectiveNextServerId,
                catalog.nextServerId,
                nextIdAfter(mergedServers),
            ),
        )

        if (updated == current) {
            _servers.value = updated.toRecords()
            return updated
        }

        saveAndPublish(updated)
        return updated
    }

    private fun saveAndPublish(library: DesktopServerLibrary) {
        saveLibrary(library).getOrThrow()
        publishLibrary(library)
        _servers.value = library.toRecords()
    }

    private fun validateLibrary(library: DesktopServerLibrary) {
        require(library.nextServerId > 0) { "Next proxy server ID must be positive" }
        require(library.servers.all { it.id > 0 }) { "Proxy server IDs must be positive" }
        require(library.servers.map(DesktopStoredProxyServer::id).distinct().size == library.servers.size) {
            "Proxy server IDs must be unique"
        }
        require(library.selectedServerId == null || library.servers.any { it.id == library.selectedServerId }) {
            "Selected proxy server ID must exist in Desktop storage"
        }
    }

    private fun DesktopServerLibrary.toCatalog(): ProxyServerCatalog {
        val records = toRecords()
        val typedIds = records.mapTo(hashSetOf(), ProxyServerRecord::id)
        val retainedIds = servers.mapTo(hashSetOf(), DesktopStoredProxyServer::id) - typedIds
        return ProxyServerCatalog(
            servers = records,
            nextServerId = effectiveNextServerId,
            selectedServerId = selectedServerId ?: NO_SELECTION,
            retainedServerIds = retainedIds,
        )
    }

    private fun DesktopServerLibrary.toRecords(): List<ProxyServerRecord> =
        servers.mapNotNull { stored -> stored.toRecordOrNull() }

    private fun DesktopStoredProxyServer.toRecordOrNull(): ProxyServerRecord? = decode().getOrNull()?.let { server ->
        ProxyServerRecord(
            id = id,
            server = server,
            sourceSubscriptionId = subscriptionId,
        )
    }

    private companion object {
        const val NO_SELECTION = 0
    }
}
