// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import features.updater.AppUpdateInfo
import features.updater.CurrentAppVersion
import features.updater.GitHubReleaseParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal sealed interface DesktopAppUpdateCheck {
    data class Available(val update: AppUpdateInfo) : DesktopAppUpdateCheck
    data object UpToDate : DesktopAppUpdateCheck
    data object Failed : DesktopAppUpdateCheck
}

private const val LatestReleaseUrl = "https://api.github.com/repos/ZloyRadetski/skipi-box/releases/latest"

/**
 * Asks GitHub for the latest release and offers it through its public page; Desktop installs
 * nothing itself. A transport or parse failure is never reported as "up to date".
 */
internal class DesktopAppUpdateChecker(
    private val current: CurrentAppVersion = CurrentAppVersion(ProjectInfo.VERSION_NAME, ProjectInfo.VERSION_CODE),
    private val fetchLatestReleaseBody: suspend () -> String = ::fetchLatestReleaseFromGitHub,
) {
    suspend fun check(): DesktopAppUpdateCheck = try {
        val body = fetchLatestReleaseBody()
        val tag = Json.parseToJsonElement(body).jsonObject["tag_name"]?.jsonPrimitive?.content
        if (tag.isNullOrBlank()) {
            DesktopAppUpdateCheck.Failed
        } else {
            GitHubReleaseParser.parseAvailability(body, current)
                ?.let(DesktopAppUpdateCheck::Available)
                ?: DesktopAppUpdateCheck.UpToDate
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        DesktopAppUpdateCheck.Failed
    }
}

private suspend fun fetchLatestReleaseFromGitHub(): String = withContext(Dispatchers.IO) {
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    val request = HttpRequest.newBuilder(URI(LatestReleaseUrl))
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/vnd.github.v3+json")
        .header("User-Agent", "SKIPI-App/${ProjectInfo.VERSION_NAME}")
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
    response.body()
}
