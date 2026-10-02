// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

/** Streaming, platform-independent reader for category tags in GeoSite and GeoIP protobuf files. */
object GeoDatTagParser {
    val DefaultGeoSiteTags = listOf(
        "category-ads-all", "category-anti-ad", "category-gov-cn", "category-media", "category-porn",
        "category-games", "category-dev", "category-finance", "category-shopping", "category-education",
        "geolocation-!cn", "geolocation-cn", "cn", "google", "youtube", "telegram", "openai", "github",
        "twitter", "facebook", "instagram", "apple", "microsoft", "amazon", "cloudflare", "netflix",
        "spotify", "disney", "discord", "steam", "tiktok", "reddit", "wikipedia", "tor", "speedtest",
        "bilibili", "baidu", "alibaba", "tencent", "bytedance", "epicgames", "origin", "ubisoft",
        "playstation", "xbox", "vk", "yandex", "mailru", "rutracker",
    )

    val DefaultGeoIpTags = listOf(
        "cn", "private", "telegram", "us", "ru", "ir", "hk", "jp", "sg", "gb", "de", "ca", "au", "nl",
        "fr", "kr", "tw", "in", "br", "ua", "tr", "it", "es", "pl", "se", "ch", "fi", "no",
    )

    fun parseTags(readByte: () -> Int, skipBytes: (Long) -> Long): List<String> {
        val reader = GeoDatReader(readByte, skipBytes)
        val tags = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        try {
            while (true) {
                val field = reader.readVarint() ?: break
                val wireType = (field and 7).toInt()
                val fieldNumber = (field ushr 3).toInt()
                if (fieldNumber == 1 && wireType == 2) {
                    val length = reader.readVarint() ?: break
                    if (length <= 0) continue
                    val tag = reader.readEntryTag(length)
                    if (!tag.isNullOrBlank() && seen.add(tag)) tags += tag
                } else {
                    reader.skipField(wireType)
                }
            }
        } catch (_: Throwable) {
            // Keep tags parsed before a malformed/truncated protobuf field.
        }
        return tags
    }

    private class GeoDatReader(
        private val readByte: () -> Int,
        private val skipBytes: (Long) -> Long,
    ) {
        fun readVarint(): Long? = readVarintCounted().first

        private fun readVarintCounted(): Pair<Long?, Long> {
            var value = 0L
            var shift = 0
            var count = 0L
            while (shift < 64) {
                val byte = readByte()
                if (byte < 0) return (if (shift == 0) null else value) to count
                count++
                value = value or ((byte and 0x7f).toLong() shl shift)
                if ((byte and 0x80) == 0) return value to count
                shift += 7
            }
            return value to count
        }

        fun readEntryTag(entryLength: Long): String? {
            var consumed = 0L
            var result: String? = null
            while (consumed < entryLength) {
                val (field, fieldBytes) = readVarintCounted()
                if (field == null) break
                consumed += fieldBytes
                val wireType = (field and 7).toInt()
                val fieldNumber = (field ushr 3).toInt()
                if (fieldNumber == 1 && wireType == 2) {
                    val (length, lengthBytes) = readVarintCounted()
                    if (length == null) break
                    consumed += lengthBytes
                    if (length in 1..4096) {
                        val bytes = ByteArray(length.toInt())
                        var index = 0
                        while (index < bytes.size) {
                            val byte = readByte()
                            if (byte < 0) break
                            bytes[index++] = byte.toByte()
                        }
                        consumed += index
                        if (index == bytes.size) result = bytes.decodeToString().trim()
                        break
                    }
                    consumed += skipFully(length)
                } else {
                    consumed += skipField(wireType)
                }
            }
            if (consumed < entryLength) skipFully(entryLength - consumed)
            return result
        }

        fun skipField(wireType: Int): Long = when (wireType) {
            0 -> readVarintCounted().second
            1 -> skipFully(8)
            2 -> {
                val (length, prefixBytes) = readVarintCounted()
                prefixBytes + skipFully(length?.coerceAtLeast(0) ?: 0)
            }
            5 -> skipFully(4)
            else -> 0
        }

        private fun skipFully(bytes: Long): Long {
            var remaining = bytes.coerceAtLeast(0)
            var skippedTotal = 0L
            while (remaining > 0) {
                val skipped = skipBytes(remaining)
                if (skipped > 0) {
                    val actual = minOf(skipped, remaining)
                    remaining -= actual
                    skippedTotal += actual
                } else {
                    if (readByte() < 0) break
                    remaining--
                    skippedTotal++
                }
            }
            return skippedTotal
        }
    }
}
