// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopCoreProfileAssetsTest {

    @Test
    fun explicitProfileDirectoryOverridesOnlyTheAssetsDirectory() = withTemporaryUserHome {
        val runtime = fakeRuntime()
        val native = FakeDesktopCoreNative()
        val loadedLibraries = mutableListOf<Path>()
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(runtime) },
            nativeLoader = { library ->
                loadedLibraries.add(library)
                native
            },
        )
        val profileAssets = Path.of(System.getProperty("user.home"), "profiles", "31")

        val result = controller.start("{}", profileAssets)

        assertTrue(result.isSuccess)
        assertEquals(profileAssets.toString(), native.initializedAssetsDirectories.single())
        assertEquals(listOf("{}"), native.startedConfigs)
        assertEquals(listOf(runtime.library.toAbsolutePath().normalize()), loadedLibraries)
        controller.stop().getOrThrow()
    }

    @Test
    fun nullProfileDirectoryUsesTheLegacyRuntimeDirectoryString() = withTemporaryUserHome {
        val runtime = fakeRuntime()
        val native = FakeDesktopCoreNative()
        val loadedLibraries = mutableListOf<Path>()
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(runtime) },
            nativeLoader = { library ->
                loadedLibraries.add(library)
                native
            },
        )

        val result = controller.start("{}", assetsDirectory = null)

        assertTrue(result.isSuccess)
        assertEquals(runtime.directory.toString(), native.initializedAssetsDirectories.single())
        assertEquals(listOf(runtime.library.toAbsolutePath().normalize()), loadedLibraries)
        controller.stop().getOrThrow()
    }

    @Test
    fun switchingProfilesReinitializesAssetsButKeepsTheLoadedNativeLibrary() = withTemporaryUserHome {
        val runtime = fakeRuntime()
        val native = FakeDesktopCoreNative()
        val loadedLibraries = mutableListOf<Path>()
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(runtime) },
            nativeLoader = { library ->
                loadedLibraries.add(library)
                native
            },
        )
        val profileAAssets = Path.of(System.getProperty("user.home"), "profiles", "41")
        val profileBAssets = Path.of(System.getProperty("user.home"), "profiles", "42")

        controller.start("{\"profile\":\"a\"}", profileAAssets).getOrThrow()
        controller.stop().getOrThrow()
        controller.start("{\"profile\":\"b\"}", profileBAssets).getOrThrow()

        assertEquals(
            listOf(profileAAssets.toString(), profileBAssets.toString()),
            native.initializedAssetsDirectories,
        )
        assertEquals(listOf(runtime.library.toAbsolutePath().normalize()), loadedLibraries)
        controller.stop().getOrThrow()
    }

    private fun fakeRuntime(): DesktopCoreRuntime = DesktopCoreRuntime(
        directory = Path.of("legacy-runtime-assets"),
        library = Path.of("runtime", "libskipicore.so"),
        geoIp = Path.of("legacy-runtime-assets", "geoip.dat"),
        geoSite = Path.of("legacy-runtime-assets", "geosite.dat"),
    )

    private class FakeDesktopCoreNative : DesktopCoreNative {
        override val apiVersion: String = DesktopCoreApiVersion
        override val coreVersion: String = "profile-assets-test"

        val initializedAssetsDirectories = mutableListOf<String>()
        val startedConfigs = mutableListOf<String>()
        var running = false

        override fun initializeAssets(directory: String) {
            initializedAssetsDirectories.add(directory)
        }

        override fun createController(): Long = 41L

        override fun destroyController(handle: Long) {
            running = false
        }

        override fun start(handle: Long, configJson: String, tunFd: Long) {
            startedConfigs.add(configJson)
            running = true
        }

        override fun stop(handle: Long) {
            running = false
        }

        override fun isRunning(handle: Long): Boolean = running

        override fun queryTrafficStats(handle: Long): String = "{\"inbound\":{},\"outbound\":{}}"

        override fun measureDelay(handle: Long, targetUrl: String): Long = 1L

        override fun readMemoryStats(): String = "{}"

        override fun forceFreeMemory() = Unit

        override fun close() = Unit
    }

    private inline fun withTemporaryUserHome(block: () -> Unit): Unit = synchronized(UserHomeLock) {
        val previousUserHome = System.getProperty("user.home")
        val temporaryUserHome = Files.createTempDirectory("skipi-core-profile-assets-")
        System.setProperty("user.home", temporaryUserHome.toString())
        try {
            block()
        } finally {
            if (previousUserHome == null) {
                System.clearProperty("user.home")
            } else {
                System.setProperty("user.home", previousUserHome)
            }
            Files.walk(temporaryUserHome).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private companion object {
        val UserHomeLock = Any()
    }
}
