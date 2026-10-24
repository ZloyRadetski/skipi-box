// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.loadProxyAppListPackages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import system.AndroidPackageProvider

/** Synthetic provider-to-projection source oracle; no host package catalog is queried. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ProxyAppListPackageLoadingAndroidOracleTest {

    @Test
    fun currentUserOnlyUserScopeExcludesSelfAndMapsSyntheticUidAndLabelFields() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        installPackage(context, OwnerPackage, uid = 10_042, label = "Owner Zebra")
        installPackage(context, WorkPackage, uid = 210_042, label = "Work Alpha")
        installPackage(context, SystemPackage, uid = 1_100_007, label = "System App", system = true)
        installPackage(context, SelfPackage, uid = 10_043, label = "Self App")

        val actual = AndroidPackageProvider(context).loadProxyAppListPackages(
            showSystemApps = false,
            currentUserOnly = true,
            excludedPackageName = SelfPackage,
        ).syntheticPackages()

        assertEquals(listOf(OwnerPackage, WorkPackage), actual.map(AppPackageEntry::packageName))
        assertEquals(listOf("Owner Zebra", "Work Alpha"), actual.map(AppPackageEntry::appLabel))
        assertEquals(listOf(0, 2), actual.map(AppPackageEntry::userId))
        assertEquals(listOf(10_042, 210_042), actual.map(AppPackageEntry::uid))
        assertTrue(actual.none(AppPackageEntry::system))
        assertFalse(actual.any { app -> app.packageName == SelfPackage })
    }

    @Test
    fun broadUserAndAllScopesUsePackageProviderAndExcludeSelf() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        installPackage(context, OwnerPackage, uid = 10_042, label = "Owner Zebra")
        installPackage(context, WorkPackage, uid = 210_042, label = "Work Alpha")
        installPackage(context, SystemPackage, uid = 1_100_007, label = "System App", system = true)
        installPackage(context, SelfPackage, uid = 10_043, label = "Self App")
        val provider = AndroidPackageProvider(context)

        val userScope = provider.loadProxyAppListPackages(
            showSystemApps = false,
            currentUserOnly = false,
            excludedPackageName = SelfPackage,
        ).syntheticPackages()
        val allScope = provider.loadProxyAppListPackages(
            showSystemApps = true,
            currentUserOnly = false,
            excludedPackageName = SelfPackage,
        ).syntheticPackages()

        assertEquals(listOf(OwnerPackage, WorkPackage), userScope.map(AppPackageEntry::packageName))
        assertEquals(listOf(OwnerPackage, WorkPackage, SystemPackage), allScope.map(AppPackageEntry::packageName))
        assertEquals(listOf("Owner Zebra", "Work Alpha", "System App"), allScope.map(AppPackageEntry::appLabel))
        assertEquals(listOf(0, 2, 11), allScope.map(AppPackageEntry::userId))
        assertEquals(listOf(10_042, 210_042, 1_100_007), allScope.map(AppPackageEntry::uid))
        assertEquals(listOf(false, false, true), allScope.map(AppPackageEntry::system))
        assertTrue((userScope + allScope).none { app -> app.packageName == SelfPackage })
    }

    private fun installPackage(
        context: Context,
        packageName: String,
        uid: Int,
        label: String,
        system: Boolean = false,
    ) {
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.uid = uid
            this.flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
            nonLocalizedLabel = label
        }
        val packageInfo = PackageInfo().apply {
            this.packageName = packageName
            this.applicationInfo = applicationInfo
        }
        shadowOf(context.packageManager).installPackage(packageInfo)
    }

    private fun List<AppPackageEntry>.syntheticPackages(): List<AppPackageEntry> =
        filter { entry -> entry.packageName.startsWith(SyntheticPackagePrefix) }

    private companion object {
        const val SyntheticPackagePrefix = "com.skipi.r03.provider."
        const val OwnerPackage = "com.skipi.r03.provider.owner"
        const val WorkPackage = "com.skipi.r03.provider.work"
        const val SystemPackage = "com.skipi.r03.provider.system"
        const val SelfPackage = "com.skipi.r03.provider.self"
    }
}
