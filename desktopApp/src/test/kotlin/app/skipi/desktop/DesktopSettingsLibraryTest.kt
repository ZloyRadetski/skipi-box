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

    private fun deleteTree(directory: java.nio.file.Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
