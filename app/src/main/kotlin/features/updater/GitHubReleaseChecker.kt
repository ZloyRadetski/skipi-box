// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.content.Context
import android.os.Build
import app.ProjectInfo
import engine.network.TunnelNetworks
import features.logs.AndroidAppLogger
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
private const val GitHubApiUrl = "https://api.github.com/repos/ZloyRadetski/skipi-box/releases/latest"

internal class GitHubReleaseChecker(
    private val context: Context? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Backward-compatible one-off check used by the explicit user action. */
    suspend fun checkLatestRelease(): AppUpdateInfo? {
        return (checkLatestReleaseResult() as? GitHubReleaseCheckResult.Success)?.update
    }

    /**
     * Performs a conditional request when an ETag is available. Callers can
     * distinguish an unchanged release from a failed request, so a temporary
     * network failure never clears a previously discovered update.
     */
    suspend fun checkLatestReleaseResult(
        ifNoneMatch: String? = null,
    ): GitHubReleaseCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            TunnelNetworks.withLocalProxyAuthenticator {
                val url = URL(GitHubApiUrl)
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
                            val releaseJson = json.parseToJsonElement(responseText).jsonObject
                            GitHubReleaseCheckResult.Success(
                                update = parseRelease(releaseJson),
                                eTag = connection.getHeaderField("ETag"),
                            )
                        }

                        HttpURLConnection.HTTP_NOT_MODIFIED -> GitHubReleaseCheckResult.NotModified

                        else -> {
                            AndroidAppLogger.warn(LogTag, "GitHub release check failed: HTTP $statusCode")
                            GitHubReleaseCheckResult.Failed
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Error checking GitHub release: ${error.message}", error)
        }.getOrElse { GitHubReleaseCheckResult.Failed }
    }

    private fun parseRelease(root: JsonObject): AppUpdateInfo? = GitHubReleaseParser.parse(
        root = root,
        current = CurrentAppVersion(name = ProjectInfo.VERSION_NAME, code = ProjectInfo.VERSION_CODE),
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
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
