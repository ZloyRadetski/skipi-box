// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.ui.text.formatTemplate

/** Host-neutral facts used to preserve Android's subscription refresh summary wording. */
data class SubscriptionRefreshMessageSummary(
    val updatedGroupCount: Int,
    val failedGroupCount: Int,
    val importedServerCount: Int,
    val firstFailureMessage: String?,
)

/** Formats the result of fetched updates; hosts remain responsible for translating exceptions. */
fun formatSubscriptionRefreshMessage(
    summary: SubscriptionRefreshMessageSummary,
    successTemplate: String,
    failedTemplate: String,
): String {
    val firstFailureMessage = summary.firstFailureMessage?.takeIf(String::isNotBlank)
    if (summary.failedGroupCount > 0 && summary.updatedGroupCount == 0) {
        if (firstFailureMessage != null) return firstFailureMessage
    } else if (summary.failedGroupCount > 0) {
        val base = failedTemplate.formatTemplate(
            "groupCount" to summary.updatedGroupCount,
            "failedCount" to summary.failedGroupCount,
            "serverCount" to summary.importedServerCount,
        )
        return if (firstFailureMessage == null) base else "$base: $firstFailureMessage"
    }
    return successTemplate.formatTemplate(
        "groupCount" to summary.updatedGroupCount,
        "failedCount" to summary.failedGroupCount,
        "serverCount" to summary.importedServerCount,
    )
}
