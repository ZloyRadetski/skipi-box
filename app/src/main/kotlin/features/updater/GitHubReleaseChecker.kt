// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.content.Context
import android.os.Build
import app.ProjectInfo
import engine.network.TunnelNetworks
import features.logs.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private const val LogTag = "GitHubReleaseChecker"

internal class GitHubReleaseChecker(
    private val context: Context? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Backward-compatible one-off check used by the explicit user action. */
    suspend fun checkLatestRelease(channel: AppUpdateChannel = AppUpdateChannel.Stable): AppUpdateInfo? {
        return (checkLatestReleaseResult(channel) as? UpdateCheckResult.Success)?.update
    }

    /**
     * Performs a conditional request when an ETag is available. Callers can
     * distinguish an unchanged release from a failed request, so a temporary
     * network failure never clears a previously discovered update.
     */
    suspend fun checkLatestReleaseResult(
        channel: AppUpdateChannel = AppUpdateChannel.Stable,
        ifNoneMatch: String? = null,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val current = CurrentAppVersion(name = ProjectInfo.VERSION_NAME, code = ProjectInfo.VERSION_CODE)
        runUpdateCheck(
            fetchManifest = { fetchManifest(channel) },
            parseManifest = UpdateManifestParser::parse,
            manifestOffer = { manifest -> UpdateManifestParser.androidOffer(manifest, current, Build.SUPPORTED_ABIS.toList()) },
            fetchReleases = { fetchReleaseList(channel, ifNoneMatch) },
            isReleaseBody = { body -> GitHubReleaseParser.isReleaseBody(body, channel) },
            releaseOffer = { body -> parseRelease(body, channel) },
            onManifestUnavailable = { error ->
                AppLog.warn(LogTag, "Update manifest unavailable, asking the release list: ${error?.message}")
            },
            onReleaseListFailed = { error ->
                AppLog.warn(LogTag, "Error checking GitHub release: ${error?.message ?: "unexpected response"}", error)
            },
        )
    }

    /** The manifest text, or null when the server does not answer 200; the app is excluded from its own VPN. */
    private fun fetchManifest(channel: AppUpdateChannel): String? = TunnelNetworks.withLocalProxyAuthenticator {
        val connection = TunnelNetworks.openHttpConnection(context, URL(channel.manifestUrl)).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SKIPI-App/${ProjectInfo.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader().use(BufferedReader::readText)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchReleaseList(channel: AppUpdateChannel, ifNoneMatch: String?): ReleaseListResponse =
        TunnelNetworks.withLocalProxyAuthenticator {
            // Bind to the tunnel explicitly when it is up; otherwise check via the direct path.
            val connection = TunnelNetworks.openHttpConnection(context, URL(channel.releasesUrl)).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "SKIPI-App/${ProjectInfo.VERSION_NAME}")
                ifNoneMatch?.takeIf(String::isNotBlank)?.let { value ->
                    setRequestProperty("If-None-Match", value)
                }
            }
            try {
                when (val statusCode = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> ReleaseListResponse.Ok(
                        body = connection.inputStream.bufferedReader().use(BufferedReader::readText),
                        eTag = connection.getHeaderField("ETag"),
                    )
                    HttpURLConnection.HTTP_NOT_MODIFIED -> ReleaseListResponse.NotModified
                    else -> {
                        AppLog.warn(LogTag, "GitHub release check failed: HTTP $statusCode")
                        ReleaseListResponse.Failed
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun parseRelease(body: String, channel: AppUpdateChannel): AppUpdateInfo? = GitHubReleaseParser.parse(
        body = body,
        current = CurrentAppVersion(name = ProjectInfo.VERSION_NAME, code = ProjectInfo.VERSION_CODE),
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        channel = channel,
    )
}
