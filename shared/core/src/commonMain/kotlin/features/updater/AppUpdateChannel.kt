// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

private const val ReleasesApiUrl = "https://api.github.com/repos/ZloyRadetski/skipi-box/releases"

/** The release that only carries the update manifests; the workflow replaces its files after every release. */
private const val ManifestBaseUrl = "https://github.com/ZloyRadetski/skipi-box/releases/download/update-manifests"

/**
 * Which GitHub releases the update check offers. [Stable] follows the published "latest" release
 * only; [Nightly] also follows pre-releases. [id] is persisted, so existing values must stay stable.
 */
enum class AppUpdateChannel(val id: String, val releasesUrl: String, val manifestUrl: String) {
    Stable("stable", "$ReleasesApiUrl/latest", "$ManifestBaseUrl/stable.json"),
    Nightly("nightly", "$ReleasesApiUrl?per_page=20", "$ManifestBaseUrl/nightly.json"),
    ;

    companion object {
        /** A missing or unknown stored value falls back to [Stable], the channel every install started on. */
        fun fromId(id: String?): AppUpdateChannel = entries.firstOrNull { it.id == id } ?: Stable
    }
}
