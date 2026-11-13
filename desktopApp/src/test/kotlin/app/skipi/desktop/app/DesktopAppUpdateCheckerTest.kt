// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.AppUpdateChannel
import features.updater.CurrentAppVersion
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopAppUpdateCheckerTest {
    private val current = CurrentAppVersion(name = "0.5.0", code = 578)
    private fun body(tag: String, code: Int) =
        """{"tag_name":"$tag","name":"R","body":"Version code: $code","html_url":"https://example.invalid/r","published_at":"x","assets":[]}"""

    @Test
    fun newerReleaseIsReportedAsAvailable() = runBlocking {
        val result = DesktopAppUpdateChecker(current, fetchManifestBody = { null }) { body("v0.6.0", 600) }.check()
        assertEquals("0.6.0", assertIs<DesktopAppUpdateCheck.Available>(result).update.versionName)
    }

    @Test
    fun sameVersionIsUpToDate() = runBlocking {
        assertEquals(DesktopAppUpdateCheck.UpToDate, DesktopAppUpdateChecker(current, fetchManifestBody = { null }) { body("v0.5.0", 578) }.check())
    }

    @Test
    fun transportOrParseFailuresAreFailuresNotUpToDate() = runBlocking {
        assertEquals(DesktopAppUpdateCheck.Failed, DesktopAppUpdateChecker(current, fetchManifestBody = { null }) { error("offline") }.check())
        assertEquals(DesktopAppUpdateCheck.Failed, DesktopAppUpdateChecker(current, fetchManifestBody = { null }) { "<html>" }.check())
    }

    @Test
    fun nightlyAsksForTheReleaseListAndOffersTheNewestPrerelease() = runBlocking {
        val asked = mutableListOf<AppUpdateChannel>()
        val list = "[" + body("v0.5.1-rc.1", 585) + "," + body("v0.5.1-rc.2", 590) + "]"
        val checker = DesktopAppUpdateChecker(current, fetchManifestBody = { null }) { channel -> asked += channel; list }

        val result = checker.check(AppUpdateChannel.Nightly)

        assertEquals(listOf(AppUpdateChannel.Nightly), asked)
        assertEquals("0.5.1-rc.2", assertIs<DesktopAppUpdateCheck.Available>(result).update.versionName)
    }

    @Test
    fun nightlyWithNothingNewerIsUpToDateAndAnEmptyListIsToo() = runBlocking {
        val current = CurrentAppVersion(name = "0.5.1-rc.2", code = 590)
        val list = "[" + body("v0.5.1-rc.2", 590) + "]"
        assertEquals(DesktopAppUpdateCheck.UpToDate, DesktopAppUpdateChecker(current, fetchManifestBody = { null }) { list }.check(AppUpdateChannel.Nightly))
        assertEquals(DesktopAppUpdateCheck.UpToDate, DesktopAppUpdateChecker(current, fetchManifestBody = { null }) { "[]" }.check(AppUpdateChannel.Nightly))
    }

    @Test
    fun aBodyOfTheWrongShapeForTheChannelIsAFailure() = runBlocking {
        assertEquals(
            DesktopAppUpdateCheck.Failed,
            DesktopAppUpdateChecker(current, fetchManifestBody = { null }) { body("v0.6.0", 600) }.check(AppUpdateChannel.Nightly),
        )
        assertEquals(
            DesktopAppUpdateCheck.Failed,
            DesktopAppUpdateChecker(current, fetchManifestBody = { null }) { "[" + body("v0.6.0", 600) + "]" }.check(AppUpdateChannel.Stable),
        )
    }

    private fun manifest(code: Int, name: String = "0.6.0") =
        """{"schema":1,"versionName":"$name","versionCode":$code,"tag":"v$name","releaseUrl":"https://example.invalid/r","assets":[]}"""

    @Test
    fun aManifestDecidesAndGitHubsReleaseListIsNotAsked() = runBlocking {
        var apiCalls = 0
        val checker = DesktopAppUpdateChecker(current, fetchManifestBody = { manifest(600) }) { apiCalls++; "[]" }

        assertEquals("0.6.0", assertIs<DesktopAppUpdateCheck.Available>(checker.check()).update.versionName)
        assertEquals(0, apiCalls)
        assertEquals(
            DesktopAppUpdateCheck.UpToDate,
            DesktopAppUpdateChecker(current, fetchManifestBody = { manifest(578, "0.5.0") }) { apiCalls++; "[]" }.check(),
        )
        assertEquals(0, apiCalls)
    }

    @Test
    fun anUnreachableOrUnusableManifestFallsBackToTheReleaseList() = runBlocking {
        val viaApi = { DesktopAppUpdateChecker(current, fetchManifestBody = { error("404") }) { body("v0.6.0", 600) } }
        assertIs<DesktopAppUpdateCheck.Available>(viaApi().check())
        assertIs<DesktopAppUpdateCheck.Available>(
            DesktopAppUpdateChecker(current, fetchManifestBody = { "<html>" }) { body("v0.6.0", 600) }.check(),
        )
        assertEquals(
            DesktopAppUpdateCheck.Failed,
            DesktopAppUpdateChecker(current, fetchManifestBody = { error("404") }) { error("offline") }.check(),
        )
    }
}
