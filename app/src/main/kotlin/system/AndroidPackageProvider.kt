// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system

import android.content.Context

class AndroidPackageProvider(
    context: Context,
) {
    private val packageRepository = AndroidPackageRepository(context.applicationContext)

    suspend fun listPackages(type: String): List<String> {
        return packageRepository.listPackages(type)
    }

    suspend fun getPackagesInfo(packages: List<String>): List<InstalledPackageInfo> {
        return packageRepository.getPackagesInfo(packages)
    }

    suspend fun getCurrentUserPackagesInfo(type: String): List<InstalledPackageInfo> {
        return packageRepository.getCurrentUserPackagesInfo(type)
    }
}
