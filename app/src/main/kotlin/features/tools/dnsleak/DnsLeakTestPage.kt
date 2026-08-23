// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.ConnectivityManager
import android.net.Network
import app.LocalAppChromeState
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import engine.network.TunnelNetworks
import engine.proxy.LocalProxyRuntime
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
import androidx.compose.ui.graphics.Color

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

    val connectivity = remember { context.getSystemService(ConnectivityManager::class.java) }

    var running by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<DnsLeakTestOutcome?>(null) }
    var failed by remember { mutableStateOf(false) }
    var failedReason by remember { mutableStateOf<String?>(null) }
    var failureKind by remember { mutableStateOf<DnsLeakFailureKind?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose { job?.cancel() }
    }

    fun startTest() {
        running = true
        failed = false
        failedReason = null
        failureKind = null
        outcome = null
        job = scope.launch {
            runCatching {
                // The app is excluded from its own VPN tunnel, so the probe
                // sockets traverse the tunnel via the local SOCKS proxy runtime.
                val vpnNetwork = TunnelNetworks.locateVpnNetwork(context)
                val proxyOptions = LocalProxyRuntime.current()
                DnsLeakTestEngine(
                    systemDnsServers = locateSystemDnsServers(connectivity, vpnNetwork),
                    onProgress = {},
                    vpnNetwork = vpnNetwork,
                    proxyOptions = proxyOptions,
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
                        failureKind = (error as? DnsLeakTestFailure)?.kind
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
        containerColor = AppTheme.colors.background,
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.tools_dns_leak_title),
                    isWideScreen = isWideScreen,
                    scrollBehavior = MiuixScrollBehavior(),
                    navigationIcon = {
                        BackNavigationIcon(
                            onClick = {
                                stopTest()
                                navigator.pop()
                            },
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        val baseListPadding = pageListPadding(contentPadding)
        // Match the app-wide horizontal content margin (same as SpeedTestPage).
        val layoutDirection = LocalLayoutDirection.current
        val listPadding = PaddingValues(
            top = baseListPadding.calculateTopPadding(),
            bottom = baseListPadding.calculateBottomPadding(),
            start = baseListPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = baseListPadding.calculateEndPadding(layoutDirection) + 16.dp,
        )
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize(),
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
                        verdictBanner(outcome, failed, running, failedReason, failureKind)
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
    failureKind: DnsLeakFailureKind? = null,
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
                text = when (failureKind) {
                    DnsLeakFailureKind.NoInternet -> stringResource(R.string.tools_dns_reason_no_internet)
                    DnsLeakFailureKind.TunnelNotPassing -> stringResource(R.string.tools_dns_reason_tunnel_not_passing)
                    null -> stringResource(R.string.tools_dns_failed_detail, failedReason.orEmpty())
                },
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

/**
 * DNS servers of the reference network: the VPN's own servers while a tunnel
 * is up (that is what other apps resolve through), otherwise the physical
 * network's servers.
 */
private fun locateSystemDnsServers(
    connectivity: ConnectivityManager?,
    vpnNetwork: Network?,
): List<String> {
    val connectivityManager = connectivity ?: return emptyList()
    val network = vpnNetwork ?: connectivityManager.activeNetwork ?: return emptyList()
    return connectivityManager.getLinkProperties(network)?.dnsServers
        ?.mapNotNull { it.hostAddress }
        .orEmpty()
}
