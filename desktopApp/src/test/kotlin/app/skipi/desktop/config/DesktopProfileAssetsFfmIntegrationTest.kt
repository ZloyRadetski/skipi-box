// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.TrafficConfigCustomResourceFile
import features.resources.ResourceFileSourceRoscomvpnGithub
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Opt-in proof that a real Core resolves an external geo file from the selected profile folder. */
class DesktopProfileAssetsFfmIntegrationTest {
    @Test
    fun realCoreLoadsProfileOnlyExternalGeoFileInsteadOfTheEmptyGlobalAssetsDirectory() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?: return
        val sourceRuntime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val tempRoot = Files.createTempDirectory("skipi-profile-assets-ffm-")
        val globalAssetsDirectory = Files.createDirectory(tempRoot.resolve("global-assets"))
        val profile = TrafficConfigState(
            id = 71,
            name = "Profile-local external geo",
            rawConfig = "",
            resourceSettings = TrafficConfigResourceSettings(source = ResourceFileSourceRoscomvpnGithub),
        )
        val snapshot = DesktopProfileResourceFiles(tempRoot.resolve("xray").toFile()).snapshot(profile)
        val profileOnlyGeoSiteName = "profile-only-geosite.dat"
        val profileOnlyGeoSite = TrafficConfigCustomResourceFile(
            id = 1,
            name = profileOnlyGeoSiteName,
            url = "",
        )
        val core = DesktopCoreController(
            runtimeProvider = {
                Result.success(sourceRuntime.copy(directory = globalAssetsDirectory))
            },
        )

        try {
            withTemporaryUserHome {
                try {
                    snapshot.replaceCustom(profileOnlyGeoSite) {
                        Files.newInputStream(sourceRuntime.geoSite)
                    }
                    assertTrue(Files.isRegularFile(snapshot.file(profileOnlyGeoSite).toPath()))
                    assertFalse(Files.exists(globalAssetsDirectory.resolve(profileOnlyGeoSiteName)))

                    val inboundPort = ServerSocket(0).use(ServerSocket::getLocalPort)
                    val config = """
                    {
                      "log": { "loglevel": "none" },
                      "inbounds": [
                        {
                          "listen": "127.0.0.1",
                          "port": $inboundPort,
                          "protocol": "http",
                          "settings": {}
                        }
                      ],
                      "routing": {
                        "rules": [
                          {
                            "type": "field",
                            "domain": ["ext:$profileOnlyGeoSiteName:geolocation-!cn"],
                            "outboundTag": "direct"
                          }
                        ]
                      },
                      "outbounds": [{ "protocol": "freedom", "tag": "direct" }]
                    }
                    """.trimIndent()

                    assertTrue(
                        core.start(config, snapshot.dataDir.toPath()).getOrThrow().isRunning,
                    )
                    assertFalse(core.stop().getOrThrow().isRunning)
                } finally {
                    core.close()
                }
            }
        } finally {
            Files.walk(tempRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private inline fun withTemporaryUserHome(block: () -> Unit) {
        val previousHome = System.getProperty("user.home")
        val temporaryHome = Files.createTempDirectory("skipi-profile-assets-home-")
        System.setProperty("user.home", temporaryHome.toString())
        try {
            block()
        } finally {
            if (previousHome == null) {
                System.clearProperty("user.home")
            } else {
                System.setProperty("user.home", previousHome)
            }
            Files.walk(temporaryHome).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private companion object {
        const val IntegrationRuntimeDirectoryEnv = "SKIPI_CORE_INTEGRATION_DIR"
    }
}
