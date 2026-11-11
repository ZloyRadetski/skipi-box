// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import engine.network.withAuthenticator
import engine.network.SocksProxyEndpoint
import engine.network.readUtf8Limited
import engine.network.setEmbeddedBasicAuth
import engine.network.toIdnUrl
import engine.network.withSocksProxyAuthenticator
import features.subscription.SubscriptionRedirectDecision
import features.subscription.decideSubscriptionRedirect
import android.content.Context
import android.os.Build
import data.AppSettingsPreferences
import features.subscription.KageSubscriptionAgeCrypto
import features.subscription.SubscriptionAgePolicy
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionFetchPolicy
import features.subscription.normalizeSkipiUserAgent
import features.subscription.SubscriptionHttpException
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import features.subscription.SubscriptionAgeCrypto

internal class AndroidSubscriptionFetcher(
    private val installationHwid: String,
    private val ageCrypto: SubscriptionAgeCrypto,
    private val context: Context? = null,
) {
    constructor(context: Context) : this(
        installationHwid = AppSettingsPreferences(context.applicationContext).getOrCreateSubscriptionHwid(),
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

    suspend fun fetchResponse(
        url: String,
        userAgent: String,
        options: AndroidSubscriptionFetchOptions,
    ): SubscriptionFetchResponse = withContext(Dispatchers.IO) {
        val proxy = options.toProxy()
        val requestCredentials = options.toRequestCredentials(
            installationHwid = installationHwid,
            ageCrypto = ageCrypto,
        )
        proxy.withAuthenticator {
            fetchWithRedirects(
                url = url.toIdnUrl(),
                userAgent = normalizeSkipiUserAgent(userAgent),
                requestCredentials = requestCredentials,
                proxy = proxy,
                timeoutSeconds = options.timeoutSeconds,
            )
        }.let { response ->
            response.copy(body = SubscriptionAgePolicy.decryptResponseBody(response.body, options.ageSecretKey, ageCrypto))
        }
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

private fun fetchWithRedirects(
    url: String,
    userAgent: String,
    requestCredentials: SubscriptionRequestCredentials,
    proxy: SocksProxyEndpoint?,
    timeoutSeconds: Int,
): SubscriptionFetchResponse {
    var currentUrl = url
    var sendIdentity = true
    val timeoutMillis = SubscriptionFetchPolicy.timeoutSeconds(timeoutSeconds) * 1000
    repeat(SubscriptionFetchPolicy.maxRedirects + 1) {
        val connection = currentUrl.toConnection(proxy, timeoutMillis)
        try {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Connection", "close")
            if (sendIdentity) {
                requestCredentials.deviceHeaders.forEach { (name, value) ->
                    connection.setRequestProperty(name, value)
                }
                requestCredentials.agePublicKey?.let { publicKey ->
                    connection.setRequestProperty("X-Age-Public-Key", publicKey)
                }
                connection.setEmbeddedBasicAuth(currentUrl)
            }

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: error("Redirect location missing")
                val target = URI(currentUrl).resolve(location).toString()
                when (val decision = decideSubscriptionRedirect(currentUrl, target)) {
                    SubscriptionRedirectDecision.Follow -> Unit
                    SubscriptionRedirectDecision.FollowWithoutIdentity -> sendIdentity = false
                    is SubscriptionRedirectDecision.Reject -> error("Subscription redirect refused: ${decision.reason}")
                }
                currentUrl = target
                return@repeat
            }
            if (code !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.use { input -> input.readTextLimited(MaxSubscriptionErrorBodyBytes) }
                }.getOrNull()
                val serverMessage = parseSubscriptionHttpErrorMessage(errorBody)
                throw SubscriptionHttpException(
                    statusCode = code,
                    serverMessage = serverMessage,
                    responseBody = errorBody,
                )
            }
            val body = connection.inputStream.use { input -> input.readTextLimited(MaxSubscriptionBodyBytes) }
            val headers = connection.headerFields
                .filterKeys { name -> name != null }
                .mapNotNull { (name, values) ->
                    name?.lowercase()?.let { normalizedName ->
                        normalizedName to values.orEmpty().joinToString(",")
                    }
                }
                .toMap()
            return SubscriptionFetchResponse(body = body, headers = headers)
        } finally {
            connection.disconnect()
        }
    }
    error("Too many redirects")
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

private fun String.toConnection(
    proxy: SocksProxyEndpoint?,
    timeoutMillis: Int,
): HttpURLConnection {
    val url = URI(this).toURL()
    val connection = when {
        proxy != null -> url.openConnection(proxy.toJavaProxy())
        else -> url.openConnection()
    }
    return (connection as HttpURLConnection).apply {
        connectTimeout = timeoutMillis
        readTimeout = timeoutMillis
        instanceFollowRedirects = false
        requestMethod = "GET"
    }
}

internal fun parseSubscriptionHttpErrorMessage(errorBody: String?): String? {
    if (errorBody.isNullOrBlank()) return null
    val trimmed = errorBody.trim()

    // 1. Try parsing JSON format
    val jsonMessage = runCatching {
        val json = org.json.JSONObject(trimmed)
        val keys = listOf("message", "error", "detail", "msg", "description", "title", "reason", "error_description")
        for (key in keys) {
            if (json.has(key)) {
                val value = json.opt(key)
                if (value is String && value.isNotBlank()) return@runCatching value.trim()
                if (value is org.json.JSONObject) {
                    val innerMsg = value.optString("message").ifBlank { value.optString("detail") }
                    if (innerMsg.isNotBlank()) return@runCatching innerMsg.trim()
                }
            }
        }
        null
    }.getOrNull()

    if (!jsonMessage.isNullOrBlank()) {
        return jsonMessage.take(200)
    }

    // 2. Check if it's an HTML document
    if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
        val titleMatch = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(trimmed)
        val title = titleMatch?.groupValues?.getOrNull(1)?.trim()
        if (!title.isNullOrBlank()) {
            return title.take(100)
        }
        return null
    }

    // 3. Plain text message
    if (trimmed.length <= 250 && !trimmed.contains("<html", ignoreCase = true)) {
        return trimmed.lines().firstOrNull { it.isNotBlank() }?.trim()?.take(150)
    }

    return null
}

private const val MaxSubscriptionBodyBytes = 32 * 1024 * 1024
private const val MaxSubscriptionErrorBodyBytes = 64 * 1024

/** Reads UTF-8 text, failing early instead of exhausting memory on a hostile or broken endpoint. */
internal fun java.io.InputStream.readTextLimited(maxBytes: Int): String =
    readUtf8Limited(maxBytes) { IllegalStateException("Subscription response is larger than $maxBytes bytes") }
