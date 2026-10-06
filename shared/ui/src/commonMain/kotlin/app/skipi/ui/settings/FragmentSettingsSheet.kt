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
import app.skipi.app.settings.isValidFragmentRange
import app.skipi.app.settings.normalizeFragmentPackets
import app.skipi.app.settings.normalizeFragmentRange
import app.skipi.app.settings.FragmentSettingsDraft
import app.skipi.app.settings.toSavedSettings
import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import engine.xray.DefaultFragmentPackets
import engine.xray.FragmentPacketsValues
import engine.xray.MaxFragmentInputLength
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
fun fragmentSettingsSummary(
    enabled: Boolean,
    packets: String,
    length: String,
    interval: String,
): String {
    if (!enabled) {
        return stringResource(Res.string.settings_fragment_none)
    }
    return stringResource(Res.string.settings_fragment_selected).formatTemplate(
        "packets" to normalizeFragmentPackets(packets),
        "length" to normalizeFragmentRange(length, DefaultFragmentLength, min = 1),
        "interval" to normalizeFragmentRange(interval, DefaultFragmentInterval, min = 0),
    )
}

fun sanitizeFragmentRangeInput(input: String): String {
    return input
        .filter { char -> char.isDigit() || char == '-' }
        .take(MaxFragmentInputLength)
}

@Composable
fun FragmentSettingsBottomSheet(
    show: Boolean,
    enabled: Boolean,
    packets: String,
    length: String,
    interval: String,
    onEnabledChange: (Boolean) -> Unit,
    onPacketsChange: (String) -> Unit,
    onLengthChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (Boolean, String, String, String) -> Unit,
) {
    val lengthError = enabled && !isValidFragmentRange(length, min = 1)
    val intervalError = enabled && !isValidFragmentRange(interval, min = 0)
    val canSave = !enabled || (!lengthError && !intervalError)
    val saveSettings = {
        if (canSave) {
            val saved = FragmentSettingsDraft(enabled, packets, length, interval).toSavedSettings()
            onSave(saved.enabled, saved.packets, saved.length, saved.interval)
        }
    }

    AppWindowBottomSheet(
        show = show,
        title = stringResource(Res.string.settings_fragment),
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
                FragmentStatusText(stringResource(Res.string.settings_fragment_description))
                SwitchPreference(
                    title = stringResource(Res.string.settings_fragment_enabled),
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
                AnimatedVisibility(
                    visible = enabled,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppWindowDropdownPreference(
                            title = stringResource(Res.string.settings_fragment_packets),
                            items = FragmentPacketsValues,
                            selectedIndex = fragmentPacketsIndex(packets),
                            modifier = Modifier.padding(bottom = 12.dp),
                            onSelectedIndexChange = { index ->
                                onPacketsChange(FragmentPacketsValues[index.coerceIn(FragmentPacketsValues.indices)])
                            },
                        )
                        FragmentTextField(
                            value = length,
                            onValueChange = onLengthChange,
                            label = stringResource(Res.string.settings_fragment_length),
                            errorText = if (lengthError) {
                                stringResource(Res.string.settings_fragment_length_error)
                            } else {
                                null
                            },
                        )
                        FragmentTextField(
                            value = interval,
                            onValueChange = onIntervalChange,
                            label = stringResource(Res.string.settings_fragment_interval),
                            errorText = if (intervalError) {
                                stringResource(Res.string.settings_fragment_interval_error)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FragmentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorText: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        SheetTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth(),
            sanitizeInput = ::sanitizeFragmentRangeInput,
        )
        errorText?.let { FragmentStatusText(text = it, error = true) }
    }
}

@Composable
private fun FragmentStatusText(
    text: String,
    error: Boolean = false,
) {
    Text(
        text = text,
        color = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun fragmentPacketsIndex(value: String): Int {
    val index = FragmentPacketsValues.indexOf(normalizeFragmentPackets(value))
    return index.coerceAtLeast(0)
}
