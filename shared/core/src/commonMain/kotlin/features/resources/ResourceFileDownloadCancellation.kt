// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

/** Signals that a user cancelled an individual resource-file download. */
class ResourceFileDownloadCancelledException(
    message: String = "Resource file update cancelled",
) : RuntimeException(message)
