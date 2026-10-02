// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionRecord
import features.subscription.SubscriptionMetadata

/** Applies response metadata to a subscription without depending on host state models. */
fun SubscriptionRecord.withRefreshedMetadata(
    response: SubscriptionMetadata,
    refreshedAtMillis: Long,
): SubscriptionRecord {
    val previous = metadata ?: SubscriptionMetadata()
    val merged = previous.copy(
        profileTitle = response.profileTitle ?: previous.profileTitle,
        profileDescription = response.profileDescription ?: previous.profileDescription,
        announce = response.announce ?: previous.announce,
        supportUrl = response.supportUrl ?: previous.supportUrl,
        supportEmail = response.supportEmail ?: previous.supportEmail,
        profileWebPageUrl = response.profileWebPageUrl ?: previous.profileWebPageUrl,
        announceUrl = response.announceUrl ?: previous.announceUrl,
        userInfoReceived = response.userInfoReceived,
        trafficUploadBytes = if (response.userInfoReceived) response.trafficUploadBytes else previous.trafficUploadBytes,
        trafficDownloadBytes = if (response.userInfoReceived) response.trafficDownloadBytes else previous.trafficDownloadBytes,
        trafficTotalBytes = if (response.userInfoReceived) response.trafficTotalBytes else previous.trafficTotalBytes,
        trafficExpireAtSeconds = if (response.userInfoReceived) response.trafficExpireAtSeconds else previous.trafficExpireAtSeconds,
        profileUpdateIntervalHours = response.profileUpdateIntervalHours ?: previous.profileUpdateIntervalHours,
        embeddedConfig = response.embeddedConfig ?: previous.embeddedConfig,
    )
    return copy(
        title = response.profileTitle?.takeIf(String::isNotBlank) ?: title,
        metadata = merged,
        lastUpdatedAtMillis = refreshedAtMillis,
    )
}
