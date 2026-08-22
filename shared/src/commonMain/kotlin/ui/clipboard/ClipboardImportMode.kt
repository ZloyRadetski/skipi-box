// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.clipboard

enum class ClipboardImportMode {
    Replace,
    Merge,
}

enum class ClipboardImportFailure {
    EmptyClipboard,
    UnsupportedFormat,
    NoValidRoutingRules,
    NoValidApps,
    InvalidAppEntry,
    InvalidAppUserId,
    UnsupportedAppMode,
}

class ClipboardImportException(
    val failure: ClipboardImportFailure,
) : IllegalArgumentException(failure.name)
