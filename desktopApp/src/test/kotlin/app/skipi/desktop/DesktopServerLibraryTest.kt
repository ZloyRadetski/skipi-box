// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import java.nio.file.Files

class DesktopServerLibraryTest {
    @Test
    fun libraryRoundTripsTheSharedServerPayloadAndSelection() {
        val path = Files.createTempFile("skipi-servers-", ".json")
        try {
            val server = ProxyServer.parse(
                "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@example.com:443#Desktop",
            )
            val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)

            DesktopServerLibraries.save(path, library).getOrThrow()
            val restored = DesktopServerLibraries.load(path).getOrThrow()

            assertEquals(1, restored.selectedServerId)
            assertEquals(1, restored.servers.size)
            assertEquals("Desktop", assertIs<VLESS>(restored.servers.single().decode().getOrThrow()).remarks)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
