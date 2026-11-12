// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.Icons
import app.skipi.ui.layout.withTopBarGap
import app.skipi.ui.layout.fadeUnderTopBar
import androidx.compose.foundation.background
import app.skipi.ui.text.themedFontWeight
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
import androidx.compose.runtime.setValue
import app.skipi.ui.components.AppSlider
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.layout.pageScrollModifiers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.modes.SubscriptionPingModeHttp
import app.modes.SubscriptionPingModeTcp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.*
import features.subscription.SubscriptionPingSettingsRules
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color

data class SubscriptionPingPageState(
    val mode: Int,
    val concurrency: Int,
    val timeoutMillis: String,
    val url: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkipiSubscriptionPingSettingsScreen(
    state: SubscriptionPingPageState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onModeChange: (Int) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onTimeoutMillisChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onPresetSelected: (String) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    val currentTimeoutSeconds = ((state.timeoutMillis.toIntOrNull() ?: 5000) / 1000).coerceIn(1, 30)
    var sliderValue by remember(currentTimeoutSeconds) { mutableFloatStateOf(currentTimeoutSeconds.toFloat()) }

    val currentConcurrency = state.concurrency.coerceIn(1, 32)
    var concurrencySliderValue by remember(currentConcurrency) { mutableFloatStateOf(currentConcurrency.toFloat()) }

    val urlError = if (SubscriptionPingSettingsRules.isValidHttpUrl(state.url)) null else {
        stringResource(Res.string.subscription_ping_url_invalid)
    }

    val urlPresets = listOf(
        "Cloudflare" to "http://cp.cloudflare.com/generate_204",
        "Apple" to "http://captive.apple.com/hotspot-detect.html",
        "Google" to "http://www.google.com/generate_204",
    )

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(Res.string.subscription_ping_settings),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
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
                    .fadeUnderTopBar(contentPadding.calculateTopPadding())
                    .pageScrollModifiers(scrollBehavior),
                contentPadding = pageListPadding(contentPadding).withTopBarGap(),
            ) {
                // Section: Ping Mode (HTTP vs TCP)
                item(key = "section_mode_title") {
                    SmallTitle(text = stringResource(Res.string.subscription_ping_mode_section))
                }

                item(key = "section_mode_card") {
                    SkipiSettingsSectionCard(bottomPadding = 12.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val isHttp = state.mode == SubscriptionPingModeHttp
                                val isTcp = state.mode == SubscriptionPingModeTcp

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isHttp) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        )
                                        .clickable {
                                            onModeChange(SubscriptionPingModeHttp)
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
                                            text = stringResource(Res.string.subscription_ping_mode_real_connection),
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
                                            onModeChange(SubscriptionPingModeTcp)
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
                                            text = stringResource(Res.string.subscription_ping_mode_direct_connect),
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
                    SmallTitle(text = stringResource(Res.string.subscription_ping_concurrency_section))
                }

                item(key = "section_concurrency_card") {
                    SkipiSettingsSectionCard(bottomPadding = 12.dp) {
                        SkipiSliderPreference(
                            title = stringResource(Res.string.subscription_ping_concurrency_label),
                            startAction = settingIcon(Icons.Rounded.Bolt),
                            valueLabel = "${concurrencySliderValue.roundToInt()}",
                            value = concurrencySliderValue,
                            onValueChange = { newValue -> concurrencySliderValue = newValue },
                            onValueChangeFinished = {
                                onConcurrencyChange(concurrencySliderValue.roundToInt().coerceIn(1, 32))
                            },
                            valueRange = 1f..32f,
                            steps = 30,
                            minLabel = "1",
                            maxLabel = "32",
                        )
                    }
                }

                // Section: Timeout Slider
                item(key = "section_timeout_title") {
                    SmallTitle(text = stringResource(Res.string.subscription_ping_timeout_section))
                }

                item(key = "section_timeout_card") {
                    SkipiSettingsSectionCard(bottomPadding = 12.dp) {
                        val secondsUnit = stringResource(Res.string.unit_seconds_short)
                        SkipiSliderPreference(
                            title = stringResource(Res.string.subscription_ping_timeout_label),
                            startAction = settingIcon(Icons.Rounded.Timer),
                            valueLabel = "${sliderValue.roundToInt()} $secondsUnit",
                            value = sliderValue,
                            onValueChange = { newValue -> sliderValue = newValue },
                            onValueChangeFinished = {
                                val seconds = sliderValue.roundToInt().coerceIn(1, 30)
                                onTimeoutMillisChange("${seconds * 1000}")
                            },
                            valueRange = 1f..30f,
                            steps = 28,
                            minLabel = "1 $secondsUnit",
                            maxLabel = "30 $secondsUnit",
                        )
                    }
                }

                // Section: URL Configuration
                item(key = "section_url_title") {
                    SmallTitle(text = stringResource(Res.string.subscription_ping_url))
                }

                item(key = "section_url_card") {
                    SkipiSettingsSectionCard(bottomPadding = 12.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            TextField(
                                value = state.url,
                                onValueChange = onUrlChange,
                                label = stringResource(Res.string.subscription_ping_url_label),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            SkipiSettingsReveal(visible = urlError != null) {
                                Text(
                                    text = urlError.orEmpty(),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = stringResource(Res.string.subscription_ping_quick_select),
                                fontSize = 12.sp,
                                fontWeight = themedFontWeight(FontWeight.Medium),
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
                                    val isSelected = state.url == presetUrl
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MiuixTheme.colorScheme.primary
                                                else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
                                            .clickable {
                                                onUrlChange(presetUrl)
                                                onPresetSelected(name)
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

