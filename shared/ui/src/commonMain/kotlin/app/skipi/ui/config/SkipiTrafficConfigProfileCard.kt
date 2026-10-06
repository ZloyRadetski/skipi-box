// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_refresh
import app.skipi.ui.resources.configs_preserved_unsupported
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class TrafficConfigCatalogProfileItem(
    val id: Int,
    val name: String,
    val sourceUrl: String,
    val active: Boolean,
    val updating: Boolean,
    val canDelete: Boolean,
    val hasUnsupportedSections: Boolean = false,
)

/** Shared profile card. The optional slots let a platform add actions or metadata without duplicating its layout. */
@Composable
fun SkipiTrafficConfigProfileCard(
    item: TrafficConfigCatalogProfileItem,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit,
    onLongPress: () -> Unit = {},
    showUpdateAction: Boolean = true,
    showEditAction: Boolean = true,
    showDeleteAction: Boolean = true,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingActions: (@Composable RowScope.() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = if (item.active) SkipiTheme.colors.surfaceVariant else SkipiTheme.colors.surface),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        onClick = onSelect,
        onLongPress = onLongPress,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            leadingContent?.invoke(this)
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.hasUnsupportedSections) {
                    Text(
                        text = stringResource(Res.string.configs_preserved_unsupported),
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                supportingContent?.invoke(this)
            }
            Spacer(Modifier.width(6.dp))
            if (showUpdateAction && item.sourceUrl.isNotBlank()) {
                IconButton(onClick = onUpdate, enabled = !item.updating) {
                    if (item.updating) InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
                    else Icon(MiuixIcons.Refresh, contentDescription = stringResource(Res.string.common_refresh), tint = MiuixTheme.colorScheme.onSurface)
                }
            }
            if (showEditAction) {
                IconButton(onClick = onEdit) {
                    Icon(MiuixIcons.Edit, contentDescription = stringResource(Res.string.configs_ui_edit), tint = MiuixTheme.colorScheme.onSurface)
                }
            }
            if (showDeleteAction) {
                IconButton(onClick = onDelete, enabled = item.canDelete) {
                    Icon(MiuixIcons.Delete, contentDescription = stringResource(Res.string.common_delete), tint = MiuixTheme.colorScheme.onSurface)
                }
            }
            trailingActions?.invoke(this)
        }
    }
}
