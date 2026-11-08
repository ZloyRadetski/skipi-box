// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

/**
 * The build starts the packaged app once with `-Dskipi.exitAfterMillis=N` so the JVM can record the
 * classes a start loads into a class-data-sharing archive, which it writes when the process exits
 * normally. Only that run sets the property; an ordinary start never exits on its own.
 */
internal fun trainingExitDelayMillis(property: String?): Long? =
    property?.toLongOrNull()?.takeIf { delay -> delay > 0 }

/** Ends the process after the training delay, when the build asked for one. */
internal fun scheduleTrainingExit(property: String? = System.getProperty("skipi.exitAfterMillis")) {
    val delay = trainingExitDelayMillis(property) ?: return
    Thread {
        Thread.sleep(delay)
        System.exit(0)
    }.apply { isDaemon = true }.start()
}
