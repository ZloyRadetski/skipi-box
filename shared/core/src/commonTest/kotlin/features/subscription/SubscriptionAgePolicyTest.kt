// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SubscriptionAgePolicyTest {
    @Test
    fun blankSecretDoesNotRequestAnAgePublicKey() {
        val crypto = RecordingAgeCrypto()

        assertNull(SubscriptionAgePolicy.publicKeyHeaderValue("  \t", crypto))
        assertNull(crypto.publicKeySecret)
    }

    @Test
    fun derivesRequestPublicKeyFromTrimmedSecret() {
        val crypto = RecordingAgeCrypto()

        assertEquals(
            "age1recipient",
            SubscriptionAgePolicy.publicKeyHeaderValue("  secret  ", crypto),
        )
        assertEquals("secret", crypto.publicKeySecret)
    }

    @Test
    fun plaintextBodyPassesThroughWithoutCallingCrypto() {
        val crypto = RecordingAgeCrypto()
        val body = "  vless://uuid@example.com:443\n"

        assertEquals(body, SubscriptionAgePolicy.decryptResponseBody(body, "secret", crypto))
        assertNull(crypto.decryptedText)
    }

    @Test
    fun armoredBodyIsDetectedAfterLeadingWhitespaceAndDecryptsWithTrimmedSecret() {
        val crypto = RecordingAgeCrypto()
        val body = " \n-----BEGIN AGE ENCRYPTED FILE-----\nfixture\n-----END AGE ENCRYPTED FILE-----\n"

        assertEquals("decrypted subscription", SubscriptionAgePolicy.decryptResponseBody(body, " secret ", crypto))
        assertEquals("secret", crypto.decryptSecret)
        assertEquals(body, crypto.decryptedText)
    }

    @Test
    fun armoredBodyRequiresASecretBeforeDecrypting() {
        val crypto = RecordingAgeCrypto()
        val body = "-----BEGIN AGE ENCRYPTED FILE-----\nfixture"

        val error = assertFailsWith<IllegalArgumentException> {
            SubscriptionAgePolicy.decryptResponseBody(body, "  ", crypto)
        }

        assertEquals("Age-encrypted subscription requires a secret key", error.message)
        assertNull(crypto.decryptSecret)
    }
}

private class RecordingAgeCrypto : SubscriptionAgeCrypto {
    var publicKeySecret: String? = null
    var decryptSecret: String? = null
    var decryptedText: String? = null

    override fun publicKey(secretKey: String): String {
        publicKeySecret = secretKey
        return "age1recipient"
    }

    override fun decryptArmored(text: String, secretKey: String): String {
        decryptedText = text
        decryptSecret = secretKey
        return "decrypted subscription"
    }
}
