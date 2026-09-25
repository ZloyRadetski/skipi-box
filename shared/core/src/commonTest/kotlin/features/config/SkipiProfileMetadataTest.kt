package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkipiProfileMetadataTest {
    @Test
    fun round_trip_updates_owned_keys_without_dropping_other_profile_data() {
        val source = """
            [SKIPI]
            profile-name = Phone profile
            profile-update-url = https://phone.example/profile
            profile-update-locked = yes
            enable-fake-dns = true

            [Rule]
            FINAL,PROXY
        """.trimIndent() + "\n"

        assertEquals("Phone profile", source.readSkipiProfileMetadata().name)
        assertEquals(true, source.readSkipiProfileMetadata().updateLocked)
        val updated = source.withSkipiProfileMetadata("Desktop profile", "https://desktop.example/profile", false)
        assertEquals("Desktop profile", updated.readSkipiProfileMetadata().name)
        assertEquals(false, updated.readSkipiProfileMetadata().updateLocked)
        assertTrue(updated.contains("enable-fake-dns = true"))
        assertTrue(updated.contains("[Rule]\nFINAL,PROXY"))
    }
}
