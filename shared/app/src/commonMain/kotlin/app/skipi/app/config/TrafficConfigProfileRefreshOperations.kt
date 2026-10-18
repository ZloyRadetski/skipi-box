// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig

/** Selects the source-of-truth policy for the portable fields in one refresh response. */
sealed interface TrafficConfigProfileRefreshMode {
    /** A user-triggered refresh accepts response policy; an explicit unlock overrides its lock. */
    data class Manual(val unlock: Boolean) : TrafficConfigProfileRefreshMode

    /** Background refresh imports portable settings but retains the current scheduling policy. */
    data object Automatic : TrafficConfigProfileRefreshMode
}

/** Result of a user request to refresh one captured profile snapshot. */
sealed interface TrafficConfigProfileRefreshRequest {
    /** The host must show Android's unlock warning and wait for an explicit confirmation. */
    data class ConfirmUnlockAndRefresh(val snapshot: TrafficConfigState) : TrafficConfigProfileRefreshRequest

    /** The host may fetch the captured URL immediately using the requested manual policy. */
    data class Refresh(
        val snapshot: TrafficConfigState,
        val mode: TrafficConfigProfileRefreshMode.Manual,
    ) : TrafficConfigProfileRefreshRequest
}

/** Shared Android-compatible commit policy for one already-fetched profile document. */
object TrafficConfigProfileRefreshOperations {
    /** Mirrors Android's click behavior while leaving the confirmation UI to the host. */
    fun requestManualRefresh(current: TrafficConfigState): TrafficConfigProfileRefreshRequest =
        if (current.updateLocked) {
            TrafficConfigProfileRefreshRequest.ConfirmUnlockAndRefresh(current)
        } else {
            TrafficConfigProfileRefreshRequest.Refresh(
                snapshot = current,
                mode = TrafficConfigProfileRefreshMode.Manual(unlock = false),
            )
        }

    /** Acknowledging Android's warning explicitly requests the unlock override. */
    fun confirmUnlockAndRefresh(
        pending: TrafficConfigProfileRefreshRequest.ConfirmUnlockAndRefresh,
    ): TrafficConfigProfileRefreshRequest.Refresh = TrafficConfigProfileRefreshRequest.Refresh(
        snapshot = pending.snapshot,
        mode = TrafficConfigProfileRefreshMode.Manual(unlock = true),
    )

    /** Dismissing the warning clears pending state without scheduling a fetch. */
    fun dismissUnlockAndRefresh(
        @Suppress("UNUSED_PARAMETER") pending: TrafficConfigProfileRefreshRequest.ConfirmUnlockAndRefresh,
    ): Nothing? = null

    /**
     * Applies a normalized response to the latest same-ID profile snapshot.
     * Network access, diagnostics, persistence, and scheduling stay in the host adapters.
     */
    fun apply(
        current: TrafficConfigState,
        normalizedDocument: String,
        capturedUrl: String,
        timestamp: Long,
        mode: TrafficConfigProfileRefreshMode,
    ): TrafficConfigState {
        val staged = current.copy(
            rawConfig = normalizedDocument,
            sourceUrl = capturedUrl,
            updateLocked = if (mode is TrafficConfigProfileRefreshMode.Manual && mode.unlock) {
                false
            } else {
                current.updateLocked
            },
            lastUpdatedAtMillis = timestamp,
        )
        val parsed = staged.withSkipiSettingsReadFromRawConfig()
        val refreshed = when (mode) {
            is TrafficConfigProfileRefreshMode.Manual -> parsed.copy(
                sourceUrl = capturedUrl.ifBlank { parsed.sourceUrl },
                updateLocked = if (mode.unlock) false else parsed.updateLocked,
            )

            TrafficConfigProfileRefreshMode.Automatic -> parsed.copy(
                sourceUrl = capturedUrl.ifBlank { parsed.sourceUrl },
                updateLocked = current.updateLocked,
                autoUpdate = current.autoUpdate,
                updateInterval = current.updateInterval,
                resourceSettings = parsed.resourceSettings.copy(
                    autoUpdate = current.resourceSettings.autoUpdate,
                    updateInterval = current.resourceSettings.updateInterval,
                ),
            )
        }
        return refreshed.withSkipiSettingsInRawConfig()
    }
}
