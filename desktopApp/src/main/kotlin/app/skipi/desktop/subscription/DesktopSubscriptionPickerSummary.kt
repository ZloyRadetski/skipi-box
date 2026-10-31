// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.subscription_provider_days_remaining
import app.skipi.ui.resources.subscription_provider_traffic
import app.skipi.ui.resources.subscription_provider_updated
import app.skipi.ui.server.editor.ServerPickerSubscriptionSummary
import app.skipi.ui.subscription.SubscriptionGroupUiState
import app.skipi.ui.text.formatTemplate
import features.subscription.subscriptionExpiryDays
import features.subscription.subscriptionTrafficProgress
import features.subscription.subscriptionTrafficSummary
import java.text.DateFormat
import java.util.Date
import org.jetbrains.compose.resources.stringResource

/** Formats persisted subscription metadata for the shared strategy-member picker. */
@Composable
internal fun SubscriptionGroupUiState.toServerPickerSubscriptionSummary(): ServerPickerSubscriptionSummary {
    val trafficSummary = subscriptionTrafficSummary(
        trafficUploadBytes,
        trafficDownloadBytes,
        trafficTotalBytes,
    )?.let { value ->
        stringResource(Res.string.subscription_provider_traffic).formatTemplate("value" to value)
    }
    val expirySummary = subscriptionExpiryDays(trafficExpireAtSeconds, System.currentTimeMillis())?.let { days ->
        stringResource(Res.string.subscription_provider_days_remaining, days)
    }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val updatedLabel = lastUpdatedAtMillis.takeIf { it > 0L }?.let { timestamp ->
        stringResource(Res.string.subscription_provider_updated)
            .formatTemplate("value" to dateFormat.format(Date(timestamp)))
    }
    return ServerPickerSubscriptionSummary(
        traffic = trafficSummary,
        expiry = expirySummary,
        trafficProgress = subscriptionTrafficProgress(
            trafficUploadBytes,
            trafficDownloadBytes,
            trafficTotalBytes,
        ),
        announce = announce,
        lastUpdated = updatedLabel,
    )
}
