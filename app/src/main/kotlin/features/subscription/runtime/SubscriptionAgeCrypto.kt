// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kage.Age
import kage.crypto.x25519.X25519Identity

internal interface SubscriptionAgeCrypto {
    fun publicKey(secretKey: String): String

    fun decryptArmored(text: String, secretKey: String): String
}

internal object KageSubscriptionAgeCrypto : SubscriptionAgeCrypto {
    override fun publicKey(secretKey: String): String {
        return try {
            identity(secretKey).recipient().encodeToString()
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid Age secret key")
        }
    }

    override fun decryptArmored(text: String, secretKey: String): String {
        return try {
            val output = ByteArrayOutputStream()
            Age.decryptStream(
                identities = listOf(identity(secretKey)),
                srcStream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)),
                dstStream = output,
            )
            output.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            throw IllegalArgumentException("Failed to decrypt Age-encrypted subscription")
        }
    }

    private fun identity(secretKey: String): X25519Identity {
        return X25519Identity.decode(secretKey.trim())
    }
}

internal object UnsupportedSubscriptionAgeCrypto : SubscriptionAgeCrypto {
    override fun publicKey(secretKey: String): String {
        unsupported()
    }

    override fun decryptArmored(text: String, secretKey: String): String {
        unsupported()
    }

    private fun unsupported(): Nothing {
        error("Age-encrypted subscriptions require Android 8.0 or later")
    }
}
