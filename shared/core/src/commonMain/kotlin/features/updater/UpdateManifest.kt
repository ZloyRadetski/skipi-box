// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the release workflow publishes for the update check: one small JSON file per channel at a fixed address, written
 * after every installer of the release is uploaded. The version number is a field, not text to be searched in the
 * release notes, and the file lists only what exists, with the size and SHA-256 of each file.
 */
@Serializable
data class UpdateManifest(
    val schema: Int = 0,
    val versionName: String = "",
    val versionCode: Int = 0,
    val tag: String = "",
    val title: String = "",
    val releaseUrl: String = "",
    val publishedAt: String = "",
    val changelog: String = "",
    val assets: List<UpdateManifestAsset> = emptyList(),
)

@Serializable
data class UpdateManifestAsset(
    val name: String = "",
    val url: String = "",
    val size: Long = 0L,
    val sha256: String = "",
)

object UpdateManifestParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** Null unless [body] is a manifest this version understands (schema 1 or newer, with a name and a build number). */
    fun parse(body: String): UpdateManifest? {
        val manifest = runCatching { json.decodeFromString<UpdateManifest>(body) }.getOrNull() ?: return null
        val usable = manifest.schema >= 1 &&
            manifest.versionName.isNotBlank() &&
            manifest.versionCode > 0 &&
            manifest.tag.isNotBlank()
        return manifest.takeIf { usable }
    }

    fun isManifest(body: String): Boolean = parse(body) != null

    /** The APK of the first supported ABI that has one, then the universal APK; null when there is none or it is not newer. */
    fun androidOffer(manifest: UpdateManifest, current: CurrentAppVersion, supportedAbis: List<String>): AppUpdateInfo? {
        if (!manifest.isNewerThan(current)) return null
        val apks = manifest.assets.filter { it.name.lowercase().endsWith(".apk") && it.url.isNotBlank() }
        if (apks.isEmpty()) return null
        val asset = supportedAbis.map(String::lowercase).firstNotNullOfOrNull { abi ->
            apks.firstOrNull { it.name.lowercase().contains(abi) }
        } ?: apks.firstOrNull { it.name.lowercase().contains("universal") } ?: apks.first()
        return manifest.offer(asset)
    }

    /** The installer of [desktopPackage] when the release has it, otherwise the release page. */
    fun desktopOffer(manifest: UpdateManifest, current: CurrentAppVersion, desktopPackage: DesktopUpdatePackage?): AppUpdateInfo? {
        if (!manifest.isNewerThan(current)) return null
        val asset = desktopPackage?.let { wanted -> manifest.assets.firstOrNull { it.name == wanted.assetName && it.url.isNotBlank() } }
        if (asset != null) return manifest.offer(asset)
        if (manifest.releaseUrl.isBlank()) return null
        return manifest.offer(asset = null)
    }

    private fun UpdateManifest.isNewerThan(current: CurrentAppVersion): Boolean = AppReleaseVersions.isVersionNewer(
        currentVersionName = current.name,
        currentVersionCode = current.code,
        remoteVersionName = versionName,
        remoteVersionCode = versionCode,
    )

    private fun UpdateManifest.offer(asset: UpdateManifestAsset?) = AppUpdateInfo(
        versionName = versionName,
        versionCode = versionCode,
        releaseTitle = title.ifBlank { tag },
        changelog = changelog,
        downloadUrl = asset?.url ?: releaseUrl,
        assetName = asset?.name.orEmpty(),
        apkSizeBytes = asset?.size ?: 0L,
        publishedAt = publishedAt,
        assetSha256 = asset?.sha256?.takeIf(String::isNotBlank)?.let { "sha256:${it.removePrefix("sha256:")}" },
    )
}
