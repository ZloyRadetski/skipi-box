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
        return compareVersions(remoteVersionName, remoteVersionCode, currentVersionName, currentVersionCode) > 0
    }

    /**
     * Orders two releases. Differing version codes decide first, matching [isVersionNewer]; otherwise
     * the names do, with a pre-release ("0.5.0-rc.6") before its release ("0.5.0"), and the codes
     * break a remaining tie.
     */
    fun compareVersions(aName: String, aCode: Int?, bName: String, bCode: Int?): Int {
        if (aCode != null && bCode != null && aCode != bCode) return aCode.compareTo(bCode)
        val byName = compareNames(aName, bName)
        return if (byName != 0) byName else (aCode ?: 0).compareTo(bCode ?: 0)
    }

    private fun compareNames(a: String, b: String): Int {
        val left = ParsedName.of(a)
        val right = ParsedName.of(b)
        for (i in 0 until maxOf(left.numbers.size, right.numbers.size)) {
            val byNumber = left.numbers.getOrElse(i) { 0 }.compareTo(right.numbers.getOrElse(i) { 0 })
            if (byNumber != 0) return byNumber
        }
        return when {
            left.preRelease.isEmpty() && right.preRelease.isEmpty() -> 0
            left.preRelease.isEmpty() -> 1
            right.preRelease.isEmpty() -> -1
            else -> comparePreRelease(left.preRelease, right.preRelease)
        }
    }

    private fun comparePreRelease(a: List<String>, b: List<String>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]
            val y = b[i]
            val xNumber = x.toIntOrNull()
            val yNumber = y.toIntOrNull()
            val order = when {
                xNumber != null && yNumber != null -> xNumber.compareTo(yNumber)
                xNumber != null -> -1
                yNumber != null -> 1
                else -> x.compareTo(y)
            }
            if (order != 0) return order
        }
        return a.size.compareTo(b.size)
    }

    /** "v0.5.0-rc.6+build" -> numbers [0, 5, 0], pre-release ["rc", "6"]. */
    private class ParsedName(val numbers: List<Int>, val preRelease: List<String>) {
        companion object {
            fun of(name: String): ParsedName {
                val withoutBuild = name.trim().removePrefix("v").substringBefore('+')
                val core = withoutBuild.substringBefore('-')
                val pre = withoutBuild.substringAfter('-', "")
                return ParsedName(
                    numbers = core.split('.').mapNotNull { it.toIntOrNull() },
                    preRelease = pre.split('.').filter(String::isNotEmpty),
                )
            }
        }
    }
}
