// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.subscription.SubscriptionRefreshCommitFailure
import app.skipi.app.subscription.SubscriptionRefreshCommitScope
import app.skipi.app.subscription.SubscriptionRefreshResult
import app.skipi.ui.home.SubscriptionRefreshMessageSummary
import app.skipi.ui.home.formatSubscriptionRefreshMessage

private fun SubscriptionRefreshCommitFailure.userMessage(): String = when (scope) {
    SubscriptionRefreshCommitScope.SERVERS ->
        "Список получен, но серверы не сохранены: ${error.message.orEmpty()}"
    SubscriptionRefreshCommitScope.SUBSCRIPTION ->
        "Серверы обновлены, но метаданные подписки не сохранены: ${error.message.orEmpty()}"
    SubscriptionRefreshCommitScope.PROFILE ->
        "Серверы и подписка обновлены, но обновление профиля не завершено: ${error.message.orEmpty()}"
}

internal fun desktopSubscriptionRefreshResultMessage(result: SubscriptionRefreshResult): String {
    val commit = result.commit
    val serverWasApplied = SubscriptionRefreshCommitScope.SERVERS in commit.appliedScopes
    val failure = commit.failure
    return when {
        !commit.applicable && serverWasApplied ->
            "Серверы обновлены (${result.loaded.servers.size}), но подписка была изменена или удалена до сохранения метаданных."
        !commit.applicable ->
            "Подписка была изменена или удалена во время загрузки; ответ не применён."
        failure != null -> failure.userMessage()
        else -> "Подписка обновлена: ${result.loaded.servers.size} серверов из ${result.loaded.urlCount} ссылок."
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
