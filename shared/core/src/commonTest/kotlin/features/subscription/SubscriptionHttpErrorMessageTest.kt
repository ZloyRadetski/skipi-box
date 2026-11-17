// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionHttpErrorMessageTest {
    @Test
    fun blankBodyHasNoMessage() {
        assertNull(parseSubscriptionHttpErrorMessage(null))
        assertNull(parseSubscriptionHttpErrorMessage("  \n"))
    }

    @Test
    fun jsonMessageComesFromTheFirstKnownKey() {
        assertEquals("Device limit reached", parseSubscriptionHttpErrorMessage("""{"message":"Device limit reached","detail":"x"}"""))
        assertEquals("Banned", parseSubscriptionHttpErrorMessage("""{"error":"Banned"}"""))
        assertEquals("Expired", parseSubscriptionHttpErrorMessage("""{"detail":" Expired "}"""))
    }

    @Test
    fun jsonMessageMayBeNested() {
        assertEquals("Nested text", parseSubscriptionHttpErrorMessage("""{"error":{"message":"Nested text"}}"""))
        assertEquals("Inner detail", parseSubscriptionHttpErrorMessage("""{"error":{"detail":"Inner detail"}}"""))
    }

    @Test
    fun aJsonMessageIsCutAtTwoHundredCharacters() {
        val long = "x".repeat(300)
        assertEquals(200, parseSubscriptionHttpErrorMessage("""{"message":"$long"}""")?.length)
    }

    @Test
    fun anHtmlPageIsReducedToItsTitle() {
        assertEquals("403 Forbidden", parseSubscriptionHttpErrorMessage("<!DOCTYPE html><html><head><title>403 Forbidden</title></head></html>"))
        assertEquals("Oops", parseSubscriptionHttpErrorMessage("<html><title> Oops </title></html>"))
        assertNull(parseSubscriptionHttpErrorMessage("<html><body>no title</body></html>"))
    }

    @Test
    fun shortPlainTextIsItsFirstLine() {
        assertEquals("Too many requests", parseSubscriptionHttpErrorMessage("\nToo many requests\nretry later"))
    }

    @Test
    fun longPlainTextHasNoMessage() {
        assertNull(parseSubscriptionHttpErrorMessage("y".repeat(300)))
    }
}
