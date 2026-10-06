// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.skipi.ui.resources.configs_add
import app.skipi.ui.resources.configs_global_proxy_groups
import app.skipi.ui.resources.configs_profiles
import app.skipi.ui.resources.configs_proxy_groups_title
import app.skipi.ui.resources.configs_preserved_unsupported
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.theme.SkipiTheme
import features.config.TrafficConfigState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class TrafficConfigProfileItem(
    val profile: TrafficConfigState,
    val active: Boolean,
    val updating: Boolean,
    val canDelete: Boolean,
    val hasUnsupportedSections: Boolean,
)

data class TrafficConfigProxyGroupItem(
    val key: String,
    val name: String,
    val subtitle: String,
    val badge: String,
)

/** Shared presentation for the portable traffic-config collection. Platform work is supplied as callbacks. */
@Composable
fun SkipiTrafficConfigScreen(
    profiles: List<TrafficConfigProfileItem>,
    globalProxyGroups: List<TrafficConfigProxyGroupItem>,
    sourcedProxyGroups: List<TrafficConfigProxyGroupItem>,
    contentPadding: PaddingValues,
    onAddConfig: () -> Unit,
    onAddGlobalProxyGroup: () -> Unit,
    onSelectProfile: (Int) -> Unit,
    onEditProfile: (Int) -> Unit,
    onDeleteProfile: (Int) -> Unit,
    onUpdateProfile: (Int) -> Unit,
    onProfileMenu: (Int) -> Unit,
    onOpenProxyGroup: (String) -> Unit,
    onDeleteGlobalProxyGroup: (String) -> Unit,
    onGlobalProxyGroupMenu: (String) -> Unit,
    onEditGlobalProxyGroup: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "global_proxy_groups_section_title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SmallTitle(
                    text = stringResource(Res.string.configs_global_proxy_groups),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAddGlobalProxyGroup) {
                    Icon(MiuixIcons.Add, contentDescription = stringResource(Res.string.configs_add), tint = MiuixTheme.colorScheme.onSurface)
                }
            }
        }
        items(globalProxyGroups, key = TrafficConfigProxyGroupItem::key) { group ->
            ProxyGroupCard(
                group = group,
                onClick = { onOpenProxyGroup(group.key) },
                onDelete = { onDeleteGlobalProxyGroup(group.key) },
                onLongPress = { onGlobalProxyGroupMenu(group.key) },
            )
        }
        if (sourcedProxyGroups.isNotEmpty()) {
            item(key = "sourced_proxy_groups_section_title") {
                SmallTitle(
                    text = stringResource(Res.string.configs_proxy_groups_title),
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
            }
            items(sourcedProxyGroups, key = TrafficConfigProxyGroupItem::key) { group ->
                ProxyGroupCard(group = group, onClick = { onOpenProxyGroup(group.key) })
            }
        }
        item(key = "configs_section_title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SmallTitle(text = stringResource(Res.string.configs_profiles), modifier = Modifier.weight(1f))
                IconButton(onClick = onAddConfig) {
                    Icon(MiuixIcons.Add, contentDescription = stringResource(Res.string.configs_add), tint = MiuixTheme.colorScheme.onSurface)
                }
            }
        }
        items(profiles, key = { it.profile.id }) { item ->
            ProfileCard(
                item = item,
                onSelect = { onSelectProfile(item.profile.id) },
                onEdit = { onEditProfile(item.profile.id) },
                onDelete = { onDeleteProfile(item.profile.id) },
                onUpdate = { onUpdateProfile(item.profile.id) },
                onLongPress = { onProfileMenu(item.profile.id) },
            )
        }
    }
}

@Composable
private fun ProfileCard(
    item: TrafficConfigProfileItem,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit,
    onLongPress: () -> Unit,
) {
    SkipiTrafficConfigProfileCard(
        item = TrafficConfigCatalogProfileItem(
            id = item.profile.id,
            name = item.profile.name,
            sourceUrl = item.profile.sourceUrl,
            active = item.active,
            updating = item.updating,
            canDelete = item.canDelete,
            hasUnsupportedSections = item.hasUnsupportedSections,
        ),
        onSelect = onSelect,
        onEdit = onEdit,
        onDelete = onDelete,
        onUpdate = onUpdate,
        onLongPress = onLongPress,
    )
}

@Composable
private fun ProxyGroupCard(
    group: TrafficConfigProxyGroupItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(14.dp),
        onClick = onClick,
        onLongPress = onLongPress,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(group.subtitle, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(group.badge, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(MiuixIcons.Delete, contentDescription = stringResource(Res.string.common_delete), tint = MiuixTheme.colorScheme.onSurface) }
        }
    }
}
