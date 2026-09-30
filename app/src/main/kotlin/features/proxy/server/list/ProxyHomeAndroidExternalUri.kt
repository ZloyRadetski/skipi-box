// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import java.net.URI

/** Subscription metadata is remote input; only HTTPS pages and one plain email recipient may leave the app. */
internal fun requireSafeAndroidExternalUri(value: String): URI {
    val uri = URI(value.trim())
    when (uri.scheme?.lowercase()) {
        "https" -> {
            require(uri.host?.isNotBlank() == true) { "HTTPS link must include a host." }
            require(uri.userInfo.isNullOrBlank()) { "HTTPS link must not contain credentials." }
        }

        "mailto" -> {
            val recipient = uri.rawSchemeSpecificPart
            require(!recipient.isNullOrBlank() && !recipient.contains('?') && !recipient.contains('#')) {
                "Mailto link must contain one address without parameters."
            }
            require(SafeMailtoRecipient.matches(recipient)) { "Mailto link contains an invalid address." }
        }

        else -> error("Only HTTPS and valid mailto links are supported.")
    }
    return uri
}

private val SafeMailtoRecipient = Regex(
    "^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+${'$'}",
)
