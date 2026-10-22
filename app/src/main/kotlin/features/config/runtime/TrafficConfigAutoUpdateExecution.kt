// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig

/** The result of executing the portable part of one automatic profile refresh. */
internal enum class TrafficConfigAutoUpdateExecutionResult {
    Success,
    Retry,
}

/**
 * Runs the fetch, validation, latest-profile commit, and group reconciliation for an automatic
 * refresh. Host callbacks retain transport, persistence, and platform-specific work.
 */
internal suspend fun executeTrafficConfigAutoUpdate(
    capturedProfile: TrafficConfigState?,
    fetch: suspend (TrafficConfigState, String) -> String,
    commit: suspend (Int, String, String) -> Unit,
    reconcile: suspend () -> Unit,
): TrafficConfigAutoUpdateExecutionResult {
    val profile = capturedProfile ?: return TrafficConfigAutoUpdateExecutionResult.Success
    if (!profile.autoUpdate || profile.updateLocked || profile.sourceUrl.isBlank()) {
        return TrafficConfigAutoUpdateExecutionResult.Success
    }

    return runCatching {
        val url = profile.sourceUrl.trim()
        val fetched = fetch(profile, url)
        val normalized = fetched.trimEnd() + "\n"
        val analysis = normalized.analyzeShadowrocketConfig()
        if (analysis.diagnostics.any { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) {
            return@runCatching TrafficConfigAutoUpdateExecutionResult.Retry
        }

        commit(profile.id, normalized, url)
        reconcile()
        TrafficConfigAutoUpdateExecutionResult.Success
    }.getOrElse {
        TrafficConfigAutoUpdateExecutionResult.Retry
    }
}
