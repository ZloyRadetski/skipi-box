// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.about_bug_report
import app.skipi.ui.resources.about_bug_report_summary
import app.skipi.ui.resources.about_other
import app.skipi.ui.resources.about_replay_onboarding
import app.skipi.ui.resources.about_replay_onboarding_summary
import app.skipi.ui.resources.about_runtime
import app.skipi.ui.resources.about_telegram_channel
import app.skipi.ui.resources.about_title
import app.skipi.ui.resources.about_view_skipi_source
import app.skipi.ui.resources.settings_updates_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import app.skipi.ui.resources.ic_about_logo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

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

const val SkipiSourceUri = "https://github.com/ZloyRadetski/skipi-box"
const val SkipiCoreSourceUri = "https://github.com/ZloyRadetski/skipi-core"
const val SkipiBugReportUri = "https://github.com/ZloyRadetski/skipi-box/issues/new"
const val SkipiTelegramChannelUri = "https://t.me/skipi_public"

@Composable
fun SkipiAboutLogo(contentDescription: String, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.ic_about_logo),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(150.dp)
            .aspectRatio(2f)
            .clip(RoundedCornerShape(30.dp)),
    )
}

/** Localized About labels shared by every host. */
@Composable
fun rememberAboutSettingsLabels(): AboutSettingsLabels = AboutSettingsLabels(
    screenTitle = stringResource(Res.string.about_title),
    updatesTitle = stringResource(Res.string.settings_updates_title),
    runtimeTitle = stringResource(Res.string.about_runtime),
    otherTitle = stringResource(Res.string.about_other),
    replayOnboardingTitle = stringResource(Res.string.about_replay_onboarding),
    replayOnboardingSummary = stringResource(Res.string.about_replay_onboarding_summary),
    telegramTitle = stringResource(Res.string.about_telegram_channel),
    bugReportTitle = stringResource(Res.string.about_bug_report),
    bugReportSummary = stringResource(Res.string.about_bug_report_summary),
    sourceTitle = stringResource(Res.string.about_view_skipi_source),
)

data class AboutRuntimeInfo(
    val appName: String,
    val appVersion: String,
    val skipiCoreVersion: String,
    val xrayCoreVersion: String,
    val hevTunnelVersion: String,
)

data class AboutSettingsCapabilities(
    val updates: Boolean = true,
    val runtime: Boolean = true,
    val replayOnboarding: Boolean = true,
    val telegram: Boolean = true,
    val bugReport: Boolean = true,
    val source: Boolean = true,
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
    updatesContent: @Composable () -> Unit,
    logo: @Composable () -> Unit = { SkipiAboutLogo(contentDescription = runtime.appName) },
    capabilities: AboutSettingsCapabilities = AboutSettingsCapabilities(),
    platformContent: (@Composable ColumnScope.() -> Unit)? = null,
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
                        if (runtime.appVersion.isNotBlank()) {
                            Text(text = "v${runtime.appVersion}", color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (capabilities.updates) item(key = "about_updates") {
                    SmallTitle(text = labels.updatesTitle)
                    updatesContent()
                }
                if (capabilities.runtime) item(key = "about_runtime") {
                    SmallTitle(text = labels.runtimeTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(title = "SKIPI Core", summary = runtime.skipiCoreVersion, onClick = onOpenCoreInfo)
                        BasicComponent(title = "Xray-core", summary = runtime.xrayCoreVersion)
                        if (runtime.hevTunnelVersion.isNotBlank()) {
                            BasicComponent(title = "hev-socks5-tunnel", summary = runtime.hevTunnelVersion)
                        }
                    }
                }
                if (capabilities.replayOnboarding || capabilities.telegram || capabilities.bugReport || capabilities.source) item(key = "about_other") {
                    SmallTitle(text = labels.otherTitle)
                    SkipiSettingsSectionCard {
                        if (capabilities.replayOnboarding) ArrowPreference(title = labels.replayOnboardingTitle, summary = labels.replayOnboardingSummary, onClick = onReplayOnboarding)
                        if (capabilities.telegram) ArrowPreference(title = labels.telegramTitle, summary = "@skipi_public", onClick = onOpenTelegram)
                        if (capabilities.bugReport) ArrowPreference(title = labels.bugReportTitle, summary = labels.bugReportSummary, onClick = onOpenBugReport)
                        if (capabilities.source) ArrowPreference(title = labels.sourceTitle, onClick = onOpenSource)
                    }
                }
                platformContent?.let { content -> item(key = "about_platform_extension") { Column(content = content) } }
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
