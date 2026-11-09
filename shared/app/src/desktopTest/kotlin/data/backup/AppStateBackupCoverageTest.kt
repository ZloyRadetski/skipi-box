// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import features.backup.AppBackupSettings
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A setting added to [AppState] but forgotten in the backup is lost the day somebody restores. Every field of
 * the state must either be in the backup's settings or be named here, with the reason it is not.
 */
class AppStateBackupCoverageTest {
    /** Live runtime state: nothing a person would want back after a restore. */
    private val runtimeOnly = setOf(
        "proxyRunning",
        "availableAppUpdate",
        "appUpdateApkFilePath",
        "appUpdateDownloadError",
        "appUpdateDownloadIsAutomatic",
        "appUpdateDownloadStatus",
        "appUpdateDownloadedBytes",
        "appUpdateTotalBytes",
        "dismissedUpdateVersion",
        "unappliedRoutingRules",
    )

    /** Rebuilt from the restored records, so a stored copy would only be able to disagree with them. */
    private val derivedOnRestore = setOf(
        "nextCustomResourceFileId",
        "nextProxyServerId",
        "nextRouteRuleId",
        "nextSubscriptionGroupId",
        "nextTrafficConfigId",
        "shadowrocketPolicyGroups",
    )

    /** Saved in their own sections of the backup, not among the settings. */
    private val ownSection = setOf(
        "proxyServers",
        "subscriptionGroups",
        "routeRules",
        "trafficConfigs",
        "activeTrafficConfigId",
        "proxyAppListSelectedApps",
    )

    /**
     * Settings tied to the device on purpose: a restore on another phone or computer must not switch the app on
     * at boot or change how it runs there (a restore always starts in VPN mode).
     */
    private val deviceBound = setOf(
        "autoConnectOnAppOpen",
        "autoConnectOnBoot",
        "runMode",
    )

    /** Backup settings that are not plain state fields: the state derives them (the category icon colours). */
    private val backupOnly = setOf(
        "customCategoryAboutColor",
        "customCategoryAppearanceColor",
        "customCategoryBackupColor",
        "customCategoryIntegrationColor",
        "customCategoryLogsColor",
        "customCategoryProxyColor",
        "customCategorySubscriptionsColor",
        "customCategoryVpnColor",
    )

    private val stateFields = AppState::class.java.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) }
        .map { it.name }
        .toSet()

    private val backupFields = AppBackupSettings.serializer().descriptor
        .let { descriptor -> (0 until descriptor.elementsCount).map(descriptor::getElementName) }
        .toSet()

    @Test
    fun everyStateFieldIsBackedUpOrNamedWithItsReason() {
        val declared = runtimeOnly + derivedOnRestore + ownSection + deviceBound

        assertEquals(
            emptyList(),
            (stateFields - backupFields - declared).sorted(),
            "AppState fields that the backup neither stores nor explains: add them to AppBackupSettings " +
                "(an optional field keeps old backups readable) or to the right list in this test",
        )
    }

    @Test
    fun theListsHoldNoFieldThatNoLongerExistsOrIsAlreadyBackedUp() {
        val declared = runtimeOnly + derivedOnRestore + ownSection + deviceBound

        assertEquals(emptyList(), (declared - stateFields).sorted(), "named here but gone from AppState")
        assertEquals(emptyList(), declared.intersect(backupFields).sorted(), "named here but stored by the backup")
    }

    @Test
    fun theBackupHoldsNoSettingTheStateDoesNotKnow() {
        assertEquals(backupOnly, backupFields - stateFields)
    }
}
