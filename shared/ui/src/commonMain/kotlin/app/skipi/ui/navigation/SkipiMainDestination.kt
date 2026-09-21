// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

/**
 * The top-level SKIPI destinations shared by every Compose host.
 *
 * Labels and icons deliberately stay with the host's localization/theme layer;
 * this keeps navigation policy common without leaking Android resources into
 * desktop (or vice versa).
 */
enum class SkipiMainDestination(
    val pageIndex: Int,
) {
    Proxy(pageIndex = 0),
    Configs(pageIndex = 1),
    Settings(pageIndex = 2),
    ;

    companion object {
        fun fromPageIndex(pageIndex: Int): SkipiMainDestination =
            entries.firstOrNull { destination -> destination.pageIndex == pageIndex } ?: Proxy
    }
}
