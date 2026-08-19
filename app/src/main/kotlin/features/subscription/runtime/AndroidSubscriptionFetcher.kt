// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import android.content.Context
import android.os.Build
import data.AppSettingsPreferences
import features.subscription.DefaultSubscriptionUserAgent
import features.subscription.SubscriptionHttpException
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.encodeBase64
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.IDN
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URI
import java.net.URL

internal class AndroidSubscriptionFetcher(
    private val installationHwid: String,
    private val ageCrypto: SubscriptionAgeCrypto,
) {
    constructor(context: Context) : this(
        installationHwid = AppSettingsPreferences(context.applicationContext).getOrCreateSubscriptionHwid(),
        ageCrypto = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            KageSubscriptionAgeCrypto
        } else {
            UnsupportedSubscriptionAgeCrypto
        },
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
                userAgent = userAgent.ifBlank { DefaultSubscriptionUserAgent },
                requestCredentials = requestCredentials,
                proxy = proxy,
                timeoutSeconds = options.timeoutSeconds,
            )
        }.let { response ->
            response.copy(
                body = response.body.decryptAgeArmoredOrPassThrough(
                    secretKey = options.ageSecretKey,
                    ageCrypto = ageCrypto,
                ),
            )
        }
    }
}

internal data class SubscriptionFetchResponse(
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

internal data class AndroidSubscriptionFetchOptions(
    val useRunningProxy: Boolean = false,
    val ageSecretKey: String = "",
    /** Controls the full set of opt-in device headers for subscription providers. */
    val sendDeviceHeaders: Boolean = true,
    val timeoutSeconds: Int = 30,
)

private data class SubscriptionRequestCredentials(
    val deviceHeaders: Map<String, String>,
    val agePublicKey: String?,
)

internal data class AndroidSubscriptionProxy(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
)

private const val MaxRedirects = 3
private val ProxyAuthenticatorLock = Any()

internal fun AndroidSubscriptionFetchOptions.toProxy(): AndroidSubscriptionProxy? {
    if (!useRunningProxy) return null
    val runtimeOptions = LocalProxyRuntime.current() ?: return null
    return AndroidSubscriptionProxy(
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
    proxy: AndroidSubscriptionProxy?,
    timeoutSeconds: Int,
): SubscriptionFetchResponse {
    var currentUrl = url
    val timeoutMillis = timeoutSeconds.coerceIn(3, 600) * 1000
    repeat(MaxRedirects) {
        val connection = currentUrl.toConnection(proxy, timeoutMillis)
        try {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Connection", "close")
            requestCredentials.deviceHeaders.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            requestCredentials.agePublicKey?.let { publicKey ->
                connection.setRequestProperty("X-Age-Public-Key", publicKey)
            }
            connection.setEmbeddedBasicAuth(currentUrl)

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: error("Redirect location missing")
                currentUrl = URI(currentUrl).resolve(location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                val serverMessage = parseSubscriptionHttpErrorMessage(errorBody)
                throw SubscriptionHttpException(
                    statusCode = code,
                    serverMessage = serverMessage,
                    responseBody = errorBody,
                )
            }
            val body = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
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
        agePublicKey = secretKey.takeIf(String::isNotEmpty)?.let(ageCrypto::publicKey),
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

private fun String.decryptAgeArmoredOrPassThrough(
    secretKey: String,
    ageCrypto: SubscriptionAgeCrypto,
): String {
    if (!trimStart().startsWith(AgeArmorHeader)) return this
    val normalizedSecretKey = secretKey.trim()
    require(normalizedSecretKey.isNotEmpty()) {
        "Age-encrypted subscription requires a secret key"
    }
    return ageCrypto.decryptArmored(this, normalizedSecretKey)
}

private fun String.toConnection(proxy: AndroidSubscriptionProxy?, timeoutMillis: Int): HttpURLConnection {
    val connection = if (proxy == null) {
        URI(this).toURL().openConnection()
    } else {
        URI(this).toURL().openConnection(proxy.toJavaProxy())
    }
    return (connection as HttpURLConnection).apply {
        connectTimeout = timeoutMillis
        readTimeout = timeoutMillis
        instanceFollowRedirects = false
        requestMethod = "GET"
    }
}

private fun AndroidSubscriptionProxy.toJavaProxy(): Proxy {
    return Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
}

private inline fun <T> AndroidSubscriptionProxy?.withAuthenticator(block: () -> T): T {
    if (this == null || username.isBlank()) return block()
    synchronized(ProxyAuthenticatorLock) {
        Authenticator.setDefault(toAuthenticator())
        return try {
            block()
        } finally {
            Authenticator.setDefault(null)
        }
    }
}

private fun AndroidSubscriptionProxy.toAuthenticator(): Authenticator {
    return object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            if (requestingHost != host || requestingPort != port) return null
            return PasswordAuthentication(username, password.toCharArray())
        }
    }
}

private fun HttpURLConnection.setEmbeddedBasicAuth(rawUrl: String) {
    val userInfo = runCatching { URL(rawUrl).userInfo }.getOrNull() ?: return
    val parts = userInfo.split(":", limit = 2)
    val user = parts.getOrElse(0) { "" }
    val password = parts.getOrElse(1) { "" }
    val token = "$user:$password".encodeBase64()
    setRequestProperty("Authorization", "Basic $token")
}

private fun String.toIdnUrl(): String {
    val parsedUrl = URL(this)
    val host = parsedUrl.host
    val asciiHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)
    return if (host == asciiHost) this else replace(host, asciiHost)
}

private const val AgeArmorHeader = "-----BEGIN AGE ENCRYPTED FILE-----"

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
