// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSkipiProfileMetadataTest {
    @Test
    fun readsAndroidMetadataAndWritesOnlyDesktopOwnedProfileKeys() {
        val androidProfile = """
            [SKIPI]
            # Android setting must survive desktop edits
            profile-name = Phone profile
            profile-update-url = https://phone.example/profile
            profile-update-locked = yes
            enable-fake-dns = true

            [Rule]
            FINAL,PROXY
        """.trimIndent() + "\n"

        val metadata = androidProfile.readDesktopSkipiProfileMetadata()
        assertEquals("Phone profile", metadata.name)
        assertEquals("https://phone.example/profile", metadata.sourceUrl)
        assertEquals(true, metadata.updateLocked)

        val desktopProfile = androidProfile.withDesktopSkipiProfileMetadata(
            name = "Desktop profile",
            sourceUrl = "https://desktop.example/profile",
            updateLocked = false,
        )
        assertEquals("Desktop profile", desktopProfile.readDesktopSkipiProfileMetadata().name)
        assertEquals("https://desktop.example/profile", desktopProfile.readDesktopSkipiProfileMetadata().sourceUrl)
        assertEquals(false, desktopProfile.readDesktopSkipiProfileMetadata().updateLocked)
        assertTrue(desktopProfile.contains("# Android setting must survive desktop edits"))
        assertTrue(desktopProfile.contains("enable-fake-dns = true"))
        assertTrue(desktopProfile.contains("[Rule]\nFINAL,PROXY"))
    }
}
