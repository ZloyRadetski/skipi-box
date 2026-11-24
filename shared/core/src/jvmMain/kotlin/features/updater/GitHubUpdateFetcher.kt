// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import java.io.IOException

/**
 * The two GETs of an update check, on any [UpdateTransport]: the channel's manifest and GitHub's release list.
 * Redirects are followed by hand and must stay on HTTPS. Blocking: call it off the main thread.
 */
class GitHubUpdateFetcher(
    private val transport: UpdateTransport,
    versionName: String,
) {
    private val userAgent = UpdateHttp.userAgent(versionName)

    /** The manifest text, or null when the server does not answer 200. */
    fun fetchManifest(channel: AppUpdateChannel): String? =
        open(channel.manifestUrl, mapOf("User-Agent" to userAgent)).use { response ->
            if (response.status != 200) null else response.body.bufferedReader().use { it.readText() }
        }

    /** The release list; a failed request throws, a conditional hit is [ReleaseListResponse.NotModified]. */
    fun fetchReleases(channel: AppUpdateChannel, ifNoneMatch: String? = null): ReleaseListResponse {
        val headers = buildMap {
            put("Accept", UpdateHttp.ApiAccept)
            put("User-Agent", userAgent)
            ifNoneMatch?.takeIf(String::isNotBlank)?.let { put("If-None-Match", it) }
        }
        return open(channel.releasesUrl, headers).use { response ->
            when (response.status) {
                200 -> ReleaseListResponse.Ok(
                    body = response.body.bufferedReader().use { it.readText() },
                    eTag = response.header("ETag"),
                )
                304 -> ReleaseListResponse.NotModified
                else -> throw IOException("HTTP ${response.status}")
            }
        }
    }

    private fun open(url: String, headers: Map<String, String>): UpdateResponse =
        transport.openFollowing(url, headers = headers) { uri -> uri.scheme.equals("https", ignoreCase = true) }
}
