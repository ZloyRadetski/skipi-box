// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigState
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.LocalProxyXrayConfigOptions

/** Desktop transport binding for traffic-config profile document refreshes. */
internal class DesktopTrafficConfigProfileFetcher(
    private val fetcher: DesktopSubscriptionFetcher,
    private val readSettings: () -> DesktopAppSettings,
    private val activeProxy: () -> LocalProxyXrayConfigOptions?,
) {
    suspend fun fetch(profile: TrafficConfigState, trimmedUrl: String): String {
        val settings = readSettings()
        val runningProxy = activeProxy()
        val proxy = runningProxy?.let { options ->
            DesktopSubscriptionSocksProxy(
                host = "127.0.0.1",
                port = options.socksPort,
                username = options.username,
                password = options.password,
            )
        }
        val deviceHeaders = if (settings.sendDeviceHeaders) {
            DesktopDeviceIdentity.deviceHeaders(settings.installationUuid)
        } else {
            emptyMap()
        }

        return withContext(Dispatchers.IO) {
            fetcher.fetchResponse(
                url = trimmedUrl,
                userAgent = profile.resourceSettings.userAgent,
                timeout = Duration.ofSeconds(settings.subscriptionFetchTimeoutSeconds.toLong()),
                proxy = proxy,
                deviceHeaders = deviceHeaders,
            ).body
        }
    }
}
