// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyAppListSelectionOperationsTest {

    @Test
    fun checkingSharedUidAddsEveryKeyOnceAndKeepsConfiguredOrder() {
        val selectedApps = listOf(
            "ru.yota.android",
            "0:com.example.first",
            "com.example.legacy",
            "com.example.legacy",
        )

        val result = updateProxyAppListSelection(
            selectedApps = selectedApps,
            selectionKeys = listOf(
                "0:com.example.first",
                "0:com.example.second",
                "0:com.example.first",
            ),
            isChecked = true,
        )

        assertEquals(
            listOf(
                "ru.yota.android",
                "0:com.example.first",
                "com.example.legacy",
                "0:com.example.second",
            ),
            result,
        )
    }

    @Test
    fun uncheckingSharedUidRemovesEveryKeyAndPreservesOtherIdsInOrder() {
        val selectedApps = listOf(
            "ru.yota.android",
            "0:com.example.first",
            "com.example.legacy",
            "0:com.example.second",
            "ru.yota.android",
            "0:com.example.other",
        )

        val result = updateProxyAppListSelection(
            selectedApps = selectedApps,
            selectionKeys = listOf(
                "0:com.example.first",
                "0:com.example.second",
            ),
            isChecked = false,
        )

        assertEquals(
            listOf(
                "ru.yota.android",
                "com.example.legacy",
                "ru.yota.android",
                "0:com.example.other",
            ),
            result,
        )
    }
}
