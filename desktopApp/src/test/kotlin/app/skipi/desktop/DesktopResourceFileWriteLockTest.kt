// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0


package app.skipi.desktop

import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.ResourceFileKind
import features.resources.ResourceFileSourceV2FlyGithub
import features.resources.runtime.ResourceFileDownloadCancellation
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopResourceFileWriteLockTest {

    @Test
    fun separateHostsSerializeOneAbsoluteTargetAndReleaseItsLockAfterWriterFailure() = withFreshCancellation {
        withXrayRoot { root ->
            withHttpServer { server ->
                val firstBody = "first-body-must-not-publish".encodeToByteArray()
                val secondBody = "second-body-published".encodeToByteArray()
                val secondResponseSent = CountDownLatch(1)
                server.createContext("/first") { exchange ->
                    exchange.sendResponseHeaders(200, firstBody.size.toLong())
                    exchange.responseBody.use { it.write(firstBody) }
                }
                server.createContext("/second") { exchange ->
                    try {
                        exchange.sendResponseHeaders(200, secondBody.size.toLong())
                        exchange.responseBody.use { it.write(secondBody) }
                    } finally {
                        secondResponseSent.countDown()
                    }
                }

                val firstFiles = DesktopProfileResourceFiles(root)
                val secondFiles = DesktopProfileResourceFiles(root)
                val firstTarget = firstFiles.snapshot(profile(61)).file(ResourceFileKind.GeoIp).apply {
                    parentFile.mkdirs()
                    writeText("old-target")
                }
                val secondTarget = secondFiles.snapshot(profile(61)).file(ResourceFileKind.GeoIp)
                assertEquals(firstTarget.absoluteFile, secondTarget.absoluteFile)

                val firstWriterEntered = CountDownLatch(1)
                val releaseFirstWriter = CountDownLatch(1)
                val secondWriterEntered = CountDownLatch(1)
                val releaseSecondWriter = CountDownLatch(1)
                val downloadExecutor = Executors.newFixedThreadPool(2) { runnable ->
                    Thread(runnable, "resource-writer-lock-test").apply { isDaemon = true }
                }
                var firstFuture: Future<*>? = null
                var secondFuture: Future<*>? = null
                try {
                    val first = downloadExecutor.submit {
                        firstFiles.createDownloader().download(
                            url = "http://127.0.0.1:${server.address.port}/first",
                            target = firstTarget,
                            onProgress = { downloaded, _ ->
                                if (downloaded == 0L) {
                                    firstWriterEntered.countDown()
                                    check(releaseFirstWriter.await(3, SECONDS))
                                    error("synthetic failure inside the atomic writer body")
                                }
                            },
                        )
                    }
                    firstFuture = first
                    assertTrue(firstWriterEntered.await(3, SECONDS), "The first writer should enter its atomic body")

                    val second = downloadExecutor.submit {
                        secondFiles.createDownloader().download(
                            url = "http://127.0.0.1:${server.address.port}/second",
                            target = secondTarget,
                            onProgress = { downloaded, _ ->
                                if (downloaded == 0L) {
                                    secondWriterEntered.countDown()
                                    check(releaseSecondWriter.await(3, SECONDS))
                                }
                            },
                        )
                    }
                    secondFuture = second
                    assertTrue(secondResponseSent.await(3, SECONDS), "The second HTTP response should be ready")
                    assertFalse(
                        secondWriterEntered.await(250, MILLISECONDS),
                        "A second writer for the same absolute target must wait for the first writer",
                    )

                    releaseFirstWriter.countDown()
                    val firstFailure = runCatching { first.get(3, SECONDS) }.exceptionOrNull()
                    val executionFailure = assertIs<ExecutionException>(firstFailure)
                    assertTrue(executionFailure.cause is IllegalStateException)

                    assertTrue(secondWriterEntered.await(3, SECONDS), "The lock should be released after failure")
                    assertEquals("old-target", firstTarget.readText())
                    releaseSecondWriter.countDown()
                    second.get(3, SECONDS)
                    assertContentEquals(secondBody, firstTarget.readBytes())
                } finally {
                    releaseFirstWriter.countDown()
                    releaseSecondWriter.countDown()
                    firstFuture?.let { runCatching { it.get(3, SECONDS) } }
                    secondFuture?.let { runCatching { it.get(3, SECONDS) } }
                    downloadExecutor.shutdownNow()
                }
            }
        }
    }

    @Test
    fun separateHostsCanWriteDistinctAbsoluteTargetsConcurrently() = withFreshCancellation {
        withXrayRoot { root ->
            withHttpServer { server ->
                val firstBody = "profile-one".encodeToByteArray()
                val secondBody = "profile-two".encodeToByteArray()
                server.createContext("/one") { exchange ->
                    exchange.sendResponseHeaders(200, firstBody.size.toLong())
                    exchange.responseBody.use { it.write(firstBody) }
                }
                server.createContext("/two") { exchange ->
                    exchange.sendResponseHeaders(200, secondBody.size.toLong())
                    exchange.responseBody.use { it.write(secondBody) }
                }

                val firstFiles = DesktopProfileResourceFiles(root)
                val secondFiles = DesktopProfileResourceFiles(root)
                val firstTarget = firstFiles.snapshot(profile(71)).file(ResourceFileKind.GeoIp)
                val secondTarget = secondFiles.snapshot(profile(72)).file(ResourceFileKind.GeoIp)
                assertFalse(firstTarget.absoluteFile == secondTarget.absoluteFile)

                val firstWriterEntered = CountDownLatch(1)
                val releaseFirstWriter = CountDownLatch(1)
                val secondWriterEntered = CountDownLatch(1)
                val downloadExecutor = Executors.newFixedThreadPool(2) { runnable ->
                    Thread(runnable, "resource-writer-distinct-test").apply { isDaemon = true }
                }
                var firstFuture: Future<*>? = null
                var secondFuture: Future<*>? = null
                try {
                    val first = downloadExecutor.submit {
                        firstFiles.createDownloader().download(
                            url = "http://127.0.0.1:${server.address.port}/one",
                            target = firstTarget,
                            onProgress = { downloaded, _ ->
                                if (downloaded == 0L) {
                                    firstWriterEntered.countDown()
                                    check(releaseFirstWriter.await(3, SECONDS))
                                }
                            },
                        )
                    }
                    firstFuture = first
                    assertTrue(firstWriterEntered.await(3, SECONDS), "The first writer should hold its target lock")

                    val second = downloadExecutor.submit {
                        secondFiles.createDownloader().download(
                            url = "http://127.0.0.1:${server.address.port}/two",
                            target = secondTarget,
                            onProgress = { downloaded, _ ->
                                if (downloaded == 0L) secondWriterEntered.countDown()
                            },
                        )
                    }
                    secondFuture = second
                    assertTrue(
                        secondWriterEntered.await(3, SECONDS),
                        "A writer for a different absolute target must proceed while the first is held",
                    )
                    releaseFirstWriter.countDown()
                    first.get(3, SECONDS)
                    second.get(3, SECONDS)

                    assertContentEquals(firstBody, firstTarget.readBytes())
                    assertContentEquals(secondBody, secondTarget.readBytes())
                } finally {
                    releaseFirstWriter.countDown()
                    firstFuture?.let { runCatching { it.get(3, SECONDS) } }
                    secondFuture?.let { runCatching { it.get(3, SECONDS) } }
                    downloadExecutor.shutdownNow()
                }
            }
        }
    }

    private fun profile(id: Int) = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = "",
        resourceSettings = TrafficConfigResourceSettings(source = ResourceFileSourceV2FlyGithub),
    )

    private inline fun <T> withFreshCancellation(block: () -> T): T = synchronized(UserHomeLock) {
        val previousUserHome = System.getProperty("user.home")
        val temporaryUserHome = Files.createTempDirectory("skipi-resource-write-lock-home-")
        System.setProperty("user.home", temporaryUserHome.toString())
        ResourceFileDownloadCancellation.begin()
        try {
            block()
        } finally {
            ResourceFileDownloadCancellation.cancel()
            ResourceFileDownloadCancellation.begin()
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

    private inline fun <T> withHttpServer(block: (HttpServer) -> T): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "resource-write-lock-http").apply { isDaemon = true }
        }
        server.executor = executor
        server.start()
        return try {
            block(server)
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private inline fun <T> withXrayRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("skipi-desktop-resource-write-lock-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private object UserHomeLock
}
