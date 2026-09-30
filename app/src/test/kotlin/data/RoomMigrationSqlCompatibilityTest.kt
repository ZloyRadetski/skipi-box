// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks down the SQL run for historical database versions without Android's SQLite runtime.
 * This proves migration dispatch and statement compatibility, but not Room's post-migration
 * schema validation or SQLite's execution semantics.
 */
class RoomMigrationSqlCompatibilityTest {
    private val migrations = listOf(
        Migration1To2,
        Migration2To3,
        Migration3To4,
        Migration4To5,
        Migration5To6,
        Migration6To7,
    )

    private val expectedSql = listOf(
        "ALTER TABLE subscription_groups ADD COLUMN hwid TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE subscription_groups ADD COLUMN ageSecretKey TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE subscription_groups ADD COLUMN profileTitle TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE subscription_groups ADD COLUMN announce TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE subscription_groups ADD COLUMN trafficUploadBytes INTEGER NOT NULL DEFAULT -1",
        "ALTER TABLE subscription_groups ADD COLUMN trafficDownloadBytes INTEGER NOT NULL DEFAULT -1",
        "ALTER TABLE subscription_groups ADD COLUMN trafficTotalBytes INTEGER NOT NULL DEFAULT -1",
        "ALTER TABLE subscription_groups ADD COLUMN trafficExpireAtSeconds INTEGER NOT NULL DEFAULT -1",
        "ALTER TABLE subscription_groups ADD COLUMN autoOverrideRules INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE subscription_groups ADD COLUMN notifyOnExpiry INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE subscription_groups ADD COLUMN customExpiryReminders TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE subscription_groups ADD COLUMN supportUrl TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE subscription_groups ADD COLUMN supportEmail TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE subscription_groups ADD COLUMN profileWebPageUrl TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE subscription_groups ADD COLUMN announceUrl TEXT NOT NULL DEFAULT ''",
        """
            DELETE FROM routing_rules
            WHERE id = 2
              AND position = 1
              AND remarks = 'block_udp_443'
              AND outboundTag = 'block'
              AND domainJson = '[]'
              AND ipJson = '[]'
              AND processJson = '[]'
              AND port = '443'
              AND protocol = ''
              AND network = 'udp'
              AND enabled = 1
        """.trimIndent(),
    )

    @Test
    fun version1AndVersions4Through6UseTheExpectedMigrationSuffix() {
        val sqlByMigration = migrations.map { migration -> captureSql(migration) }
        assertEquals(expectedSql.map(::normalizeSql), sqlByMigration.flatten().map(::normalizeSql))

        // Room chooses a continuous migration suffix when opening v4, v5, or v6 databases.
        for (startVersion in 4..6) {
            val suffix = migrations.filter { migration -> migration.startVersion >= startVersion }
            val expectedSuffix = sqlByMigration
                .drop(startVersion - 1)
                .flatten()
            assertEquals(
                "Unexpected SQL migration path from schema v$startVersion",
                expectedSuffix.map(::normalizeSql),
                suffix.flatMap(::captureSql).map(::normalizeSql),
            )
        }
    }

    private fun captureSql(migration: Migration): List<String> {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL") {
                statements += args!![0] as String
                null
            } else {
                throw UnsupportedOperationException("Unexpected SupportSQLiteDatabase call: ${method.name}")
            }
        } as SupportSQLiteDatabase

        migration.migrate(database)
        return statements
    }

    private fun normalizeSql(sql: String): String = sql.trim().replace(Regex("\\s+"), " ")
}
