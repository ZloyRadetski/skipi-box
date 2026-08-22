// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent

fun String.decodeUrlComponentPreservingPlus(): String {
    return runCatching {
        decodeURLQueryComponent(plusIsSpace = false)
    }.getOrElse { this }
}

fun String.decodeFlexibleBase64ToStringOrRaw(): String {
    return decodeFlexibleBase64OrNull()?.decodeToString() ?: this
}

fun Url.userInfoOrNull(): String? {
    val username = user ?: return null
    return if (password == null) username else "$username:${password.orEmpty()}"
}

fun Url.proxyUrlRemarks(): String {
    return fragment.toProxyUrlRemarks()
}

fun String.toProxyUrlRemarks(): String {
    return decodeUrlComponentPreservingPlus().ifBlank { "none" }
}
