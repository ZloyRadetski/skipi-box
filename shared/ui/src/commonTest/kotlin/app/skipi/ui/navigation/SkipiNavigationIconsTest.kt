// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Settings
import kotlin.test.Test
import kotlin.test.assertSame

/** The bottom bar looks the same on every host: Android's icons are the reference. */
class SkipiNavigationIconsTest {
    @Test
    fun everyHostShowsTheSameIconForEachDestination() {
        assertSame(MiuixIcons.Layers, SkipiMainDestination.Proxy.navigationIcon)
        assertSame(MiuixIcons.MindMap, SkipiMainDestination.Configs.navigationIcon)
        assertSame(MiuixIcons.Settings, SkipiMainDestination.Settings.navigationIcon)
    }
}
