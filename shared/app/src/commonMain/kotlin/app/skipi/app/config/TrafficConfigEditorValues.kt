// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

/** Historical boolean parsing used by Android's config editor. */
fun String?.isConfigEditorBoolean(): Boolean = this?.trim()?.lowercase() in setOf("true", "yes", "1")
