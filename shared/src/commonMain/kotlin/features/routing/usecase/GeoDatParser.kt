// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast, streaming Protobuf parser to extract category / country_code tags from
 * GeoSite and GeoIP `.dat` files without parsing the heavy domain/CIDR payload.
 */
object GeoDatParser {

    private val cache = ConcurrentHashMap<String, CachedGeoTags>()

    private data class CachedGeoTags(
        val lastModified: Long,
        val length: Long,
        val tags: List<String>,
    )

    /**
     * Extracts tags from a `.dat` file (standard or custom).
     * Results are cached based on file timestamp and size.
     */
    fun parseTags(file: File): List<String> {
        if (!file.isFile || file.length() <= 0) return emptyList()

        val lastModified = file.lastModified()
        val length = file.length()
        val cacheKey = file.absolutePath

        val cached = cache[cacheKey]
        if (cached != null && cached.lastModified == lastModified && cached.length == length) {
            return cached.tags
        }

        val parsed = runCatching {
            file.inputStream().use { input ->
                parseTagsFromStream(input)
            }
        }.getOrDefault(emptyList())

        if (parsed.isNotEmpty()) {
            cache[cacheKey] = CachedGeoTags(lastModified, length, parsed)
        }
        return parsed
    }

    /**
     * Streams through a Protobuf serialized GeoSiteList or GeoIPList and extracts
     * all `country_code` field values (field 1 in GeoSite/GeoIP entry).
     */
    fun parseTagsFromStream(input: InputStream): List<String> {
        val stream = if (input is BufferedInputStream) input else BufferedInputStream(input, 64 * 1024)
        val tags = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        try {
            while (true) {
                val tagWithWire = readVarint(stream) ?: break
                val wireType = (tagWithWire and 0x07).toInt()
                val fieldNumber = (tagWithWire ushr 3).toInt()

                // Field 1 in GeoSiteList / GeoIPList is `repeated GeoSite entry = 1` or `repeated GeoIP entry = 1`
                if (fieldNumber == 1 && wireType == 2) {
                    val entryLength = readVarint(stream) ?: break
                    if (entryLength <= 0) continue

                    // Parse inner fields of GeoSite / GeoIP up to entryLength bytes
                    val tag = parseSingleEntryTag(stream, entryLength)
                    if (!tag.isNullOrBlank() && seen.add(tag)) {
                        tags.add(tag)
                    }
                } else {
                    skipField(stream, wireType)
                }
            }
        } catch (_: Throwable) {
            // Return whatever tags were successfully collected before any error
        }

        return tags
    }

    private fun parseSingleEntryTag(stream: InputStream, entryLength: Long): String? {
        var bytesRead = 0L
        var extractedTag: String? = null

        while (bytesRead < entryLength) {
            val tagWithWire = readVarintCounted(stream) { bytesRead += it } ?: break
            val wireType = (tagWithWire and 0x07).toInt()
            val fieldNumber = (tagWithWire ushr 3).toInt()

            // Field 1 in GeoSite / GeoIP is `string country_code = 1`
            if (fieldNumber == 1 && wireType == 2) {
                val strLen = readVarintCounted(stream) { bytesRead += it } ?: break
                if (strLen in 1..4096) {
                    val buffer = ByteArray(strLen.toInt())
                    readFully(stream, buffer)
                    bytesRead += strLen
                    extractedTag = String(buffer, Charsets.UTF_8).trim()

                    // Once country_code is read, skip the rest of this entry
                    val remaining = entryLength - bytesRead
                    if (remaining > 0) {
                        skipFully(stream, remaining)
                        bytesRead += remaining
                    }
                    break
                } else {
                    skipFully(stream, strLen)
                    bytesRead += strLen
                }
            } else {
                val skipped = skipFieldCounted(stream, wireType)
                bytesRead += skipped
            }
        }

        return extractedTag
    }

    private inline fun readVarintCounted(stream: InputStream, onBytes: (Long) -> Unit): Long? {
        var result = 0L
        var shift = 0
        var count = 0L
        while (shift < 64) {
            val b = stream.read()
            if (b == -1) {
                onBytes(count)
                return if (shift == 0) null else result
            }
            count++
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) {
                onBytes(count)
                return result
            }
            shift += 7
        }
        onBytes(count)
        return result
    }

    private fun readVarint(stream: InputStream): Long? {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val b = stream.read()
            if (b == -1) {
                return if (shift == 0) null else result
            }
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        return result
    }

    private fun skipField(stream: InputStream, wireType: Int) {
        when (wireType) {
            0 -> readVarint(stream)
            1 -> skipFully(stream, 8)
            2 -> {
                val len = readVarint(stream) ?: 0L
                if (len > 0) skipFully(stream, len)
            }
            5 -> skipFully(stream, 4)
            else -> {}
        }
    }

    private fun skipFieldCounted(stream: InputStream, wireType: Int): Long {
        var count = 0L
        when (wireType) {
            0 -> readVarintCounted(stream) { count += it }
            1 -> {
                skipFully(stream, 8)
                count += 8
            }
            2 -> {
                val len = readVarintCounted(stream) { count += it } ?: 0L
                if (len > 0) {
                    skipFully(stream, len)
                    count += len
                }
            }
            5 -> {
                skipFully(stream, 4)
                count += 4
            }
            else -> {}
        }
        return count
    }

    private fun readFully(stream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = stream.read(buffer, offset, buffer.size - offset)
            if (count == -1) break
            offset += count
        }
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val tempBuffer = ByteArray(8192)
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                val toRead = minOf(remaining, tempBuffer.size.toLong()).toInt()
                val read = stream.read(tempBuffer, 0, toRead)
                if (read == -1) break
                remaining -= read
            } else {
                remaining -= skipped
            }
        }
    }

    /** Popular standard GeoSite categories used as offline defaults or instant previews */
    val DefaultGeoSiteTags = listOf(
        "category-ads-all",
        "category-anti-ad",
        "category-gov-cn",
        "category-media",
        "category-porn",
        "category-games",
        "category-dev",
        "category-finance",
        "category-shopping",
        "category-education",
        "geolocation-!cn",
        "geolocation-cn",
        "cn",
        "google",
        "youtube",
        "telegram",
        "openai",
        "github",
        "twitter",
        "facebook",
        "instagram",
        "apple",
        "microsoft",
        "amazon",
        "cloudflare",
        "netflix",
        "spotify",
        "disney",
        "discord",
        "steam",
        "tiktok",
        "reddit",
        "wikipedia",
        "tor",
        "speedtest",
        "bilibili",
        "baidu",
        "alibaba",
        "tencent",
        "bytedance",
        "epicgames",
        "origin",
        "ubisoft",
        "playstation",
        "xbox",
        "vk",
        "yandex",
        "mailru",
        "rutracker",
    )

    /** Popular standard GeoIP tags used as offline defaults or instant previews */
    val DefaultGeoIpTags = listOf(
        "cn",
        "private",
        "telegram",
        "us",
        "ru",
        "ir",
        "hk",
        "jp",
        "sg",
        "gb",
        "de",
        "ca",
        "au",
        "nl",
        "fr",
        "kr",
        "tw",
        "in",
        "br",
        "ua",
        "tr",
        "it",
        "es",
        "pl",
        "se",
        "ch",
        "fi",
        "no",
    )
}
