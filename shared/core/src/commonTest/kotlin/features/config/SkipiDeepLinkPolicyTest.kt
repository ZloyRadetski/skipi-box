// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals

class SkipiDeepLinkPolicyTest {
    @Test
    fun linksThatAddServersOrConfigsNeedTheUsersConfirmation() {
        assertEquals(
            DeepLinkGate.NeedsConfirmation,
            SkipiDeepLink.Subscription("https://example.test/sub").gate(),
        )
        assertEquals(
            DeepLinkGate.NeedsConfirmation,
            SkipiDeepLink.ManualServer("vless://uuid@example.test:443").gate(),
        )
        assertEquals(
            DeepLinkGate.NeedsConfirmation,
            SkipiDeepLink.TrafficConfig(content = "{}", activate = true).gate(),
        )
    }

    @Test
    fun connectionControlLinksNeedAutomationToBeEnabled() {
        assertEquals(DeepLinkGate.NeedsAutomationEnabled, SkipiDeepLink.Connect.gate())
        assertEquals(DeepLinkGate.NeedsAutomationEnabled, SkipiDeepLink.Disconnect.gate())
        assertEquals(DeepLinkGate.NeedsAutomationEnabled, SkipiDeepLink.Toggle.gate())
    }

    @Test
    fun openingAndClosingTheAppIsAlwaysAllowed() {
        assertEquals(DeepLinkGate.Allowed, SkipiDeepLink.Open.gate())
        assertEquals(DeepLinkGate.Allowed, SkipiDeepLink.Close.gate())
    }

    @Test
    fun confirmationNamesTheHostThatWillBeContactedOrImported() {
        assertEquals(
            "example.test",
            SkipiDeepLink.Subscription("https://user:pw@example.test:8443/path?token=1").confirmationTarget(),
        )
        assertEquals(
            "example.test",
            SkipiDeepLink.ManualServer("vless://uuid@example.test:443?security=tls#name").confirmationTarget(),
        )
        assertEquals(
            "config.example.test",
            SkipiDeepLink.TrafficConfig(
                content = "https://config.example.test/rules.json",
                activate = false,
            ).confirmationTarget(),
        )
    }

    @Test
    fun inlineConfigContentHasNoHostToShow() {
        assertEquals(
            "",
            SkipiDeepLink.TrafficConfig(content = "{\"rules\":[]}", activate = true).confirmationTarget(),
        )
    }

    @Test
    fun aThemeLinkNeedsConfirmationAndNamesTheThemeNotAHost() {
        val link = SkipiDeepLink.Theme(
            CustomThemeShare(
                name = "Night harbour",
                dark = true,
                accent = 0xFF3B82F6,
                background = 0xFF081427,
                surface = 0xFF102443,
                surfaceVariant = 0xFF18345E,
                text = 0xFFE7F1FF,
                textSecondary = 0xFFB2C8EA,
            ),
        )

        assertEquals(DeepLinkGate.NeedsConfirmation, link.gate())
        assertEquals("Night harbour", link.confirmationTarget())
    }
}
