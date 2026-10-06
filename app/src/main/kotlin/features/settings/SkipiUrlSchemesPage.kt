// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import app.skipi.ui.settings.SkipiUrlSchemesScreen
import kotlinx.coroutines.launch
import ui.clipboard.setPlainText

/** Android platform adapter for clipboard, toast, and navigation services. */
@Composable
fun SkipiUrlSchemesPage(padding: androidx.compose.foundation.layout.PaddingValues) {
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val clipboard = LocalClipboard.current
    val notifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.common_copied)
    SkipiUrlSchemesScreen(
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onCopyCommand = { command ->
            scope.launch {
                clipboard.setPlainText(command)
                notifier.show(copiedMessage)
            }
        },
    )
}
