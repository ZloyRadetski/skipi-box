// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.AppUpdateChannel
import features.updater.AppUpdateInfo
import features.updater.AppUpdateState
import features.updater.DesktopUpdatePackage
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface DesktopUpdateInstallResult {
    /** A helper waits for this app to exit and then installs the update: the app must exit now. */
    data object Restarting : DesktopUpdateInstallResult

    /** The system's installer has the file; the app keeps running. */
    data object HandedOver : DesktopUpdateInstallResult

    data class Failed(val message: String) : DesktopUpdateInstallResult
}

/**
 * Owns the whole Desktop update: the offer found on GitHub, its download and the install. The state is the
 * shared [AppUpdateState] the Android build uses, so one banner shows both. A download that fails or is
 * cancelled leaves the offer in place, and a downloaded file is only handed to the installer as it was verified.
 */
internal class DesktopAppUpdater(
    private val scope: CoroutineScope,
    private val updatesRoot: Path,
    private val checkRelease: suspend (AppUpdateChannel, DesktopUpdatePackage?) -> DesktopAppUpdateCheck,
    private val downloadFile: suspend (AppUpdateInfo, Path, (Long, Long) -> Unit) -> Result<Path>,
    private val installer: DesktopUpdateInstaller = DesktopUpdateInstaller(),
    private val facts: () -> DesktopRuntimeFacts = DesktopRuntimeFacts::current,
    private val processId: () -> Long = { ProcessHandle.current().pid() },
) {
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()
    private var downloadJob: Job? = null

    /** The package this build was installed from; null when no release asset replaces it. */
    val installedPackage: DesktopUpdatePackage? get() = detectDesktopUpdatePackage(facts())

    suspend fun check(channel: AppUpdateChannel): DesktopAppUpdateCheck {
        val outcome = checkRelease(channel, installedPackage)
        when (outcome) {
            is DesktopAppUpdateCheck.Available -> {
                val before = mutableState.value
                val next = before.applyCheckedRelease(outcome.update).state
                // A new release replaces the one being downloaded, so its transfer must not keep running.
                if (before.availableUpdate?.isSameRelease(outcome.update) != true) downloadJob?.cancel()
                mutableState.value = next
            }
            DesktopAppUpdateCheck.UpToDate -> {
                downloadJob?.cancel()
                mutableState.value = mutableState.value.applyCheckedRelease(null).state
            }
            DesktopAppUpdateCheck.Failed -> Unit
        }
        return outcome
    }

    /** An offer found on another channel is not valid on this one. */
    fun reset() {
        downloadJob?.cancel()
        mutableState.value = AppUpdateState()
    }

    fun dismiss() {
        mutableState.update { it.dismissed() }
    }

    fun download(): Job? {
        val update = mutableState.value.availableUpdate ?: return null
        if (!update.canDownload || !mutableState.value.needsDownloadWork(update, automatic = false)) return null
        mutableState.update { it.withQueuedDownload(update, automatic = false) }
        val folder = update.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val job = scope.launch {
            withContext(Dispatchers.IO) { runCatching { pruneOldUpdateDownloads(updatesRoot, folder) } }
            mutableState.update { it.markDownloading(update) }
            downloadFile(update, updatesRoot.resolve(folder)) { done, total ->
                mutableState.update { it.markProgress(update, done, total) }
            }.onSuccess { file ->
                mutableState.update { it.markReady(update, file.toString()) }
            }.onFailure { error ->
                mutableState.update { it.markFailed(update, error.message ?: error::class.simpleName.orEmpty()) }
            }
        }
        downloadJob = job
        return job
    }

    suspend fun install(): DesktopUpdateInstallResult {
        val current = mutableState.value
        val update = current.availableUpdate ?: return DesktopUpdateInstallResult.Failed("There is no update to install")
        val file = current.apkFilePath?.let(Path::of)?.takeIf { Files.isRegularFile(it) }
            ?: return DesktopUpdateInstallResult.Failed("The downloaded update is missing; download it again")
                .also { mutableState.update { state -> state.withAvailableUpdate(update) } }
        val pkg = installedPackage
            ?: return DesktopUpdateInstallResult.Failed("This installation cannot update itself")
        val runtime = facts()
        val launcher = runtime.executable?.let(Path::of)
            ?: return DesktopUpdateInstallResult.Failed("The application launcher is unknown")
        val portableRoot = if ("windows" in runtime.osName.lowercase()) launcher.parent else launcher.parent?.parent
        val plan = desktopUpdateInstallPlan(
            pkg = pkg,
            file = file,
            location = DesktopInstallLocation(launcher, portableRoot, processId()),
            appImage = runtime.appImage,
        ) ?: return DesktopUpdateInstallResult.Failed("This installation cannot update itself")
        return withContext(Dispatchers.IO) { installer.install(plan) }.fold(
            onSuccess = { mustExit ->
                if (mustExit) DesktopUpdateInstallResult.Restarting else DesktopUpdateInstallResult.HandedOver
            },
            onFailure = { error -> DesktopUpdateInstallResult.Failed(error.message ?: "The installer did not start") },
        )
    }
}

private fun AppUpdateInfo.isSameRelease(other: AppUpdateInfo) =
    versionName == other.versionName && versionCode == other.versionCode && assetName == other.assetName

/** Where downloaded updates wait: next to the app's other data, never inside the installation. */
internal fun desktopUpdatesDirectory(): Path {
    val base = System.getenv("APPDATA")?.takeIf(String::isNotBlank) ?: System.getProperty("user.home")
    return Path.of(base, "SKIPI", "updates")
}

/** Packages the system installs itself: the app opens the file and keeps running. */
internal fun DesktopUpdatePackage.isInstalledBySystem(): Boolean = when (this) {
    DesktopUpdatePackage.LinuxDeb, DesktopUpdatePackage.LinuxRpm, DesktopUpdatePackage.MacDmg -> true
    else -> false
}
