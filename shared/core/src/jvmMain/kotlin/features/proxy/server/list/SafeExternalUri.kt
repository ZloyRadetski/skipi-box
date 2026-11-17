// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import java.net.URI

/** What a refused link says; the defaults are English, a host with localized texts passes its own. */
data class ExternalLinkMessages(
    val httpsHostRequired: String = "HTTPS link must include a host.",
    val httpsNoCredentials: String = "HTTPS link must not contain credentials.",
    val mailtoSingleAddress: String = "Mailto link must contain one address without parameters.",
    val mailtoInvalidAddress: String = "Mailto link contains an invalid address.",
    val unsupportedScheme: String = "Only HTTPS and valid mailto links are supported.",
)

/** Subscription metadata is remote input; only HTTPS pages and one plain email recipient may leave the app. */
fun requireSafeExternalUri(value: String, messages: ExternalLinkMessages = ExternalLinkMessages()): URI {
    val uri = URI(value.trim())
    when (uri.scheme?.lowercase()) {
        "https" -> {
            require(uri.host?.isNotBlank() == true) { messages.httpsHostRequired }
            require(uri.userInfo.isNullOrBlank()) { messages.httpsNoCredentials }
        }

        "mailto" -> {
            val recipient = uri.rawSchemeSpecificPart
            require(!recipient.isNullOrBlank() && !recipient.contains('?') && !recipient.contains('#')) {
                messages.mailtoSingleAddress
            }
            require(SafeMailtoRecipient.matches(recipient)) { messages.mailtoInvalidAddress }
        }

        else -> error(messages.unsupportedScheme)
    }
    return uri
}

private val SafeMailtoRecipient = Regex(
    "^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+${'$'}",
)
