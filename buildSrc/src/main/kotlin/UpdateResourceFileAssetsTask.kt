// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream

abstract class UpdateResourceFileAssetsTask : DefaultTask() {
    @get:Input
    abstract val xrayCoreVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val forceUpdate: Property<Boolean>

    @get:OutputFile
    abstract val xrayCoreFile: RegularFileProperty

    @get:OutputDirectory
    abstract val resourceFileAssetsDir: DirectoryProperty

    init {
        group = "resources"
        description = "Download and update bundled resource and geo file assets."
    }

    @TaskAction
    fun updateAssets() {
        val force = forceUpdate.orNull == true
        downloadZipEntry(
            url = xrayCoreArchiveUrl(),
            entryName = "xray",
            target = xrayCoreFile.get().asFile,
            force = force,
        )

        val assetsBaseDir = resourceFileAssetsDir.get().asFile
        BundledGeoResourceAssets.forEach { asset ->
            downloadFile(
                url = asset.url,
                target = File(assetsBaseDir, asset.relativePath),
                force = force,
            )
        }
    }

    private fun xrayCoreArchiveUrl(): String {
        val version = xrayCoreVersion.get()
        return "https://github.com/XTLS/Xray-core/releases/download/$version/Xray-android-arm64-v8a.zip"
    }

    private fun useExistingFile(target: File, force: Boolean): Boolean {
        if (force || !target.isFile || target.length() <= 0) return false
        logger.lifecycle("Using existing ${target.absolutePath} (${target.length()} bytes)")
        return true
    }

    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 10) {
            val connection = (URI.create(currentUrl).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
            }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 ||
                status == 308
            ) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (!location.isNullOrBlank()) {
                    currentUrl = if (location.startsWith("http")) location else URI.create(currentUrl).resolve(location).toString()
                    redirects++
                    continue
                }
            }
            return connection
        }
        throw GradleException("Too many redirects for $initialUrl")
    }

    private fun downloadZipEntry(url: String, entryName: String, target: File, force: Boolean) {
        if (useExistingFile(target, force)) return
        target.parentFile.mkdirs()
        logger.lifecycle("Downloading $url")
        try {
            val connection = openConnectionWithRedirects(url)
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    if (target.isFile && target.length() > 0) {
                        logger.warn("HTTP $code downloading $url; using existing ${target.absolutePath}")
                        return
                    }
                    throw GradleException("Failed to download $url: HTTP $code")
                }
                ZipInputStream(connection.inputStream).use { zip ->
                    var found = false
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.substringAfterLast('/') == entryName) {
                            target.outputStream().use { output ->
                                zip.copyTo(output)
                            }
                            found = true
                            break
                        }
                        zip.closeEntry()
                    }
                    if (!found) {
                        if (target.isFile && target.length() > 0) {
                            logger.warn("Archive does not contain $entryName: $url; using existing ${target.absolutePath}")
                            return
                        }
                        throw GradleException("Archive does not contain $entryName: $url")
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (target.length() <= 0) {
                target.delete()
                throw GradleException("Downloaded file is empty: $url")
            }
            logger.lifecycle("Updated ${target.absolutePath} (${target.length()} bytes)")
        } catch (e: Exception) {
            if (target.isFile && target.length() > 0) {
                logger.warn("Error downloading $url (${e.message}); using existing ${target.absolutePath}")
            } else {
                target.delete()
                throw (e as? GradleException ?: GradleException("Failed to download $url", e))
            }
        }
    }

    private fun downloadFile(url: String, target: File, force: Boolean) {
        if (useExistingFile(target, force)) return
        target.parentFile.mkdirs()
        logger.lifecycle("Downloading $url")
        try {
            val connection = openConnectionWithRedirects(url)
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    if (target.isFile && target.length() > 0) {
                        logger.warn("HTTP $code downloading $url; using existing ${target.absolutePath}")
                        return
                    }
                    throw GradleException("Failed to download $url: HTTP $code")
                }
                connection.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
            if (target.length() <= 0) {
                target.delete()
                throw GradleException("Downloaded file is empty: $url")
            }
            logger.lifecycle("Updated ${target.absolutePath} (${target.length()} bytes)")
        } catch (e: Exception) {
            if (target.isFile && target.length() > 0) {
                logger.warn("Error downloading $url (${e.message}); using existing ${target.absolutePath}")
            } else {
                target.delete()
                throw (e as? GradleException ?: GradleException("Failed to download $url", e))
            }
        }
    }
}

private data class GeoResourceFileAsset(
    val relativePath: String,
    val url: String,
)

private val BundledGeoResourceAssets = listOf(
    // Loyalsoldier (default)
    GeoResourceFileAsset("geo/loyalsoldier/geoip.dat", "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"),
    GeoResourceFileAsset("geo/loyalsoldier/geosite.dat", "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"),
    // Root default fallbacks
    GeoResourceFileAsset("geoip.dat", "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"),
    GeoResourceFileAsset("geosite.dat", "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"),

    // V2Fly
    GeoResourceFileAsset("geo/v2fly/geoip.dat", "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat"),
    GeoResourceFileAsset("geo/v2fly/geosite.dat", "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat"),
    GeoResourceFileAsset("geo/v2fly/geoip-only-cn-private.dat", "https://github.com/v2fly/geoip/releases/latest/download/geoip-only-cn-private.dat"),
    GeoResourceFileAsset("geoip-only-cn-private.dat", "https://github.com/v2fly/geoip/releases/latest/download/geoip-only-cn-private.dat"),

    // Chocolate4U (Iran)
    GeoResourceFileAsset("geo/chocolate4u/geoip.dat", "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geoip.dat"),
    GeoResourceFileAsset("geo/chocolate4u/geosite.dat", "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geosite.dat"),

    // RunetFreedom (Russia)
    GeoResourceFileAsset("geo/runetfreedom/geoip.dat", "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geoip.dat"),
    GeoResourceFileAsset("geo/runetfreedom/geosite.dat", "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geosite.dat"),

    // Roscomvpn (Russia)
    GeoResourceFileAsset("geo/roscomvpn/geoip.dat", "https://cdn.jsdelivr.net/gh/hydraponique/roscomvpn-geoip/release/geoip.dat"),
    GeoResourceFileAsset("geo/roscomvpn/geosite.dat", "https://cdn.jsdelivr.net/gh/hydraponique/roscomvpn-geosite/release/geosite.dat"),

    // Direct CIDR lists
    GeoResourceFileAsset("xray/direct-cidr-v4.txt", "https://raw.githubusercontent.com/mayaxcn/china-ip-list/master/chnroute.txt"),
    GeoResourceFileAsset("xray/direct-cidr-v6.txt", "https://raw.githubusercontent.com/mayaxcn/china-ip-list/master/chnroute_v6.txt"),
)
