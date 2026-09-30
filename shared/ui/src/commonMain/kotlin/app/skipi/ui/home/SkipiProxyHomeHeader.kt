// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
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
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme

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
    val latencyActionDescription: String? = null,
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
    val latencyDescription = state.latencyActionDescription ?: labels.latencyActionDescription

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SkipiTheme.homeMetrics.toolbarHeight)
            .padding(horizontal = SkipiTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.title,
            color = colors.text,
            style = SkipiTheme.typography.titleLarge,
            fontWeight = themedFontWeight(FontWeight.SemiBold),
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
                            .semantics { contentDescription = latencyDescription },
                        contentAlignment = Alignment.Center,
                    ) {
                        SkipiProxyHeroAnimatedHourglassIcon(
                            color = colors.accent,
                            size = 20.dp,
                        )
                    }
                } else {
                    SkipiProxyHeroStaticHourglassIcon(
                        modifier = Modifier
                            .size(25.dp)
                            .semantics { contentDescription = latencyDescription },
                        color = colors.text,
                        size = 20.dp,
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

    SkipiHomeDropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            navigationStack = emptyList()
            onDismissRequest()
        },
    ) {
        AnimatedContent(
            targetState = navigationStack,
            transitionSpec = {
                val direction = if (targetState.size > initialState.size) 1 else -1
                (fadeIn(tween(durationMillis = 180)) + slideInHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetX = { width -> direction * width / 7 },
                )).togetherWith(
                    fadeOut(tween(durationMillis = 120)) + slideOutHorizontally(
                        animationSpec = tween(durationMillis = 180),
                        targetOffsetX = { width -> -direction * width / 7 },
                    ),
                ).using(
                    SizeTransform(clip = false) { _, _ -> tween(durationMillis = 220) },
                )
            },
            label = "home_header_menu_navigation",
        ) { menuStack ->
            Column {
                if (menuStack.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = menuStack.last().title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = skipiHomeDropdownItemColors(),
                        onClick = { navigationStack = navigationStack.dropLast(1) },
                    )
                }
                val visibleActions = menuStack.lastOrNull()?.children ?: actions
                visibleActions.forEach { action ->
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
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
                        colors = skipiHomeDropdownItemColors(),
                        onClick = {
                            if (action.children.isNotEmpty()) {
                                navigationStack = navigationStack + action
                            } else {
                                navigationStack = emptyList()
                                onAction(action.id)
                            }
                        },
                    )
                }
            }
        }
    }
}
