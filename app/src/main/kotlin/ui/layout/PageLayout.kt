// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.ui.layout.AdaptiveTopAppBar as SharedAdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPadding as sharedPageContentPadding
import app.skipi.ui.layout.pageContentPaddingWithCutout as sharedPageContentPaddingWithCutout
import app.skipi.ui.layout.pageContentPaddingWithIme as sharedPageContentPaddingWithIme
import app.skipi.ui.layout.pageListPadding as sharedPageListPadding
import app.skipi.ui.layout.pageScrollModifiers as sharedPageScrollModifiers
import app.skipi.ui.layout.pageWindowPadding as sharedPageWindowPadding
import top.yukonga.miuix.kmp.basic.ScrollBehavior

fun Modifier.pageScrollModifiers(topAppBarScrollBehavior: ScrollBehavior): Modifier =
    sharedPageScrollModifiers(topAppBarScrollBehavior)

@Composable
fun Modifier.pageWindowPadding(outerPadding: PaddingValues): Modifier =
    sharedPageWindowPadding(outerPadding)

@Composable
fun pageContentPadding(innerPadding: PaddingValues, outerPadding: PaddingValues, isWideScreen: Boolean, extraTop: Dp = 0.dp, extraStart: Dp = 0.dp, extraEnd: Dp = 0.dp): PaddingValues =
    sharedPageContentPadding(innerPadding, outerPadding, isWideScreen, extraTop, extraStart, extraEnd)

@Composable
fun pageContentPaddingWithCutout(innerPadding: PaddingValues, outerPadding: PaddingValues, isWideScreen: Boolean, extraTop: Dp = 0.dp): PaddingValues =
    sharedPageContentPaddingWithCutout(innerPadding, outerPadding, isWideScreen, extraTop)

@Composable
fun pageContentPaddingWithIme(contentPadding: PaddingValues): PaddingValues =
    sharedPageContentPaddingWithIme(contentPadding)

@Composable
fun pageListPadding(contentPadding: PaddingValues, bottomExtra: Dp = 12.dp): PaddingValues =
    sharedPageListPadding(contentPadding, bottomExtra)

@Composable
fun AdaptiveTopAppBar(title: String, isWideScreen: Boolean, scrollBehavior: ScrollBehavior, modifier: Modifier = Modifier, color: Color = Color.Transparent, subtitle: String = "", navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}, bottomContent: @Composable () -> Unit = {}) =
    SharedAdaptiveTopAppBar(title, isWideScreen, scrollBehavior, modifier, color, subtitle, navigationIcon, actions, bottomContent)
