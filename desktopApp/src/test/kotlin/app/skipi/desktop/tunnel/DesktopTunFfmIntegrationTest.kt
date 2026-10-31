// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Opt-in checks against the real native libraries (SKIPI_CORE_INTEGRATION_DIR). They only read
 * system state: no interface is created and no route is changed.
 */
class DesktopTunFfmIntegrationTest {
    private fun runtimeOrNull(): DesktopCoreRuntime? = System.getenv("SKIPI_CORE_INTEGRATION_DIR")
        ?.takeIf(String::isNotBlank)
        ?.let { DesktopCoreRuntimes.fromDirectory(Path.of(it)).getOrThrow() }

    @Test
    fun theBundledRuntimeIncludesTheHevLibrary() {
        val runtime = runtimeOrNull() ?: return

        assertNotNull(runtime.hevLibrary, "libhev-socks5-tunnel.so is not in the runtime directory")
    }

    @Test
    fun hevLoadsThroughFfmAndIsIdleUntilStarted() {
        val hev = runtimeOrNull()?.hevLibrary ?: return

        val tunnel = DesktopHevFfmTunnel(hev)

        assertFalse(tunnel.isRunning())
        assertTrue(tunnel.stop().isSuccess)
    }

    @Test
    fun hevRejectsAnInvalidDescriptorInsteadOfStartingSilently() {
        val hev = runtimeOrNull()?.hevLibrary ?: return

        val result = DesktopHevFfmTunnel(hev).start("not: [valid", tunFd = -1)

        assertTrue(result.isFailure)
    }

    @Test
    fun theCoreLibraryExportsTheSocketBindingEntryPoint() {
        val runtime = runtimeOrNull() ?: return
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })

        // An invalid name is rejected by the Core itself, before anything is bound: the call
        // reaching that check proves the symbol resolves in the packaged library.
        val failure = runCatching { controller.nativeForTun().getOrThrow().prepareTun("a/b") }.exceptionOrNull()

        assertTrue("invalid interface name" in failure?.message.orEmpty(), "got: ${failure?.message}")
    }

    @Test
    fun theCoreProbeReportsWhetherNetworkManagerWouldAllowTun() {
        val runtime = runtimeOrNull() ?: return
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })

        val reason = controller.nativeForTun().getOrThrow().tunUnavailableReason()

        // On a machine with NetworkManager granting the user network-control this is null;
        // elsewhere it is an explanation. Either way the call returns without side effects.
        println("TUN availability on this machine: ${reason ?: "available"}")
        assertEquals(reason, DesktopTunHost.forCore(controller) { Result.success(runtime) }.unavailableReason())
        assertTrue(reason == null || reason.isNotBlank())
        assertNull(controller.state().sessionId)
    }
}
