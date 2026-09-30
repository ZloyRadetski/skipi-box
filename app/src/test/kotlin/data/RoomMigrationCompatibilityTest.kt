// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomMigrationCompatibilityTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SkipiAppDatabase::class.java,
    )

    private val migrations = arrayOf(
        Migration1To2,
        Migration2To3,
        Migration3To4,
        Migration4To5,
        Migration5To6,
        Migration6To7,
    )

    @Test
    fun version1DatabaseOpensAtVersion7AndKeepsHomeData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-v1-to-v7.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacyDb ->
            val schema = javaClass.getResourceAsStream("/data/database/room-v1.sql")!!
                .bufferedReader()
                .use { it.readText() }
            schema.split(';').map(String::trim).filter(String::isNotEmpty).forEach(legacyDb::execSQL)
            legacyDb.execSQL(
                """INSERT INTO subscription_groups
                    (id, position, name, url, userAgent, updateInterval, updateViaProxy, enabled, builtIn, lastUpdatedAtMillis)
                    VALUES (42, 0, 'Work', 'https://example.com/sub', 'Old client', '24', 0, 1, 0, 123)""".trimIndent(),
            )
            legacyDb.execSQL(
                """INSERT INTO proxy_servers (id, position, groupId, serverJson)
                    VALUES (52, 0, 42, '{"protocol":"http","payload":{"server":"proxy.example.com"}}')""".trimIndent(),
            )
            insertStockAndCustomRules { sql -> legacyDb.execSQL(sql) }
            legacyDb.execSQL(
                "INSERT INTO proxy_app_list_selected_apps (packageKey, position) VALUES ('com.example.browser', 0)",
            )
            legacyDb.version = 1
        }

        val database = Room.databaseBuilder(context, SkipiAppDatabase::class.java, name)
            .addMigrations(*migrations)
            .build()
        try {
            val migrated = database.openHelper.writableDatabase
            assertEquals(7, migrated.version)
            assertPreservedHomeData(migrated)

            migrated.query(
                "SELECT hwid, ageSecretKey, profileTitle, announce, trafficTotalBytes, autoOverrideRules, " +
                    "notifyOnExpiry, customExpiryReminders, supportUrl, supportEmail, profileWebPageUrl, announceUrl " +
                    "FROM subscription_groups WHERE id = 42",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals("", cursor.getString(3))
                assertEquals(-1L, cursor.getLong(4))
                assertEquals(1, cursor.getInt(5))
                assertEquals(1, cursor.getInt(6))
                assertEquals("", cursor.getString(7))
                assertEquals("", cursor.getString(8))
                assertEquals("", cursor.getString(9))
                assertEquals("", cursor.getString(10))
                assertEquals("", cursor.getString(11))
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun exportedVersions4Through6MigrateTo7AndKeepHomeData() {
        for (fromVersion in 4..6) {
            val name = "migration-v${fromVersion}-to-v7.db"
            val legacy = migrationHelper.createDatabase(name, fromVersion)
            try {
                insertHomeData(legacy)
                insertStockAndCustomRules { sql -> legacy.execSQL(sql) }
            } finally {
                legacy.close()
            }

            val suffix = migrations.filter { migration -> migration.startVersion >= fromVersion }.toTypedArray()
            val migrated = migrationHelper.runMigrationsAndValidate(name, 7, true, *suffix)
            try {
                assertEquals("Unexpected database version after migration from v$fromVersion", 7, migrated.version)
                assertPreservedHomeData(migrated)
            } finally {
                migrated.close()
            }
        }
    }

    private fun insertHomeData(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO subscription_groups
                (id, position, name, url, userAgent, updateInterval, updateViaProxy, enabled, builtIn, lastUpdatedAtMillis)
                VALUES (42, 0, 'Work', 'https://example.com/sub', 'Old client', '24', 0, 1, 0, 123)""".trimIndent(),
        )
        db.execSQL(
            """INSERT INTO proxy_servers (id, position, groupId, serverJson)
                VALUES (52, 0, 42, '{"protocol":"http","payload":{"server":"proxy.example.com"}}')""".trimIndent(),
        )
        db.execSQL(
            "INSERT INTO proxy_app_list_selected_apps (packageKey, position) VALUES ('com.example.browser', 0)",
        )
    }

    private fun insertStockAndCustomRules(execSql: (String) -> Unit) {
        execSql(
            """INSERT INTO routing_rules
                (id, position, remarks, outboundTag, domainJson, ipJson, processJson, port, protocol, network, enabled)
                VALUES (2, 1, 'block_udp_443', 'block', '[]', '[]', '[]', '443', '', 'udp', 1)""".trimIndent(),
        )
        // Same meaning, different id: the migration is meant to remove only the shipped stock row.
        execSql(
            """INSERT INTO routing_rules
                (id, position, remarks, outboundTag, domainJson, ipJson, processJson, port, protocol, network, enabled)
                VALUES (3, 2, 'my_udp_443_rule', 'block', '[]', '[]', '[]', '443', '', 'udp', 1)""".trimIndent(),
        )
    }

    private fun assertPreservedHomeData(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query("SELECT id, name, userAgent FROM subscription_groups WHERE id = 42").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42, cursor.getInt(0))
            assertEquals("Work", cursor.getString(1))
            assertEquals("Old client", cursor.getString(2))
            assertFalse(cursor.moveToNext())
        }
        db.query("SELECT id, groupId, serverJson FROM proxy_servers WHERE id = 52").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(52, cursor.getInt(0))
            assertEquals(42, cursor.getInt(1))
            assertTrue(cursor.getString(2).contains("proxy.example.com"))
            assertFalse(cursor.moveToNext())
        }
        db.query("SELECT packageKey FROM proxy_app_list_selected_apps").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("com.example.browser", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
        db.query("SELECT id, remarks FROM routing_rules ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
            assertEquals("my_udp_443_rule", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
    }
}
