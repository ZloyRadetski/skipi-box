// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

/**
 * Platform-neutral optimistic-concurrency decisions used by refresh committers.
 * The values are deliberately generic: persistence adapters can keep their
 * existing JSON models while sharing the conflict policy.
 */
fun <T> refreshTargetWasChanged(baseline: T?, latest: T?): Boolean = baseline != latest

fun <T> subscriptionServerGroupWasChanged(
    baseline: List<T>,
    latest: List<T>,
): Boolean = baseline != latest

data class EmbeddedProfileRefreshSnapshot(
    val sourceUrl: String,
    val updateLocked: Boolean,
)

enum class EmbeddedProfileRefreshDecision {
    APPLY,
    CONFLICT,
    LOCKED,
}

fun decideEmbeddedProfileRefresh(
    baseline: EmbeddedProfileRefreshSnapshot?,
    latest: EmbeddedProfileRefreshSnapshot?,
): EmbeddedProfileRefreshDecision = when {
    latest != baseline -> EmbeddedProfileRefreshDecision.CONFLICT
    latest?.updateLocked == true -> EmbeddedProfileRefreshDecision.LOCKED
    else -> EmbeddedProfileRefreshDecision.APPLY
}
