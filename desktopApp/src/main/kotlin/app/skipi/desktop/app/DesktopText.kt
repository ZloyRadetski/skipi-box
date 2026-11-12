// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Reads a shared string resource outside composition (status messages built by controllers, runners and
 * event handlers). The text follows the process locale, which [applyDesktopStartupLanguage] and the
 * language setting keep in sync, so it is always in the language the user picked. Inside composition
 * use `stringResource` instead, so the text also follows a language change.
 */
internal fun desktopText(resource: StringResource, vararg args: Any): String =
    runBlocking { getString(resource, *args) }

/** True when a status line reports a failure; [localizedMarkers] is `desktop_status_error_markers`. */
internal fun desktopStatusMessageIsError(message: String, localizedMarkers: String): Boolean {
    val markers = localizedMarkers.split('|').filter(String::isNotBlank) + listOf("failed", "rejected")
    return markers.any { marker -> message.contains(marker, ignoreCase = true) }
}
