// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.clipboard

/** The user-selected way to apply portable clipboard data. */
enum class ClipboardImportMode {
    Replace,
    Merge,
}

/**
 * Machine-readable import failures. Presentation layers map these to their
 * localized messages without putting parsing policy in a platform UI module.
 */
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
