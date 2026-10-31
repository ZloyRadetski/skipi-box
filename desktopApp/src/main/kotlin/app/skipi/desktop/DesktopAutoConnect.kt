// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

/**
 * Android's "connect when the app opens". Desktop waits for onboarding like the rest of the
 * application and needs a server or profile to connect to.
 */
internal fun shouldAutoConnectOnOpen(
    settings: DesktopAppSettings,
    hasSelection: Boolean,
    tunnelRunning: Boolean,
): Boolean = settings.autoConnectOnAppOpen && settings.onboardingCompleted && hasSelection && !tunnelRunning

/** Pauses between automatic attempts; the network or NetworkManager may not be up yet at login. */
internal val AutoConnectRetryDelaysMillis = listOf(3_000L, 5_000L, 10_000L, 20_000L, 30_000L, 60_000L)

/**
 * Connects on open and keeps trying after failures. An unattended start at login often runs before
 * the network is ready, so one failed attempt must not leave the user disconnected. Any action of
 * the user ([intentVersion] changes) or closing the application ends the retries.
 */
internal suspend fun autoConnectWithRetry(
    connect: suspend () -> Unit,
    isRunning: () -> Boolean,
    intentVersion: () -> Long,
    shouldStop: () -> Boolean,
    retryDelaysMillis: List<Long> = AutoConnectRetryDelaysMillis,
    /** Told about each failed attempt (1-based) and the pause that follows it, 0 after the last. */
    onAttemptFailed: (attempt: Int, waitMillis: Long) -> Unit = { _, _ -> },
    pause: suspend (Long) -> Unit = { millis -> kotlinx.coroutines.delay(millis) },
) {
    var retries = 0
    while (true) {
        connect()
        if (isRunning() || shouldStop()) return
        val versionAfterFailure = intentVersion()
        val wait = retryDelaysMillis.getOrNull(retries)
        onAttemptFailed(retries + 1, wait ?: 0L)
        if (wait == null) return
        pause(wait)
        if (shouldStop() || intentVersion() != versionAfterFailure) return
        retries += 1
    }
}
