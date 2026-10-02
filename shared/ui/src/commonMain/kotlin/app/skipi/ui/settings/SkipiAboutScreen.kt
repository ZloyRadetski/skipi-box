// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference

data class AboutSettingsLabels(
    val screenTitle: String,
    val updatesTitle: String,
    val runtimeTitle: String,
    val otherTitle: String,
    val replayOnboardingTitle: String,
    val replayOnboardingSummary: String,
    val telegramTitle: String,
    val bugReportTitle: String,
    val bugReportSummary: String,
    val sourceTitle: String,
)

data class AboutRuntimeInfo(
    val appName: String,
    val appVersion: String,
    val skipiCoreVersion: String,
    val xrayCoreVersion: String,
    val hevTunnelVersion: String,
)

/** Shared About layout. Branding assets, update flow, navigation, and external links are host-owned. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiAboutScreen(
    labels: AboutSettingsLabels,
    runtime: AboutRuntimeInfo,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onOpenCoreInfo: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenTelegram: () -> Unit,
    onOpenBugReport: () -> Unit,
    onOpenSource: () -> Unit,
    logo: @Composable () -> Unit,
    updatesContent: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            key(labels.screenTitle) {
                SettingsBackTopBar(labels.screenTitle, isWideScreen, onBack)
            }
        },
    ) { innerPadding ->
        val contentPadding = settingsPageContentPadding(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "about_header") {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(20.dp))
                        logo()
                        Spacer(Modifier.height(14.dp))
                        Text(text = runtime.appName, style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title2)
                        Text(text = "v${runtime.appVersion}", color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item(key = "about_updates") {
                    SmallTitle(text = labels.updatesTitle)
                    updatesContent()
                }
                item(key = "about_runtime") {
                    SmallTitle(text = labels.runtimeTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(title = "SKIPI Core", summary = runtime.skipiCoreVersion, onClick = onOpenCoreInfo)
                        BasicComponent(title = "Xray-core", summary = runtime.xrayCoreVersion)
                        BasicComponent(title = "hev-socks5-tunnel", summary = runtime.hevTunnelVersion)
                    }
                }
                item(key = "about_other") {
                    SmallTitle(text = labels.otherTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(title = labels.replayOnboardingTitle, summary = labels.replayOnboardingSummary, onClick = onReplayOnboarding)
                        ArrowPreference(title = labels.telegramTitle, summary = "@skipi_public", onClick = onOpenTelegram)
                        ArrowPreference(title = labels.bugReportTitle, summary = labels.bugReportSummary, onClick = onOpenBugReport)
                        ArrowPreference(title = labels.sourceTitle, onClick = onOpenSource)
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}
