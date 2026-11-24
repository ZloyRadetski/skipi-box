// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import engine.network.AutomaticSubscriptionResourceUrlGuard
import engine.network.SocksProxyEndpoint
import engine.network.SubscriptionHttpRequest
import engine.network.SubscriptionHttpTransport
import android.content.Context
import android.os.Build
import data.AppSettingsPreferences
import features.subscription.KageSubscriptionAgeCrypto
import features.subscription.SubscriptionAgePolicy
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionFetchPolicy
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import features.subscription.SubscriptionAgeCrypto

internal class AndroidSubscriptionFetcher private constructor(
    hwidProvider: () -> String,
    private val ageCrypto: SubscriptionAgeCrypto,
    private val context: Context?,
    private val transport: SubscriptionHttpTransport = SubscriptionHttpTransport(),
) {
    // Resolved on the first fetch, which runs on IO: the Keystore round trip stays off the main thread.
    private val installationHwid: String by lazy(hwidProvider)

    constructor(
        installationHwid: String,
        ageCrypto: SubscriptionAgeCrypto,
        context: Context? = null,
    ) : this({ installationHwid }, ageCrypto, context)

    constructor(context: Context) : this(
        hwidProvider = { AppSettingsPreferences(context.applicationContext).getOrCreateSubscriptionHwid() },
        ageCrypto = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            KageSubscriptionAgeCrypto
        } else {
            UnsupportedSubscriptionAgeCrypto
        },
        context = context,
    )

    suspend fun fetch(
        url: String,
        userAgent: String,
        options: AndroidSubscriptionFetchOptions,
    ): String = fetchResponse(url, userAgent, options).body

    /**
     * [automaticResource] marks a URL a downloaded subscription named (Mihomo provider, linked config): it is
     * refused when it, or a redirect hop, points at a loopback, private or metadata address. The URL the user
     * typed is never checked.
     */
    suspend fun fetchResponse(
        url: String,
        userAgent: String,
        options: AndroidSubscriptionFetchOptions,
        automaticResource: Boolean = false,
    ): SubscriptionFetchResponse = withContext(Dispatchers.IO) {
        val proxy = options.toProxy()
        val requestCredentials = options.toRequestCredentials(
            installationHwid = installationHwid,
            ageCrypto = ageCrypto,
        )
        val response = transport.fetch(
            SubscriptionHttpRequest(
                url = url,
                userAgent = userAgent,
                timeoutMillis = SubscriptionFetchPolicy.timeoutSeconds(options.timeoutSeconds) * 1000,
                identityHeaders = requestCredentials.deviceHeaders,
                agePublicKey = requestCredentials.agePublicKey,
                socksProxy = proxy,
                urlGuard = AutomaticSubscriptionResourceUrlGuard.takeIf { automaticResource },
            ),
        )
        response.copy(body = SubscriptionAgePolicy.decryptResponseBody(response.body, options.ageSecretKey, ageCrypto))
    }
}

internal data class AndroidSubscriptionFetchOptions(
    val useRunningProxy: Boolean = false,
    val ageSecretKey: String = "",
    /** Controls the full set of opt-in device headers for subscription providers. */
    val sendDeviceHeaders: Boolean = true,
    val timeoutSeconds: Int = SubscriptionFetchPolicy.defaultTimeoutSeconds,
)

private data class SubscriptionRequestCredentials(
    val deviceHeaders: Map<String, String>,
    val agePublicKey: String?,
)

internal fun AndroidSubscriptionFetchOptions.toProxy(): SocksProxyEndpoint? {
    if (!useRunningProxy) return null
    val runtimeOptions = LocalProxyRuntime.current() ?: return null
    return SocksProxyEndpoint(
        host = LocalProxyLoopbackAddress,
        port = runtimeOptions.port,
        username = runtimeOptions.username,
        password = runtimeOptions.password,
    )
}

private fun AndroidSubscriptionFetchOptions.toRequestCredentials(
    installationHwid: String,
    ageCrypto: SubscriptionAgeCrypto,
): SubscriptionRequestCredentials {
    val secretKey = ageSecretKey.trim()
    return SubscriptionRequestCredentials(
        deviceHeaders = if (sendDeviceHeaders) {
            subscriptionDeviceHeaders(installationHwid)
        } else {
            emptyMap()
        },
        agePublicKey = SubscriptionAgePolicy.publicKeyHeaderValue(secretKey, ageCrypto),
    )
}

private fun subscriptionDeviceHeaders(installationHwid: String): Map<String, String> {
    return linkedMapOf(
        "x-client" to "SKIPI",
        "x-app-version" to app.ProjectInfo.VERSION_NAME,
        "x-device-os" to "Android",
        "x-ver-os" to Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" },
        "x-device-model" to Build.MODEL.orEmpty().ifBlank { "Android" },
        "x-hwid" to installationHwid,
        "X-Device-ID" to installationHwid,
    )
}
