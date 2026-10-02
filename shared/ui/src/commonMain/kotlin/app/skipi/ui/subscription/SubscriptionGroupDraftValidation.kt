// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.subscription

import features.subscription.isPlainHttpSubscriptionUrl
import features.subscription.isValidManualSubscriptionUrl
import features.subscription.isValidSubscriptionIntervalInput

enum class SubscriptionGroupDraftIssue {
    InvalidInterval,
    InvalidUrl,
    InsecureHttpConfirmationRequired,
}

/** Returns the first validation issue using the same order as the group editor. */
fun validateSubscriptionGroupDraft(
    url: String,
    updateInterval: String,
    allowInsecureHttp: Boolean = false,
): SubscriptionGroupDraftIssue? = when {
    !isValidSubscriptionIntervalInput(updateInterval) -> SubscriptionGroupDraftIssue.InvalidInterval
    url.isNotBlank() && !url.isValidManualSubscriptionUrl() -> SubscriptionGroupDraftIssue.InvalidUrl
    !allowInsecureHttp && url.isPlainHttpSubscriptionUrl() -> SubscriptionGroupDraftIssue.InsecureHttpConfirmationRequired
    else -> null
}
