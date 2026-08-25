// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

import androidx.compose.foundation.background
import ui.text.themedFontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppChromeState
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.components.BackNavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection

/** Full-screen connection speed test: ping, jitter, download and upload. */
@Composable
fun SpeedTestPage(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    // State lives in the app-scoped session so rotation keeps the test alive.
    val state = SpeedTestSession.state

    fun startTest() = SpeedTestSession.start()
    fun stopTest() = SpeedTestSession.stop()

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.tools_speed_test_title),
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        BackNavigationIcon(onClick = { navigator.pop() })
                    },
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        val baseListPadding = pageListPadding(contentPadding)
        val layoutDirection = LocalLayoutDirection.current
        val listPadding = PaddingValues(
            top = baseListPadding.calculateTopPadding(),
            bottom = baseListPadding.calculateBottomPadding(),
            start = baseListPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = baseListPadding.calculateEndPadding(layoutDirection) + 16.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(topAppBarScrollBehavior)
                .verticalScroll(rememberScrollState())
                .padding(listPadding),
        ) {
            Spacer(Modifier.height(12.dp))
            SpeedGaugeCard(
                state = state,
                onStart = ::startTest,
                onStop = ::stopTest,
            )
            Spacer(Modifier.height(12.dp))
            ResultCard(state.result)
        }
    }
}

@Composable
private fun SpeedGaugeCard(
    state: SpeedTestState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = phaseLabel(state),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
            val headline = when (state.phase) {
                SpeedTestPhase.Download, SpeedTestPhase.Upload ->
                    "%.1f".format(state.currentMbps ?: 0.0)
                else -> state.result?.let { "%.1f".format(it.downloadMbps) } ?: "--"
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = headline,
                    fontSize = 52.sp,
                    fontWeight = themedFontWeight(FontWeight.Bold),
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.tools_speed_mbps),
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                )
            }
            if (state.phase == SpeedTestPhase.Failed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tools_speed_failed, state.errorMessage.orEmpty()),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            ProgressBar(state.progress)
            Spacer(Modifier.height(16.dp))
            val isRunning = state.phase in setOf(
                SpeedTestPhase.Ping,
                SpeedTestPhase.Download,
                SpeedTestPhase.Upload,
            )
            TextButton(
                text = stringResource(
                    if (isRunning) R.string.tools_speed_stop else R.string.tools_speed_start,
                ),
                onClick = { if (isRunning) onStop() else onStart() },
            )
        }
    }
}

@Composable
private fun ResultCard(result: SpeedTestResult?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            MetricRow(stringResource(R.string.tools_speed_result_ping), result?.pingMs)
            MetricRow(stringResource(R.string.tools_speed_result_jitter), result?.jitterMs)
            MetricRow(stringResource(R.string.tools_speed_result_download), result?.downloadMbps)
            MetricRow(stringResource(R.string.tools_speed_result_upload), result?.uploadMbps)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: Double?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(
            text = value?.let { "%.1f".format(it) } ?: "--",
            fontSize = 14.sp,
            fontWeight = themedFontWeight(FontWeight.SemiBold),
            color = AppTheme.colors.onSurface,
        )
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun phaseLabel(state: SpeedTestState): String {
    return stringResource(
        when (state.phase) {
            SpeedTestPhase.Idle -> R.string.tools_speed_phase_idle
            SpeedTestPhase.Ping -> R.string.tools_speed_phase_ping
            SpeedTestPhase.Download -> R.string.tools_speed_phase_download
            SpeedTestPhase.Upload -> R.string.tools_speed_phase_upload
            SpeedTestPhase.Finished -> R.string.tools_speed_phase_finished
            SpeedTestPhase.Failed -> R.string.tools_speed_phase_failed
        },
    )
}
