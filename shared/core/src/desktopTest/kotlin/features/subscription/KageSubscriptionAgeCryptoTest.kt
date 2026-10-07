// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kage.Age
import kage.crypto.x25519.X25519Identity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KageSubscriptionAgeCryptoTest {
    @Test
    fun derives_recipient_key_and_decrypts_an_armored_age_response() {
        val identity = X25519Identity.new()
        val plaintext = "vless://uuid@example.com:443\n"
        val encrypted = ByteArrayOutputStream().use { output ->
            Age.encryptStream(
                listOf(identity.recipient()),
                ByteArrayInputStream(plaintext.encodeToByteArray()),
                output,
                true,
            )
            output.toString(Charsets.UTF_8.name())
        }
        val secretKey = identity.encodeToString()
        val recipient = identity.recipient().encodeToString()

        assertTrue(encrypted.startsWith("-----BEGIN AGE ENCRYPTED FILE-----"))
        assertEquals(
            recipient,
            SubscriptionAgePolicy.publicKeyHeaderValue(secretKey, KageSubscriptionAgeCrypto),
        )
        assertEquals(
            plaintext,
            SubscriptionAgePolicy.decryptResponseBody(encrypted, secretKey, KageSubscriptionAgeCrypto),
        )
    }
}
