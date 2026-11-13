// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidPackageRepositoryLabelLoadTest {
    private val labelLoads = mutableListOf<String>()

    private fun record(pkg: String, uid: Int, label: String, system: Boolean = false) =
        InstalledApplicationRecord(
            packageName = pkg,
            uid = uid,
            isSystem = system,
            label = {
                labelLoads += pkg
                label
            },
        )

    private fun repository() = AndroidPackageRepository(
        InstalledApplicationSource {
            listOf(
                record("a.app", 20002, "Bravo"),
                record("b.app", 20001, "Alpha"),
                record("c.sys", 10001, "Sys", system = true),
                record("b.app", 20001, "Alpha"),
            )
        },
    )

    @Test
    fun listPackages_returnsSortedDistinctNamesWithoutLoadingLabels() = runBlocking {
        val names = repository().listPackages(ANDROID_PACKAGE_SCOPE_ALL)
        assertEquals(listOf("a.app", "b.app", "c.sys"), names)
        assertEquals(emptyList<String>(), labelLoads)
    }

    @Test
    fun getPackagesInfo_loadsEachRequestedLabelOnce_andSkipsOthers() = runBlocking {
        val info = repository().getPackagesInfo(listOf("b.app", "c.sys"))
        assertEquals(listOf("c.sys", "b.app"), info.map { it.packageName })
        assertEquals(listOf("Sys", "Alpha"), info.map { it.appLabel })
        assertEquals(listOf(true, false), info.map { it.isSystem })
        assertEquals(listOf("b.app", "c.sys").sorted(), labelLoads.sorted())
    }

    @Test
    fun getCurrentUserPackagesInfo_keepsScopeSortAndDistinct() = runBlocking {
        val user = repository().getCurrentUserPackagesInfo(ANDROID_PACKAGE_SCOPE_USER)
        assertEquals(listOf("b.app", "a.app"), user.map { it.packageName })
    }
}
