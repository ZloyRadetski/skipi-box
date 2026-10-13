// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.yaml

import java.net.URI

/** Applies the Android importer's HTTP(S), host, trim, and URI-string rules. */
fun normalizeMihomoProviderUrl(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    return uri.toString()
}

/** Returns only the provider host for safe failure logging. */
fun mihomoProviderUrlLogHost(url: String): String =
    runCatching { URI(url).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: "<unknown>"
