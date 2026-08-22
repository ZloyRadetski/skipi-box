// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.compose.resources.stringResource
import app.shared.res.Res
import app.shared.res.color_picker_apply
import app.shared.res.color_picker_hex
import app.shared.res.color_picker_quick_presets
import app.shared.res.common_cancel
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme

private val PresetPaletteColors = listOf(
    Color(0xFFE53935), // Red
    Color(0xFFFF5722), // Deep Orange
    Color(0xFFFFB300), // Amber
    Color(0xFF43A047), // Green
    Color(0xFF00897B), // Teal
    Color(0xFF00ACC1), // Cyan
    Color(0xFF1E88E5), // Blue
    Color(0xFF3949AB), // Indigo
    Color(0xFF8E24AA), // Purple
    Color(0xFFD81B60), // Pink
    Color(0xFF282A31), // Surface Dark
    Color(0xFF16171A), // Background Dark
    Color(0xFFF5F6F8), // Background Light
    Color(0xFFFFFFFF), // White
)

private fun formatHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

private fun parseHex(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    return runCatching {
        when (clean.length) {
            6 -> Color(0xFF000000L or clean.toLong(16))
            8 -> Color(clean.toLong(16))
            3 -> {
                val r = clean[0]
                val g = clean[1]
                val b = clean[2]
                Color(0xFF000000L or "$r$r$g$g$b$b".toLong(16))
            }
            else -> null
        }
    }.getOrNull()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    show: Boolean,
    title: String,
    initialColor: Color,
    onDismissRequest: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    if (!show) return

    var red by remember(initialColor) { mutableIntStateOf((initialColor.red * 255f).toInt().coerceIn(0, 255)) }
    var green by remember(initialColor) { mutableIntStateOf((initialColor.green * 255f).toInt().coerceIn(0, 255)) }
    var blue by remember(initialColor) { mutableIntStateOf((initialColor.blue * 255f).toInt().coerceIn(0, 255)) }
    var hexText by remember(initialColor) { mutableStateOf(formatHex(initialColor)) }

    val currentColor = remember(red, green, blue) {
        Color(red = red / 255f, green = green / 255f, blue = blue / 255f, alpha = 1f)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                colors = CardDefaults.defaultColors(
                    color = AppTheme.colors.surface,
                    contentColor = AppTheme.colors.onSurface,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )

                    // Large preview badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(initialColor)
                                .border(1.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(14.dp)),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "→",
                            fontSize = 20.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(currentColor)
                                .border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                        )
                    }

                    // Hex input field
                    TextField(
                        value = hexText,
                        onValueChange = { input ->
                            hexText = input
                            val parsed = parseHex(input)
                            if (parsed != null) {
                                red = (parsed.red * 255f).toInt()
                                green = (parsed.green * 255f).toInt()
                                blue = (parsed.blue * 255f).toInt()
                            }
                        },
                        label = stringResource(Res.string.color_picker_hex),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                    )

                    // Quick presets
                    Text(
                        text = stringResource(Res.string.color_picker_quick_presets),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PresetPaletteColors.forEach { preset ->
                            val isSelected = currentColor.toArgb() == preset.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(preset)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.dividerLine,
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        red = (preset.red * 255f).toInt()
                                        green = (preset.green * 255f).toInt()
                                        blue = (preset.blue * 255f).toInt()
                                        hexText = formatHex(preset)
                                    },
                            )
                        }
                    }

                    // Red Slider
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "R: $red",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE53935),
                            modifier = Modifier.width(52.dp),
                        )
                        AppSlider(
                            value = red.toFloat(),
                            onValueChange = {
                                red = it.toInt().coerceIn(0, 255)
                                hexText = formatHex(Color(red = red / 255f, green = green / 255f, blue = blue / 255f))
                            },
                            valueRange = 0f..255f,
                            activeTrackColor = Color(0xFFE53935),
                            thumbColor = Color(0xFFE53935),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Green Slider
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "G: $green",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF43A047),
                            modifier = Modifier.width(52.dp),
                        )
                        AppSlider(
                            value = green.toFloat(),
                            onValueChange = {
                                green = it.toInt().coerceIn(0, 255)
                                hexText = formatHex(Color(red = red / 255f, green = green / 255f, blue = blue / 255f))
                            },
                            valueRange = 0f..255f,
                            activeTrackColor = Color(0xFF43A047),
                            thumbColor = Color(0xFF43A047),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Blue Slider
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "B: $blue",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E88E5),
                            modifier = Modifier.width(52.dp),
                        )
                        AppSlider(
                            value = blue.toFloat(),
                            onValueChange = {
                                blue = it.toInt().coerceIn(0, 255)
                                hexText = formatHex(Color(red = red / 255f, green = green / 255f, blue = blue / 255f))
                            },
                            valueRange = 0f..255f,
                            activeTrackColor = Color(0xFF1E88E5),
                            thumbColor = Color(0xFF1E88E5),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            text = stringResource(Res.string.common_cancel),
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(16.dp))
                        TextButton(
                            text = stringResource(Res.string.color_picker_apply),
                            onClick = {
                                onColorSelected(currentColor)
                                onDismissRequest()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}