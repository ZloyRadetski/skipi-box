// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SubscriptionRedirectPolicyTest {
    private fun decide(from: String, to: String) = decideSubscriptionRedirect(from, to)

    @Test
    fun sameHostHttpsRedirectIsFollowedWithIdentity() {
        assertEquals(
            SubscriptionRedirectDecision.Follow,
            decide("https://sub.example.test/a", "https://sub.example.test/b"),
        )
    }

    @Test
    fun anotherHostIsFollowedButNeverReceivesDeviceIdentity() {
        assertEquals(
            SubscriptionRedirectDecision.FollowWithoutIdentity,
            decide("https://sub.example.test/a", "https://cdn.example.test/b"),
        )
    }

    @Test
    fun httpsToHttpDowngradeIsRejected() {
        assertIs<SubscriptionRedirectDecision.Reject>(decide("https://sub.example.test/a", "http://sub.example.test/b"))
    }

    @Test
    fun nonHttpSchemesAreRejected() {
        listOf("file:///data/data/x", "ftp://example.test/x", "jar:file:///x!/y", "content://x/y").forEach { target ->
            assertIs<SubscriptionRedirectDecision.Reject>(decide("https://sub.example.test/a", target), target)
        }
    }

    @Test
    fun redirectsToInternalAddressLiteralsAreRejected() {
        listOf(
            "https://127.0.0.1/x",
            "https://localhost/x",
            "https://10.0.0.5/x",
            "https://172.16.4.4/x",
            "https://192.168.31.1/x",
            "https://169.254.169.254/latest/meta-data",
            "https://0.0.0.0/x",
            "https://[::1]/x",
            "https://[fd00::1]/x",
            "https://[fe80::1]/x",
            "https://router.local/x",
        ).forEach { target ->
            assertIs<SubscriptionRedirectDecision.Reject>(decide("https://sub.example.test/a", target), target)
        }
    }

    @Test
    fun publicAddressLiteralsAreNotMistakenForInternalOnes() {
        assertEquals(
            SubscriptionRedirectDecision.FollowWithoutIdentity,
            decide("https://sub.example.test/a", "https://172.32.0.1/x"),
        )
        assertEquals(
            SubscriptionRedirectDecision.FollowWithoutIdentity,
            decide("https://sub.example.test/a", "https://8.8.8.8/x"),
        )
    }

    @Test
    fun userInfoInTheTargetDoesNotHideTheRealHost() {
        assertIs<SubscriptionRedirectDecision.Reject>(
            decide("https://sub.example.test/a", "https://sub.example.test@127.0.0.1/x"),
        )
    }

    @Test
    fun aRedirectInsideAnInternalHostTheUserChoseIsFollowed() {
        assertEquals(
            SubscriptionRedirectDecision.Follow,
            decide("http://192.168.31.10:8080/sub", "http://192.168.31.10:8080/final"),
        )
    }

    @Test
    fun aPublicHostCannotRedirectToTheInternalHostTheUserDidNotChoose() {
        assertIs<SubscriptionRedirectDecision.Reject>(
            decide("https://sub.example.test/a", "https://192.168.31.10/final"),
        )
    }
}
