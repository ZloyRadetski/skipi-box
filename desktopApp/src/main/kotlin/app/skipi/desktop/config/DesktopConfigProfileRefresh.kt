// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import app.skipi.app.config.TrafficConfigProfileRefreshOperations
import app.skipi.app.config.TrafficConfigProfileRefreshRequest
import app.skipi.app.config.toTrafficConfigState
import features.config.ConfigProfile

/** Routes Desktop's stored-profile snapshot through the same manual decision as Android. */
internal fun requestDesktopConfigProfileRefresh(
    profile: ConfigProfile,
    resourceUserAgentFallback: String = versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME),
): TrafficConfigProfileRefreshRequest = TrafficConfigProfileRefreshOperations.requestManualRefresh(
    profile.toTrafficConfigState(resourceUserAgentFallback),
)
