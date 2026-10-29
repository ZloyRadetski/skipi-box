// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A generated Xray config holds credentials and can be hundreds of KB: only a fingerprint may be logged. */
class ConfigFingerprintTest {
    private val secret = """{"outbounds":[{"settings":{"vnext":[{"users":[{"id":"top-secret-uuid"}]}]}}]}"""

    @Test
    fun fingerprintNeverContainsTheConfigContent() {
        val text = configFingerprint(secret)
        assertFalse(text.contains("top-secret-uuid"))
        assertFalse(text.contains("outbounds"))
        assertTrue(text.contains("chars=${secret.length}"))
    }

    @Test
    fun equalConfigsShareAFingerprintAndDifferentOnesDoNot() {
        assertEquals(configFingerprint(secret), configFingerprint(secret))
        assertNotEquals(configFingerprint(secret), configFingerprint(secret.replace("secret", "other")))
    }
}
