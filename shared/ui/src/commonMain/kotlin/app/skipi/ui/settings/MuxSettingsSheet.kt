// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import app.skipi.ui.components.AppWindowBottomSheet
import app.skipi.ui.components.AppWindowDropdownPreference
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.app.settings.isValidMuxConcurrency
import app.skipi.app.settings.isValidMuxXudpConcurrency
import app.skipi.app.settings.normalizeMuxInteger
import app.skipi.app.settings.sanitizeMuxUdp443Index as sanitizeMuxUdp443IndexInApp
import app.skipi.app.settings.MuxSettingsDraft
import app.skipi.app.settings.toSavedSettings
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxXudpConcurrency
import engine.xray.MuxUdp443Values
import app.skipi.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import app.skipi.ui.text.formatTemplate

@Composable
fun muxSettingsSummary(
    enabled: Boolean,
    concurrency: String,
    xudpConcurrency: String,
    xudpProxyUdp443: Int,
): String {
    if (!enabled) {
        return stringResource(Res.string.settings_mux_none)
    }
    return stringResource(Res.string.settings_mux_selected).formatTemplate(
        "tcp" to muxConcurrencyDisplay(concurrency),
        "xudp" to muxXudpConcurrencyDisplay(xudpConcurrency),
        "udp443" to muxUdp443Options()[sanitizeMuxUdp443Index(xudpProxyUdp443)],
    )
}

fun sanitizeMuxIntegerInput(input: String): String {
    return input
        .filterIndexed { index, char -> char.isDigit() || (char == '-' && index == 0) }
        .take(5)
}

/** Compatibility helper used by the Android persistence adapter. */
fun sanitizeMuxUdp443Index(index: Int): Int = sanitizeMuxUdp443IndexInApp(index)

@Composable
fun MuxSettingsBottomSheet(
    show: Boolean,
    enabled: Boolean,
    concurrency: String,
    xudpConcurrency: String,
    xudpProxyUdp443: Int,
    onEnabledChange: (Boolean) -> Unit,
    onConcurrencyChange: (String) -> Unit,
    onXudpConcurrencyChange: (String) -> Unit,
    onXudpProxyUdp443Change: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (Boolean, String, String, Int) -> Unit,
) {
    val concurrencyError = enabled && !isValidMuxConcurrency(concurrency)
    val xudpConcurrencyError = enabled && !isValidMuxXudpConcurrency(xudpConcurrency)
    val canSave = !enabled || (!concurrencyError && !xudpConcurrencyError)
    val saveSettings = {
        if (canSave) {
            val saved = MuxSettingsDraft(enabled, concurrency, xudpConcurrency, xudpProxyUdp443).toSavedSettings()
            onSave(saved.enabled, saved.concurrency, saved.xudpConcurrency, saved.xudpProxyUdp443)
        }
    }

    AppWindowBottomSheet(
        show = show,
        title = stringResource(Res.string.settings_mux),
        startAction = {
            TextButton(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(Res.string.common_save),
                onClick = saveSettings,
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        key(show) {
            SettingsSheetContent {
                MuxStatusText(stringResource(Res.string.settings_mux_description))
                SwitchPreference(
                    title = stringResource(Res.string.settings_mux_enabled),
                    summary = stringResource(Res.string.settings_mux_enabled_summary),
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.padding(bottom = if (enabled) 12.dp else 0.dp),
                )
                AnimatedVisibility(
                    visible = enabled,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MuxNumberField(
                            value = concurrency,
                            onValueChange = onConcurrencyChange,
                            label = stringResource(Res.string.settings_mux_concurrency),
                            helpText = stringResource(Res.string.settings_mux_concurrency_summary),
                            errorText = if (concurrencyError) {
                                stringResource(Res.string.settings_mux_concurrency_error)
                            } else {
                                null
                            },
                        )
                        MuxNumberField(
                            value = xudpConcurrency,
                            onValueChange = onXudpConcurrencyChange,
                            label = stringResource(Res.string.settings_mux_xudp_concurrency),
                            helpText = stringResource(Res.string.settings_mux_xudp_concurrency_summary),
                            errorText = if (xudpConcurrencyError) {
                                stringResource(Res.string.settings_mux_xudp_concurrency_error)
                            } else {
                                null
                            },
                        )
                        AppWindowDropdownPreference(
                            title = stringResource(Res.string.settings_mux_udp443),
                            items = muxUdp443Options(),
                            selectedIndex = sanitizeMuxUdp443Index(xudpProxyUdp443),
                            onSelectedIndexChange = onXudpProxyUdp443Change,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MuxNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    helpText: String,
    errorText: String?,
) {
    SheetTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        sanitizeInput = ::sanitizeMuxIntegerInput,
    )
    MuxStatusText(helpText)
    errorText?.let { MuxStatusText(text = it, error = true) }
}

@Composable
private fun MuxStatusText(
    text: String,
    error: Boolean = false,
) {
    Text(
        text = text,
        color = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun muxUdp443Options(): List<String> {
    return listOf(
        stringResource(Res.string.settings_mux_udp443_reject),
        stringResource(Res.string.settings_mux_udp443_allow),
        stringResource(Res.string.settings_mux_udp443_skip),
    )
}

@Composable
private fun muxConcurrencyDisplay(value: String): String {
    val concurrency = value.toIntOrNull()
    return when {
        concurrency == null -> stringResource(Res.string.settings_mux_display_default_8)
        concurrency < 0 -> stringResource(Res.string.settings_mux_display_disabled)
        concurrency == 0 -> stringResource(Res.string.settings_mux_display_default_8)
        else -> concurrency.toString()
    }
}

@Composable
private fun muxXudpConcurrencyDisplay(value: String): String {
    val concurrency = value.toIntOrNull()
    return when {
        concurrency == null -> stringResource(Res.string.settings_mux_display_same_as_tcp)
        concurrency < 0 -> stringResource(Res.string.settings_mux_display_disabled)
        concurrency == 0 -> stringResource(Res.string.settings_mux_display_same_as_tcp)
        else -> concurrency.toString()
    }
}

