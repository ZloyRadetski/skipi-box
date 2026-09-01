// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.decodePersistedProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING

@Serializable
data class DesktopStoredProxyServer(
    val id: Int,
    val serverJson: String,
    /** Null means a manually added server; otherwise this is its subscription group. */
    val subscriptionId: Int? = null,
) {
    fun decode(): Result<ProxyServer<*>> = runCatching(serverJson::decodePersistedProxyServer)
}

@Serializable
data class DesktopServerLibrary(
    val selectedServerId: Int? = null,
    val servers: List<DesktopStoredProxyServer> = emptyList(),
)

/** User-owned local library. Its server payload format is shared with Android backups/storage. */
object DesktopServerLibraries {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun defaultPath(): Path {
        val base = System.getenv("APPDATA")
            ?.takeIf(String::isNotBlank)
            ?: System.getProperty("user.home")
        return Path.of(base, "SKIPI", "servers.json")
    }

    fun loadDefault(): Result<DesktopServerLibrary> = load(defaultPath())

    fun load(path: Path): Result<DesktopServerLibrary> = runCatching {
        if (!Files.exists(path)) return@runCatching DesktopServerLibrary()
        val content = Files.readString(path, StandardCharsets.UTF_8)
        if (content.isBlank()) DesktopServerLibrary() else json.decodeFromString<DesktopServerLibrary>(content).normalized()
    }

    fun saveDefault(library: DesktopServerLibrary): Result<Unit> = save(defaultPath(), library)

    fun save(path: Path, library: DesktopServerLibrary): Result<Unit> = runCatching {
        val normalized = library.normalized()
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(
            temporary,
            json.encodeToString(normalized),
            StandardCharsets.UTF_8,
            CREATE,
            TRUNCATE_EXISTING,
        )
        try {
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, REPLACE_EXISTING)
        }
    }

    fun add(
        library: DesktopServerLibrary,
        server: ProxyServer<*>,
        subscriptionId: Int? = null,
    ): DesktopServerLibrary {
        val nextId = (library.servers.maxOfOrNull(DesktopStoredProxyServer::id) ?: 0) + 1
        return library.copy(
            selectedServerId = nextId,
            servers = library.servers + DesktopStoredProxyServer(
                id = nextId,
                serverJson = server.encodePersistedProxyServer(),
                subscriptionId = subscriptionId,
            ),
        )
    }

    /**
     * Replaces exactly one subscription group, leaving manual servers and the
     * rest of the subscription catalogue untouched. This mirrors Android's
     * subscription update semantics and prevents duplicate servers on refresh.
     */
    fun replaceSubscriptionServers(
        library: DesktopServerLibrary,
        subscriptionId: Int,
        servers: List<ProxyServer<*>>,
    ): DesktopServerLibrary {
        require(subscriptionId > 0) { "Subscription ID must be positive" }
        val previousGroup = library.servers.filter { stored -> stored.subscriptionId == subscriptionId }
        val previousIdsByFingerprint = previousGroup
            .mapNotNull { stored ->
                stored.decode().getOrNull()?.connectionFingerprint()?.let { fingerprint -> fingerprint to stored.id }
            }
            .groupBy({ (fingerprint, _) -> fingerprint }, { (_, id) -> id })
            .mapValues { (_, ids) -> ids.toMutableList() }
            .toMutableMap()
        val remaining = library.servers.filterNot { stored -> stored.subscriptionId == subscriptionId }
        val usedIds = remaining.mapTo(mutableSetOf(), DesktopStoredProxyServer::id)
        var nextId = (library.servers.maxOfOrNull(DesktopStoredProxyServer::id) ?: 0) + 1

        fun nextFreeId(): Int {
            while (nextId in usedIds) nextId += 1
            return nextId++.also(usedIds::add)
        }

        val replacements = servers
            .distinctBy { server -> server.connectionFingerprint() }
            .map { server ->
                val fingerprint = server.connectionFingerprint()
                val retainedIds = previousIdsByFingerprint[fingerprint]
                val retainedId = if (retainedIds.isNullOrEmpty()) null else retainedIds.removeAt(0)
                val id = retainedId?.takeUnless { it in usedIds } ?: nextFreeId()
                usedIds += id
                DesktopStoredProxyServer(
                    id = id,
                    serverJson = server.encodePersistedProxyServer(),
                    subscriptionId = subscriptionId,
                )
            }
        val combined = remaining + replacements
        val selected = library.selectedServerId
            ?.takeIf { selectedId -> combined.any { stored -> stored.id == selectedId } }
            ?: replacements.firstOrNull()?.id
            ?: remaining.firstOrNull()?.id
        return library.copy(selectedServerId = selected, servers = combined).normalized()
    }

    fun removeSubscriptionServers(library: DesktopServerLibrary, subscriptionId: Int): DesktopServerLibrary {
        val remaining = library.servers.filterNot { stored -> stored.subscriptionId == subscriptionId }
        return library.copy(servers = remaining).normalized()
    }

    fun serversForSubscription(library: DesktopServerLibrary, subscriptionId: Int): List<DesktopStoredProxyServer> =
        library.servers.filter { stored -> stored.subscriptionId == subscriptionId }

    fun select(library: DesktopServerLibrary, serverId: Int): DesktopServerLibrary {
        require(library.servers.any { it.id == serverId }) { "Unknown desktop server ID: $serverId" }
        return library.copy(selectedServerId = serverId)
    }

    /** Updates an existing record in place instead of turning an edit into a duplicate server. */
    fun update(
        library: DesktopServerLibrary,
        serverId: Int,
        server: ProxyServer<*>,
    ): DesktopServerLibrary {
        val existing = library.servers.firstOrNull { stored -> stored.id == serverId }
            ?: error("Unknown desktop server ID: $serverId")
        return library.copy(
            servers = library.servers.map { stored ->
                if (stored.id == serverId) {
                    DesktopStoredProxyServer(
                        id = existing.id,
                        serverJson = server.encodePersistedProxyServer(),
                        subscriptionId = existing.subscriptionId,
                    )
                } else {
                    stored
                }
            },
        ).normalized()
    }

    fun remove(library: DesktopServerLibrary, serverId: Int): DesktopServerLibrary {
        return library.copy(
            selectedServerId = library.selectedServerId.takeIf { it != serverId },
            servers = library.servers.filterNot { it.id == serverId },
        ).normalized()
    }

    private fun DesktopServerLibrary.normalized(): DesktopServerLibrary {
        val distinctServers = servers.distinctBy(DesktopStoredProxyServer::id)
        val selected = selectedServerId?.takeIf { selectedId -> distinctServers.any { it.id == selectedId } }
        return copy(selectedServerId = selected, servers = distinctServers)
    }
}
