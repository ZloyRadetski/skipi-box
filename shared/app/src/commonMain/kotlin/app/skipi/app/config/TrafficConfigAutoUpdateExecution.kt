// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig

/** Outcome mapped by a host to its automatic-refresh scheduler result. */
public enum class TrafficConfigAutoUpdateExecutionResult {
    Success,
    Retry,
}

/**
 * Executes the portable part of one automatic traffic-config refresh.
 *
 * The captured profile is checked before any callbacks run. A missing profile, disabled automatic
 * refresh, locked profile, or blank source URL completes with [TrafficConfigAutoUpdateExecutionResult.Success].
 * For an eligible profile, this trims the captured URL, fetches using the captured profile object,
 * trims trailing whitespace from the document and appends one newline, then retries if analysis
 * reports an error diagnostic. Otherwise it invokes [commit] with the captured profile ID, the
 * normalized document, and the trimmed captured URL, in that order, then invokes [reconcile].
 *
 * A normally returning [commit] callback always proceeds to [reconcile], including when the host's
 * latest-state update found no matching profile ID and therefore made no change. Any thrown failure
 * during this execution, including [kotlinx.coroutines.CancellationException], maps to
 * [TrafficConfigAutoUpdateExecutionResult.Retry]. Catching cancellation as Retry intentionally
 * preserves the Android worker's source behavior.
 *
 * @param capturedProfile profile snapshot selected by the host before execution, or `null` if absent
 * @param fetch host fetch callback, called with the captured profile object and trimmed source URL
 * @param commit host callback to apply the profile ID, normalized document, and trimmed captured URL
 * @param reconcile host callback to reconcile proxy groups after a normal commit callback return
 * @return [TrafficConfigAutoUpdateExecutionResult.Success] for a preflight skip or successful run;
 *   [TrafficConfigAutoUpdateExecutionResult.Retry] for an error diagnostic or any thrown failure
 */
public suspend fun executeTrafficConfigAutoUpdate(
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
