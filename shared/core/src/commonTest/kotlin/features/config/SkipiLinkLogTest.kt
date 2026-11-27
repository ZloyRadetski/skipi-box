// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals

class SkipiLinkLogTest {
    @Test
    fun a_logged_link_never_carries_its_payload() {
        val cases = mapOf(
            "skipi://add/https%3A%2F%2Fexample.test%2Fsub%3Ftoken%3Dsecret" to "skipi://add/…",
            "skipi://import/vless://secret@example.test:443" to "skipi://import/…",
            "SKIPI://CONF/ONADD/c2VjcmV0" to "skipi://conf/onadd/…",
            "skipi://routing/add/c2VjcmV0" to "skipi://routing/add/…",
            "skipi://conf/secret" to "skipi://conf/…",
            "skipi://theme/c2VjcmV0" to "skipi://theme/…",
            "skipi://install-config?url=secret" to "skipi://…",
            "skipi://user:secret@connect" to "skipi://…",
            "skipi://connect?key=secret" to "skipi://connect/…",
            "vless://secret@example.test" to "skipi://…",
        )
        cases.forEach { (link, logged) -> assertEquals(logged, redactSkipiLinkForLog(link), link) }
    }

    @Test
    fun a_command_without_payload_is_logged_as_it_is() {
        assertEquals("skipi://toggle", redactSkipiLinkForLog("skipi://toggle"))
        assertEquals("skipi://open", redactSkipiLinkForLog("SKIPI://OPEN/"))
    }
}
