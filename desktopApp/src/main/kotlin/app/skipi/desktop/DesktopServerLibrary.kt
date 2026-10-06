// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.decodePersistedProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import features.subscription.SubscriptionServerRecord
import features.subscription.SubscriptionServerLibrary
import features.subscription.replaceSubscriptionServerGroup
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

/** Returns the next allocatable positive ID, or [Int.MAX_VALUE] when allocation is exhausted. */
internal fun nextIdAfter(servers: List<DesktopStoredProxyServer>): Int {
    val maxId = servers.maxOfOrNull(DesktopStoredProxyServer::id) ?: 0
    return if (maxId >= Int.MAX_VALUE) Int.MAX_VALUE else maxOf(1, maxId + 1)
}

@Serializable
data class DesktopServerLibrary(
    val selectedServerId: Int? = null,
    val servers: List<DesktopStoredProxyServer> = emptyList(),
    /** High-water mark; a missing field in older JSON is derived from all stored IDs. */
    val nextServerId: Int = nextIdAfter(servers),
)

/** Effective high-water mark, including IDs hidden from shared records because their payloads do not decode. */
internal val DesktopServerLibrary.effectiveNextServerId: Int
    get() = maxOf(nextServerId.coerceAtLeast(1), nextIdAfter(servers))

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
        val nextId = library.effectiveNextServerId
        check(nextId < Int.MAX_VALUE && library.servers.none { it.id == nextId }) {
            "No desktop server IDs remain"
        }
        val servers = library.servers + DesktopStoredProxyServer(
            id = nextId,
            serverJson = server.encodePersistedProxyServer(),
            subscriptionId = subscriptionId,
        )
        return library.copy(
            selectedServerId = nextId,
            servers = servers,
            nextServerId = nextIdAfter(servers),
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
        val nextServerId = library.effectiveNextServerId
        val portable = replaceSubscriptionServerGroup(
            library = SubscriptionServerLibrary(
                selectedServerId = library.selectedServerId,
                servers = library.servers.map { stored ->
                    SubscriptionServerRecord(
                        id = stored.id,
                        subscriptionId = stored.subscriptionId,
                        server = stored.decode().getOrNull(),
                    )
                },
            ),
            subscriptionId = subscriptionId,
            incoming = servers,
            firstNewServerId = nextServerId,
        )
        val updatedServers = portable.servers.map { record ->
            val stored = record.server?.let { server ->
                DesktopStoredProxyServer(
                    id = record.id,
                    serverJson = server.encodePersistedProxyServer(),
                    subscriptionId = record.subscriptionId,
                )
            }
            stored ?: library.servers.first { it.id == record.id }
        }
        return library.copy(
            selectedServerId = portable.selectedServerId,
            servers = updatedServers,
            nextServerId = maxOf(nextServerId, nextIdAfter(updatedServers)),
        ).normalized()
    }

    fun removeSubscriptionServers(library: DesktopServerLibrary, subscriptionId: Int): DesktopServerLibrary {
        val remaining = library.servers.filterNot { stored -> stored.subscriptionId == subscriptionId }
        val selectedWasRemoved = library.selectedServerId?.let { selectedId ->
            library.servers.any { it.id == selectedId && it.subscriptionId == subscriptionId }
        } == true
        return library.copy(
            selectedServerId = if (selectedWasRemoved) remaining.firstOrNull()?.id else library.selectedServerId,
            servers = remaining,
            nextServerId = library.effectiveNextServerId,
        ).normalized()
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
        val remaining = library.servers.filterNot { it.id == serverId }
        return library.copy(
            selectedServerId = if (library.selectedServerId == serverId) remaining.firstOrNull()?.id else library.selectedServerId,
            servers = remaining,
            nextServerId = library.effectiveNextServerId,
        ).normalized()
    }

    private fun DesktopServerLibrary.normalized(): DesktopServerLibrary {
        val distinctServers = servers.distinctBy(DesktopStoredProxyServer::id)
        val selected = selectedServerId?.takeIf { selectedId -> distinctServers.any { it.id == selectedId } }
        return copy(
            selectedServerId = selected,
            servers = distinctServers,
            nextServerId = maxOf(effectiveNextServerId, nextIdAfter(distinctServers)),
        )
    }
}
