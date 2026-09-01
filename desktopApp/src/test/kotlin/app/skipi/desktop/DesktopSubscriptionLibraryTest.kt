package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Files
import features.subscription.SubscriptionMetadata

class DesktopSubscriptionLibraryTest {
    @Test fun persists_and_replaces_by_url() {
        val path = Files.createTempDirectory("skipi-subscriptions").resolve("subscriptions.json")
        val first = DesktopSubscriptionLibraries.addOrReplace(DesktopSubscriptionLibrary(), "https://example.com/sub", "SKIPI")
        val updated = DesktopSubscriptionLibraries.addOrReplace(first, "https://example.com/sub", "Agent", "Provider")
        DesktopSubscriptionLibraries.save(path, updated).getOrThrow()
        val stored = DesktopSubscriptionLibraries.load(path).getOrThrow().subscriptions.single()
        assertEquals("Agent", stored.userAgent); assertEquals("Provider", stored.name)
        assertEquals(0, DesktopSubscriptionLibraries.remove(updated, stored.id).subscriptions.size)
    }

    @Test fun rejects_invalid_urls() {
        assertFailsWith<IllegalArgumentException> { DesktopSubscriptionLibraries.addOrReplace(DesktopSubscriptionLibrary(), "file:///bad", "SKIPI") }
    }

    @Test fun normalizes_manual_url_before_replacing() {
        val first = DesktopSubscriptionLibraries.addOrReplace(
            DesktopSubscriptionLibrary(),
            "  https://example.com/sub  ",
            "SKIPI",
        )
        val updated = DesktopSubscriptionLibraries.addOrReplace(first, "https://example.com/sub", "Updated")

        assertEquals(1, updated.subscriptions.size)
        assertEquals("https://example.com/sub", updated.subscriptions.single().url)
        assertEquals("Updated", updated.subscriptions.single().userAgent)
    }

    @Test fun persists_server_metadata_between_desktop_restarts() {
        val updated = DesktopSubscriptionLibraries.addOrReplace(
            library = DesktopSubscriptionLibrary(),
            url = "https://example.com/sub",
            userAgent = "SKIPI",
            metadata = SubscriptionMetadata(
                profileTitle = "Provider",
                profileDescription = "Premium nodes",
                announce = "Maintenance tonight",
                userInfoReceived = true,
                trafficUploadBytes = 10,
                trafficDownloadBytes = 20,
                trafficTotalBytes = 100,
            ),
        )
        val stored = updated.subscriptions.single()

        assertEquals("Provider", stored.name)
        assertEquals("Premium nodes", stored.metadata.description)
        assertEquals("Maintenance tonight", stored.metadata.announce)
        assertEquals(100, stored.metadata.trafficTotalBytes)
        assertEquals(30, stored.metadata.trafficUploadBytes + stored.metadata.trafficDownloadBytes)

        val preserved = DesktopSubscriptionLibraries.addOrReplace(updated, stored.url, "Updated")
        assertEquals(stored.metadata, preserved.subscriptions.single().metadata)
    }

    @Test fun keeps_traffic_metadata_when_a_successful_response_has_no_userinfo_header() {
        val withQuota = DesktopSubscriptionLibraries.addOrReplace(
            library = DesktopSubscriptionLibrary(),
            url = "https://example.com/sub",
            userAgent = "SKIPI",
            metadata = SubscriptionMetadata(
                profileTitle = "Provider",
                userInfoReceived = true,
                trafficUploadBytes = 10,
                trafficDownloadBytes = 20,
                trafficTotalBytes = 100,
            ),
        )

        val refreshed = DesktopSubscriptionLibraries.addOrReplace(
            library = withQuota,
            url = "https://example.com/sub",
            userAgent = "SKIPI",
            metadata = SubscriptionMetadata(profileDescription = "Updated description"),
        ).subscriptions.single()

        assertEquals(10, refreshed.metadata.trafficUploadBytes)
        assertEquals(20, refreshed.metadata.trafficDownloadBytes)
        assertEquals(100, refreshed.metadata.trafficTotalBytes)
        assertEquals("Updated description", refreshed.metadata.description)
    }
}
