// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One installed application; [label] is loaded only when something actually needs it. */
internal class InstalledApplicationRecord(
    val packageName: String,
    val uid: Int,
    val isSystem: Boolean,
    val label: () -> String,
)

internal fun interface InstalledApplicationSource {
    fun installed(): List<InstalledApplicationRecord>
}

private class PackageManagerApplicationSource(
    private val packageManager: PackageManager,
) : InstalledApplicationSource {
    override fun installed(): List<InstalledApplicationRecord> =
        packageManager.getInstalledApplicationsCompat().map { applicationInfo ->
            InstalledApplicationRecord(
                packageName = applicationInfo.packageName,
                uid = applicationInfo.uid,
                isSystem = applicationInfo.isSystemApp(),
                label = { applicationInfo.loadLabel(packageManager).toString() },
            )
        }
}

internal class AndroidPackageRepository(
    private val source: InstalledApplicationSource,
) {
    constructor(context: Context) : this(
        PackageManagerApplicationSource(context.applicationContext.packageManager),
    )

    /** Only names are needed here, so no label is loaded. */
    suspend fun listPackages(type: String): List<String> = withContext(Dispatchers.IO) {
        source.installed()
            .filter { record -> record.matches(type) }
            .map { record -> record.packageName }
            .distinct()
            .sorted()
    }

    suspend fun getPackagesInfo(packages: List<String>): List<InstalledPackageInfo> = withContext(Dispatchers.IO) {
        val packageSet = packages.toSet()
        if (packageSet.isEmpty()) {
            return@withContext emptyList()
        }
        source.installed()
            .distinctBy { record -> record.identity() }
            .filter { record -> record.packageName in packageSet }
            .map { record -> record.toInfo() }
            .sortedByAppIdentity()
    }

    suspend fun getCurrentUserPackagesInfo(type: String): List<InstalledPackageInfo> = withContext(Dispatchers.IO) {
        source.installed()
            .filter { record -> record.matches(type) }
            .distinctBy { record -> record.identity() }
            .map { record -> record.toInfo() }
            .sortedByAppIdentity()
    }

    private fun InstalledApplicationRecord.matches(type: String): Boolean = when (type) {
        ANDROID_PACKAGE_SCOPE_SYSTEM -> isSystem
        ANDROID_PACKAGE_SCOPE_USER -> !isSystem
        else -> true
    }

    private fun InstalledApplicationRecord.identity(): String = "$uid:$packageName"

    private fun InstalledApplicationRecord.toInfo() = InstalledPackageInfo(
        packageName = packageName,
        appLabel = label(),
        isSystem = isSystem,
        uid = uid,
    )
}

internal fun Context.currentAndroidUser(): AndroidUser {
    val userId = Process.myUid().toAndroidUserId()
    return AndroidUser(
        id = userId,
        name = "User $userId",
    )
}

private fun ApplicationInfo.isSystemApp(): Boolean {
    return flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
}

private fun List<InstalledPackageInfo>.sortedByAppIdentity(): List<InstalledPackageInfo> {
    return sortedWith(
        compareBy<InstalledPackageInfo> { info -> info.uid }
            .thenBy { info -> info.appLabel },
    )
}
