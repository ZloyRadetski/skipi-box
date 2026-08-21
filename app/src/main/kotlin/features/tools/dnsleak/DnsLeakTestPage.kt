// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.ConnectivityManager
import app.LocalAppChromeState
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.components.BackNavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding

/** Full-screen DNS leak test: shows which resolvers really see the queries. */
@Composable
fun DnsLeakTestPage(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val systemDnsServers = remember {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity?.activeNetwork
            ?.let { connectivity.getLinkProperties(it)?.dnsServers }
            ?.mapNotNull { it.hostAddress }
            .orEmpty()
    }

    var running by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<DnsLeakTestOutcome?>(null) }
    var failed by remember { mutableStateOf(false) }
    var failedReason by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose { job?.cancel() }
    }

    fun startTest() {
        running = true
        failed = false
        failedReason = null
        outcome = null
        job = scope.launch {
            runCatching {
                DnsLeakTestEngine(
                    systemDnsServers = systemDnsServers,
                    onProgress = {},
                ).run()
            }
                .onSuccess { result ->
                    outcome = result
                    running = false
                }
                .onFailure { error ->
                    if (error !is kotlinx.coroutines.CancellationException) {
                        failed = true
                        failedReason = error.message
                        running = false
                    }
                }
        }
    }

    fun stopTest() {
        job?.cancel()
        job = null
        running = false
    }

    Scaffold(
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.tools_dns_leak_title),
                    isWideScreen = isWideScreen,
                    scrollBehavior = MiuixScrollBehavior(),
                    navigationIcon = {
                        BackNavigationIcon(onClick = { navigator.pop() })
                    },
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        val listPadding = pageListPadding(contentPadding)
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
            contentPadding = listPadding,
        ) {
            item(key = "description") {
                SmallTitle(text = stringResource(R.string.tools_dns_resolvers_title))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.tools_dns_description),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(12.dp))
                        verdictBanner(outcome, failed, running, failedReason)
                        outcome?.exit?.let { exit ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.tools_dns_exit_info,
                                    exit.ip,
                                    exit.countryName.ifBlank { exit.countryCode },
                                ),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            text = stringResource(
                                if (running) R.string.tools_dns_stop else R.string.tools_dns_start,
                            ),
                            onClick = { if (running) stopTest() else startTest() },
                        )
                    }
                }
            }
            outcome?.resolvers?.forEachIndexed { index, resolver ->
                item(key = "resolver_$index") {
                    SmallTitle(text = stringResource(R.string.tools_dns_resolver_entry, index + 1))
                    ResolverCard(resolver)
                }
            }
        }
    }
}

@Composable
private fun verdictBanner(
    outcome: DnsLeakTestOutcome?,
    failed: Boolean,
    running: Boolean,
    failedReason: String?,
) {
    val text = when {
        failed -> stringResource(R.string.tools_dns_failed)
        running -> stringResource(R.string.tools_dns_running)
        outcome == null -> stringResource(R.string.tools_dns_idle)
        else -> when (outcome.verdict) {
            DnsLeakVerdict.NoLeak -> stringResource(R.string.tools_dns_no_leak)
            DnsLeakVerdict.SuspectedLeak -> stringResource(R.string.tools_dns_suspected_leak)
            DnsLeakVerdict.Unknown -> stringResource(R.string.tools_dns_unknown)
        }
    }
    val bannerColor = when {
        failed -> MiuixTheme.colorScheme.error
        running || outcome == null -> MiuixTheme.colorScheme.primary
        outcome.verdict == DnsLeakVerdict.NoLeak -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.error
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(bannerColor),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.onSurface,
            )
        }
        if (failed && !failedReason.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tools_dns_failed_detail, failedReason),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun ResolverCard(resolver: DnsLeakResolver) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = DnsLeakAnalysis.countryFlagEmoji(resolver.observedCountryCode) ?: "🌐",
                fontSize = 22.sp,
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = resolver.isp.ifBlank {
                        stringResource(R.string.tools_dns_isp_unknown)
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOfNotNull(
                        stringResource(
                            if (resolver.isSystemServer) {
                                R.string.tools_dns_server_system
                            } else {
                                R.string.tools_dns_server_public
                            },
                        ) + " ${resolver.server}",
                        resolver.observedIp?.let { observedIp ->
                            if (resolver.clientSubnetIp != null) {
                                stringResource(R.string.tools_dns_observed_subnet, observedIp)
                            } else {
                                stringResource(R.string.tools_dns_observed_egress, observedIp)
                            }
                        },
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
