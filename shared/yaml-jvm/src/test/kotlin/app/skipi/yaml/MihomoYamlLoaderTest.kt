// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MihomoYamlLoaderTest {
    @Test
    fun rejectsUnregisteredCustomAndJavaTags() {
        assertFails {
            loadMihomoYamlDocument("value: !custom-tag payload")
        }
        assertFails {
            loadMihomoYamlDocument("value: !!java/object:java.lang.ProcessBuilder []")
        }
    }

    @Test
    fun preservesTheDefaultCollectionAliasLimit() {
        assertNotNull(loadMihomoYamlDocument(collectionAliases(50)))
        assertFails {
            loadMihomoYamlDocument(collectionAliases(51))
        }
    }

    @Test
    fun parsesScalarAliasesAndBlockScalarsIntoAPlainObjectTree() {
        val root = assertIs<Map<*, *>>(loadMihomoYamlDocument(anchoredYamlFixture))
        val proxies = assertIs<List<*>>(root["proxies"])
        val socks = assertIs<Map<*, *>>(proxies[0])
        val http = assertIs<Map<*, *>>(proxies[1])

        assertEquals("proxy.example.test", socks["server"])
        assertEquals(socks["server"], http["server"])
        assertEquals("1080", socks["port"])
        assertEquals(socks["port"], http["port"])
        assertEquals("shared-user", socks["username"])
        assertEquals(socks["username"], http["username"])
        assertEquals("first secret line\nsecond secret line\n", socks["password"])
        assertEquals(socks["password"], http["password"])
    }

    @Test
    fun stripsLeadingBomAndReturnsNullForAnEmptyDocument() {
        val root = assertIs<Map<*, *>>(loadMihomoYamlDocument("\uFEFFproxies: []"))
        assertEquals(emptyList<Any?>(), root["proxies"])
        assertNull(loadMihomoYamlDocument("\uFEFF  \n"))
    }

    @Test
    fun propagatesMalformedYamlAsAParseFailure() {
        assertFails {
            loadMihomoYamlDocument("\uFEFFproxies: [")
        }
    }
}

private fun collectionAliases(count: Int): String = buildString {
    appendLine("tree: &tree [value]")
    repeat(count) { index -> appendLine("alias$index: *tree") }
}

private val anchoredYamlFixture = """
    proxies:
      - name: Socks shared scalar
        type: socks
        server: &sharedServer proxy.example.test
        port: &sharedPort "1080"
        username: &sharedUser shared-user
        password: &sharedPassword |
          first secret line
          second secret line
      - name: HTTP alias node
        type: http
        server: *sharedServer
        port: *sharedPort
        username: *sharedUser
        password: *sharedPassword
""".trimIndent()
