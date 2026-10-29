// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseTitle: String,
    val changelog: String,
    val downloadUrl: String,
    val assetName: String,
    val apkSizeBytes: Long,
    val publishedAt: String,
    /** SHA-256 published by GitHub, when the release API provides one. */
    val assetSha256: String? = null,
)

/** Durable state exposed by the UI while WorkManager owns the transfer. */
enum class AppUpdateDownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    READY_TO_INSTALL,
    FAILED,
}

sealed interface AppUpdateDownloadProgress {
    data object Idle : AppUpdateDownloadProgress
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : AppUpdateDownloadProgress
    data class Completed(val apkFilePath: String) : AppUpdateDownloadProgress
    data class Failed(val errorMessage: String) : AppUpdateDownloadProgress
}
