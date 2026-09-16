// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val SkipiDatabaseName = "skipi.db"

@Database(
    entities = [
        SubscriptionGroupEntity::class,
        ProxyServerEntity::class,
        RouteRuleEntity::class,
        ProxyAppListSelectedAppEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
internal abstract class SkipiAppDatabase : RoomDatabase() {
    abstract fun appStateDao(): AppStateDao
}

internal val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE subscription_groups ADD COLUMN hwid TEXT NOT NULL DEFAULT ''",
        )
        db.execSQL(
            "ALTER TABLE subscription_groups ADD COLUMN ageSecretKey TEXT NOT NULL DEFAULT ''",
        )
    }
}

internal val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN profileTitle TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN announce TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN trafficUploadBytes INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN trafficDownloadBytes INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN trafficTotalBytes INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN trafficExpireAtSeconds INTEGER NOT NULL DEFAULT -1")
    }
}

internal val Migration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN autoOverrideRules INTEGER NOT NULL DEFAULT 1")
    }
}

internal val Migration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN notifyOnExpiry INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN customExpiryReminders TEXT NOT NULL DEFAULT ''")
    }
}

internal val Migration5To6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN supportUrl TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN supportEmail TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN profileWebPageUrl TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE subscription_groups ADD COLUMN announceUrl TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Removes only the former stock QUIC-blocking rule. The narrow match leaves
 * independently created UDP:443 block rules untouched.
 */
internal val Migration6To7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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
    }
}
