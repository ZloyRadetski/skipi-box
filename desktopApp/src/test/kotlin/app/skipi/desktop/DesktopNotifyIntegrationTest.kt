// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlin.test.Test

/**
 * Shows one real desktop notification through the packaged Core. Opt-in, because it is visible:
 * SKIPI_CORE_INTEGRATION_DIR and SKIPI_NOTIFY_INTEGRATION=1.
 */
class DesktopNotifyIntegrationTest {
    @Test
    fun theSessionsNotificationServiceAcceptsANotification() {
        val directory = System.getenv("SKIPI_CORE_INTEGRATION_DIR")?.takeIf(String::isNotBlank) ?: return
        if (System.getenv("SKIPI_NOTIFY_INTEGRATION") != "1") return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(directory)).getOrThrow()
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })

        val native = controller.nativeForTun().getOrThrow()
        // The second one comes after the first has expired: the case that once showed nothing.
        native.notify("SKIPI", "Проверка 1 из 2")
        Thread.sleep(6_000)
        native.notify("SKIPI", "Проверка 2 из 2")
    }
}
