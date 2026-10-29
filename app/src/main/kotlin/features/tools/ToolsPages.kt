// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import app.LocalAppServices
import app.LocalIsWideScreen
import app.LocalNavigator
import app.skipi.ui.diagnostics.SkipiDnsLeakTestScreen
import app.skipi.ui.diagnostics.SkipiIpInfoScreen
import app.skipi.ui.diagnostics.SkipiSpeedTestScreen
import kotlinx.coroutines.launch
import ui.clipboard.setPlainText

/** Full-screen connection speed test: ping, jitter, download and upload. */
@Composable
fun SpeedTestPage(padding: PaddingValues) {
    val controller = AndroidDiagnostics.speedTest(LocalContext.current)
    val state by controller.state.collectAsState()
    val navigator = LocalNavigator.current
    SkipiSpeedTestScreen(
        state = state,
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        onBack = { navigator.pop() },
        onStart = controller::start,
        onStop = controller::stop,
    )
}

/** Full-screen DNS leak test: shows which resolvers really see the queries. */
@Composable
fun DnsLeakTestPage(padding: PaddingValues) {
    val controller = AndroidDiagnostics.dnsLeak(LocalContext.current)
    val state by controller.state.collectAsState()
    val navigator = LocalNavigator.current
    SkipiDnsLeakTestScreen(
        state = state,
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        onBack = {
            controller.stop()
            navigator.pop()
        },
        onStart = controller::start,
        onStop = controller::stop,
    )
}

/** Full-screen IP details of the active connection. */
@Composable
fun IpInfoPage(padding: PaddingValues) {
    val controller = AndroidDiagnostics.ipInfo(LocalContext.current)
    val state by controller.state.collectAsState()
    val navigator = LocalNavigator.current
    val clipboard = LocalClipboard.current
    val tipNotifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()
    SkipiIpInfoScreen(
        state = state,
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        onBack = {
            controller.stop()
            navigator.pop()
        },
        onRefresh = controller::refresh,
        onCopy = { text, confirmation ->
            scope.launch {
                clipboard.setPlainText(text)
                tipNotifier.show(confirmation)
            }
        },
    )
}
