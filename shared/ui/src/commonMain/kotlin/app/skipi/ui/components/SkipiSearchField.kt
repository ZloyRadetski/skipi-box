// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.search_clear
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource

/** A low field, taller only when a large font needs it. */
private val SearchFieldMinHeight = 44.dp

/**
 * The search field of the application: one line with a magnifier, a [placeholder] while empty and a button that
 * clears the text. Hosts own the query; the width is that of the place the field is put in.
 */
@Composable
fun SkipiSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = SkipiTheme.typography.bodyLarge.copy(color = SkipiTheme.colors.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(SkipiTheme.colors.accent),
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SearchFieldMinHeight)
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) SkipiTheme.colors.accent else SkipiTheme.colorScheme.outline,
                        shape = SkipiTheme.shapes.medium,
                    )
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipiContentIcon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    fallbackTint = SkipiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                // Both centred on their own: the editor settles its height a frame later and must not move the text.
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = SkipiTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(SkipiTheme.shapes.small)
                            .clickable(enabled = enabled, role = Role.Button) { onValueChange("") }
                            .testTag("search-clear"),
                        contentAlignment = Alignment.Center,
                    ) {
                        SkipiContentIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(Res.string.search_clear),
                            fallbackTint = SkipiTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    )
}
