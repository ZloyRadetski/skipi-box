// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WarningConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    dismissText: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    detailsMaxHeight: Dp = 240.dp,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (!show) return

    val warningColor = MiuixTheme.colorScheme.error
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
                    color = SkipiTheme.colors.surface,
                    contentColor = SkipiTheme.colors.onSurface,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(warningColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "!",
                            fontSize = 24.sp,
                            fontWeight = themedFontWeight(FontWeight.SemiBold),
                            color = warningColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        text = title,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(warningColor.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = summary,
                            modifier = Modifier.fillMaxWidth(),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = themedFontWeight(FontWeight.Medium),
                            color = warningColor,
                            textAlign = TextAlign.Start,
                        )
                    }
                    if (details != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .heightIn(max = detailsMaxHeight)
                                .clip(RoundedCornerShape(8.dp))
                                .background(warningColor.copy(alpha = 0.06f))
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            content = details,
                        )
                    }
                    if (warningDialogStacksButtons(dismissText, confirmText)) {
                        // A half-width button would wrap a long label onto several lines.
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(text = confirmText, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                            TextButton(text = dismissText, onClick = onDismissRequest, modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextButton(text = dismissText, onClick = onDismissRequest, modifier = Modifier.weight(1f))
                            TextButton(text = confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private const val MaxSideBySideLabelLength = 14

/** Two buttons share a row only when both labels comfortably fit in half of a phone-width dialog. */
internal fun warningDialogStacksButtons(dismissText: String, confirmText: String): Boolean =
    maxOf(dismissText.length, confirmText.length) > MaxSideBySideLabelLength
