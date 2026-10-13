// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSubscriptionInstallRepositoryTest {
    @Test
    fun exactUrlDuplicateIsReturnedAndPersistedWithoutChangingExistingOptions() = runBlocking {
        val path = Files.createTempFile("skipi-install-duplicate", ".json")
        val install = checkNotNull(
            DesktopSubscriptionInstallUri.parseOrNull(
                "v2rayng://install-config?url=https%3A%2F%2Fexample.test%2Fsub&name=Incoming",
            ),
        )
        val initial = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                DesktopStoredSubscription(
                    id = 7,
                    url = install.url,
                    userAgent = "Saved UA",
                    name = "Saved title",
                    enabled = false,
                    updateInterval = "18",
                    hwid = "saved-device",
                    ageSecretKey = "saved-secret",
                    updateViaProxy = true,
                    autoOverrideRules = false,
                    notifyOnExpiry = false,
                ),
            ),
            nextSubscriptionId = 30,
        )
        var library = initial

        try {
            DesktopSubscriptionLibraries.save(path, initial).getOrThrow()
            val repository = DesktopSubscriptionRepository(
                initialLibrary = initial,
                readLibrary = { library },
                saveLibrary = { DesktopSubscriptionLibraries.save(path, it) },
                publishLibrary = { library = it },
            )

            val prepared = install.prepareAndPersistIn(repository)

            assertEquals(7, prepared.id)
            assertEquals(initial, library)
            assertEquals(initial, DesktopSubscriptionLibraries.load(path).getOrThrow())
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(path.resolveSibling("${path.fileName}.tmp"))
        }
    }

    @Test
    fun newInstallPersistsThePreparedAndroidCompatibleDefaultsAndNextId() = runBlocking {
        val path = Files.createTempFile("skipi-install-new", ".json")
        val install = checkNotNull(DesktopSubscriptionInstallUri.parseOrNull("http://example.test/sub#New%20list"))
        val initial = DesktopSubscriptionLibrary(
            subscriptions = listOf(DesktopStoredSubscription(id = 4, url = "https://old.example/sub")),
            nextSubscriptionId = 10,
        )
        var library = initial

        try {
            DesktopSubscriptionLibraries.save(path, initial).getOrThrow()
            val repository = DesktopSubscriptionRepository(
                initialLibrary = initial,
                readLibrary = { library },
                saveLibrary = { DesktopSubscriptionLibraries.save(path, it) },
                publishLibrary = { library = it },
            )

            val prepared = install.prepareAndPersistIn(repository)
            val persisted = DesktopSubscriptionLibraries.load(path).getOrThrow()
            val added = persisted.subscriptions.single { it.url == install.url }

            assertEquals(10, prepared.id)
            assertEquals("New list", added.name)
            assertEquals(install.userAgent, added.userAgent)
            assertEquals(true, added.enabled)
            assertEquals("", added.updateInterval)
            assertEquals("", added.hwid)
            assertEquals("", added.ageSecretKey)
            assertEquals(false, added.updateViaProxy)
            assertEquals(true, added.autoOverrideRules)
            assertEquals(true, added.notifyOnExpiry)
            assertEquals(11, persisted.nextSubscriptionId)
            assertEquals(persisted, library)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(path.resolveSibling("${path.fileName}.tmp"))
        }
    }
}
