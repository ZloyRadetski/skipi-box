// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.text

fun String.formatTemplate(vararg values: Pair<String, Any?>): String {
    return values.fold(this) { text, (key, value) ->
        text.replace("{$key}", value?.toString().orEmpty())
    }
}
