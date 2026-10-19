// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.runtime.Stable
import features.config.TrafficConfigCustomResourceFile
import features.proxy.server.model.ProxyServer
import kotlinx.serialization.Serializable

typealias ExpiryReminderUnit = features.subscription.ExpiryReminderUnit
typealias SubscriptionExpiryReminder = features.subscription.SubscriptionExpiryReminder

val DefaultSubscriptionExpiryReminders: List<SubscriptionExpiryReminder> =
    features.subscription.DefaultSubscriptionExpiryReminders

@Stable
data class SubscriptionGroupState(
    val id: Int,
    val name: String,
    val url: String,
    val userAgent: String,
    val updateInterval: String,
    val hwid: String = "",
    val ageSecretKey: String = "",
    val updateViaProxy: Boolean = false,
    val autoOverrideRules: Boolean = true,
    val enabled: Boolean,
    val builtIn: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
    val profileTitle: String = "",
    val announce: String = "",
    val supportUrl: String = "",
    val supportEmail: String = "",
    val profileWebPageUrl: String = "",
    val announceUrl: String = "",
    val trafficUploadBytes: Long = -1L,
    val trafficDownloadBytes: Long = -1L,
    val trafficTotalBytes: Long = -1L,
    val trafficExpireAtSeconds: Long = -1L,
    val notifyOnExpiry: Boolean = true,
    val customExpiryReminders: List<SubscriptionExpiryReminder>? = null,
)

data class ProxyServerState(
    val id: Int,
    val server: ProxyServer<*>,
    val groupId: Int,
    val latency: String = "",
)

const val ProxyServerLatencyTesting = "__TESTING__"

val ProxyServerState.isTestingLatency: Boolean
    get() = latency == ProxyServerLatencyTesting

fun ProxyServerState.proxyServerOutboundTag(): String {
    return id.toString()
}

typealias ResourceFileKind = features.resources.ResourceFileKind

typealias ResourceFileStatus = features.resources.ResourceFileStatus

/** Android source-compatible name for the portable shared resource-file record. */
typealias CustomResourceFileState = TrafficConfigCustomResourceFile

typealias CustomResourceFileStatus = features.resources.CustomResourceFileStatus

typealias ResourceFilesStatus = features.resources.ResourceFilesStatus

typealias ResourceFileUpdateSource = features.resources.ResourceFileUpdateSource

val ResourceFileUpdateSources = features.resources.ResourceFileUpdateSources

fun resourceFileUpdateSourceAt(index: Int): ResourceFileUpdateSource {
    return features.resources.resourceFileUpdateSourceAt(index)
}

fun AppState.resourceFileUpdateSource(): ResourceFileUpdateSource = features.resources.resolveResourceFileUpdateSource(
    source = resourceFileSource,
    customGeoIpUrl = customResourceFileGeoIpUrl,
    customGeoSiteUrl = customResourceFileGeoSiteUrl,
    customGeoIpOnlyCnPrivateUrl = customResourceFileGeoIpOnlyCnPrivateUrl,
    customDirectCidrIpv4Url = customResourceFileDirectCidrIpv4Url,
    customDirectCidrIpv6Url = customResourceFileDirectCidrIpv6Url,
)

fun ResourceFilesStatus.statusOf(kind: ResourceFileKind): ResourceFileStatus {
    return when (kind) {
        ResourceFileKind.GeoIp -> geoIp
        ResourceFileKind.GeoSite -> geoSite
        ResourceFileKind.GeoIpOnlyCnPrivate -> geoIpOnlyCnPrivate
        ResourceFileKind.DirectCidrIpv4 -> directCidrIpv4
        ResourceFileKind.DirectCidrIpv6 -> directCidrIpv6
        ResourceFileKind.XrayCore -> xrayCore
    }
}

fun sanitizeCustomResourceFileName(value: String, fallback: String): String {
    return features.config.sanitizeCustomResourceFileName(value, fallback)
}

fun customResourceFileNameOrNull(value: String): String? {
    if (value.isBlank() || value != value.trim()) return null
    if (value.any { char -> char.isWhitespace() || char == ':' }) return null
    return sanitizeCustomResourceFileName(value, fallback = "")
        .takeIf { sanitized -> sanitized == value }
}
