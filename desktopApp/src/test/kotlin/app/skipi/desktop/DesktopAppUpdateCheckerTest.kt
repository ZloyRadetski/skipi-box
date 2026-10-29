// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

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
        val result = DesktopAppUpdateChecker(current) { body("v0.6.0", 600) }.check()
        assertEquals("0.6.0", assertIs<DesktopAppUpdateCheck.Available>(result).update.versionName)
    }

    @Test
    fun sameVersionIsUpToDate() = runBlocking {
        assertEquals(DesktopAppUpdateCheck.UpToDate, DesktopAppUpdateChecker(current) { body("v0.5.0", 578) }.check())
    }

    @Test
    fun transportOrParseFailuresAreFailuresNotUpToDate() = runBlocking {
        assertEquals(DesktopAppUpdateCheck.Failed, DesktopAppUpdateChecker(current) { error("offline") }.check())
        assertEquals(DesktopAppUpdateCheck.Failed, DesktopAppUpdateChecker(current) { "<html>" }.check())
    }
}
