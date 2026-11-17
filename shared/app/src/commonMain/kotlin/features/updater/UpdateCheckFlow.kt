// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.coroutines.cancellation.CancellationException

/** What the release list answered; the platform owns the transport and the ETag. */
sealed interface ReleaseListResponse {
    data class Ok(val body: String, val eTag: String?) : ReleaseListResponse
    data object NotModified : ReleaseListResponse
    data object Failed : ReleaseListResponse
}

sealed interface UpdateCheckResult {
    /** [update] is null when this build is the newest; a transport or parse failure is never reported as that. */
    data class Success(val update: AppUpdateInfo?, val eTag: String?) : UpdateCheckResult
    data object NotModified : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

/**
 * The update check both platforms run: the manifest the release workflow publishes at a fixed address first (no
 * GitHub API, no rate limit, and it names a release only after its files are uploaded), the release list when the
 * manifest cannot be had or read. Only the transports and the offer rules differ per platform.
 */
suspend fun runUpdateCheck(
    fetchManifest: suspend () -> String?,
    parseManifest: (String) -> UpdateManifest?,
    manifestOffer: (UpdateManifest) -> AppUpdateInfo?,
    fetchReleases: suspend () -> ReleaseListResponse,
    isReleaseBody: (String) -> Boolean,
    releaseOffer: (String) -> AppUpdateInfo?,
    onManifestUnavailable: (Throwable?) -> Unit = {},
    onReleaseListFailed: (Throwable?) -> Unit = {},
): UpdateCheckResult {
    val manifest = try {
        fetchManifest()?.let(parseManifest)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        onManifestUnavailable(error)
        null
    }
    if (manifest != null) return UpdateCheckResult.Success(manifestOffer(manifest), eTag = null)

    return try {
        when (val response = fetchReleases()) {
            is ReleaseListResponse.Ok -> {
                // A malformed body is a failed check, so a corrupt answer never clears an update already found.
                if (isReleaseBody(response.body)) {
                    UpdateCheckResult.Success(releaseOffer(response.body), response.eTag)
                } else {
                    onReleaseListFailed(null)
                    UpdateCheckResult.Failed
                }
            }
            ReleaseListResponse.NotModified -> UpdateCheckResult.NotModified
            ReleaseListResponse.Failed -> UpdateCheckResult.Failed
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        onReleaseListFailed(error)
        UpdateCheckResult.Failed
    }
}
