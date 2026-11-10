// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.AppUpdateChannel
import features.updater.AppUpdateDownloadStatus
import features.updater.AppUpdateInfo
import features.updater.DesktopUpdatePackage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class DesktopAppUpdaterTest {
    private val root: Path = Files.createTempDirectory("skipi-updater-")

    @AfterTest
    fun cleanup() {
        root.toFile().deleteRecursively()
    }

    private val windowsFacts = DesktopRuntimeFacts(
        osName = "Windows 11", osArch = "amd64", executable = "C:\\Program Files\\SKIPI\\SKIPI.exe", appImage = null,
        programFilesDirs = listOf("C:\\Program Files"), hasDpkg = false, hasRpm = false,
    )

    private fun info(asset: String = "SKIPI-windows-x64.msi", version: String = "0.6.0") = AppUpdateInfo(
        versionName = version, versionCode = 600, releaseTitle = "t", changelog = "",
        downloadUrl = if (asset.isEmpty()) "https://github.com/page" else "https://github.com/dl/$asset",
        assetName = asset, apkSizeBytes = if (asset.isEmpty()) 0 else 1000, publishedAt = "",
    )

    private fun updater(
        offer: AppUpdateInfo? = info(),
        facts: DesktopRuntimeFacts = windowsFacts,
        download: suspend (AppUpdateInfo, Path, (Long, Long) -> Unit) -> Result<Path> = { update, folder, progress ->
            progress(500, 1000)
            Files.createDirectories(folder)
            Result.success(Files.write(folder.resolve(update.assetName), ByteArray(1000)))
        },
        installer: DesktopUpdateInstaller = DesktopUpdateInstaller { Result.success(Unit) },
        started: MutableList<DesktopUpdatePackage?> = mutableListOf(),
    ) = DesktopAppUpdater(
        scope = CoroutineScope(Dispatchers.Unconfined),
        updatesRoot = root,
        checkRelease = { _: AppUpdateChannel, pkg: DesktopUpdatePackage? ->
            started += pkg
            offer?.let { DesktopAppUpdateCheck.Available(it) } ?: DesktopAppUpdateCheck.UpToDate
        },
        downloadFile = download,
        installer = installer,
        facts = { facts },
        processId = { 4242 },
    )

    @Test
    fun theCheckAsksForTheInstallerOfTheRunningPackage() = runBlocking {
        val asked = mutableListOf<DesktopUpdatePackage?>()
        val updater = updater(started = asked)

        updater.check(AppUpdateChannel.Stable)

        assertEquals(listOf<DesktopUpdatePackage?>(DesktopUpdatePackage.WindowsMsi), asked)
        assertEquals("0.6.0", updater.state.value.availableUpdate?.versionName)
    }

    @Test
    fun anUpToDateCheckClearsTheOffer() = runBlocking {
        val updater = updater()
        updater.check(AppUpdateChannel.Stable)

        val none = updater(offer = null)
        none.check(AppUpdateChannel.Stable)

        assertNull(none.state.value.availableUpdate)
    }

    @Test
    fun aDownloadEndsReadyWithTheVerifiedFile() = runBlocking {
        val updater = updater()
        updater.check(AppUpdateChannel.Stable)

        updater.download()?.join()

        val state = updater.state.value
        assertEquals(AppUpdateDownloadStatus.READY_TO_INSTALL, state.status)
        assertTrue(Files.isRegularFile(Path.of(state.apkFilePath!!)))
        assertEquals(null, state.error)
    }

    @Test
    fun aFailedDownloadKeepsTheOfferAndCanBeRetried() = runBlocking {
        var attempts = 0
        val updater = updater(download = { update, folder, _ ->
            attempts++
            if (attempts == 1) {
                Result.failure(java.io.IOException("offline"))
            } else {
                Files.createDirectories(folder)
                Result.success(Files.write(folder.resolve(update.assetName), ByteArray(1)))
            }
        })
        updater.check(AppUpdateChannel.Stable)

        updater.download()?.join()
        assertEquals(AppUpdateDownloadStatus.FAILED, updater.state.value.status)
        assertEquals("offline", updater.state.value.error)
        assertEquals("0.6.0", updater.state.value.availableUpdate?.versionName)

        updater.download()?.join()
        assertEquals(AppUpdateDownloadStatus.READY_TO_INSTALL, updater.state.value.status)
    }

    @Test
    fun anOfferThatIsOnlyAPageLinkIsNotDownloaded() = runBlocking {
        var called = false
        val updater = updater(offer = info(asset = ""), download = { _, _, _ -> called = true; Result.failure(IllegalStateException()) })
        updater.check(AppUpdateChannel.Stable)

        updater.download()?.join()

        assertFalse(called)
        assertEquals(AppUpdateDownloadStatus.IDLE, updater.state.value.status)
    }

    @Test
    fun resettingCancelsTheTransferAndForgetsTheOffer() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val updater = updater(download = { _, _, _ -> gate.await(); Result.failure(IllegalStateException("late")) })
        updater.check(AppUpdateChannel.Stable)
        val job = updater.download()
        assertTrue(updater.state.value.status in listOf(AppUpdateDownloadStatus.QUEUED, AppUpdateDownloadStatus.DOWNLOADING))

        updater.reset()

        assertNull(updater.state.value.availableUpdate)
        assertEquals(AppUpdateDownloadStatus.IDLE, updater.state.value.status)
    }

    @Test
    fun installingWithoutADownloadedFileFails() = runBlocking<Unit> {
        val updater = updater()
        updater.check(AppUpdateChannel.Stable)

        assertIs<DesktopUpdateInstallResult.Failed>(updater.install())
    }

    @Test
    fun aDownloadedMsiStartsAHelperAndAsksTheAppToExit() = runBlocking {
        val commands = mutableListOf<List<String>>()
        val updater = updater(installer = DesktopUpdateInstaller { command -> commands += command; Result.success(Unit) })
        updater.check(AppUpdateChannel.Stable)
        updater.download()?.join()

        val result = updater.install()

        assertEquals(DesktopUpdateInstallResult.Restarting, result)
        val script = commands.single().last()
        assertTrue(script.startsWith("Wait-Process -Id 4242"), script)
        assertTrue("SKIPI-windows-x64.msi" in script, script)
    }

    @Test
    fun aDmgIsHandedToTheSystemAndTheAppKeepsRunning() = runBlocking {
        val mac = DesktopRuntimeFacts(
            osName = "Mac OS X", osArch = "aarch64", executable = "/Applications/SKIPI.app/Contents/MacOS/SKIPI",
            appImage = null, programFilesDirs = emptyList(), hasDpkg = false, hasRpm = false,
        )
        val updater = updater(
            offer = info(asset = "SKIPI-macos-arm64.dmg"),
            facts = mac,
        )
        updater.check(AppUpdateChannel.Stable)
        updater.download()?.join()

        assertEquals(DesktopUpdateInstallResult.HandedOver, updater.install())
    }

    @Test
    fun anInstallerThatCannotStartLeavesTheDownloadReadyToTryAgain() = runBlocking {
        val updater = updater(installer = DesktopUpdateInstaller { Result.failure(java.io.IOException("no powershell")) })
        updater.check(AppUpdateChannel.Stable)
        updater.download()?.join()

        val result = updater.install()

        assertEquals("no powershell", assertIs<DesktopUpdateInstallResult.Failed>(result).message)
        assertEquals(AppUpdateDownloadStatus.READY_TO_INSTALL, updater.state.value.status)
    }

    @Test
    fun aBuildNoReleaseFileReplacesCannotInstall() = runBlocking<Unit> {
        val unknown = windowsFacts.copy(osArch = "aarch64")
        val updater = updater(facts = unknown, offer = info(asset = ""))
        updater.check(AppUpdateChannel.Stable)

        assertNull(updater.installedPackage)
        assertIs<DesktopUpdateInstallResult.Failed>(updater.install())
    }
}
