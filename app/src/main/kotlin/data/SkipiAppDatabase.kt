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
    version = 6,
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
