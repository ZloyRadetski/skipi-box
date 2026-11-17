// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import features.updater.AppUpdateChannel
import features.updater.AppUpdateInfo
import features.updater.CurrentAppVersion
import features.updater.DesktopUpdatePackage
import features.updater.GitHubReleaseParser
import features.updater.runUpdateCheck
import features.updater.ReleaseListResponse
import features.updater.UpdateCheckResult
import features.updater.UpdateManifestParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface DesktopAppUpdateCheck {
    data class Available(val update: AppUpdateInfo) : DesktopAppUpdateCheck
    data object UpToDate : DesktopAppUpdateCheck
    data object Failed : DesktopAppUpdateCheck
}

/**
 * Asks GitHub for the newest release of an [AppUpdateChannel] and offers it with the installer of the given package;
 * a release without that installer is offered through its public page. A transport or parse failure is never "up to date".
 */
internal class DesktopAppUpdateChecker(
    private val current: CurrentAppVersion = CurrentAppVersion(ProjectInfo.VERSION_NAME, ProjectInfo.VERSION_CODE),
    private val fetchManifestBody: suspend (AppUpdateChannel) -> String? = ::fetchManifestFromGitHub,
    private val fetchReleaseBody: suspend (AppUpdateChannel) -> String = ::fetchReleasesFromGitHub,
) {
    suspend fun check(
        channel: AppUpdateChannel = AppUpdateChannel.Stable,
        desktopPackage: DesktopUpdatePackage? = null,
    ): DesktopAppUpdateCheck = when (
        val result = runUpdateCheck(
            fetchManifest = { fetchManifestBody(channel) },
            parseManifest = UpdateManifestParser::parse,
            manifestOffer = { manifest -> UpdateManifestParser.desktopOffer(manifest, current, desktopPackage) },
            fetchReleases = {
                ReleaseListResponse.Ok(body = fetchReleaseBody(channel), eTag = null)
            },
            isReleaseBody = { body -> GitHubReleaseParser.isReleaseBody(body, channel) },
            releaseOffer = { body -> GitHubReleaseParser.parseAvailability(body, current, channel, desktopPackage) },
        )
    ) {
        is UpdateCheckResult.Success -> result.update?.let(DesktopAppUpdateCheck::Available) ?: DesktopAppUpdateCheck.UpToDate
        UpdateCheckResult.NotModified, UpdateCheckResult.Failed -> DesktopAppUpdateCheck.Failed
    }
}

private suspend fun fetchReleasesFromGitHub(channel: AppUpdateChannel): String = withContext(Dispatchers.IO) {
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    val request = HttpRequest.newBuilder(URI(channel.releasesUrl))
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/vnd.github.v3+json")
        .header("User-Agent", "SKIPI-App/${ProjectInfo.VERSION_NAME}")
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
    response.body()
}

private suspend fun fetchManifestFromGitHub(channel: AppUpdateChannel): String? = withContext(Dispatchers.IO) {
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build()
    val request = HttpRequest.newBuilder(URI(channel.manifestUrl))
        .timeout(Duration.ofSeconds(20))
        .header("User-Agent", "SKIPI-App/${ProjectInfo.VERSION_NAME}")
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    response.body().takeIf { response.statusCode() == 200 }
}
