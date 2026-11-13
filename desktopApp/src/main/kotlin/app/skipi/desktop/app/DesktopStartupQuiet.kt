// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * How long after the first frame the background work of a start waits (the update check, subscription and resource
 * refreshes). Started together with the first composition they shared three cores with it and the JIT, so the window
 * came up late and stuttered; a few seconds later the same work is not felt.
 */
internal const val DesktopStartupQuietMillis = 8_000L

/** Returns once [firstFrame] has been drawn and [quietMillis] more have passed. */
internal suspend fun awaitDesktopStartupQuiet(
    quietMillis: Long = DesktopStartupQuietMillis,
    firstFrame: suspend () -> Unit,
) {
    firstFrame()
    delay(quietMillis)
}

/** False until the start has settled (see [awaitDesktopStartupQuiet]), then true for the rest of the run. */
@Composable
internal fun rememberDesktopStartupSettled(): State<Boolean> {
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        awaitDesktopStartupQuiet { withFrameNanos { } }
        settled.value = true
    }
    return settled
}
