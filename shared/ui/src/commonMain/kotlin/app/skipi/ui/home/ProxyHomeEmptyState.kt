// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.home_action_add_subscription
import app.skipi.ui.resources.common_add
import app.skipi.ui.resources.common_paste
import app.skipi.ui.resources.home_action_add_subscription
import app.skipi.ui.resources.home_empty_hint
import app.skipi.ui.resources.home_empty_search
import app.skipi.ui.resources.home_empty_servers
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EmptyServerListCard(
    hasSearch: Boolean,
    actions: ProxyHomeEmptyStateActions,
    canAddSubscription: Boolean,
    serverKinds: Collection<ProxyHomeServerKind>,
    onAction: (ProxyHomeAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SkipiTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = SkipiTheme.colors.surface),
        border = BorderStroke(1.dp, SkipiTheme.colors.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SkipiTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(if (hasSearch) Res.string.home_empty_search else Res.string.home_empty_servers),
                color = SkipiTheme.colors.onSurface,
                style = SkipiTheme.typography.titleMedium,
                fontWeight = themedFontWeight(FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!hasSearch) {
                Text(
                    text = stringResource(Res.string.home_empty_hint),
                    color = SkipiTheme.colors.onSurfaceVariant,
                    style = SkipiTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (actions.any) {
                EmptyServerListActions(
                    actions = actions,
                    canAddSubscription = canAddSubscription,
                    serverKinds = serverKinds,
                    onAction = onAction,
                )
            }
        }
    }
}

/**
 * Add, Paste and a square Scan-QR button on one line: tonal, borderless buttons. Add carries the
 * accent, Paste is neutral, and the QR button is icon-only so the two labelled buttons never wrap.
 */

@Composable
internal fun EmptyServerListActions(
    actions: ProxyHomeEmptyStateActions,
    canAddSubscription: Boolean,
    serverKinds: Collection<ProxyHomeServerKind>,
    onAction: (ProxyHomeAction) -> Unit,
) {
    val colors = SkipiTheme.colors
    val neutralContainer = colors.onSurface.copy(alpha = 0.08f)
    val neutralContent = colors.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
    ) {
        if (actions.add) {
            var menuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                EmptyStateActionButton(
                    icon = Icons.Outlined.Add,
                    label = stringResource(Res.string.common_add),
                    containerColor = colors.accent.copy(alpha = 0.18f),
                    contentColor = colors.accent,
                    onClick = { menuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                SkipiHomeDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (canAddSubscription) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.home_action_add_subscription)) },
                            onClick = {
                                menuExpanded = false
                                onAction(ProxyHomeAction.AddSubscription)
                            },
                        )
                    }
                    serverKinds.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(serverKindTitle(kind)) },
                            onClick = {
                                menuExpanded = false
                                onAction(ProxyHomeAction.AddServer(kind))
                            },
                        )
                    }
                }
            }
        }
        if (actions.paste) {
            EmptyStateActionButton(
                icon = Icons.Outlined.ContentPaste,
                label = stringResource(Res.string.common_paste),
                containerColor = neutralContainer,
                contentColor = neutralContent,
                onClick = { onAction(ProxyHomeAction.ImportServers(ProxyHomeImportSource.Clipboard)) },
                modifier = Modifier.weight(1f),
            )
        }
        if (actions.scanQr) {
            EmptyStateActionButton(
                icon = Icons.Outlined.QrCodeScanner,
                label = null,
                contentDescription = importSourceTitle(ProxyHomeImportSource.QrCode),
                containerColor = neutralContainer,
                contentColor = neutralContent,
                onClick = { onAction(ProxyHomeAction.ImportServers(ProxyHomeImportSource.QrCode)) },
                modifier = Modifier.width(EmptyStateButtonHeight),
            )
        }
    }
}

internal val EmptyStateButtonHeight = 52.dp

@Composable
internal fun EmptyStateActionButton(
    icon: ImageVector,
    label: String?,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .height(EmptyStateButtonHeight)
            .clip(shape)
            .background(containerColor)
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        if (label != null) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = label,
                color = contentColor,
                maxLines = 1,
                style = SkipiTheme.typography.bodyMedium,
                fontWeight = themedFontWeight(FontWeight.Medium),
            )
        }
    }
}
