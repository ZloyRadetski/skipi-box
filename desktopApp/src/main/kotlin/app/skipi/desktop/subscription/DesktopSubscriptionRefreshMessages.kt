// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.subscription.SubscriptionRefreshCommitFailure
import app.skipi.app.subscription.SubscriptionRefreshCommitScope
import app.skipi.app.subscription.SubscriptionRefreshResult
import app.skipi.ui.home.SubscriptionRefreshMessageSummary
import app.skipi.ui.home.formatSubscriptionRefreshMessage
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_subscription_changed_after_servers
import app.skipi.ui.resources.desktop_subscription_changed_during_load
import app.skipi.ui.resources.desktop_subscription_commit_metadata_failed
import app.skipi.ui.resources.desktop_subscription_commit_profile_failed
import app.skipi.ui.resources.desktop_subscription_commit_servers_failed
import app.skipi.ui.resources.desktop_subscription_refreshed

private fun SubscriptionRefreshCommitFailure.userMessage(): String = when (scope) {
    SubscriptionRefreshCommitScope.SERVERS ->
        desktopText(Res.string.desktop_subscription_commit_servers_failed, error.message.orEmpty())
    SubscriptionRefreshCommitScope.SUBSCRIPTION ->
        desktopText(Res.string.desktop_subscription_commit_metadata_failed, error.message.orEmpty())
    SubscriptionRefreshCommitScope.PROFILE ->
        desktopText(Res.string.desktop_subscription_commit_profile_failed, error.message.orEmpty())
}

internal fun desktopSubscriptionRefreshResultMessage(result: SubscriptionRefreshResult): String {
    val commit = result.commit
    val serverWasApplied = SubscriptionRefreshCommitScope.SERVERS in commit.appliedScopes
    val failure = commit.failure
    return when {
        !commit.applicable && serverWasApplied ->
            desktopText(Res.string.desktop_subscription_changed_after_servers, result.loaded.servers.size)
        !commit.applicable ->
            desktopText(Res.string.desktop_subscription_changed_during_load)
        failure != null -> failure.userMessage()
        else -> desktopText(Res.string.desktop_subscription_refreshed, result.loaded.servers.size, result.loaded.urlCount)
    }
}

/** Summary of "refresh all subscriptions"; [singleFailure] is set when the batch committed one subscription. */
internal fun desktopSubscriptionBatchRefreshMessage(
    updatedCount: Int,
    importedServerCount: Int,
    failedCount: Int,
    firstFailure: Throwable?,
    singleFailure: SubscriptionRefreshCommitFailure?,
    noUpdatesMessage: String,
    successTemplate: String,
    failedTemplate: String,
): String = when {
    updatedCount == 0 && failedCount == 0 -> noUpdatesMessage
    singleFailure != null -> singleFailure.userMessage()
    else -> formatSubscriptionRefreshMessage(
        summary = SubscriptionRefreshMessageSummary(
            updatedGroupCount = updatedCount,
            failedGroupCount = failedCount,
            importedServerCount = importedServerCount,
            firstFailureMessage = firstFailure?.let(::desktopSubscriptionRefreshFailureMessage),
        ),
        successTemplate = successTemplate,
        failedTemplate = failedTemplate,
    )
}
