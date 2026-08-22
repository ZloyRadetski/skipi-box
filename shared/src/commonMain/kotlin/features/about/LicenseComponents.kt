// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import features.about.license.Library
import features.settings.SettingsSectionCard
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun LibraryLicenseCard(
    library: Library,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    SettingsSectionCard(
        modifier = modifier,
        bottomPadding = 12.dp,
    ) {
        ArrowPreference(
            title = library.name,
            summary = "${library.artifactVersion}, ${library.licenses.firstOrNull()}",
            onClick = {
                library.website?.let { url -> uriHandler.openUri(url) }
            },
        )
    }
}
