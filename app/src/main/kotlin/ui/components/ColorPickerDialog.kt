// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.R
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
    Color(0xFF0070F3), // Vercel / Electric Blue
    Color(0xFF3B82F6), // Sky Blue
    Color(0xFF06B6D4), // Cyan
    Color(0xFF10B981), // Emerald
    Color(0xFF22C55E), // Green
    Color(0xFFF59E0B), // Amber
    Color(0xFFFF5722), // Deep Orange
    Color(0xFFEF4444), // Crimson Red
    Color(0xFFEC4899), // Pink
    Color(0xFF8B5CF6), // Violet / Purple
    Color(0xFF6366F1), // Indigo
    Color(0xFF14B8A6), // Teal
    Color(0xFF1E293B), // Slate Dark
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

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255),
        hsv,
    )
    return hsv
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val argb = android.graphics.Color.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 360f),
            saturation.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f),
        )
    )
    return Color(argb)
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

    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    var hexText by remember(initialColor) { mutableStateOf(formatHex(initialColor)) }

    val currentColor = remember(hue, saturation, value) {
        hsvToColor(hue, saturation, value)
    }

    val rainbowColors = remember {
        listOf(
            Color(0xFFFF0000), // 0° Red
            Color(0xFFFFFF00), // 60° Yellow
            Color(0xFF00FF00), // 120° Green
            Color(0xFF00FFFF), // 180° Cyan
            Color(0xFF0000FF), // 240° Blue
            Color(0xFFFF00FF), // 300° Magenta
            Color(0xFFFF0000), // 360° Red
        )
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
                    .widthIn(max = 440.dp)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )

                    // 2D Saturation / Value Color Square Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(16.dp)),
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(hue) {
                                    detectTapGestures { offset ->
                                        saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                        value = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                        hexText = formatHex(hsvToColor(hue, saturation, value))
                                    }
                                }
                                .pointerInput(hue) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                            value = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                            hexText = formatHex(hsvToColor(hue, saturation, value))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                            value = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                                            hexText = formatHex(hsvToColor(hue, saturation, value))
                                        },
                                    )
                                },
                        ) {
                            val pureHueColor = hsvToColor(hue, 1f, 1f)

                            // 1. Horizontal gradient: White -> Pure Hue Color
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.White, pureHueColor),
                                    startX = 0f,
                                    endX = size.width,
                                ),
                            )

                            // 2. Vertical gradient: Transparent -> Black
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                    startY = 0f,
                                    endY = size.height,
                                ),
                            )

                            // 3. Thumb selector circle
                            val selectorX = (saturation * size.width).coerceIn(0f, size.width)
                            val selectorY = ((1f - value) * size.height).coerceIn(0f, size.height)
                            val selectorCenter = Offset(selectorX, selectorY)

                            // Outer shadow/black ring
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.6f),
                                radius = 11.dp.toPx(),
                                center = selectorCenter,
                                style = Stroke(width = 3.dp.toPx()),
                            )
                            // White ring
                            drawCircle(
                                color = Color.White,
                                radius = 9.dp.toPx(),
                                center = selectorCenter,
                                style = Stroke(width = 2.5.dp.toPx()),
                            )
                            // Inner color fill
                            drawCircle(
                                color = currentColor,
                                radius = 6.5.dp.toPx(),
                                center = selectorCenter,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Rainbow Hue Slider Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(14.dp)),
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        hue = ((offset.x / size.width) * 360f).coerceIn(0f, 360f)
                                        hexText = formatHex(hsvToColor(hue, saturation, value))
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            hue = ((offset.x / size.width) * 360f).coerceIn(0f, 360f)
                                            hexText = formatHex(hsvToColor(hue, saturation, value))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            hue = ((change.position.x / size.width) * 360f).coerceIn(0f, 360f)
                                            hexText = formatHex(hsvToColor(hue, saturation, value))
                                        },
                                    )
                                },
                        ) {
                            // Rainbow background
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = rainbowColors,
                                    startX = 0f,
                                    endX = size.width,
                                ),
                                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                            )

                            // Hue thumb indicator
                            val thumbX = ((hue / 360f) * size.width).coerceIn(12.dp.toPx(), size.width - 12.dp.toPx())
                            val thumbCenter = Offset(thumbX, size.height / 2f)

                            drawCircle(
                                color = Color.Black.copy(alpha = 0.5f),
                                radius = 12.dp.toPx(),
                                center = thumbCenter,
                                style = Stroke(width = 2.dp.toPx()),
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 10.dp.toPx(),
                                center = thumbCenter,
                                style = Stroke(width = 3.dp.toPx()),
                            )
                            drawCircle(
                                color = hsvToColor(hue, 1f, 1f),
                                radius = 7.dp.toPx(),
                                center = thumbCenter,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Color Preview Swatch (Initial vs Current) & Hex Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Comparison badges
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Initial Color (clickable to restore)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(initialColor)
                                    .border(1.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val restoredHsv = colorToHsv(initialColor)
                                        hue = restoredHsv[0]
                                        saturation = restoredHsv[1]
                                        value = restoredHsv[2]
                                        hexText = formatHex(initialColor)
                                    },
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "→",
                                fontSize = 18.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.width(10.dp))
                            // Current Color
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(currentColor)
                                    .border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        // Hex input field
                        TextField(
                            value = hexText,
                            onValueChange = { input ->
                                hexText = input
                                val parsed = parseHex(input)
                                if (parsed != null) {
                                    val parsedHsv = colorToHsv(parsed)
                                    hue = parsedHsv[0]
                                    saturation = parsedHsv[1]
                                    value = parsedHsv[2]
                                }
                            },
                            label = stringResource(R.string.color_picker_hex),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done,
                            ),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Quick presets palette
                    Text(
                        text = stringResource(R.string.color_picker_quick_presets),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
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
                                        val presetHsv = colorToHsv(preset)
                                        hue = presetHsv[0]
                                        saturation = presetHsv[1]
                                        value = presetHsv[2]
                                        hexText = formatHex(preset)
                                    },
                            )
                        }
                    }

                    // Action buttons (Cancel / Apply)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismissRequest,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onColorSelected(currentColor)
                                onDismissRequest()
                            },
                            colors = ButtonDefaults.buttonColors(
                                color = currentColor,
                            ),
                        ) {
                            Text(
                                text = stringResource(android.R.string.ok),
                                color = if (value > 0.6f && saturation < 0.4f) Color.Black else Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
