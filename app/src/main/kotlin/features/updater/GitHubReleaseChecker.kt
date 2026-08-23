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
    suspend fun checkLatestRelease(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            TunnelNetworks.withLocalProxyAuthenticator {
                val url = URL(GitHubApiUrl)
                // The app is excluded from its own VPN, so bind to the tunnel
                // explicitly when it is up; otherwise check via the direct path.
                val connection = (TunnelNetworks.openHttpConnection(context, url) as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "SKIPI-App/${ProjectInfo.VERSION_NAME}")
                }

                val statusCode = connection.responseCode
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    AndroidAppLogger.warn(LogTag, "GitHub release check failed: HTTP $statusCode")
                    return@withLocalProxyAuthenticator null
                }

                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val releaseJson = json.parseToJsonElement(responseText).jsonObject
                parseRelease(releaseJson)
            }
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Error checking GitHub release: ${error.message}", error)
        }.getOrNull()
    }

    private fun parseRelease(root: JsonObject): AppUpdateInfo? {
        val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val releaseTitle = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { tagName }
        val changelog = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val assets = root["assets"]?.jsonArray ?: return null

        // tag_name is the single source of truth for the release version.
        val remoteVersionName = tagName.removePrefix("v").trim()
        if (remoteVersionName.isBlank()) return null

        // APK asset names carry no version information ("SKIPI-universal.apk"),
        // so the code is read from the release notes ("Version code: 180").
        val remoteVersionCode = extractVersionCodeFromBody(changelog)

        val bestAsset = selectBestApkAsset(assets) ?: return null
        val downloadUrl = bestAsset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val assetName = bestAsset["name"]?.jsonPrimitive?.contentOrNull ?: "SKIPI.apk"
        val apkSize = bestAsset["size"]?.jsonPrimitive?.longOrNull ?: 0L

        val isNewer = isVersionNewer(
            currentVersionName = ProjectInfo.VERSION_NAME,
            currentVersionCode = ProjectInfo.VERSION_CODE,
            remoteVersionName = remoteVersionName,
            remoteVersionCode = remoteVersionCode,
        )

        if (!isNewer) {
            AndroidAppLogger.debug(LogTag, "Already up-to-date: current=${ProjectInfo.VERSION_NAME} remote=$remoteVersionName")
            return null
        }

        return AppUpdateInfo(
            versionName = remoteVersionName,
            versionCode = remoteVersionCode ?: (ProjectInfo.VERSION_CODE + 1),
            releaseTitle = releaseTitle,
            changelog = changelog,
            downloadUrl = downloadUrl,
            assetName = assetName,
            apkSizeBytes = apkSize,
            publishedAt = publishedAt,
        )
    }

    private fun selectBestApkAsset(assets: JsonArray): JsonObject? {
        val apkAssets = assets.mapNotNull { it as? JsonObject }.filter {
            val name = it["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
            name.endsWith(".apk")
        }
        if (apkAssets.isEmpty()) return null

        val supportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }
        for (abi in supportedAbis) {
            val matched = apkAssets.firstOrNull { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                name.contains(abi)
            }
            if (matched != null) {
                return matched
            }
        }

        return apkAssets.firstOrNull {
            val name = it["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
            name.contains("universal")
        } ?: apkAssets.firstOrNull()
    }

    companion object {
        /**
         * Reads the version code from the release notes, e.g.
         * "- Version code: 180". Asset file names do not contain it.
         */
        internal fun extractVersionCodeFromBody(body: String): Int? {
            return Regex("""version\s*code\s*[:=\-]?\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        fun isVersionNewer(
            currentVersionName: String,
            currentVersionCode: Int,
            remoteVersionName: String,
            remoteVersionCode: Int?,
        ): Boolean {
            if (remoteVersionCode != null && remoteVersionCode > currentVersionCode) {
                return true
            }

            val currentParts = currentVersionName.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
            val remoteParts = remoteVersionName.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(currentParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val c = currentParts.getOrElse(i) { 0 }
                val r = remoteParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }

            return (remoteVersionCode ?: 0) > currentVersionCode
        }
    }
}
