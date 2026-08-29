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

    fun add(library: DesktopServerLibrary, server: ProxyServer<*>): DesktopServerLibrary {
        val nextId = (library.servers.maxOfOrNull(DesktopStoredProxyServer::id) ?: 0) + 1
        return library.copy(
            selectedServerId = nextId,
            servers = library.servers + DesktopStoredProxyServer(
                id = nextId,
                serverJson = server.encodePersistedProxyServer(),
            ),
        )
    }

    fun select(library: DesktopServerLibrary, serverId: Int): DesktopServerLibrary {
        require(library.servers.any { it.id == serverId }) { "Unknown desktop server ID: $serverId" }
        return library.copy(selectedServerId = serverId)
    }

    private fun DesktopServerLibrary.normalized(): DesktopServerLibrary {
        val distinctServers = servers.distinctBy(DesktopStoredProxyServer::id)
        val selected = selectedServerId?.takeIf { selectedId -> distinctServers.any { it.id == selectedId } }
        return copy(selectedServerId = selected, servers = distinctServers)
    }
}
