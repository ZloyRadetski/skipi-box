// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import features.updater.AppUpdateChannel
import features.updater.AppUpdateInfo
import features.updater.CurrentAppVersion
import features.updater.DesktopUpdatePackage
import features.updater.GitHubReleaseParser
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
    ): DesktopAppUpdateCheck = checkManifest(channel, desktopPackage) ?: checkReleaseList(channel, desktopPackage)

    /**
     * The manifest the release workflow publishes at a fixed address: no GitHub API, no rate limit, and it names a
     * release only after all its installers are uploaded. Null when it cannot be had or read, so the release list answers.
     */
    private suspend fun checkManifest(channel: AppUpdateChannel, desktopPackage: DesktopUpdatePackage?): DesktopAppUpdateCheck? = try {
        val manifest = fetchManifestBody(channel)?.let(UpdateManifestParser::parse)
        manifest?.let { UpdateManifestParser.desktopOffer(it, current, desktopPackage) }
            ?.let(DesktopAppUpdateCheck::Available)
            ?: manifest?.let { DesktopAppUpdateCheck.UpToDate }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        null
    }

    private suspend fun checkReleaseList(channel: AppUpdateChannel, desktopPackage: DesktopUpdatePackage?): DesktopAppUpdateCheck = try {
        val body = fetchReleaseBody(channel)
        if (!GitHubReleaseParser.isReleaseBody(body, channel)) {
            DesktopAppUpdateCheck.Failed
        } else {
            GitHubReleaseParser.parseAvailability(body, current, channel, desktopPackage)
                ?.let(DesktopAppUpdateCheck::Available)
                ?: DesktopAppUpdateCheck.UpToDate
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        DesktopAppUpdateCheck.Failed
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
