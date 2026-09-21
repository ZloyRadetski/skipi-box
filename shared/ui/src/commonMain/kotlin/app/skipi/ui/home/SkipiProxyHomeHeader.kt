// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_more
import app.skipi.ui.resources.proxy_server_list_add
import app.skipi.ui.resources.proxy_server_list_latency_test
import org.jetbrains.compose.resources.stringResource

/** A host-provided command rendered in either the add or overflow menu. */
data class SkipiProxyHomeHeaderAction(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val children: List<SkipiProxyHomeHeaderAction> = emptyList(),
)

data class SkipiProxyHomeHeaderState(
    val title: String,
    val latencyTesting: Boolean = false,
    val latencyEnabled: Boolean = true,
)

data class SkipiProxyHomeHeaderLabels(
    val latencyActionDescription: String,
    val addActionDescription: String,
    val moreActionDescription: String,
)

@Composable
fun defaultSkipiProxyHomeHeaderLabels(): SkipiProxyHomeHeaderLabels = SkipiProxyHomeHeaderLabels(
    latencyActionDescription = stringResource(Res.string.proxy_server_list_latency_test),
    addActionDescription = stringResource(Res.string.proxy_server_list_add),
    moreActionDescription = stringResource(Res.string.common_more),
)

data class SkipiProxyHomeHeaderColors(
    val text: Color,
    val mutedText: Color,
    val accent: Color,
)

/**
 * Common Home action bar. A platform maps domain actions into the lightweight
 * menu model and remains responsible for actually performing those actions.
 */
@Composable
fun SkipiProxyHomeHeader(
    state: SkipiProxyHomeHeaderState,
    colors: SkipiProxyHomeHeaderColors,
    addActions: List<SkipiProxyHomeHeaderAction>,
    toolActions: List<SkipiProxyHomeHeaderAction>,
    onTestLatency: () -> Unit,
    onAddAction: (String) -> Unit,
    onToolAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    labels: SkipiProxyHomeHeaderLabels = defaultSkipiProxyHomeHeaderLabels(),
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    var toolsMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.title,
            color = colors.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onTestLatency,
                enabled = state.latencyEnabled,
            ) {
                if (state.latencyTesting) {
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .semantics { contentDescription = labels.latencyActionDescription },
                        contentAlignment = Alignment.Center,
                    ) {
                        SkipiProxyHeroAnimatedHourglassIcon(
                            color = colors.accent,
                            size = 20.dp,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.HourglassEmpty,
                        contentDescription = labels.latencyActionDescription,
                        tint = colors.text,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
            if (addActions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { addMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = labels.addActionDescription,
                            tint = colors.text,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    SkipiProxyHomeHeaderMenu(
                        expanded = addMenuExpanded,
                        actions = addActions,
                        accent = colors.accent,
                        onDismissRequest = { addMenuExpanded = false },
                        onAction = { actionId ->
                            addMenuExpanded = false
                            onAddAction(actionId)
                        },
                    )
                }
            }
            if (toolActions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { toolsMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = labels.moreActionDescription,
                            tint = colors.text,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                    SkipiProxyHomeHeaderMenu(
                        expanded = toolsMenuExpanded,
                        actions = toolActions,
                        accent = colors.accent,
                        onDismissRequest = { toolsMenuExpanded = false },
                        onAction = { actionId ->
                            toolsMenuExpanded = false
                            onToolAction(actionId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipiProxyHomeHeaderMenu(
    expanded: Boolean,
    actions: List<SkipiProxyHomeHeaderAction>,
    accent: Color,
    onDismissRequest: () -> Unit,
    onAction: (String) -> Unit,
) {
    var navigationStack by remember(actions) {
        mutableStateOf(emptyList<SkipiProxyHomeHeaderAction>())
    }
    val currentActions = navigationStack.lastOrNull()?.children ?: actions

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            navigationStack = emptyList()
            onDismissRequest()
        },
    ) {
        if (navigationStack.isNotEmpty()) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = navigationStack.last().title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { navigationStack = navigationStack.dropLast(1) },
            )
        }
        currentActions.forEach { action ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = action.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                enabled = action.enabled,
                trailingIcon = {
                    when {
                        action.children.isNotEmpty() -> Icon(
                            imageVector = Icons.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )

                        action.selected -> Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )

                        else -> Unit
                    }
                },
                onClick = {
                    if (action.children.isNotEmpty()) {
                        navigationStack = navigationStack + action
                    } else {
                        onAction(action.id)
                    }
                },
            )
        }
    }
}
