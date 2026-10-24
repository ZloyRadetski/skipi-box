// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Synthetic PackageManager baseline for the Android app catalog adapter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidPackageRepositorySyntheticOracleTest {

    @Test
    fun packageCatalogScopesAndSortsOnlySyntheticInstalledApplications() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        installPackage(context, "com.skipi.r03.synthetic.system", uid = 10001, label = "System", system = true)
        installPackage(context, "com.skipi.r03.synthetic.zulu", uid = 20001, label = "Zulu")
        installPackage(context, "com.skipi.r03.synthetic.alpha", uid = 20001, label = "Alpha")
        installPackage(context, "com.skipi.r03.synthetic.earlier", uid = 20000, label = "Earlier")
        val repository = AndroidPackageRepository(context)

        val all = repository.getCurrentUserPackagesInfo(ANDROID_PACKAGE_SCOPE_ALL).syntheticPackages()
        val system = repository.getCurrentUserPackagesInfo(ANDROID_PACKAGE_SCOPE_SYSTEM).syntheticPackages()
        val user = repository.getCurrentUserPackagesInfo(ANDROID_PACKAGE_SCOPE_USER).syntheticPackages()

        assertEquals(
            listOf(
                "com.skipi.r03.synthetic.system",
                "com.skipi.r03.synthetic.earlier",
                "com.skipi.r03.synthetic.alpha",
                "com.skipi.r03.synthetic.zulu",
            ),
            all.map(InstalledPackageInfo::packageName),
        )
        assertEquals(listOf("com.skipi.r03.synthetic.system"), system.map(InstalledPackageInfo::packageName))
        assertEquals(
            listOf(
                "com.skipi.r03.synthetic.earlier",
                "com.skipi.r03.synthetic.alpha",
                "com.skipi.r03.synthetic.zulu",
            ),
            user.map(InstalledPackageInfo::packageName),
        )
        assertEquals(listOf(false, false, false), user.map(InstalledPackageInfo::isSystem))
    }

    @Test
    fun packageDetailsAreFilteredToRequestedInstalledEntriesAndSortedByUidThenLabel() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        installPackage(context, "com.skipi.r03.synthetic.zulu", uid = 20001, label = "Zulu")
        installPackage(context, "com.skipi.r03.synthetic.alpha", uid = 20001, label = "Alpha")
        installPackage(context, "com.skipi.r03.synthetic.earlier", uid = 20000, label = "Earlier")
        val repository = AndroidPackageRepository(context)

        val actual = repository.getPackagesInfo(
            listOf(
                "com.skipi.r03.synthetic.zulu",
                "com.skipi.r03.synthetic.missing",
                "com.skipi.r03.synthetic.alpha",
                "com.skipi.r03.synthetic.earlier",
            ),
        )

        assertEquals(
            listOf(
                "com.skipi.r03.synthetic.earlier",
                "com.skipi.r03.synthetic.alpha",
                "com.skipi.r03.synthetic.zulu",
            ),
            actual.map(InstalledPackageInfo::packageName),
        )
        assertEquals(listOf("Earlier", "Alpha", "Zulu"), actual.map(InstalledPackageInfo::appLabel))
        assertEquals(listOf(20_000, 20_001, 20_001), actual.map(InstalledPackageInfo::uid))
        assertEquals(listOf(false, false, false), actual.map(InstalledPackageInfo::isSystem))
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

    private fun List<InstalledPackageInfo>.syntheticPackages(): List<InstalledPackageInfo> =
        filter { entry -> entry.packageName.startsWith(SyntheticPackagePrefix) }

    private companion object {
        const val SyntheticPackagePrefix = "com.skipi.r03.synthetic."
    }
}
