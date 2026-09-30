// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.DefaultLocalHttpProxyPort
import platform.DefaultLocalSocksPort

class DesktopSettingsLibraryTest {
    @Test
    fun roundTripsNormalizedSettings() {
        val directory = Files.createTempDirectory("skipi-settings-")
        try {
            val path = directory.resolve("settings.json")
            val settings = DesktopAppSettings(
                localProxyPort = 20_480,
                localHttpProxyPort = 20_481,
                useWindowsSystemProxy = false,
                localProxyListenAddress = " 0.0.0.0 ",
                coreLogLevel = " INFO ",
                subscriptionUserAgent = " SKIPI test agent ",
                subscriptionFetchTimeoutSeconds = 45,
                themeMode = DesktopThemeMode.Amoled,
                compactHome = true,
                showTunnelMemory = false,
                confirmDeletion = false,
            )

            DesktopSettingsLibraries.save(path, settings).getOrThrow()

            assertEquals(
                settings.copy(
                    localProxyListenAddress = "0.0.0.0",
                    coreLogLevel = "info",
                    subscriptionUserAgent = "SKIPI test agent",
                ),
                DesktopSettingsLibraries.load(path).getOrThrow(),
            )
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun rejectsInvalidPortTimeoutAndBlankListenAddressWhenSaving() {
        val directory = Files.createTempDirectory("skipi-settings-invalid-")
        try {
            val path = directory.resolve("settings.json")

            assertTrue(
                DesktopSettingsLibraries.save(
                    path,
                    DesktopAppSettings(localProxyPort = 0),
                ).isFailure,
            )
            assertTrue(
                DesktopSettingsLibraries.save(
                    path,
                    DesktopAppSettings(localProxyPort = 65_536),
                ).isFailure,
            )
            assertTrue(
                DesktopSettingsLibraries.save(
                    path,
                    DesktopAppSettings(localHttpProxyPort = 0),
                ).isFailure,
            )
            assertTrue(
                DesktopSettingsLibraries.save(
                    path,
                    DesktopAppSettings(localHttpProxyPort = DefaultLocalSocksPort),
                ).isFailure,
            )
            assertTrue(
                DesktopSettingsLibraries.save(
                    path,
                    DesktopAppSettings(subscriptionFetchTimeoutSeconds = 9),
                ).isFailure,
            )
            assertTrue(
                DesktopSettingsLibraries.save(
                    path,
                    DesktopAppSettings(subscriptionFetchTimeoutSeconds = 121),
                ).isFailure,
            )
            assertTrue(
                DesktopSettingsLibraries.save(
                    path,
                    DesktopAppSettings(localProxyListenAddress = "   "),
                ).isFailure,
            )
            assertTrue(
                DesktopSettingsLibraries.save(
                    path,
                    DesktopAppSettings(proxyServerListColumns = 0),
                ).isFailure,
            )
            assertTrue(
                DesktopSettingsLibraries.save(
                    path,
                    DesktopAppSettings(proxyServerListColumns = 4),
                ).isFailure,
            )
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun missingBlankAndMalformedFilesHaveSafeDefaultOrFailureResults() {
        val directory = Files.createTempDirectory("skipi-settings-load-")
        try {
            val path = directory.resolve("settings.json")

            assertEquals(DesktopAppSettings(), DesktopSettingsLibraries.load(path).getOrThrow())

            Files.writeString(path, "   \n")
            assertEquals(DesktopAppSettings(), DesktopSettingsLibraries.load(path).getOrThrow())

            Files.writeString(path, "{ not-json }")
            assertTrue(DesktopSettingsLibraries.load(path).isFailure)
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun normalizesLoadedLegacyWhitespaceAndTimeout() {
        val directory = Files.createTempDirectory("skipi-settings-normalize-")
        try {
            val path = directory.resolve("settings.json")
            Files.writeString(
                path,
                """
                {
                  "localProxyPort": $DefaultLocalSocksPort,
                  "localHttpProxyPort": 0,
                  "localProxyListenAddress": "   ",
                  "coreLogLevel": " INFO ",
                  "subscriptionUserAgent": "  ",
                  "subscriptionFetchTimeoutSeconds": 1
                }
                """.trimIndent(),
            )

            assertEquals(
                DesktopAppSettings(
                    localProxyListenAddress = "127.0.0.1",
                    coreLogLevel = "info",
                    subscriptionFetchTimeoutSeconds = 10,
                ),
                DesktopSettingsLibraries.load(path).getOrThrow(),
            )
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun repairsLegacySocksPortThatWouldCollideWithNewHttpProxyPort() {
        val directory = Files.createTempDirectory("skipi-settings-legacy-http-")
        try {
            val path = directory.resolve("settings.json")
            Files.writeString(path, "{\"localProxyPort\": $DefaultLocalHttpProxyPort}")

            val settings = DesktopSettingsLibraries.load(path).getOrThrow()

            assertEquals(DefaultLocalHttpProxyPort, settings.localProxyPort)
            assertEquals(DefaultLocalHttpProxyPort + 1, settings.localHttpProxyPort)
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun keepsLanSocksListenerSeparateFromWindowsHttpProxy() {
        val settings = DesktopAppSettings(
            localProxyListenAddress = "192.168.1.10",
            useWindowsSystemProxy = true,
        ).normalized(
            validate = true,
            supportsWindowsSystemProxy = true,
        )

        assertEquals("192.168.1.10", settings.localProxyListenAddress)
        assertTrue(settings.useWindowsSystemProxy)
    }

    @Test
    fun onlyEnablesWindowsSystemProxyOnWindowsHosts() {
        assertTrue(isDesktopWindowsSystemProxySupported("Windows 11"))
        assertFalse(isDesktopWindowsSystemProxySupported("Mac OS X"))
        assertFalse(isDesktopWindowsSystemProxySupported("Linux"))

        val normalized = DesktopAppSettings(useWindowsSystemProxy = true).normalized(
            supportsWindowsSystemProxy = false,
        )

        assertFalse(normalized.useWindowsSystemProxy)
    }

    @Test
    fun roundTripsHomeDisplayOptionsWithNonDefaultValuesAndCustomColumns() {
        val directory = Files.createTempDirectory("skipi-settings-home-")
        try {
            val path = directory.resolve("settings.json")
            for (columns in listOf(2, 3)) {
                val settings = DesktopAppSettings(
                    compactHome = true,
                    showTunnelMemory = false,
                    pinConnectionPanelOnHome = true,
                    classicShowFloatingPowerButton = true,
                    enableAllProxyGroup = true,
                    showServerSearch = true,
                    enableSubscriptionSwipe = false,
                    proxyServerListColumns = columns,
                )

                DesktopSettingsLibraries.save(path, settings).getOrThrow()
                val loaded = DesktopSettingsLibraries.load(path).getOrThrow()

                assertTrue(loaded.compactHome)
                assertFalse(loaded.showTunnelMemory)
                assertTrue(loaded.pinConnectionPanelOnHome)
                assertTrue(loaded.classicShowFloatingPowerButton)
                assertTrue(loaded.enableAllProxyGroup)
                assertTrue(loaded.showServerSearch)
                assertFalse(loaded.enableSubscriptionSwipe)
                assertEquals(columns, loaded.proxyServerListColumns)
                assertEquals(settings, loaded)
            }
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun loadsOldJsonMissingNewHomeFieldsAndPreservesDefaultsAndLegacyValues() {
        val directory = Files.createTempDirectory("skipi-settings-old-")
        try {
            val path = directory.resolve("settings.json")
            Files.writeString(
                path,
                """
                {
                  "compactHome": true,
                  "showTunnelMemory": false
                }
                """.trimIndent(),
            )

            val loaded = DesktopSettingsLibraries.load(path).getOrThrow()
            assertTrue(loaded.compactHome)
            assertFalse(loaded.showTunnelMemory)
            assertFalse(loaded.pinConnectionPanelOnHome)
            assertFalse(loaded.classicShowFloatingPowerButton)
            assertFalse(loaded.enableAllProxyGroup)
            assertFalse(loaded.showServerSearch)
            assertTrue(loaded.enableSubscriptionSwipe)
            assertEquals(1, loaded.proxyServerListColumns)

            Files.writeString(
                path,
                """
                {
                  "compactHome": false,
                  "showTunnelMemory": true
                }
                """.trimIndent(),
            )
            val loadedClassic = DesktopSettingsLibraries.load(path).getOrThrow()
            assertFalse(loadedClassic.compactHome)
            assertTrue(loadedClassic.showTunnelMemory)
            assertFalse(loadedClassic.pinConnectionPanelOnHome)
            assertFalse(loadedClassic.classicShowFloatingPowerButton)
            assertFalse(loadedClassic.enableAllProxyGroup)
            assertFalse(loadedClassic.showServerSearch)
            assertTrue(loadedClassic.enableSubscriptionSwipe)
            assertEquals(1, loadedClassic.proxyServerListColumns)
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun validatesProxyServerListColumnsOnSaveAndNormalizesOnLoad() {
        val directory = Files.createTempDirectory("skipi-settings-columns-")
        try {
            val path = directory.resolve("settings.json")

            assertTrue(DesktopSettingsLibraries.save(path, DesktopAppSettings(proxyServerListColumns = 0)).isFailure)
            assertTrue(DesktopSettingsLibraries.save(path, DesktopAppSettings(proxyServerListColumns = 4)).isFailure)
            assertTrue(DesktopSettingsLibraries.save(path, DesktopAppSettings(proxyServerListColumns = -1)).isFailure)

            Files.writeString(path, """{"proxyServerListColumns": 0}""")
            assertEquals(1, DesktopSettingsLibraries.load(path).getOrThrow().proxyServerListColumns)

            Files.writeString(path, """{"proxyServerListColumns": 4}""")
            assertEquals(3, DesktopSettingsLibraries.load(path).getOrThrow().proxyServerListColumns)

            Files.writeString(path, """{"proxyServerListColumns": -5}""")
            assertEquals(1, DesktopSettingsLibraries.load(path).getOrThrow().proxyServerListColumns)
        } finally {
            deleteTree(directory)
        }
    }

    private fun deleteTree(directory: java.nio.file.Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
