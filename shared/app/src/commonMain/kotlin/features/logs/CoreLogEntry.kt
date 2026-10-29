// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

/** One line shown by the log viewers; [id] is stable within a repository snapshot. */
data class CoreLogEntry(
    val id: Long,
    val time: String,
    val level: String,
    val message: String,
)
