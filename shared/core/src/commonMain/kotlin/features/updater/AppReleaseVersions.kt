// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

/** Installed application version, as reported by the host's build metadata. */
data class CurrentAppVersion(val name: String, val code: Int)

/** Version rules for deciding whether a published release is newer than the running build. */
object AppReleaseVersions {
    /**
     * Reads the version code from the release notes, e.g. "- Version code: 180".
     * Asset file names do not contain it.
     */
    fun extractVersionCodeFromBody(body: String): Int? {
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
