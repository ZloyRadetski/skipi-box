// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

/** Platform/JVM implementation for Age key derivation and armored decryption. */
interface SubscriptionAgeCrypto {
    fun publicKey(secretKey: String): String

    fun decryptArmored(text: String, secretKey: String): String
}

/** Shared request/response rules for optional Age-encrypted subscription bodies. */
object SubscriptionAgePolicy {
    private const val AgeArmorHeader = "-----BEGIN AGE ENCRYPTED FILE-----"

    /** Returns the request header value when a non-blank secret key was supplied. */
    fun publicKeyHeaderValue(secretKey: String, crypto: SubscriptionAgeCrypto): String? {
        val normalizedSecretKey = secretKey.trim()
        if (normalizedSecretKey.isEmpty()) return null
        return crypto.publicKey(normalizedSecretKey)
    }

    /** Decrypts Age-armored responses and leaves all other response text byte-for-byte intact. */
    fun decryptResponseBody(
        body: String,
        secretKey: String,
        crypto: SubscriptionAgeCrypto,
    ): String {
        if (!body.trimStart().startsWith(AgeArmorHeader)) return body
        val normalizedSecretKey = secretKey.trim()
        require(normalizedSecretKey.isNotEmpty()) {
            "Age-encrypted subscription requires a secret key"
        }
        return crypto.decryptArmored(body, normalizedSecretKey)
    }
}
