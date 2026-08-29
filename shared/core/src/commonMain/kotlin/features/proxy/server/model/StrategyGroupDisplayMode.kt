// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

/** Portable display policies for profile-owned proxy groups. */
object StrategyGroupDisplayMode {
    const val NEVER = "never"
    const val ALWAYS = "always"
    const val ACTIVE_CONFIG = "active_config"

    val MODES = listOf(ALWAYS, ACTIVE_CONFIG, NEVER)
}
