// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.seasonal

/** A seasonal decoration of the app; only one is active at a time. */
enum class SeasonalEvent {
    NewYear,
}

/**
 * The event whose season contains the given calendar day, or null when none does or the user turned
 * seasonal decorations off. The host supplies today's local month (1..12) and day of month.
 */
fun activeSeasonalEvent(month: Int, dayOfMonth: Int, enabled: Boolean): SeasonalEvent? {
    if (!enabled) return null
    val inNewYearSeason = (month == 12) || (month == 1 && dayOfMonth <= 14)
    return if (inNewYearSeason) SeasonalEvent.NewYear else null
}
