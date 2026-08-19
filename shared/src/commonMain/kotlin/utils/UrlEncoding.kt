// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent

internal fun String.decodeUrlComponentPreservingPlus(): String {
    return runCatching {
        decodeURLQueryComponent(plusIsSpace = false)
    }.getOrElse { this }
}

internal fun String.decodeFlexibleBase64ToStringOrRaw(): String {
    return decodeFlexibleBase64OrNull()?.decodeToString() ?: this
}

internal fun Url.userInfoOrNull(): String? {
    val username = user ?: return null
    return if (password == null) username else "$username:${password.orEmpty()}"
}

internal fun Url.proxyUrlRemarks(): String {
    return fragment.toProxyUrlRemarks()
}

internal fun String.toProxyUrlRemarks(): String {
    return decodeUrlComponentPreservingPlus().ifBlank { "none" }
}
