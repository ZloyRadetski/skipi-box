// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.SkipiMenu
import app.skipi.ui.components.SkipiMenuItem
import app.skipi.ui.components.SkipiMenuSection
import app.skipi.ui.theme.SkipiTheme

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_more
import app.skipi.ui.resources.ic_skipi_mark_dots
import app.skipi.ui.resources.ic_skipi_mark_glyph
import app.skipi.ui.resources.proxy_server_list_add
import app.skipi.ui.resources.settings_header_tools
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A host-provided command rendered in either the add or overflow menu. */
data class SkipiProxyHomeHeaderAction(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    /** Drawn before the title when set. */
    val icon: ImageVector? = null,
    val children: List<SkipiProxyHomeHeaderAction> = emptyList(),
)

data class SkipiProxyHomeHeaderState(
    val title: String,
)

data class SkipiProxyHomeHeaderLabels(
    val addActionDescription: String,
    val moreActionDescription: String,
    val pinnedToolsActionDescription: String,
)

@Composable
fun defaultSkipiProxyHomeHeaderLabels(): SkipiProxyHomeHeaderLabels = SkipiProxyHomeHeaderLabels(
    addActionDescription = stringResource(Res.string.proxy_server_list_add),
    moreActionDescription = stringResource(Res.string.common_more),
    pinnedToolsActionDescription = stringResource(Res.string.settings_header_tools),
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
    onAddAction: (String) -> Unit,
    onToolAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    labels: SkipiProxyHomeHeaderLabels = defaultSkipiProxyHomeHeaderLabels(),
    /** The pinned tools; when not empty a button before "add" opens them as a menu. */
    pinnedToolActions: List<SkipiProxyHomeHeaderAction> = emptyList(),
    onPinnedToolAction: (String) -> Unit = {},
    /** When set, the logo plays the tap-tap-boom easter egg; the overlay is drawn by the owner of this state. */
    easterEgg: HeaderEasterEggState? = null,
) {
    var pinnedMenuExpanded by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var toolsMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SkipiTheme.homeMetrics.toolbarHeight)
            .padding(horizontal = SkipiTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkipiHeaderMark(glyphColor = colors.text, contentDescription = state.title, easterEgg = easterEgg)
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pinnedToolActions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { pinnedMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.GridView,
                            contentDescription = labels.pinnedToolsActionDescription,
                            tint = colors.text,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    SkipiProxyHomeHeaderMenu(
                        expanded = pinnedMenuExpanded,
                        actions = pinnedToolActions,
                        accent = colors.accent,
                        onDismissRequest = { pinnedMenuExpanded = false },
                        onAction = { actionId ->
                            pinnedMenuExpanded = false
                            onPinnedToolAction(actionId)
                        },
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
    SkipiMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        sections = listOf(SkipiMenuSection(actions.map { action -> action.toMenuItem(onAction) })),
        accent = accent,
    )
}

private fun SkipiProxyHomeHeaderAction.toMenuItem(onAction: (String) -> Unit): SkipiMenuItem = SkipiMenuItem(
    title = title,
    enabled = enabled,
    selected = selected,
    icon = icon,
    children = children.map { child -> child.toMenuItem(onAction) },
    onClick = { onAction(id) },
    key = id,
)
/**
 * The app icon without its tile, the header's only title: the glyph takes the text color of the header so it reads on any theme,
 * the three dots keep the logo's own green.
 */
@Composable
private fun SkipiHeaderMark(
    glyphColor: Color,
    contentDescription: String,
    easterEgg: HeaderEasterEggState?,
    modifier: Modifier = Modifier,
) {
    val tapModifier = if (easterEgg == null) {
        Modifier
    } else {
        // A tap gesture rather than a click, so a screen reader does not announce the logo as a button.
        Modifier
            .easterEggLogo(easterEgg)
            .pointerInput(easterEgg) { detectTapGestures { easterEgg.onTap() } }
    }
    Box(modifier = modifier.size(32.dp).then(tapModifier)) {
        Image(
            painter = painterResource(Res.drawable.ic_skipi_mark_glyph),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(glyphColor),
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(Res.drawable.ic_skipi_mark_dots),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
