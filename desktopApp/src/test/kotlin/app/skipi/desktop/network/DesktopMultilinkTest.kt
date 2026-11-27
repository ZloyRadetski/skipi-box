// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.multilink.MultilinkSettings
import features.multilink.MultilinkStatus
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMultilinkTest {
    private class FakeCore(
        var failWith: Throwable? = null,
        var status: String = """{"enabled":true,"merging":true,"links":[{"id":"{eth}","name":"Ethernet 2"}]}""",
    ) {
        val applied = mutableListOf<String>()
        var statusReads = 0

        fun apply(json: String): Result<Unit> = runCatching {
            failWith?.let { throw it }
            applied += json
        }

        fun readStatus(): Result<String> = runCatching {
            statusReads++
            failWith?.let { throw it }
            status
        }
    }

    private fun multilink(core: FakeCore) = DesktopMultilink(apply = core::apply, readStatus = core::readStatus)

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

    @Test
    fun appliesOnlyWhatChanged() {
        val core = FakeCore()
        val multilink = multilink(core)
        multilink.sync(on, tunnelRunning = true).getOrThrow()
        multilink.sync(on, tunnelRunning = true).getOrThrow()
        assertEquals(1, core.applied.size, "the same settings are not sent twice")
        multilink.sync(on.withLimit("{eth}", 10.0), tunnelRunning = true).getOrThrow()
        assertEquals(2, core.applied.size)
    }

    @Test
    fun theTunnelStoppingSwitchesMergingOffInCore() {
        val core = FakeCore()
        val multilink = multilink(core)
        multilink.sync(on, tunnelRunning = true).getOrThrow()
        multilink.sync(on, tunnelRunning = false).getOrThrow()
        assertTrue(""""enabled":true""" in core.applied[0])
        assertTrue(""""enabled":false""" in core.applied[1])
    }

    /** A refused setting is retried with the next change, and the error reaches the caller. */
    @Test
    fun aRefusedSettingIsReportedAndNotRemembered() {
        val core = FakeCore(failWith = IllegalStateException("probe target \"x\" is not an ip:port"))
        val multilink = multilink(core)
        val result = multilink.sync(on.copy(probeTargets = listOf("x")), tunnelRunning = true)
        assertTrue(result.isFailure)
        core.failWith = null
        multilink.sync(on.copy(probeTargets = listOf("x")), tunnelRunning = true).getOrThrow()
        assertEquals(1, core.applied.size, "the refused settings are sent again once Core accepts them")
    }

    /** An older Core library has no link merging: the app says so once instead of failing on every change. */
    @Test
    fun anOlderCoreIsReportedAsUnsupported() {
        val core = FakeCore(failWith = UnsupportedOperationException("This SKIPI Core library has no link merging"))
        val multilink = multilink(core)
        assertTrue(multilink.sync(on, tunnelRunning = true).isFailure)
        assertTrue(multilink.unsupported)
        core.failWith = null
        assertTrue(multilink.sync(on.withLimit("{eth}", 1.0), tunnelRunning = true).isSuccess)
        assertEquals(0, core.applied.size, "nothing more is sent to a Core without the feature")
        assertEquals(MultilinkStatus(), multilink.status())
        assertEquals(0, core.statusReads)
    }

    @Test
    fun statusIsReadFromCore() {
        val core = FakeCore()
        val status = multilink(core).status()
        assertTrue(status.merging)
        assertEquals("Ethernet 2", status.links.single().name)
    }

    @Test
    fun anUnreadableStatusIsOff() {
        val core = FakeCore(failWith = IllegalStateException("boom"))
        assertEquals(MultilinkStatus(), multilink(core).status())
    }
}
