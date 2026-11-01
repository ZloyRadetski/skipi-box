// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import features.proxy.server.model.AmneziaWg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The AmneziaWG editor has about forty fields. Each one is its own list item so the lazy list only composes
 * what is on screen; this table is what the editor draws, in order, so it must cover every editable
 * property of the profile exactly once.
 */
class AmneziaWgEditorFieldsTest {
    private val properties = listOf(
        "remarks", "server", "port", "secretKey", "publicKey", "preSharedKey", "reserved", "address", "mtu",
    )
    private val obfuscation = listOf(
        "jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5",
        "headerProtectionKey", "contentPaddingAddition", "rekeyAfterTime", "rekeyTimeout", "rejectAfterTime",
        "keepaliveTimeout", "maxHandshakeAttempts", "persistentKeepalive", "randomTrailers", "disableCookies",
    )

    @Test
    fun thePropertiesSectionListsTheConnectionFieldsInTheEditorOrder() {
        assertEquals(properties, amneziaWgPropertyFields.map { it.key })
    }

    @Test
    fun theObfuscationSectionListsEveryAmneziaFieldInTheEditorOrder() {
        assertEquals(obfuscation, amneziaWgObfuscationFields.map { it.key })
    }

    @Test
    fun everyFieldIsUniqueAndWritesAndReadsItsOwnProperty() {
        val all = amneziaWgPropertyFields + amneziaWgObfuscationFields
        assertEquals(all.size, all.map { it.key }.toSet().size, "field keys are list item keys, so they are unique")
        all.forEach { field ->
            val server = AmneziaWg()
            val value = if (field.digitsOnly) "123" else "v-${field.key}"
            field.write(server, value)
            assertEquals(value, field.read(server), field.key)
            // Writing one field changes no other: the rest of the profile is as a fresh one.
            val untouched = AmneziaWg()
            val others = (all - field).filter { other -> other.read(server) != other.read(untouched) }
            assertEquals(emptyList(), others.map { it.key }, "writing ${field.key} must not change another field")
        }
    }

    @Test
    fun onlyTheNumericFieldsAcceptDigitsOnly() {
        val numeric = (amneziaWgPropertyFields + amneziaWgObfuscationFields).filter { it.digitsOnly }.map { it.key }
        assertEquals(listOf("port", "mtu", "jc", "jmin", "jmax", "s1", "s2", "s3", "s4"), numeric)
    }

    @Test
    fun theTextFilterRejectsAnythingThatIsNotDigits() {
        assertTrue("12345".isDigitsOnly())
        assertTrue(!"12a".isDigitsOnly())
        assertTrue(!"1-2".isDigitsOnly())
    }
}
