// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ui.components.AppSlider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.modes.SubscriptionPingModeHttp
import app.modes.SubscriptionPingModeTcp
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import features.subscription.SubscriptionExpiryReminderList
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.components.BackNavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import java.net.URI
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionPingSettingsPage(
    padding: PaddingValues,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val tipNotifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()

    val currentTimeoutSeconds = ((appState.subscriptionPingTimeoutMillis.toIntOrNull() ?: 5000) / 1000).coerceIn(1, 30)
    var sliderValue by remember(currentTimeoutSeconds) { mutableFloatStateOf(currentTimeoutSeconds.toFloat()) }

    val currentConcurrency = appState.subscriptionPingConcurrency.coerceIn(1, 32)
    var concurrencySliderValue by remember(currentConcurrency) { mutableFloatStateOf(currentConcurrency.toFloat()) }

    val urlError = if (appState.subscriptionPingUrl.isSubscriptionPingUrl()) null else {
        stringResource(R.string.subscription_ping_url_invalid)
    }

    val urlPresets = listOf(
        "Cloudflare" to "http://cp.cloudflare.com/generate_204",
        "Apple" to "http://captive.apple.com/hotspot-detect.html",
        "Google" to "http://www.google.com/generate_204",
    )

    Scaffold(
        containerColor = AppTheme.colors.background,
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.subscription_ping_settings),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = navigator::pop) },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )

        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(scrollBehavior),
                contentPadding = pageListPadding(contentPadding),
            ) {
                // Section: Ping Mode (HTTP vs TCP)
                item(key = "section_mode_title") {
                    SmallTitle(text = stringResource(R.string.subscription_ping_mode_section))
                }

                item(key = "section_mode_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val isHttp = appState.subscriptionPingMode == SubscriptionPingModeHttp
                                val isTcp = appState.subscriptionPingMode == SubscriptionPingModeTcp

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isHttp) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        )
                                        .clickable {
                                            updateAppState { it.copy(subscriptionPingMode = SubscriptionPingModeHttp) }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "HTTP",
                                            fontSize = 14.sp,
                                            fontWeight = if (isHttp) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isHttp) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(R.string.subscription_ping_mode_real_connection),
                                            fontSize = 11.sp,
                                            color = if (isHttp) MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isTcp) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        )
                                        .clickable {
                                            updateAppState { it.copy(subscriptionPingMode = SubscriptionPingModeTcp) }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "TCP",
                                            fontSize = 14.sp,
                                            fontWeight = if (isTcp) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isTcp) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(R.string.subscription_ping_mode_direct_connect),
                                            fontSize = 11.sp,
                                            color = if (isTcp) MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: Concurrency Slider
                item(key = "section_concurrency_title") {
                    SmallTitle(text = stringResource(R.string.subscription_ping_concurrency_section))
                }

                item(key = "section_concurrency_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.subscription_ping_concurrency_label),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${concurrencySliderValue.roundToInt()}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            AppSlider(
                                value = concurrencySliderValue,
                                onValueChange = { newValue ->
                                    concurrencySliderValue = newValue
                                },
                                onValueChangeFinished = {
                                    val concurrency = concurrencySliderValue.roundToInt().coerceIn(1, 32)
                                    updateAppState {
                                        it.copy(subscriptionPingConcurrency = concurrency)
                                    }
                                },
                                valueRange = 1f..32f,
                                steps = 30,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "1",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                                Text(
                                    text = "32",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }

                // Section: Timeout Slider
                item(key = "section_timeout_title") {
                    SmallTitle(text = stringResource(R.string.subscription_ping_timeout_section))
                }

                item(key = "section_timeout_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val secondsUnit = stringResource(R.string.unit_seconds_short)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.subscription_ping_timeout_label),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${sliderValue.roundToInt()} $secondsUnit",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            AppSlider(
                                value = sliderValue,
                                onValueChange = { newValue ->
                                    sliderValue = newValue
                                },
                                onValueChangeFinished = {
                                    val seconds = sliderValue.roundToInt().coerceIn(1, 30)
                                    updateAppState {
                                        it.copy(subscriptionPingTimeoutMillis = "${seconds * 1000}")
                                    }
                                },
                                valueRange = 1f..30f,
                                steps = 28,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "1 $secondsUnit",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                                Text(
                                    text = "30 $secondsUnit",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }

                // Section: URL Configuration
                item(key = "section_url_title") {
                    SmallTitle(text = stringResource(R.string.subscription_ping_url))
                }

                item(key = "section_url_card") {
                    SettingsSectionCard {
                        val selectedTestToastTemplate = stringResource(R.string.subscription_ping_selected_test)
                        Column(modifier = Modifier.padding(16.dp)) {
                            TextField(
                                value = appState.subscriptionPingUrl,
                                onValueChange = { newUrl ->
                                    updateAppState { it.copy(subscriptionPingUrl = newUrl.trim()) }
                                },
                                label = stringResource(R.string.subscription_ping_url_label),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (urlError != null) {
                                Text(
                                    text = urlError,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = stringResource(R.string.subscription_ping_quick_select),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3 Quick Preset Buttons
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                urlPresets.forEach { (name, presetUrl) ->
                                    val isSelected = appState.subscriptionPingUrl == presetUrl
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MiuixTheme.colorScheme.primary
                                                else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
                                            .clickable {
                                                updateAppState { it.copy(subscriptionPingUrl = presetUrl) }
                                                scope.launch {
                                                    tipNotifier.show(String.format(selectedTestToastTemplate, name))
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = name,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary
                                            else MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.isSubscriptionPingUrl(): Boolean {
    return runCatching {
        val uri = URI(trim())
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

