// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.StoredSubscription
import features.subscription.StoredSubscriptionMetadata
import features.subscription.SubscriptionExpiryReminder
import features.subscription.SubscriptionMetadata
import features.subscription.SubscriptionProviderEdit
import features.subscription.SubscriptionProviderLibraries
import features.subscription.SubscriptionProviderLibrary
import features.subscription.toProviderEdit as toSharedProviderEdit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Compatibility aliases preserve Desktop call sites and persisted JSON field names. */
typealias DesktopStoredSubscriptionMetadata = StoredSubscriptionMetadata
typealias DesktopStoredSubscription = StoredSubscription
typealias DesktopSubscriptionLibrary = SubscriptionProviderLibrary
typealias DesktopSubscriptionProviderEdit = SubscriptionProviderEdit
typealias DesktopSubscriptionExpiryReminderUnit = features.subscription.ExpiryReminderUnit
typealias DesktopSubscriptionExpiryReminder = SubscriptionExpiryReminder

fun DesktopStoredSubscription.toProviderEdit(): DesktopSubscriptionProviderEdit =
    toSharedProviderEdit()

/** Desktop filesystem/JSON adapter; provider operations and validation live in shared core. */
object DesktopSubscriptionLibraries {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun defaultPath(): Path = Path.of(
        System.getenv("APPDATA")?.takeIf(String::isNotBlank) ?: System.getProperty("user.home"),
        "SKIPI", "subscriptions.json",
    )

    fun loadDefault(): Result<DesktopSubscriptionLibrary> = load(defaultPath())
    fun saveDefault(library: DesktopSubscriptionLibrary): Result<Unit> = save(defaultPath(), library)

    fun load(path: Path): Result<DesktopSubscriptionLibrary> = runCatching {
        if (!Files.exists(path)) DesktopSubscriptionLibrary()
        else Files.readString(path, StandardCharsets.UTF_8).takeIf(String::isNotBlank)
            ?.let { json.decodeFromString<SubscriptionProviderLibrary>(it) } ?: DesktopSubscriptionLibrary()
    }

    fun save(path: Path, library: DesktopSubscriptionLibrary): Result<Unit> = runCatching {
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(library), StandardCharsets.UTF_8)
        try {
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, REPLACE_EXISTING)
        }
    }

    fun addOrReplace(
        library: DesktopSubscriptionLibrary,
        url: String,
        userAgent: String = "",
        name: String = "",
        metadata: SubscriptionMetadata? = null,
    ): DesktopSubscriptionLibrary = SubscriptionProviderLibraries.addOrReplace(
        library = library,
        url = url,
        userAgent = userAgent,
        name = name,
        metadata = metadata,
        nowMillis = System.currentTimeMillis(),
    )

    fun updateProvider(
        library: DesktopSubscriptionLibrary,
        subscriptionId: Int,
        edit: DesktopSubscriptionProviderEdit,
    ): DesktopSubscriptionLibrary = SubscriptionProviderLibraries.updateProvider(library, subscriptionId, edit)

    fun remove(library: DesktopSubscriptionLibrary, subscriptionId: Int): DesktopSubscriptionLibrary =
        SubscriptionProviderLibraries.remove(library, subscriptionId)
}
