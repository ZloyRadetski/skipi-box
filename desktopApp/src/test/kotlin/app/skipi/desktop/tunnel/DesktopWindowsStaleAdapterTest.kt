// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A killed hev leaves its Wintun adapter behind and Wintun then names the next one "skipi0 1", so the
 * leftovers are removed before a start. Finding out whether there are any must be cheap: the removal is a
 * PowerShell start (most of a second) and used to run on every connect, holding Core's start back.
 */
class DesktopWindowsStaleAdapterTest {
    // `netsh interface show interface`, whatever language: the interface name is the last column.
    private val russian = """

Состояние адм.  Состояние     Тип              Имя интерфейса
---------------------------------------------------------------------
Разрешен       Подключен      Выделенный       Radmin VPN
Разрешен       Подключен      Выделенный       Ethernet 2
Разрешен       Отключен       Выделенный       Подключение по локальной сети
"""

    private fun withInterface(name: String) = russian + "Разрешен       Отключен      Выделенный       $name\n"

    @Test
    fun anAdapterNamedLikeTheTunnelOrNumberedAfterItIsStale() {
        assertTrue(windowsStaleAdapterPresent(withInterface("skipi0"), "skipi0"))
        assertTrue(windowsStaleAdapterPresent(withInterface("skipi0 1"), "skipi0"))
        assertTrue(windowsStaleAdapterPresent(withInterface("skipi0 12"), "skipi0"))
    }

    @Test
    fun otherAdaptersAreNotStale() {
        assertFalse(windowsStaleAdapterPresent(russian, "skipi0"))
        assertFalse(windowsStaleAdapterPresent(withInterface("skipi01"), "skipi0"))
        assertFalse(windowsStaleAdapterPresent(withInterface("my skipi0"), "skipi0"))
        assertFalse(windowsStaleAdapterPresent(withInterface("skipi0 backup"), "skipi0"))
    }

    @Test
    fun withNothingStaleThePowerShellCleanupIsNotStarted() {
        var removed = 0

        removeStaleWintunAdapters("skipi0", listInterfaces = { Result.success(russian) }, remove = { removed += 1 })

        assertEquals(0, removed)
    }

    @Test
    fun aStaleAdapterIsRemoved() {
        var removed = 0

        removeStaleWintunAdapters("skipi0", listInterfaces = { Result.success(withInterface("skipi0 1")) }, remove = { removed += 1 })

        assertEquals(1, removed)
    }

    @Test
    fun whenTheListCannotBeReadTheCleanupRunsAnyway() {
        var removed = 0

        removeStaleWintunAdapters("skipi0", listInterfaces = { Result.failure(IllegalStateException("netsh failed")) }, remove = { removed += 1 })

        assertEquals(1, removed)
    }
}
