// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** Shared Android routing editor/selector shell. Platform navigation and actions stay in the caller. */
@Composable
fun SkipiRoutingPageScaffold(
    title: String,
    subtitle: String = "",
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    showNavigationIcon: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable (contentPadding: PaddingValues, scrollBehavior: ScrollBehavior) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = SkipiTheme.colors.background,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                subtitle = subtitle,
                navigationIcon = { if (showNavigationIcon) BackNavigationIcon(onClick = onBack) },
                actions = { actions?.invoke() },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            content(pageContentPaddingWithCutout(innerPadding, padding, isWideScreen), scrollBehavior)
        }
    }
}
