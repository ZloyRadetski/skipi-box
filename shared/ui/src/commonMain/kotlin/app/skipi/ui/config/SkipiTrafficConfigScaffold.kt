// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.skipi.ui.text.themedFontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.components.NavigationIcon
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** Shared shell for the Android traffic-config full-screen editors. */
@Composable
fun SkipiTrafficConfigFullScreenScaffold(
    title: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    saveLabel: String,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    topContentInset: Dp = 8.dp,
    content: @Composable BoxScope.(contentPadding: PaddingValues, listPadding: PaddingValues, scrollBehavior: ScrollBehavior) -> Unit,
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
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    actions?.invoke()
                    if (onSave != null) {
                        NavigationIcon(onClick = onSave, imageVector = MiuixIcons.Ok, contentDescription = saveLabel)
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentPadding = app.skipi.ui.layout.pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        val basePadding = app.skipi.ui.layout.pageListPadding(contentPadding)
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val listPadding = PaddingValues(
            start = basePadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = basePadding.calculateTopPadding() + topContentInset,
            end = basePadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = basePadding.calculateBottomPadding() + 12.dp,
        )
        Box(modifier = Modifier.fillMaxSize().background(SkipiTheme.colors.background)) {
            content(contentPadding, listPadding, scrollBehavior)
        }
    }
}

@Composable
fun SkipiTrafficConfigEditorGroupCard(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = themedFontWeight(FontWeight.SemiBold), color = MiuixTheme.colorScheme.onSurface)
                Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(imageVector = MiuixIcons.Edit, contentDescription = null, tint = SkipiTheme.colors.onSurfaceVariant)
        }
    }
}

