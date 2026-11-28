// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingImportCandidateTest {
    @Test
    fun naiveLinksAreCandidates() {
        assertTrue(isOnboardingImportCandidate("naive+https://alice:pw@naive.example.com:443#N"))
        assertTrue(isOnboardingImportCandidate("  NAIVE+QUIC://alice:pw@naive.example.com:443#N  "))
    }

    @Test
    fun existingSchemesStayCandidates() {
        listOf("vless://x", "vmess://x", "ss://x", "trojan://x", "hysteria2://x", "hy2://x", "masque://x", "http://x", "https://x")
            .forEach { assertTrue(isOnboardingImportCandidate(it), it) }
    }

    @Test
    fun otherTextIsNotACandidate() {
        assertFalse(isOnboardingImportCandidate("hello world"))
        assertFalse(isOnboardingImportCandidate("naive://example.com"))
    }
}
