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
        return (checkLatestReleaseResult(channel) as? GitHubReleaseCheckResult.Success)?.update
    }

    /**
     * Performs a conditional request when an ETag is available. Callers can
     * distinguish an unchanged release from a failed request, so a temporary
     * network failure never clears a previously discovered update.
     */
    suspend fun checkLatestReleaseResult(
        channel: AppUpdateChannel = AppUpdateChannel.Stable,
        ifNoneMatch: String? = null,
    ): GitHubReleaseCheckResult = withContext(Dispatchers.IO) {
        checkManifest(channel)?.let { return@withContext it }
        runCatching {
            TunnelNetworks.withLocalProxyAuthenticator {
                val url = URL(channel.releasesUrl)
                // The app is excluded from its own VPN, so bind to the tunnel
                // explicitly when it is up; otherwise check via the direct path.
                val connection = TunnelNetworks.openHttpConnection(context, url).apply {
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
                        HttpURLConnection.HTTP_OK -> {
                            val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                            // A malformed body throws and is reported as a failed check, so a
                            // previously discovered update is never cleared by a corrupt response.
                            check(GitHubReleaseParser.isReleaseBody(responseText, channel)) {
                                "Unexpected ${channel.id} release response"
                            }
                            GitHubReleaseCheckResult.Success(
                                update = parseRelease(responseText, channel),
                                eTag = connection.getHeaderField("ETag"),
                            )
                        }

                        HttpURLConnection.HTTP_NOT_MODIFIED -> GitHubReleaseCheckResult.NotModified

                        else -> {
                            AppLog.warn(LogTag, "GitHub release check failed: HTTP $statusCode")
                            GitHubReleaseCheckResult.Failed
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }.onFailure { error ->
            AppLog.warn(LogTag, "Error checking GitHub release: ${error.message}", error)
        }.getOrElse { GitHubReleaseCheckResult.Failed }
    }

    /**
     * The manifest the release workflow publishes at a fixed address: no GitHub API and no rate limit, and it names a
     * release only after all its files are uploaded. Null when it cannot be had or read, so the release list answers.
     */
    private suspend fun checkManifest(channel: AppUpdateChannel): GitHubReleaseCheckResult? = runCatching {
        TunnelNetworks.withLocalProxyAuthenticator {
            val connection = TunnelNetworks.openHttpConnection(context, URL(channel.manifestUrl)).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SKIPI-App/${ProjectInfo.VERSION_NAME}")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withLocalProxyAuthenticator null
                val manifest = UpdateManifestParser.parse(connection.inputStream.bufferedReader().use(BufferedReader::readText))
                    ?: return@withLocalProxyAuthenticator null
                GitHubReleaseCheckResult.Success(
                    update = UpdateManifestParser.androidOffer(
                        manifest,
                        CurrentAppVersion(name = ProjectInfo.VERSION_NAME, code = ProjectInfo.VERSION_CODE),
                        Build.SUPPORTED_ABIS.toList(),
                    ),
                    // Nothing to send back as If-None-Match: the manifest is a few hundred bytes.
                    eTag = null,
                )
            } finally {
                connection.disconnect()
            }
        }
    }.onFailure { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        AppLog.warn(LogTag, "Update manifest unavailable, asking the release list: ${error.message}")
    }.getOrNull()

    private fun parseRelease(body: String, channel: AppUpdateChannel): AppUpdateInfo? = GitHubReleaseParser.parse(
        body = body,
        current = CurrentAppVersion(name = ProjectInfo.VERSION_NAME, code = ProjectInfo.VERSION_CODE),
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        channel = channel,
    )
}

internal sealed interface GitHubReleaseCheckResult {
    data class Success(
        val update: AppUpdateInfo?,
        val eTag: String?,
    ) : GitHubReleaseCheckResult

    data object NotModified : GitHubReleaseCheckResult

    data object Failed : GitHubReleaseCheckResult
}
