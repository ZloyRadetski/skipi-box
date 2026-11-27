// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.multilink.MultilinkSettings
import features.multilink.MultilinkSync
import features.multilink.parseMultilinkStatus
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Link merging through the real Core library; opt-in like the other native tests (SKIPI_CORE_INTEGRATION_DIR). */
class DesktopMultilinkRealCoreTest {
    @Test
    fun coreSwitchesLinkMergingAndReportsTheLinks() {
        val directory = System.getenv("SKIPI_CORE_INTEGRATION_DIR")?.takeIf(String::isNotBlank) ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(directory)).getOrThrow()
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        try {
            val multilink = MultilinkSync(apply = controller::applyMultilink, readStatus = controller::multilinkStatus)
            multilink.sync(MultilinkSettings(enabled = true), tunnelRunning = true).getOrThrow()
            val on = multilink.status()
            assertTrue(on.enabled, "Core does not report merging on")
            assertTrue(on.links.all { it.id.isNotBlank() && it.name.isNotBlank() }, "a link without id or name: $on")
            println("links: " + on.links.joinToString { "${it.name} ${it.ipv4} ${it.kind}" } + " skipped: " + on.skipped)

            // A refused setting is reported and leaves merging as it was.
            val refused = multilink.sync(MultilinkSettings(enabled = true, probeTargets = listOf("example.com:443")), tunnelRunning = true)
            assertTrue(refused.isFailure, "Core accepted a probe target that is not an IP")
            assertTrue(multilink.status().enabled)

            multilink.sync(MultilinkSettings(enabled = true), tunnelRunning = false).getOrThrow()
            assertFalse(parseMultilinkStatus(controller.multilinkStatus().getOrThrow()).enabled)
        } finally {
            controller.close()
        }
    }
}
