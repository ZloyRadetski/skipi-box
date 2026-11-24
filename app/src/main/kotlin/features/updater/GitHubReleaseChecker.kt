// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.content.Context
import android.os.Build
import app.ProjectInfo
import features.logs.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val LogTag = "GitHubReleaseChecker"

internal class GitHubReleaseChecker(
    context: Context? = null,
    private val fetcher: GitHubUpdateFetcher = GitHubUpdateFetcher(
        AndroidUpdateTransport(context, UpdateHttp.ApiConnectTimeoutMs, UpdateHttp.ApiReadTimeoutMs),
        ProjectInfo.VERSION_NAME,
    ),
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
            fetchManifest = { fetcher.fetchManifest(channel) },
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

    private fun fetchReleaseList(channel: AppUpdateChannel, ifNoneMatch: String?): ReleaseListResponse = try {
        fetcher.fetchReleases(channel, ifNoneMatch)
    } catch (error: IOException) {
        AppLog.warn(LogTag, "GitHub release check failed: ${error.message}")
        ReleaseListResponse.Failed
    }

    private fun parseRelease(body: String, channel: AppUpdateChannel): AppUpdateInfo? = GitHubReleaseParser.parse(
        body = body,
        current = CurrentAppVersion(name = ProjectInfo.VERSION_NAME, code = ProjectInfo.VERSION_CODE),
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        channel = channel,
    )
}
