// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.multilink.MultilinkSettings
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopMultilinkSettingsTest {
    private val on = MultilinkSettings(enabled = true)

    @Test
    fun settingsStartOffAndOlderFilesLoadAsOff() {
        assertEquals(MultilinkSettings(), DesktopAppSettings().multilink)
        val file = Files.createTempFile("skipi-settings", ".json")
        try {
            file.writeText("""{"enableTunMode":true}""")
            val loaded = DesktopSettingsLibraries.load(file).getOrThrow()
            assertEquals(MultilinkSettings(), loaded.multilink)
            DesktopSettingsLibraries.save(file, loaded.copy(multilink = on.withLimit("{eth}", 5.0))).getOrThrow()
            assertEquals(on.withLimit("{eth}", 5.0), DesktopSettingsLibraries.load(file).getOrThrow().multilink)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    /** Merging is switched in Core while the tunnel runs; nothing of the tunnel is rebuilt for it. */
    @Test
    fun changingLinkMergingNeverReconnects() {
        val base = DesktopAppSettings()
        val changed = base.copy(multilink = on.withLinkUsed("{wifi}", used = false).withLimit("{eth}", 10.0))
        assertFalse(desktopSettingsNeedReconnect(base, changed))
    }
}
