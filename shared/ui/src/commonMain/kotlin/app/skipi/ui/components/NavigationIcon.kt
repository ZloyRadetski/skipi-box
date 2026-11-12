// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NavigationIcon(
    onClick: () -> Unit,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    IconButton(modifier = modifier, onClick = onClick) {
        Icon(
            modifier = Modifier.graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
            },
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/**
 * The back actions of the screens that are on screen, newest last. A host without a system Back (the
 * desktop, with Escape and the mouse's back button) provides one and sends its Back to [invokeNewest].
 */
class BackActions {
    private val actions = mutableListOf<() -> Unit>()

    fun push(action: () -> Unit) {
        actions += action
    }

    fun remove(action: () -> Unit) {
        actions.remove(action)
    }

    /** Runs the newest registered action; false when no screen has one. */
    fun invokeNewest(): Boolean {
        val action = actions.lastOrNull() ?: return false
        action()
        return true
    }
}

val LocalBackActions = compositionLocalOf<BackActions?> { null }

/** The top-bar back arrow; while it is on screen its action is also what the host's Back does. */
@Composable
fun BackNavigationIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val registry = LocalBackActions.current
    val latest by rememberUpdatedState(onClick)
    DisposableEffect(registry) {
        val action: () -> Unit = { latest() }
        registry?.push(action)
        onDispose { registry?.remove(action) }
    }
    NavigationIcon(onClick = onClick, imageVector = MiuixIcons.Back, modifier = modifier)
}
