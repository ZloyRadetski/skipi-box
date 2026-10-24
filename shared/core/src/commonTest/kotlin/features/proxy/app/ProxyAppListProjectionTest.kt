// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.ProxyAppListPreparedData
import features.proxy.app.model.ProxyAppListUserSpace
import features.proxy.app.model.prepareProxyAppListProjection
import features.proxy.app.model.projectedUserSpaceTabs
import features.proxy.app.model.sortedForProxyAppListRefresh
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyAppListProjectionTest {

    @Test
    fun trimmedCaseInsensitiveSearchMatchesLabelPackageAndBlankLabelFallback() {
        val packages = listOf(
            app("Com.Example.Calendar", "Travel Calendar", userId = 0, uid = 10001),
            app("org.example.weather", "Weather", userId = 0, uid = 10002),
            app("org.example.blanklabel", "   ", userId = 0, uid = 10003),
        )

        val labelMatch = prepare(packages, searchValue = "  tRaVeL  ")
        val packageMatch = prepare(packages, searchValue = " EXAMPLE.WEATHER ")
        val fallbackNameMatch = prepare(packages, searchValue = " blanklabel ")

        assertEquals(
            listOf("0:Com.Example.Calendar"),
            labelMatch.visibleItemsByUser.getValue(0).map { item -> item.key },
        )
        assertEquals(
            listOf("0:org.example.weather"),
            packageMatch.visibleItemsByUser.getValue(0).map { item -> item.key },
        )
        assertEquals(
            listOf("0:org.example.blanklabel"),
            fallbackNameMatch.visibleItemsByUser.getValue(0).map { item -> item.key },
        )
        assertEquals(
            listOf(
                "0:Com.Example.Calendar",
                "0:org.example.weather",
                "0:org.example.blanklabel",
            ),
            labelMatch.allItemsByUser.getValue(0).map { item -> item.key },
        )
    }

    @Test
    fun userSpacesSortAndSynthesizeFallbackTabsWithoutCountingUnknownKeys() {
        val prepared = prepare(
            appPackages = listOf(
                app("com.work.app", "Work App", userId = 10, uid = 1010010),
                app("com.other.app", "Other App", userId = 3, uid = 310011),
                app("com.owner.app", "Owner App", userId = 0, uid = 10012),
            ),
            userSpaces = listOf(
                ProxyAppListUserSpace(id = 10, name = "Work"),
                ProxyAppListUserSpace(id = 0, name = ""),
            ),
        )

        val tabs = prepared.projectedUserSpaceTabs(
            setOf("0:com.owner.app", "3:com.other.app", "99:unknown.app"),
        )

        assertEquals(
            listOf(
                ProxyAppListUserSpace(id = 0, name = ""),
                ProxyAppListUserSpace(id = 3, name = "User 3"),
                ProxyAppListUserSpace(id = 10, name = "Work"),
            ),
            prepared.displayedUserSpaces,
        )
        assertEquals(listOf("User 0", "User 3", "Work"), tabs.map { tab -> tab.label })
        assertEquals(listOf(1, 1, 0), tabs.map { tab -> tab.checkedCount })
    }

    @Test
    fun sharedUidKeysAndCountsStayWithinEachUserSpace() {
        val prepared = prepare(
            appPackages = listOf(
                app("com.example.zeta", "Zeta", userId = 0, uid = 10042),
                app("com.example.alpha", "Alpha", userId = 0, uid = 10042),
                app("com.example.alpha", "Alpha work", userId = 1, uid = 110042),
            ),
            userSpaces = listOf(
                ProxyAppListUserSpace(id = 0, name = "Owner"),
                ProxyAppListUserSpace(id = 1, name = "Work"),
            ),
        )
        val ownerItems = prepared.allItemsByUser.getValue(0)
        val workItems = prepared.allItemsByUser.getValue(1)
        val tabs = prepared.projectedUserSpaceTabs(
            setOf("0:com.example.alpha", "9:unknown.app"),
        )

        assertEquals(listOf(true, true), ownerItems.map { item -> item.sharedUid })
        assertEquals(
            listOf(
                listOf("0:com.example.zeta", "0:com.example.alpha"),
                listOf("0:com.example.zeta", "0:com.example.alpha"),
            ),
            ownerItems.map { item -> item.selectionKeys },
        )
        assertEquals(listOf(false), workItems.map { item -> item.sharedUid })
        assertEquals(listOf("1:com.example.alpha"), workItems.single().selectionKeys)
        assertEquals(listOf(2, 0), tabs.map { tab -> tab.checkedCount })
    }

    @Test
    fun fixedUserRemapsUidToAppIdAndKeepsFirstDuplicatePackage() {
        val prepared = prepare(
            appPackages = listOf(
                app("com.example.same", "First", userId = 1, uid = 112345),
                app("com.example.same", "Duplicate", userId = 2, uid = 212345),
                app("com.example.other", "Other", userId = 2, uid = 299999),
                app("com.example.current", "Already fixed", userId = 9, uid = 900007),
            ),
            userSpaces = listOf(ProxyAppListUserSpace(id = 9, name = "User 9")),
            fixedUserId = 9,
        )
        val items = prepared.allItemsByUser.getValue(9)

        assertEquals(
            listOf("com.example.same", "com.example.other", "com.example.current"),
            items.map { item -> item.app.packageName },
        )
        assertEquals(listOf("First", "Other", "Already fixed"), items.map { item -> item.app.appLabel })
        assertEquals(listOf(12345, 99999, 900007), items.map { item -> item.app.uid })
        assertEquals(
            listOf(
                "9:com.example.same",
                "9:com.example.other",
                "9:com.example.current",
            ),
            items.map { item -> item.key },
        )
    }

    @Test
    fun refreshSortPrioritizesSelectedGroupsThenGroupNameAndKey() {
        val packages = listOf(
            app("com.aardvark", "Aardvark", userId = 0, uid = 801),
            app("com.echo", "Echo", userId = 0, uid = 2001),
            app("com.shared.fable", "Fable", userId = 0, uid = 100),
            app("com.shared.echo", "Echo", userId = 0, uid = 100),
        )

        val result = packages.sortedForProxyAppListRefresh(
            setOf("0:com.echo", "0:com.shared.fable"),
        )

        assertEquals(
            listOf("com.echo", "com.shared.echo", "com.shared.fable", "com.aardvark"),
            result.map { entry -> entry.packageName },
        )
    }

    private fun prepare(
        appPackages: List<AppPackageEntry>,
        searchValue: String = "",
        userSpaces: List<ProxyAppListUserSpace> = listOf(ProxyAppListUserSpace(id = 0, name = "Owner")),
        fixedUserId: Int? = null,
    ): ProxyAppListPreparedData = prepareProxyAppListProjection(
        userSpaces = userSpaces,
        appPackages = appPackages,
        searchValue = searchValue,
        fixedUserId = fixedUserId,
    )

    private fun app(
        packageName: String,
        appLabel: String,
        userId: Int,
        uid: Int,
    ) = AppPackageEntry(
        packageName = packageName,
        appLabel = appLabel,
        userId = userId,
        uid = uid,
    )
}
