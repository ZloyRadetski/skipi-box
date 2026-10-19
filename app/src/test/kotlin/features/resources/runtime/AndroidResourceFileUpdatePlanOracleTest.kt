// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import app.ResourceFileKind
import app.ResourceFileUpdateSource
import com.sun.net.httpserver.HttpServer
import features.config.TrafficConfigCustomResourceFile
import features.resources.ResourceFileScope
import features.resources.ResourceFileUpdateOptions
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android repository baseline for the ordered plan moved into shared resource policy. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = android.app.Application::class,
    shadows = [RealOsRenameShadow::class],
)
class AndroidResourceFileUpdatePlanOracleTest {
    @Test
    fun allUpdateDownloadsBuiltInsThenEligibleCustomsAndMarksRawScopeSource() {
        withLoopbackServer { server, requests ->
            withFilesContext { context, root ->
                val scope = ResourceFileScope(trafficConfigId = 77, resourceFileSource = 999)
                val source = source(server.address.port)
                val reservedFiles = ResourceFileKind.entries.mapIndexed { index, kind ->
                    TrafficConfigCustomResourceFile(
                        id = index + 1,
                        name = "nested/${kind.fileName}",
                        url = url(server.address.port, "/reserved-${kind.name.lowercase()}"),
                    )
                }
                val blankUrlFile = TrafficConfigCustomResourceFile(
                    id = 10,
                    name = "blank-url.dat",
                    url = " \t ",
                )
                val duplicateIdFirst = TrafficConfigCustomResourceFile(
                    id = 20,
                    name = "nested/shared-custom.dat",
                    url = "  ${url(server.address.port, "/same-id-first")}  ",
                )
                val duplicateIdSecond = TrafficConfigCustomResourceFile(
                    id = 20,
                    name = "shared-custom.dat",
                    url = url(server.address.port, "/same-id-second"),
                )
                val duplicateNameDifferentId = TrafficConfigCustomResourceFile(
                    id = 21,
                    name = "other/shared-custom.dat",
                    url = url(server.address.port, "/same-name-different-id"),
                )
                val fallbackName = TrafficConfigCustomResourceFile(
                    id = 22,
                    name = "  ",
                    url = url(server.address.port, "/fallback-name"),
                )
                val customFiles = reservedFiles + listOf(
                    blankUrlFile,
                    duplicateIdFirst,
                    duplicateIdSecond,
                    duplicateNameDifferentId,
                    fallbackName,
                )
                val repository = AndroidResourceFileRepository(context)
                val store = AndroidResourceFileStore(context, scope)

                runBlocking {
                    repository.update(
                        scope = scope,
                        source = source,
                        options = ResourceFileUpdateOptions(showNotifications = false),
                        customResourceFiles = customFiles,
                    )
                }

                assertEquals(
                    listOf(
                        "/geoip",
                        "/geosite",
                        "/geoip-only-cn-private",
                        "/direct-v4",
                        "/direct-v6",
                        "/same-id-first",
                        "/same-id-second",
                        "/same-name-different-id",
                        "/fallback-name",
                    ),
                    requests.toList(),
                )
                assertEquals(body("/geoip"), store.file(ResourceFileKind.GeoIp).readBytes().toStringUtf8())
                assertEquals(body("/geosite"), store.file(ResourceFileKind.GeoSite).readBytes().toStringUtf8())
                assertEquals(body("/geoip-only-cn-private"), store.file(ResourceFileKind.GeoIpOnlyCnPrivate).readBytes().toStringUtf8())
                assertEquals(body("/direct-v4"), store.file(ResourceFileKind.DirectCidrIpv4).readBytes().toStringUtf8())
                assertEquals(body("/direct-v6"), store.file(ResourceFileKind.DirectCidrIpv6).readBytes().toStringUtf8())
                assertEquals(body("/same-name-different-id"), store.file(duplicateIdFirst).readBytes().toStringUtf8())
                assertEquals("custom-resource-22.dat", store.file(fallbackName).name)
                assertEquals(body("/fallback-name"), store.file(fallbackName).readBytes().toStringUtf8())
                assertEquals("999", sourceMarker(root, scope).readText())
            }
        }
    }

    @Test
    fun allUpdateKeepsPublishedPrefixAndDoesNotMarkOrContinueAfterFailure() {
        withLoopbackServer(failingPaths = setOf("/geosite")) { server, requests ->
            withFilesContext { context, root ->
                val scope = ResourceFileScope(trafficConfigId = 78, resourceFileSource = 999)
                val repository = AndroidResourceFileRepository(context)
                val store = AndroidResourceFileStore(context, scope)
                val error = runCatching {
                    runBlocking {
                        repository.update(
                            scope = scope,
                            source = source(server.address.port),
                            options = ResourceFileUpdateOptions(showNotifications = false),
                        )
                    }
                }.exceptionOrNull()

                assertTrue(error is RuntimeException)
                assertTrue(error?.message.orEmpty().contains(ResourceFileKind.GeoSite.fileName))
                assertEquals(listOf("/geoip", "/geosite"), requests.toList())
                assertEquals(body("/geoip"), store.file(ResourceFileKind.GeoIp).readBytes().toStringUtf8())
                assertFalse(store.file(ResourceFileKind.GeoSite).exists())
                assertFalse(store.file(ResourceFileKind.DirectCidrIpv4).exists())
                assertFalse(sourceMarker(root, scope).exists())
            }
        }
    }

    @Test
    fun singleBuiltInAndCustomDoNotMarkSourceAndCoreIsRejectedBeforeNetwork() {
        withLoopbackServer { server, requests ->
            withFilesContext { context, root ->
                val scope = ResourceFileScope(trafficConfigId = 79, resourceFileSource = 999)
                val repository = AndroidResourceFileRepository(context)
                val store = AndroidResourceFileStore(context, scope)
                val source = source(server.address.port)
                val options = ResourceFileUpdateOptions(showNotifications = false)

                runBlocking {
                    repository.update(
                        scope = scope,
                        kind = ResourceFileKind.GeoSite,
                        source = source,
                        options = options,
                    )
                }
                assertEquals(listOf("/geosite"), requests.toList())
                assertFalse(sourceMarker(root, scope).exists())

                val whitespaceBuiltInError = runCatching {
                    runBlocking {
                        repository.update(
                            scope = scope,
                            kind = ResourceFileKind.GeoIp,
                            source = source.copy(geoIpUrl = " "),
                            options = options,
                        )
                    }
                }.exceptionOrNull()
                assertTrue(whitespaceBuiltInError is RuntimeException)
                assertEquals(listOf("/geosite"), requests.toList())
                assertFalse(sourceMarker(root, scope).exists())

                val customFile = TrafficConfigCustomResourceFile(
                    id = 40,
                    name = "single-custom.dat",
                    url = "  ${url(server.address.port, "/single-custom")} \t",
                )
                runBlocking {
                    repository.updateCustom(
                        scope = scope,
                        customFile = customFile,
                        options = options,
                        customResourceFiles = listOf(customFile),
                    )
                }
                assertEquals(listOf("/geosite", "/single-custom"), requests.toList())
                assertEquals(body("/single-custom"), store.file(customFile).readBytes().toStringUtf8())
                assertFalse(sourceMarker(root, scope).exists())

                val emptyCustomFile = TrafficConfigCustomResourceFile(
                    id = 41,
                    name = "empty-url.dat",
                    url = " \t ",
                )
                runBlocking {
                    repository.updateCustom(
                        scope = scope,
                        customFile = emptyCustomFile,
                        options = options,
                        customResourceFiles = listOf(customFile, emptyCustomFile),
                    )
                }
                assertEquals(listOf("/geosite", "/single-custom"), requests.toList())
                assertFalse(sourceMarker(root, scope).exists())

                val coreError = runCatching {
                    runBlocking {
                        repository.update(
                            scope = scope,
                            kind = ResourceFileKind.XrayCore,
                            source = source,
                            options = options,
                        )
                    }
                }.exceptionOrNull()

                assertTrue(coreError is IllegalStateException)
                assertEquals(
                    "${ResourceFileKind.XrayCore.displayName} cannot be updated from URL",
                    coreError?.message,
                )
                assertEquals(listOf("/geosite", "/single-custom"), requests.toList())
                assertFalse(sourceMarker(root, scope).exists())
            }
        }
    }

    private fun source(port: Int) = ResourceFileUpdateSource(
        id = 0,
        geoIpUrl = url(port, "/geoip"),
        geoSiteUrl = url(port, "/geosite"),
        geoIpOnlyCnPrivateUrl = url(port, "/geoip-only-cn-private"),
        directCidrIpv4Url = url(port, "/direct-v4"),
        directCidrIpv6Url = url(port, "/direct-v6"),
    )

    private fun url(port: Int, path: String) = "http://127.0.0.1:$port$path"

    private fun body(path: String) = "response:$path"

    private fun sourceMarker(root: File, scope: ResourceFileScope) =
        File(root, "xray/profiles/${scope.trafficConfigId}/.resource-source")

    private fun ByteArray.toStringUtf8() = decodeToString()

    private inline fun withFilesContext(block: (Context, File) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "resource-update-plan-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
        try {
            block(context, root)
        } finally {
            root.deleteRecursively()
        }
    }

    private inline fun withLoopbackServer(
        failingPaths: Set<String> = emptySet(),
        block: (HttpServer, List<String>) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = CopyOnWriteArrayList<String>()
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.rawPath
            requests += path
            val bytes = body(path).encodeToByteArray()
            exchange.sendResponseHeaders(if (path in failingPaths) 503 else 200, bytes.size.toLong())
            exchange.responseBody.use { output -> output.write(bytes) }
        }
        server.start()
        try {
            block(server, requests)
        } finally {
            server.stop(0)
        }
    }
}
