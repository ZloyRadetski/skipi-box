// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalAppChromeState
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import app.skipi.ui.diagnostics.SkipiSpeedTestContent
import app.skipi.ui.diagnostics.SpeedTestUiPhase
import app.skipi.ui.diagnostics.SpeedTestUiResult
import ui.AppTheme
import ui.components.BackNavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection

/** Full-screen connection speed test: ping, jitter, download and upload. */
@Composable
fun SpeedTestPage(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val appContext = LocalContext.current.applicationContext
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    // State lives in the app-scoped session so rotation keeps the test alive.
    val state = SpeedTestSession.state

    fun startTest() = SpeedTestSession.start(appContext)
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
            SkipiSpeedTestContent(
                phase = when (state.phase) {
                    SpeedTestPhase.Idle -> SpeedTestUiPhase.Idle
                    SpeedTestPhase.Ping -> SpeedTestUiPhase.Ping
                    SpeedTestPhase.Download -> SpeedTestUiPhase.Download
                    SpeedTestPhase.Upload -> SpeedTestUiPhase.Upload
                    SpeedTestPhase.Finished -> SpeedTestUiPhase.Finished
                    SpeedTestPhase.Failed -> SpeedTestUiPhase.Failed
                },
                progress = state.progress,
                currentMbps = state.currentMbps,
                result = state.result?.let {
                    SpeedTestUiResult(it.pingMs, it.jitterMs, it.downloadMbps, it.uploadMbps)
                },
                errorMessage = state.errorMessage,
                onStart = ::startTest,
                onStop = ::stopTest,
            )
        }
    }
}
