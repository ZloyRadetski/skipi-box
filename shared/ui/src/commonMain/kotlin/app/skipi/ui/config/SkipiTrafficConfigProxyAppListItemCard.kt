// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.proxy_app_list_entry_summary
import app.skipi.ui.text.formatTemplate
import app.skipi.ui.text.themedFontWeight
import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.name
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ProxyAppListItemCard(
    app: AppPackageEntry,
    checked: Boolean,
    enabled: Boolean,
    sharedUid: Boolean,
    iconContent: @Composable (app: AppPackageEntry, enabled: Boolean) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggle = {
        if (enabled) {
            onCheckedChange(!checked)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        colors = CardDefaults.defaultColors(
            color = if (app.system) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                SkipiTheme.colors.surface
            },
            contentColor = SkipiTheme.colors.onSurface,
        ),
        insideMargin = PaddingValues(14.dp),
        onClick = toggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent(app, enabled)
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.name,
                        fontSize = 17.sp,
                        fontWeight = themedFontWeight(FontWeight.Medium),
                        color = if (enabled) {
                            MiuixTheme.colorScheme.onSurface
                        } else {
                            MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(Res.string.proxy_app_list_entry_summary)
                            .formatTemplate("package" to app.packageName),
                        style = MiuixTheme.textStyles.body2,
                        color = if (enabled) {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        } else {
                            MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                ProxyAppListUidChip(
                    uid = app.uid,
                    enabled = enabled,
                    sharedUid = sharedUid,
                )
            }
            Spacer(Modifier.width(12.dp))
            Checkbox(
                state = ToggleableState(checked),
                onClick = toggle,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ProxyAppListUidChip(
    uid: Int?,
    enabled: Boolean,
    sharedUid: Boolean,
) {
    val backgroundColor = if (!enabled) {
        SkipiTheme.colors.surfaceVariant.copy(alpha = 0.5f)
    } else if (sharedUid) {
        MiuixTheme.colorScheme.primary
    } else {
        SkipiTheme.colors.surfaceVariant
    }
    val textColor = if (!enabled) {
        SkipiTheme.colors.onSurfaceVariant.copy(alpha = 0.5f)
    } else if (sharedUid) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        SkipiTheme.colors.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (sharedUid) {
                "SUID:${uid?.toString() ?: "..."}"
            } else {
                "UID:${uid?.toString() ?: "..."}"
            },
            fontSize = 12.sp,
            fontWeight = if (sharedUid) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
