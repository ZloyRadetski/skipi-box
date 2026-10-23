// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.ui.text.formatTemplate

/** Adds Android-compatible saved-title wording to a host-formatted install refresh result. */
fun formatSubscriptionInstallMessage(
    existingTitle: String?,
    refreshMessage: String,
    existingUrlTemplate: String,
): String {
    if (existingTitle == null) return refreshMessage
    val existingNotice = existingUrlTemplate.formatTemplate("name" to existingTitle)
    return "$existingNotice\n$refreshMessage"
}
