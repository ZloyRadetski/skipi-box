// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** olcRTC servers are gone: loading a library that still holds one drops it and the selection on it. */
class DesktopServerLibraryRemovedProtocolTest {
    @Test
    fun loadingDropsOlcRtcRowsAndTheirSelectionButKeepsTheOthers() {
        val keep = DesktopStoredProxyServer(
            id = 1,
            serverJson = VLESS(remarks = "Keep", id = "u", server = "keep.example", port = "443").encodePersistedProxyServer(),
        )
        val gone = DesktopStoredProxyServer(
            id = 2,
            serverJson = """{"protocol":"olcrtc","payload":{"remarks":"Gone"}}""",
        )
        val directory = Files.createTempDirectory("skipi-removed-protocol")
        val path = directory.resolve("servers.json")
        DesktopServerLibraries.save(path, DesktopServerLibrary(selectedServerId = 2, servers = listOf(keep, gone))).getOrThrow()

        val loaded = DesktopServerLibraries.load(path).getOrThrow()

        assertEquals(listOf(1), loaded.servers.map { it.id })
        assertNull(loaded.selectedServerId)
        assertEquals(3, loaded.nextServerId)
    }
}
