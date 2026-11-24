// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

/** What the release list answered; the platform owns the transport and the ETag. */
sealed interface ReleaseListResponse {
    data class Ok(val body: String, val eTag: String?) : ReleaseListResponse
    data object NotModified : ReleaseListResponse
    data object Failed : ReleaseListResponse
}
