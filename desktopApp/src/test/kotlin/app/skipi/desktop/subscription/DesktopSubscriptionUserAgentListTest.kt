// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.DefaultSubscriptionUserAgent
import features.subscription.DefaultSubscriptionUserAgents
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSubscriptionUserAgentListTest {
    @Test
    fun freshSettingsStartWithTheAndroidPresetList() {
        assertEquals(DefaultSubscriptionUserAgents, DesktopAppSettings().subscriptionUserAgents)
    }

    @Test
    fun savedSettingsNormalizeSkipiAgentsAndRestoreAnEmptyList() {
        val directory = Files.createTempDirectory("skipi-settings-ua-")
        try {
            val path = directory.resolve("settings.json")
            DesktopSettingsLibraries.save(
                path,
                DesktopAppSettings(subscriptionUserAgents = listOf("SKIPI/0.1.0/Android", "Custom")),
            ).getOrThrow()
            assertEquals(
                listOf(DefaultSubscriptionUserAgent, "Custom"),
                DesktopSettingsLibraries.load(path).getOrThrow().subscriptionUserAgents,
            )

            DesktopSettingsLibraries.save(path, DesktopAppSettings(subscriptionUserAgents = emptyList())).getOrThrow()
            assertEquals(
                DefaultSubscriptionUserAgents,
                DesktopSettingsLibraries.load(path).getOrThrow().subscriptionUserAgents,
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun theLegacySingleFallbackAgentKeyIsIgnoredWhenLoading() {
        val directory = Files.createTempDirectory("skipi-settings-legacy-ua-")
        try {
            val path = directory.resolve("settings.json")
            Files.writeString(path, """{"subscriptionUserAgent": "SKIPI Desktop"}""")
            assertEquals(
                DefaultSubscriptionUserAgents,
                DesktopSettingsLibraries.load(path).getOrThrow().subscriptionUserAgents,
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
