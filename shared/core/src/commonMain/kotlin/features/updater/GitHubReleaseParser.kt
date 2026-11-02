// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Turns a GitHub "latest release" response into an update offer for the running build. */
object GitHubReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Null when the body is not a release (a list of releases for [AppUpdateChannel.Nightly]), has no
     * usable APK, or is not newer than [current]. The nightly list offers its newest usable release.
     */
    fun parse(
        body: String,
        current: CurrentAppVersion,
        supportedAbis: List<String>,
        channel: AppUpdateChannel = AppUpdateChannel.Stable,
    ): AppUpdateInfo? = parseChannel(body, channel) { root -> parse(root, current, supportedAbis) }

    fun parse(root: JsonObject, current: CurrentAppVersion, supportedAbis: List<String>): AppUpdateInfo? {
        val tagName = root.string("tag_name").trim()
        val releaseTitle = root.string("name").ifBlank { tagName }
        val changelog = root.string("body").trim()
        val publishedAt = root.string("published_at")
        val assets = root["assets"] as? JsonArray ?: return null

        // tag_name is the single source of truth for the release version.
        val remoteVersionName = tagName.removePrefix("v").trim()
        if (remoteVersionName.isBlank()) return null

        // APK asset names carry no version information ("SKIPI-universal.apk"), so the code is
        // read from the release notes ("Version code: 180").
        val remoteVersionCode = AppReleaseVersions.extractVersionCodeFromBody(changelog)

        val bestAsset = selectBestApkAsset(assets, supportedAbis) ?: return null
        val downloadUrl = bestAsset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val assetName = bestAsset["name"]?.jsonPrimitive?.contentOrNull ?: "SKIPI.apk"
        val apkSize = bestAsset["size"]?.jsonPrimitive?.longOrNull ?: 0L
        val assetSha256 = bestAsset["digest"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { digest -> digest.startsWith("sha256:", ignoreCase = true) }

        val isNewer = AppReleaseVersions.isVersionNewer(
            currentVersionName = current.name,
            currentVersionCode = current.code,
            remoteVersionName = remoteVersionName,
            remoteVersionCode = remoteVersionCode,
        )
        if (!isNewer) return null

        return AppUpdateInfo(
            versionName = remoteVersionName,
            versionCode = remoteVersionCode ?: (current.code + 1),
            releaseTitle = releaseTitle,
            changelog = changelog,
            downloadUrl = downloadUrl,
            assetName = assetName,
            apkSizeBytes = apkSize,
            publishedAt = publishedAt,
            assetSha256 = assetSha256,
        )
    }

    /**
     * Whether [body] has the shape GitHub answers with for [channel]: a release for [AppUpdateChannel.Stable],
     * a list for [AppUpdateChannel.Nightly]. A host reports anything else as a failed check rather than "up to date".
     */
    fun isReleaseBody(body: String, channel: AppUpdateChannel): Boolean {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return false
        return when (channel) {
            AppUpdateChannel.Stable -> (element as? JsonObject)?.string("tag_name")?.isNotBlank() == true
            AppUpdateChannel.Nightly -> element is JsonArray
        }
    }

    /**
     * Availability-only variant for hosts that cannot install an APK (Desktop): a newer release is
     * offered through its public page. Null for malformed bodies, releases without a page, or
     * releases that are not newer than [current].
     */
    fun parseAvailability(
        body: String,
        current: CurrentAppVersion,
        channel: AppUpdateChannel = AppUpdateChannel.Stable,
    ): AppUpdateInfo? = parseChannel(body, channel) { root -> parseAvailability(root, current) }

    private fun parseAvailability(root: JsonObject, current: CurrentAppVersion): AppUpdateInfo? {
        val tagName = root.string("tag_name").trim()
        val remoteVersionName = tagName.removePrefix("v").trim()
        val pageUrl = root.string("html_url").takeIf(String::isNotBlank) ?: return null
        if (remoteVersionName.isBlank()) return null
        val changelog = root.string("body").trim()
        val remoteVersionCode = AppReleaseVersions.extractVersionCodeFromBody(changelog)
        val isNewer = AppReleaseVersions.isVersionNewer(
            currentVersionName = current.name,
            currentVersionCode = current.code,
            remoteVersionName = remoteVersionName,
            remoteVersionCode = remoteVersionCode,
        )
        if (!isNewer) return null
        return AppUpdateInfo(
            versionName = remoteVersionName,
            versionCode = remoteVersionCode ?: (current.code + 1),
            releaseTitle = root.string("name").ifBlank { tagName },
            changelog = changelog,
            downloadUrl = pageUrl,
            assetName = "",
            apkSizeBytes = 0L,
            publishedAt = root.string("published_at"),
        )
    }

    /** [Stable] reads one release object; [Nightly] reads a release list and keeps the newest usable offer. */
    private inline fun parseChannel(
        body: String,
        channel: AppUpdateChannel,
        parseRelease: (JsonObject) -> AppUpdateInfo?,
    ): AppUpdateInfo? {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        return when (channel) {
            AppUpdateChannel.Stable -> (element as? JsonObject)?.let(parseRelease)
            AppUpdateChannel.Nightly -> (element as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.filterNot { it["draft"]?.jsonPrimitive?.booleanOrNull == true }
                ?.mapNotNull(parseRelease)
                ?.maxWithOrNull { a, b ->
                    AppReleaseVersions.compareVersions(a.versionName, a.versionCode, b.versionName, b.versionCode)
                }
        }
    }

    private fun selectBestApkAsset(assets: JsonArray, supportedAbis: List<String>): JsonObject? {
        val apkAssets = assets.mapNotNull { it as? JsonObject }.filter { it.assetName().endsWith(".apk") }
        if (apkAssets.isEmpty()) return null

        for (abi in supportedAbis.map { it.lowercase() }) {
            apkAssets.firstOrNull { it.assetName().contains(abi) }?.let { return it }
        }
        return apkAssets.firstOrNull { it.assetName().contains("universal") } ?: apkAssets.firstOrNull()
    }

    private fun JsonObject.assetName() = string("name").lowercase()

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}
