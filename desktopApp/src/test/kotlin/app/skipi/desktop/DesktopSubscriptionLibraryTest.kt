package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Files

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
}
