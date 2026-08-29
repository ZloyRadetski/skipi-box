// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionMetadata
import features.subscription.SubscriptionServerImportResult
import features.subscription.importSubscriptionServers
import features.subscription.isValidManualSubscriptionUrl
import features.subscription.subscriptionMetadata
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Desktop HTTP adapter for subscriptions. Parsing and validation stay in the shared core. */
class DesktopSubscriptionFetcher(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(DefaultConnectTimeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    fun fetchAndImport(
        url: String,
        userAgent: String = DefaultDesktopSubscriptionUserAgent,
        timeout: Duration = DefaultRequestTimeout,
    ): DesktopSubscriptionUpdate {
        val response = fetch(url, userAgent, timeout)
        return DesktopSubscriptionUpdate(
            response = response,
            importResult = response.body.importSubscriptionServers(),
            metadata = response.subscriptionMetadata(),
        )
    }

    fun fetch(
        url: String,
        userAgent: String = DefaultDesktopSubscriptionUserAgent,
        timeout: Duration = DefaultRequestTimeout,
    ): SubscriptionFetchResponse {
        require(url.isValidManualSubscriptionUrl()) { "Invalid subscription URL" }
        val request = HttpRequest.newBuilder(URI(url.trim()))
            .timeout(timeout)
            .header("User-Agent", userAgent.ifBlank { DefaultDesktopSubscriptionUserAgent })
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw DesktopSubscriptionHttpException(
                statusCode = response.statusCode(),
                responseBody = response.body(),
            )
        }
        return SubscriptionFetchResponse(
            body = response.body(),
            headers = response.headers().map().mapValues { (_, values) -> values.joinToString(",") },
        )
    }
}

class DesktopSubscriptionHttpException(
    val statusCode: Int,
    val responseBody: String,
) : IllegalStateException("Subscription server returned HTTP $statusCode")

data class DesktopSubscriptionUpdate(
    val response: SubscriptionFetchResponse,
    val importResult: SubscriptionServerImportResult,
    val metadata: SubscriptionMetadata,
)

const val DefaultDesktopSubscriptionUserAgent = "SKIPI Desktop"

private val DefaultConnectTimeout: Duration = Duration.ofSeconds(10)
private val DefaultRequestTimeout: Duration = Duration.ofSeconds(30)
