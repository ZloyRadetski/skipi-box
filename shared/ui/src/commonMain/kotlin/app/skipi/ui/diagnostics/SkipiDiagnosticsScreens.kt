// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.app.tools.DnsLeakTestUiState
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.components.NavigationIcon
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_copy_field
import app.skipi.ui.resources.tools_dns_failed
import app.skipi.ui.resources.tools_dns_failed_detail
import app.skipi.ui.resources.tools_dns_idle
import app.skipi.ui.resources.tools_dns_leak_title
import app.skipi.ui.resources.tools_dns_no_leak
import app.skipi.ui.resources.tools_dns_reason_no_internet
import app.skipi.ui.resources.tools_dns_reason_tunnel_not_passing
import app.skipi.ui.resources.tools_dns_description
import app.skipi.ui.resources.tools_dns_resolver_entry
import app.skipi.ui.resources.tools_dns_resolvers_title
import app.skipi.ui.resources.tools_dns_running
import app.skipi.ui.resources.tools_dns_suspected_leak
import app.skipi.ui.resources.tools_dns_unknown
import app.skipi.ui.resources.tools_ip_info_asn
import app.skipi.ui.resources.tools_ip_info_capital
import app.skipi.ui.resources.tools_ip_info_card_location
import app.skipi.ui.resources.tools_ip_info_card_network
import app.skipi.ui.resources.tools_ip_info_card_time
import app.skipi.ui.resources.tools_ip_info_city
import app.skipi.ui.resources.tools_ip_info_continent
import app.skipi.ui.resources.tools_ip_info_coordinates
import app.skipi.ui.resources.tools_ip_info_copied_all
import app.skipi.ui.resources.tools_ip_info_copied_ip
import app.skipi.ui.resources.tools_ip_info_copy_all
import app.skipi.ui.resources.tools_ip_info_country
import app.skipi.ui.resources.tools_ip_info_domain
import app.skipi.ui.resources.tools_ip_info_failed
import app.skipi.ui.resources.tools_ip_info_ipv4
import app.skipi.ui.resources.tools_ip_info_ipv6
import app.skipi.ui.resources.tools_ip_info_isp
import app.skipi.ui.resources.tools_ip_info_loading
import app.skipi.ui.resources.tools_ip_info_local_time
import app.skipi.ui.resources.tools_ip_info_org
import app.skipi.ui.resources.tools_ip_info_postal
import app.skipi.ui.resources.tools_ip_info_refresh
import app.skipi.ui.resources.tools_ip_info_region
import app.skipi.ui.resources.tools_ip_info_timezone
import app.skipi.ui.resources.tools_ip_info_title
import app.skipi.ui.resources.tools_ip_info_tunnel_direct
import app.skipi.ui.resources.tools_ip_info_tunnel_vpn
import app.skipi.ui.resources.tools_ip_info_utc_offset
import app.skipi.ui.resources.tools_speed_test_title
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import features.tools.dnsleak.DnsLeakFailureKind
import features.tools.dnsleak.DnsLeakVerdict
import features.tools.ipinfo.IpInfoData
import features.tools.ipinfo.IpInfoState
import features.tools.speedtest.SpeedTestPhase
import features.tools.speedtest.SpeedTestState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Text the host copies to its clipboard and then confirms to the user with [confirmation]. */
typealias DiagnosticsCopyHandler = (text: String, confirmation: String) -> Unit

@Composable
private fun DiagnosticsPageScaffold(
    title: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues, ScrollBehavior) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = SkipiTheme.colors.background,
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = actions,
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        val base = pageListPadding(contentPadding)
        val direction = LocalLayoutDirection.current
        // Match the app-wide horizontal content margin shared by every tool page.
        val listPadding = PaddingValues(
            top = base.calculateTopPadding(),
            bottom = base.calculateBottomPadding(),
            start = base.calculateStartPadding(direction) + 16.dp,
            end = base.calculateEndPadding(direction) + 16.dp,
        )
        content(listPadding, scrollBehavior)
    }
}

/** Full-screen connection speed test: ping, jitter, download and upload. */
@Composable
fun SkipiSpeedTestScreen(
    state: SpeedTestState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    DiagnosticsPageScaffold(
        title = stringResource(Res.string.tools_speed_test_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = onBack,
    ) { listPadding, scrollBehavior ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(scrollBehavior)
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
                result = state.result?.let { SpeedTestUiResult(it.pingMs, it.jitterMs, it.downloadMbps, it.uploadMbps) },
                errorMessage = state.errorMessage,
                onStart = onStart,
                onStop = onStop,
            )
        }
    }
}

/** Full-screen DNS leak test: shows which resolvers really see the queries. */
@Composable
fun SkipiDnsLeakTestScreen(
    state: DnsLeakTestUiState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val outcome = state.outcome
    DiagnosticsPageScaffold(
        title = stringResource(Res.string.tools_dns_leak_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = onBack,
    ) { listPadding, scrollBehavior ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
        ) {
            item(key = "description") {
                SmallTitle(text = stringResource(Res.string.tools_dns_resolvers_title))
                val statusText = stringResource(
                    when {
                        state.failed -> Res.string.tools_dns_failed
                        state.running -> Res.string.tools_dns_running
                        outcome == null -> Res.string.tools_dns_idle
                        outcome.verdict == DnsLeakVerdict.NoLeak -> Res.string.tools_dns_no_leak
                        outcome.verdict == DnsLeakVerdict.SuspectedLeak -> Res.string.tools_dns_suspected_leak
                        else -> Res.string.tools_dns_unknown
                    },
                )
                val failureDetail = when (state.failureKind) {
                    DnsLeakFailureKind.NoInternet -> stringResource(Res.string.tools_dns_reason_no_internet)
                    DnsLeakFailureKind.TunnelNotPassing -> stringResource(Res.string.tools_dns_reason_tunnel_not_passing)
                    null -> state.failedReason?.let { stringResource(Res.string.tools_dns_failed_detail, it) }
                }
                SkipiDnsLeakSummary(
                    description = stringResource(Res.string.tools_dns_description),
                    statusText = statusText,
                    detailText = failureDetail,
                    isError = state.failed || outcome?.verdict == DnsLeakVerdict.SuspectedLeak,
                    isRunning = state.running,
                    exitIp = outcome?.exit?.ip,
                    exitCountry = outcome?.exit?.let { it.countryName.ifBlank { it.countryCode } },
                    onStart = onStart,
                    onStop = onStop,
                )
            }
            outcome?.resolvers?.forEachIndexed { index, resolver ->
                item(key = "resolver_$index") {
                    SmallTitle(text = stringResource(Res.string.tools_dns_resolver_entry, index + 1))
                    SkipiDnsLeakResolverCard(
                        DnsLeakResolverUi(
                            server = resolver.server,
                            isSystemServer = resolver.isSystemServer,
                            observedIp = resolver.observedIp,
                            clientSubnetIp = resolver.clientSubnetIp,
                            observedCountryCode = resolver.observedCountryCode,
                            isp = resolver.isp,
                        ),
                    )
                }
            }
        }
    }
}

/** Full-screen IP details of the active connection; the first visit starts a lookup. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiIpInfoScreen(
    state: IpInfoState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: DiagnosticsCopyHandler,
) {
    LaunchedEffect(Unit) {
        if (state is IpInfoState.Idle) onRefresh()
    }
    val copiedIp = stringResource(Res.string.tools_ip_info_copied_ip)
    val copiedAll = stringResource(Res.string.tools_ip_info_copied_all)
    DiagnosticsPageScaffold(
        title = stringResource(Res.string.tools_ip_info_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = onBack,
        actions = { NavigationIcon(onClick = onRefresh, imageVector = MiuixIcons.Refresh) },
    ) { listPadding, scrollBehavior ->
        val lazyListState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
                contentPadding = listPadding,
            ) {
                when (state) {
                    is IpInfoState.Loading, is IpInfoState.Idle -> item(key = "loading") { IpInfoLoadingCard() }
                    is IpInfoState.Failure -> item(key = "failure") { IpInfoFailureCard(state.errorMessage, onRefresh) }
                    is IpInfoState.Success -> ipInfoItems(state.data, onRefresh, onCopy, copiedIp, copiedAll)
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = listPadding,
            )
        }
    }
}

@Composable
private fun IpInfoLoadingCard() {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            InfiniteProgressIndicator(modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.tools_ip_info_loading),
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun IpInfoFailureCard(errorMessage: String?, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.error.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    fontSize = 24.sp,
                    fontWeight = themedFontWeight(FontWeight.Bold),
                    color = MiuixTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.tools_ip_info_failed),
                fontSize = 16.sp,
                fontWeight = themedFontWeight(FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            TextButton(text = stringResource(Res.string.tools_ip_info_refresh), onClick = onRefresh)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ipInfoItems(
    data: IpInfoData,
    onRefresh: () -> Unit,
    onCopy: DiagnosticsCopyHandler,
    copiedIp: String,
    copiedAll: String,
) {
    item(key = "ip_main") { IpInfoMainCard(data, onCopy, copiedIp) }

    val locationEntries = buildList<Pair<StringResource, String>> {
        if (data.country.isNotBlank()) {
            add(Res.string.tools_ip_info_country to (data.country + if (data.countryCode.isNotBlank()) " (${data.countryCode})" else ""))
        }
        if (data.region.isNotBlank()) add(Res.string.tools_ip_info_region to data.region)
        if (data.city.isNotBlank()) add(Res.string.tools_ip_info_city to data.city)
        if (data.postal.isNotBlank()) add(Res.string.tools_ip_info_postal to data.postal)
        if (data.continent.isNotBlank()) add(Res.string.tools_ip_info_continent to data.continent)
        if (data.capital.isNotBlank()) add(Res.string.tools_ip_info_capital to data.capital)
        if (data.latitude != null && data.longitude != null) {
            add(Res.string.tools_ip_info_coordinates to "${data.latitude}, ${data.longitude}")
        }
    }
    infoCard("card_location", Res.string.tools_ip_info_card_location, locationEntries)

    val networkEntries = buildList<Pair<StringResource, String>> {
        if (data.isp.isNotBlank()) add(Res.string.tools_ip_info_isp to data.isp)
        if (data.org.isNotBlank() && data.org != data.isp) add(Res.string.tools_ip_info_org to data.org)
        if (data.asn != null) add(Res.string.tools_ip_info_asn to "AS${data.asn}")
        if (data.domain.isNotBlank()) add(Res.string.tools_ip_info_domain to data.domain)
    }
    infoCard("card_network", Res.string.tools_ip_info_card_network, networkEntries)

    val timeEntries = buildList<Pair<StringResource, String>> {
        if (data.timezoneId.isNotBlank()) {
            add(Res.string.tools_ip_info_timezone to (data.timezoneId + if (data.timezoneAbbr.isNotBlank()) " (${data.timezoneAbbr})" else ""))
        }
        if (data.currentTime.isNotBlank()) add(Res.string.tools_ip_info_local_time to data.currentTime)
        if (data.timezoneUtc.isNotBlank()) add(Res.string.tools_ip_info_utc_offset to "UTC ${data.timezoneUtc}")
    }
    infoCard("card_time", Res.string.tools_ip_info_card_time, timeEntries)

    item(key = "actions") {
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = stringResource(Res.string.tools_ip_info_refresh),
                modifier = Modifier.weight(1f),
                onClick = onRefresh,
            )
            TextButton(
                text = stringResource(Res.string.tools_ip_info_copy_all),
                modifier = Modifier.weight(1f),
                onClick = { onCopy(data.toSummaryText(), copiedAll) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.infoCard(
    key: String,
    title: StringResource,
    entries: List<Pair<StringResource, String>>,
) {
    if (entries.isEmpty()) return
    item(key = key) {
        SmallTitle(text = stringResource(title))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                entries.forEachIndexed { index, (label, value) ->
                    if (index > 0) HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    SkipiIpInfoRow(label = stringResource(label), value = value)
                }
            }
        }
    }
}

@Composable
private fun IpInfoMainCard(data: IpInfoData, onCopy: DiagnosticsCopyHandler, copiedIp: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (data.isVpnTunnel) {
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (data.isVpnTunnel) Res.string.tools_ip_info_tunnel_vpn else Res.string.tools_ip_info_tunnel_direct,
                        ),
                        fontSize = 12.sp,
                        fontWeight = themedFontWeight(FontWeight.Medium),
                        color = if (data.isVpnTunnel) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = data.ipType,
                        fontSize = 11.sp,
                        fontWeight = themedFontWeight(FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            val ipv4Label = stringResource(Res.string.tools_ip_info_ipv4)
            val ipv6Label = stringResource(Res.string.tools_ip_info_ipv6)
            if (!data.ipv4.isNullOrBlank()) {
                SkipiIpAddressRow(
                    label = ipv4Label,
                    ip = data.ipv4.orEmpty(),
                    copyDescription = stringResource(Res.string.common_copy_field, ipv4Label),
                    onCopy = { onCopy(data.ipv4.orEmpty(), copiedIp) },
                )
            }
            if (!data.ipv4.isNullOrBlank() && !data.ipv6.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                Spacer(Modifier.height(10.dp))
            }
            if (!data.ipv6.isNullOrBlank()) {
                SkipiIpAddressRow(
                    label = ipv6Label,
                    ip = data.ipv6.orEmpty(),
                    copyDescription = stringResource(Res.string.common_copy_field, ipv6Label),
                    onCopy = { onCopy(data.ipv6.orEmpty(), copiedIp) },
                )
            }

            if (data.country.isNotBlank() || data.city.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (data.flagEmoji.isNotBlank()) {
                        Text(text = data.flagEmoji, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = listOf(data.city, data.country).filter(String::isNotBlank).joinToString(", "),
                            fontSize = 15.sp,
                            fontWeight = themedFontWeight(FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        if (data.isp.isNotBlank() || data.org.isNotBlank()) {
                            Text(
                                text = data.isp.ifBlank { data.org },
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}
