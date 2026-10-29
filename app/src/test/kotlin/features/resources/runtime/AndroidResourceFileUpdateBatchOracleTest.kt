// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import features.resources.ResourceFileKind
import features.resources.ResourceFileUpdateSource
import com.sun.net.httpserver.HttpServer
import engine.proxy.LocalProxyRuntime
import features.config.TrafficConfigCustomResourceFile
import features.resources.ResourceFileScope
import features.resources.ResourceFileUpdateOptions
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import features.resources.ResourceFileDownloadCancelledException

/** Android-source baseline for the shared batch executor boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = android.app.Application::class,
    shadows = [RealOsRenameShadow::class],
)
class AndroidResourceFileUpdateBatchOracleTest {
    @Before
    fun resetHostDownloadState() {
        ResourceFileDownloadCancellation.begin()
        LocalProxyRuntime.clear()
    }

    @After
    fun clearHostDownloadState() {
        ResourceFileDownloadCancellation.begin()
        LocalProxyRuntime.clear()
    }

    @Test
    fun emptyCustomUpdateDoesNotCreateDirectoryResetCancellationOrResolveProxy() {
        withFilesContext { context, root ->
            val scope = ResourceFileScope(trafficConfigId = 88, resourceFileSource = 999)
            val emptyCustomFile = TrafficConfigCustomResourceFile(
                id = 90,
                name = "empty-resource.dat",
                url = " \t ",
            )
            val store = AndroidResourceFileStore(context, scope)
            ResourceFileDownloadCancellation.cancel()

            val status = runBlocking {
                AndroidResourceFileRepository(context).updateCustom(
                    scope = scope,
                    customFile = emptyCustomFile,
                    options = ResourceFileUpdateOptions(
                        useRunningProxy = true,
                        fallbackProxyPort = null,
                        showNotifications = false,
                    ),
                    customResourceFiles = listOf(emptyCustomFile),
                )
            }

            assertEquals(1, status.customResourceFiles.size)
            assertFalse(status.customResourceFiles.single().status.exists)
            assertFalse(store.dataDir.exists())
            assertFalse(File(root, "xray/profiles/${scope.trafficConfigId}").exists())
            assertTrue(ResourceFileDownloadCancellation.isCancelled())
        }
    }

    @Test
    fun cancellationOnSecondAllDownloadKeepsPublishedPrefixAndDoesNotMarkSource() {
        withLoopbackServer(cancelPath = "/geosite") { server, requests ->
            withFilesContext { context, root ->
                val scope = ResourceFileScope(trafficConfigId = 89, resourceFileSource = 999)
                val store = AndroidResourceFileStore(context, scope)
                store.dataDir.mkdirs()
                store.file(ResourceFileKind.GeoIp).writeText("old geoip")
                store.file(ResourceFileKind.GeoSite).writeText("old geosite")
                store.file(ResourceFileKind.DirectCidrIpv4).writeText("old direct-v4")

                val error = runCatching {
                    runBlocking {
                        AndroidResourceFileRepository(context).update(
                            scope = scope,
                            source = source(server.address.port),
                            options = ResourceFileUpdateOptions(showNotifications = false),
                        )
                    }
                }.exceptionOrNull()

                assertTrue(error is ResourceFileDownloadCancelledException)
                assertEquals(
                    context.getString(app.R.string.resource_file_download_notification_cancelled),
                    error?.message,
                )
                assertEquals(listOf("/geoip", "/geosite"), requests.toList())
                assertEquals("response:/geoip", store.file(ResourceFileKind.GeoIp).readText())
                assertEquals("old geosite", store.file(ResourceFileKind.GeoSite).readText())
                assertEquals("old direct-v4", store.file(ResourceFileKind.DirectCidrIpv4).readText())
                assertFalse(sourceMarker(root, scope).exists())
                assertTrue(ResourceFileDownloadCancellation.isCancelled())
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

    private fun sourceMarker(root: File, scope: ResourceFileScope) =
        File(root, "xray/profiles/${scope.trafficConfigId}/.resource-source")

    private inline fun withFilesContext(block: (Context, File) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "resource-batch-${System.nanoTime()}").apply { mkdirs() }
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
        cancelPath: String,
        block: (HttpServer, List<String>) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = CopyOnWriteArrayList<String>()
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.rawPath
            requests += path
            val bytes = "response:$path".encodeToByteArray()
            if (path == cancelPath) {
                ResourceFileDownloadCancellation.cancel()
                try {
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { output -> output.write(bytes) }
                } catch (_: IOException) {
                    exchange.close()
                }
            } else {
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { output -> output.write(bytes) }
            }
        }
        server.start()
        try {
            block(server, requests)
        } finally {
            server.stop(0)
        }
    }
}

