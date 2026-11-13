// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

/** How long a requested exit may take before the process is ended anyway. */
internal const val DesktopExitWatchdogMillis = 10_000L

/**
 * Exit is asked for through the application scope, and that scope only ends when every coroutine in it has ended.
 * One stuck in blocking I/O (a download that is still reading its socket) kept the process alive for minutes with
 * the window already gone, the tray icon dead and the class archive unwritten. The watchdog gives the normal exit
 * [delayMillis] and then ends the process itself; a normal exit still runs the shutdown hooks and writes the archive.
 */
internal fun startDesktopExitWatchdog(
    delayMillis: Long = DesktopExitWatchdogMillis,
    sleep: (Long) -> Unit = Thread::sleep,
    exit: () -> Unit = { Runtime.getRuntime().exit(0) },
): Thread = Thread({
    runCatching { sleep(delayMillis) }
    exit()
}, "skipi-exit-watchdog").apply {
    isDaemon = true
    start()
}
