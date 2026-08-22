// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

fun hasPendingStringListEdit(
    input: String,
    editingIndex: Int,
    normalize: (String) -> String = String::trim,
): Boolean = normalize(input).isNotEmpty() || editingIndex >= 0