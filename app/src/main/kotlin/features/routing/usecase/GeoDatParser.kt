// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Android file/stream adapter for the portable GeoSite/GeoIP tag parser. */
object GeoDatParser {
    private val cache = ConcurrentHashMap<String, CachedGeoTags>()

    private data class CachedGeoTags(val lastModified: Long, val length: Long, val tags: List<String>)

    fun parseTags(file: File): List<String> {
        if (!file.isFile || file.length() <= 0) return emptyList()
        val modified = file.lastModified()
        val length = file.length()
        val key = file.absolutePath
        cache[key]?.takeIf { it.lastModified == modified && it.length == length }?.let { return it.tags }
        val parsed = runCatching { file.inputStream().use(::parseTagsFromStream) }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) cache[key] = CachedGeoTags(modified, length, parsed)
        return parsed
    }

    fun parseTagsFromStream(input: InputStream): List<String> {
        val stream = if (input is BufferedInputStream) input else BufferedInputStream(input, 64 * 1024)
        return GeoDatTagParser.parseTags(
            readByte = stream::read,
            skipBytes = stream::skip,
        )
    }

    val DefaultGeoSiteTags: List<String> get() = GeoDatTagParser.DefaultGeoSiteTags
    val DefaultGeoIpTags: List<String> get() = GeoDatTagParser.DefaultGeoIpTags
}
