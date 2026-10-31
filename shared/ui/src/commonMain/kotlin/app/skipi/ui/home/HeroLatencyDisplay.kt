// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

/** What the connection panel shows for the selected server's latency. */
enum class HeroLatencyDisplay { Hidden, Value, Failure }

/**
 * A running test adds no placeholder: a long "testing" line pushed the panel around until the first
 * answer. The last known value stays so the chip does not blink out and back; a failure is only shown
 * once no test is running.
 */
fun heroLatencyDisplay(latencyMs: Long?, testing: Boolean, failed: Boolean): HeroLatencyDisplay = when {
    latencyMs != null -> HeroLatencyDisplay.Value
    testing -> HeroLatencyDisplay.Hidden
    failed -> HeroLatencyDisplay.Failure
    else -> HeroLatencyDisplay.Hidden
}
