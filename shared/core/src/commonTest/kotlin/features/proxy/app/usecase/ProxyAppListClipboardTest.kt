// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app.usecase

import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import features.clipboard.ClipboardImportException
import features.clipboard.ClipboardImportFailure
import features.clipboard.ClipboardImportMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProxyAppListClipboardTest {

    @Test
    fun encodesModeAndSelectedIdsInSourceOrderAfterTrimAndDeduplication() {
        val selectedApps = listOf(
            " 10:com.example.first ",
            "0:com.example.second",
            "10:com.example.first",
            " ",
        )

        assertEquals(
            "mode=blacklist",
            encodeProxyAppListForClipboard(emptyList(), ProxyAppListModeBlacklist),
        )
        assertEquals(
            "mode=whitelist",
            encodeProxyAppListForClipboard(emptyList(), ProxyAppListModeWhitelist),
        )
        assertEquals(
            "mode=global\n10:com.example.first\n0:com.example.second",
            encodeProxyAppListForClipboard(selectedApps, ProxyAppListModeGlobal),
        )
    }

    @Test
    fun decodesEachNamedModeAndKeepsExplicitUserIdsAndPackageOrder() {
        val expectedModes = listOf(
            "blacklist" to ProxyAppListModeBlacklist,
            "whitelist" to ProxyAppListModeWhitelist,
            "global" to ProxyAppListModeGlobal,
        )

        expectedModes.forEach { (modeName, expectedMode) ->
            val imported = decodeProxyAppListFromClipboard(
                text = "\uFEFFmode=$modeName\n10:com.example.first\n0:com.example.second\n10:com.example.first",
                currentUserId = 4,
                selfPackageName = "com.skipi",
            )

            assertEquals(expectedMode, imported.mode)
            assertEquals(
                listOf("10:com.example.first", "0:com.example.second"),
                imported.selectedApps,
            )
        }
    }

    @Test
    fun prefixesBarePackagesWithTheCallerProvidedUserIdIncludingZeroFallback() {
        val selectedUserImport = decodeProxyAppListFromClipboard(
            text = "com.example.selected-user",
            currentUserId = 10,
            selfPackageName = "com.skipi",
        )
        val noSelectedUserImport = decodeProxyAppListFromClipboard(
            text = "com.example.no-selected-user",
            currentUserId = 0,
            selfPackageName = "com.skipi",
        )

        assertEquals(null, selectedUserImport.mode)
        assertEquals(listOf("10:com.example.selected-user"), selectedUserImport.selectedApps)
        assertEquals(null, noSelectedUserImport.mode)
        assertEquals(listOf("0:com.example.no-selected-user"), noSelectedUserImport.selectedApps)
    }

    @Test
    fun acceptsLegacyBooleanModeLinesAsBlacklistAndWhitelist() {
        val blacklist = decodeProxyAppListFromClipboard(
            text = "true\n0:com.example.one",
            currentUserId = 0,
            selfPackageName = "com.skipi",
        )
        val whitelist = decodeProxyAppListFromClipboard(
            text = "false\n10:com.example.two",
            currentUserId = 0,
            selfPackageName = "com.skipi",
        )

        assertEquals(ProxyAppListModeBlacklist, blacklist.mode)
        assertEquals(listOf("0:com.example.one"), blacklist.selectedApps)
        assertEquals(ProxyAppListModeWhitelist, whitelist.mode)
        assertEquals(listOf("10:com.example.two"), whitelist.selectedApps)
    }

    @Test
    fun missingModeKeepsNullAndSelfFilteringLeavesOtherIdsInOrder() {
        val imported = decodeProxyAppListFromClipboard(
            text = """
                10:com.example.first
                10:com.skipi
                0:com.example.second
                10:com.example.first
            """.trimIndent(),
            currentUserId = 10,
            selfPackageName = "com.skipi",
        )

        assertEquals(null, imported.mode)
        assertEquals(
            listOf("10:com.example.first", "0:com.example.second"),
            imported.selectedApps,
        )
    }

    @Test
    fun replaceUsesImportedModeAndReplacesTheSelectedIdList() {
        val imported = decodeProxyAppListFromClipboard(
            text = "mode=whitelist\n0:com.example.one\n10:com.example.two",
            currentUserId = 0,
            selfPackageName = "com.skipi",
        )

        val result = applyProxyAppListClipboardImport(
            currentMode = ProxyAppListModeBlacklist,
            currentSelectedApps = listOf("0:com.example.old"),
            imported = imported,
            mode = ClipboardImportMode.Replace,
        )

        assertEquals(ProxyAppListModeWhitelist, result.mode)
        assertEquals(
            listOf("0:com.example.one", "10:com.example.two"),
            result.selectedApps,
        )
    }

    @Test
    fun mergeRetainsCurrentModeWhenImportHasNoModeAndDeduplicatesInOrder() {
        val imported = decodeProxyAppListFromClipboard(
            text = "0:com.example.existing\n10:com.example.new",
            currentUserId = 0,
            selfPackageName = "com.skipi",
        )

        val result = applyProxyAppListClipboardImport(
            currentMode = ProxyAppListModeGlobal,
            currentSelectedApps = listOf(" 0:com.example.existing ", "legacy.package"),
            imported = imported,
            mode = ClipboardImportMode.Merge,
        )

        assertEquals(ProxyAppListModeGlobal, result.mode)
        assertEquals(
            listOf("0:com.example.existing", "legacy.package", "10:com.example.new"),
            result.selectedApps,
        )
    }

    @Test
    fun reportsEmptyClipboardWithTheExistingFailure() {
        assertDecodeFailure(
            text = "\uFEFF  ",
            expectedFailure = ClipboardImportFailure.EmptyClipboard,
        )
    }

    @Test
    fun reportsExistingFailuresForUnsupportedModeMalformedIdsAndNoValidApps() {
        assertDecodeFailure(
            text = "mode=deny\n0:com.example.app",
            expectedFailure = ClipboardImportFailure.UnsupportedAppMode,
        )
        assertDecodeFailure(
            text = "not-a-user-id:com.example.app",
            expectedFailure = ClipboardImportFailure.InvalidAppUserId,
        )
        assertDecodeFailure(
            text = "0:com.example.bad app",
            expectedFailure = ClipboardImportFailure.InvalidAppEntry,
        )
        assertDecodeFailure(
            text = "10:com.skipi",
            expectedFailure = ClipboardImportFailure.NoValidApps,
        )
    }

    private fun assertDecodeFailure(
        text: String,
        expectedFailure: ClipboardImportFailure,
    ) {
        val error = assertFailsWith<ClipboardImportException> {
            decodeProxyAppListFromClipboard(
                text = text,
                currentUserId = 0,
                selfPackageName = "com.skipi",
            )
        }

        assertEquals(expectedFailure, error.failure)
    }
}
