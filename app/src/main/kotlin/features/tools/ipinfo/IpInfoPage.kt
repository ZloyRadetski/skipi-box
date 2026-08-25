// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.ipinfo

import androidx.compose.foundation.background
import ui.text.themedFontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppChromeState
import app.LocalAppServices
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import engine.network.TunnelNetworks
import engine.proxy.LocalProxyRuntime
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.clipboard.setPlainText
import ui.components.BackNavigationIcon
import ui.components.NavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun IpInfoPage(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val tipNotifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val state = IpInfoSession.state

    fun startRefresh() {
        val vpnNetwork = TunnelNetworks.locateVpnNetwork(context)
        val proxyOptions = LocalProxyRuntime.current()
        IpInfoSession.refresh {
            IpInfoEngine(
                vpnNetwork = vpnNetwork,
                proxyOptions = proxyOptions,
            ).fetch()
        }
    }

    LaunchedEffect(Unit) {
        if (state is IpInfoState.Idle) {
            startRefresh()
        }
    }

    val copiedIpMessage = stringResource(R.string.tools_ip_info_copied_ip)
    val copiedAllMessage = stringResource(R.string.tools_ip_info_copied_all)

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.tools_ip_info_title),
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        BackNavigationIcon(onClick = {
                            IpInfoSession.stop()
                            navigator.pop()
                        })
                    },
                    actions = {
                        NavigationIcon(
                            onClick = ::startRefresh,
                            imageVector = MiuixIcons.Refresh,
                        )
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

        val lazyListState = rememberLazyListState()

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = listPadding,
            ) {
                    when (state) {
                        is IpInfoState.Loading, is IpInfoState.Idle -> {
                            item(key = "loading") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        InfiniteProgressIndicator(
                                            modifier = Modifier.size(40.dp),
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = stringResource(R.string.tools_ip_info_loading),
                                            fontSize = 15.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                        }

                        is IpInfoState.Failure -> {
                            item(key = "failure") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
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
                                            text = stringResource(R.string.tools_ip_info_failed),
                                            fontSize = 16.sp,
                                            fontWeight = themedFontWeight(FontWeight.SemiBold),
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                        if (!state.errorMessage.isNullOrBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = state.errorMessage,
                                                fontSize = 13.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        TextButton(
                                            text = stringResource(R.string.tools_ip_info_refresh),
                                            onClick = ::startRefresh,
                                        )
                                    }
                                }
                            }
                        }

                        is IpInfoState.Success -> {
                            val data = state.data

                            // 1. Primary IP Summary Card
                            item(key = "ip_main") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 12.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                    ) {
                                        // Route badge (VPN vs Direct) + IP Type
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
                                                        if (data.isVpnTunnel) {
                                                            R.string.tools_ip_info_tunnel_vpn
                                                        } else {
                                                            R.string.tools_ip_info_tunnel_direct
                                                        },
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

                                        // IPv4 Entry (if present)
                                        if (!data.ipv4.isNullOrBlank()) {
                                            IpAddressRow(
                                                label = stringResource(R.string.tools_ip_info_ipv4),
                                                ip = data.ipv4,
                                                onCopy = {
                                                    scope.launch {
                                                        clipboard.setPlainText(data.ipv4)
                                                        tipNotifier.show(copiedIpMessage)
                                                    }
                                                },
                                            )
                                        }

                                        // Divider if both are present
                                        if (!data.ipv4.isNullOrBlank() && !data.ipv6.isNullOrBlank()) {
                                            Spacer(Modifier.height(10.dp))
                                            HorizontalDivider(
                                                color = MiuixTheme.colorScheme.dividerLine,
                                            )
                                            Spacer(Modifier.height(10.dp))
                                        }

                                        // IPv6 Entry (if present)
                                        if (!data.ipv6.isNullOrBlank()) {
                                            IpAddressRow(
                                                label = stringResource(R.string.tools_ip_info_ipv6),
                                                ip = data.ipv6,
                                                onCopy = {
                                                    scope.launch {
                                                        clipboard.setPlainText(data.ipv6)
                                                        tipNotifier.show(copiedIpMessage)
                                                    }
                                                },
                                            )
                                        }

                                        if (data.country.isNotBlank() || data.city.isNotBlank()) {
                                            Spacer(Modifier.height(12.dp))
                                            HorizontalDivider(
                                                color = MiuixTheme.colorScheme.dividerLine,
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                if (data.flagEmoji.isNotBlank()) {
                                                    Text(
                                                        text = data.flagEmoji,
                                                        fontSize = 22.sp,
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                }
                                                Column {
                                                    Text(
                                                        text = listOf(data.city, data.country)
                                                            .filter(String::isNotBlank)
                                                            .joinToString(", "),
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

                            // 2. Location Card
                            val locationEntries = buildList {
                                if (data.country.isNotBlank()) add(R.string.tools_ip_info_country to (data.country + if (data.countryCode.isNotBlank()) " (${data.countryCode})" else ""))
                                if (data.region.isNotBlank()) add(R.string.tools_ip_info_region to data.region)
                                if (data.city.isNotBlank()) add(R.string.tools_ip_info_city to data.city)
                                if (data.postal.isNotBlank()) add(R.string.tools_ip_info_postal to data.postal)
                                if (data.continent.isNotBlank()) add(R.string.tools_ip_info_continent to data.continent)
                                if (data.capital.isNotBlank()) add(R.string.tools_ip_info_capital to data.capital)
                                if (data.latitude != null && data.longitude != null) {
                                    add(R.string.tools_ip_info_coordinates to "${data.latitude}, ${data.longitude}")
                                }
                            }
                            if (locationEntries.isNotEmpty()) {
                                item(key = "card_location") {
                                    SmallTitle(text = stringResource(R.string.tools_ip_info_card_location))
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                            locationEntries.forEachIndexed { index, (labelRes, value) ->
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        color = MiuixTheme.colorScheme.dividerLine,
                                                    )
                                                }
                                                InfoRow(
                                                    label = stringResource(labelRes),
                                                    value = value,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Network & Provider Card
                            val networkEntries = buildList {
                                if (data.isp.isNotBlank()) add(R.string.tools_ip_info_isp to data.isp)
                                if (data.org.isNotBlank() && data.org != data.isp) add(R.string.tools_ip_info_org to data.org)
                                if (data.asn != null) add(R.string.tools_ip_info_asn to "AS${data.asn}")
                                if (data.domain.isNotBlank()) add(R.string.tools_ip_info_domain to data.domain)
                            }
                            if (networkEntries.isNotEmpty()) {
                                item(key = "card_network") {
                                    SmallTitle(text = stringResource(R.string.tools_ip_info_card_network))
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                            networkEntries.forEachIndexed { index, (labelRes, value) ->
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        color = MiuixTheme.colorScheme.dividerLine,
                                                    )
                                                }
                                                InfoRow(
                                                    label = stringResource(labelRes),
                                                    value = value,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Timezone Card
                            val timeEntries = buildList {
                                if (data.timezoneId.isNotBlank()) add(R.string.tools_ip_info_timezone to (data.timezoneId + if (data.timezoneAbbr.isNotBlank()) " (${data.timezoneAbbr})" else ""))
                                if (data.currentTime.isNotBlank()) add(R.string.tools_ip_info_local_time to data.currentTime)
                                if (data.timezoneUtc.isNotBlank()) add(R.string.tools_ip_info_utc_offset to "UTC ${data.timezoneUtc}")
                            }
                            if (timeEntries.isNotEmpty()) {
                                item(key = "card_time") {
                                    SmallTitle(text = stringResource(R.string.tools_ip_info_card_time))
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                            timeEntries.forEachIndexed { index, (labelRes, value) ->
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        color = MiuixTheme.colorScheme.dividerLine,
                                                    )
                                                }
                                                InfoRow(
                                                    label = stringResource(labelRes),
                                                    value = value,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Actions Card
                            item(key = "actions") {
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    TextButton(
                                        text = stringResource(R.string.tools_ip_info_refresh),
                                        modifier = Modifier.weight(1f),
                                        onClick = ::startRefresh,
                                    )
                                    TextButton(
                                        text = stringResource(R.string.tools_ip_info_copy_all),
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            scope.launch {
                                                clipboard.setPlainText(data.toSummaryText())
                                                tipNotifier.show(copiedAllMessage)
                                            }
                                        },
                                    )
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = listPadding,
            )
        }
    }
}

@Composable
private fun IpAddressRow(
    label: String,
    ip: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = themedFontWeight(FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = ip,
                fontSize = if (ip.length > 20) 17.sp else 22.sp,
                fontWeight = themedFontWeight(FontWeight.Bold),
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Copy,
                contentDescription = stringResource(R.string.common_copy_field, label),
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = themedFontWeight(FontWeight.Medium),
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
