// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals

class StringTemplateTest {

    @Test
    fun testFormatTemplateSingle() {
        val template = "Server {name} connected"
        val formatted = template.formatTemplate("name" to "US-Fast")
        assertEquals("Server US-Fast connected", formatted)
    }

    @Test
    fun testFormatTemplateMultiple() {
        val template = "Updated {groupCount} groups, {serverCount} servers"
        val formatted = template.formatTemplate("groupCount" to 3, "serverCount" to 42)
        assertEquals("Updated 3 groups, 42 servers", formatted)
    }
}
