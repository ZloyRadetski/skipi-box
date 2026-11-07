// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/**
 * The tray icon speaks StatusNotifierItem on the session bus, so it shows up in every panel that hosts
 * SNI (KDE, waybar, quickshell, GNOME with the AppIndicator extension), not only where an XEmbed tray exists.
 * These tests run it against a private bus with a fake watcher and read it back with busctl, as a panel would.
 */
class DesktopStatusNotifierTest {
    private val pixel = DesktopTrayPixmap(1, 1, intArrayOf(0x80FF8040.toInt()))

    private fun state(tooltip: String = "SKIPI — Disconnected") =
        DesktopStatusNotifierState(tooltip = tooltip, icons = listOf(pixel))

    private class Calls {
        val activations = CopyOnWriteArrayList<String>()
    }

    private fun notifier(address: String?, calls: Calls) = DesktopStatusNotifier(
        busAddress = address,
        onActivate = { calls.activations += "activate" },
        onContextMenu = { x, y -> calls.activations += "menu $x,$y" },
    )

    @Test
    fun theItemIsReadableByAPanelAndExportsNoMenuOfItsOwn() {
        val bus = PrivateSessionBus.startOrNull() ?: return
        bus.use {
            val watcher = FakeWatcher(bus.address).also { it.start() }
            val notifier = notifier(bus.address, Calls())
            try {
                notifier.start(state())
                assertTrue(eventually { watcher.registrations.isNotEmpty() }, "the item must register with the watcher")
                val service = watcher.registrations.single()
                assertTrue(eventually { notifier.registered.value })

                fun prop(name: String) = busctl(bus.address, "get-property", service, "/StatusNotifierItem", "org.kde.StatusNotifierItem", name)
                assertEquals("""s "skipi"""", prop("Id"))
                assertEquals("""s "SKIPI"""", prop("Title"))
                assertEquals("""s "ApplicationStatus"""", prop("Category"))
                assertEquals("""s "Active"""", prop("Status"))
                assertEquals("""o "/NO_DBUSMENU"""", prop("Menu"))
                assertEquals("b false", prop("ItemIsMenu"))
                assertEquals("a(iiay) 1 1 1 4 128 255 128 64", prop("IconPixmap"))
                assertEquals("""(sa(iiay)ss) "" 0 "SKIPI \342\200\224 Disconnected" """"", prop("ToolTip"))

            } finally {
                notifier.close()
                watcher.stop()
            }
        }
    }

    @Test
    fun aClickAndAContextMenuRequestReachTheApp() {
        val bus = PrivateSessionBus.startOrNull() ?: return
        bus.use {
            val watcher = FakeWatcher(bus.address).also { it.start() }
            val calls = Calls()
            val notifier = notifier(bus.address, calls)
            try {
                notifier.start(state())
                assertTrue(eventually { watcher.registrations.isNotEmpty() })
                val service = watcher.registrations.single()

                busctl(bus.address, "call", service, "/StatusNotifierItem", "org.kde.StatusNotifierItem", "Activate", "ii", "0", "0")
                busctl(bus.address, "call", service, "/StatusNotifierItem", "org.kde.StatusNotifierItem", "ContextMenu", "ii", "40", "50")

                assertTrue(eventually { calls.activations.size >= 2 }, calls.activations.toString())
                assertEquals(listOf("activate", "menu 40,50"), calls.activations.toList())
            } finally {
                notifier.close()
                watcher.stop()
            }
        }
    }

    @Test
    fun anUpdateChangesTooltipAndIconAndTellsThePanel() {
        val bus = PrivateSessionBus.startOrNull() ?: return
        bus.use {
            val watcher = FakeWatcher(bus.address).also { it.start() }
            val notifier = notifier(bus.address, Calls())
            val monitor = SignalMonitor(bus.address)
            try {
                notifier.start(state())
                assertTrue(eventually { watcher.registrations.isNotEmpty() })
                val service = watcher.registrations.single()

                val green = DesktopTrayPixmap(1, 1, intArrayOf(0xFF00FF00.toInt()))
                notifier.update(state(tooltip = "SKIPI — Connected").copy(icons = listOf(green)))

                assertEquals(
                    """(sa(iiay)ss) "" 0 "SKIPI \342\200\224 Connected" """"",
                    busctl(bus.address, "get-property", service, "/StatusNotifierItem", "org.kde.StatusNotifierItem", "ToolTip"),
                )
                assertEquals(
                    "a(iiay) 1 1 1 4 255 0 255 0",
                    busctl(bus.address, "get-property", service, "/StatusNotifierItem", "org.kde.StatusNotifierItem", "IconPixmap"),
                )
                assertTrue(monitor.sawAll("NewIcon", "NewToolTip"), monitor.lines.toString())
            } finally {
                monitor.close()
                notifier.close()
                watcher.stop()
            }
        }
    }

    @Test
    fun anUpdateThatChangesNothingSendsNoSignals() {
        val bus = PrivateSessionBus.startOrNull() ?: return
        bus.use {
            val watcher = FakeWatcher(bus.address).also { it.start() }
            val notifier = notifier(bus.address, Calls())
            val monitor = SignalMonitor(bus.address)
            try {
                notifier.start(state())
                assertTrue(eventually { watcher.registrations.isNotEmpty() })

                notifier.update(state())

                assertFalse(monitor.sawAny("NewIcon", "NewToolTip"), monitor.lines.toString())
            } finally {
                monitor.close()
                notifier.close()
                watcher.stop()
            }
        }
    }

    @Test
    fun theItemRegistersWhenTheWatcherAppearsLater() {
        val bus = PrivateSessionBus.startOrNull() ?: return
        bus.use {
            val notifier = notifier(bus.address, Calls())
            val watcher = FakeWatcher(bus.address)
            try {
                notifier.start(state())
                assertFalse(notifier.registered.value, "no watcher yet, so no tray")

                watcher.start()

                assertTrue(eventually { notifier.registered.value })
                assertEquals(1, watcher.registrations.size)
            } finally {
                notifier.close()
                watcher.stop()
            }
        }
    }

    @Test
    fun aNewWatcherGetsTheItemAgainAndTheOldOneLeavesNoTray() {
        val bus = PrivateSessionBus.startOrNull() ?: return
        bus.use {
            val first = FakeWatcher(bus.address).also { it.start() }
            val notifier = notifier(bus.address, Calls())
            val second = FakeWatcher(bus.address)
            try {
                notifier.start(state())
                assertTrue(eventually { notifier.registered.value })

                first.stop()
                assertTrue(eventually { !notifier.registered.value }, "the watcher is gone, so the tray is gone")

                second.start()
                assertTrue(eventually { notifier.registered.value })
                assertEquals(1, second.registrations.size)
                assertEquals(1, first.registrations.size)
            } finally {
                notifier.close()
                first.stop()
                second.stop()
            }
        }
    }

    @Test
    fun withoutAHostTheItemIsRegisteredButNotCountedAsShown() {
        val bus = PrivateSessionBus.startOrNull() ?: return
        bus.use {
            val watcher = FakeWatcher(bus.address, hostRegistered = false).also { it.start() }
            val notifier = notifier(bus.address, Calls())
            try {
                notifier.start(state())
                assertTrue(eventually { watcher.registrations.isNotEmpty() })
                assertFalse(notifier.registered.value, "a watcher with no panel behind it shows nothing")

                watcher.setHostRegistered(true)

                assertTrue(eventually { notifier.registered.value })
                assertEquals(1, watcher.registrations.size, "a host appearing must not register the item a second time")
            } finally {
                notifier.close()
                watcher.stop()
            }
        }
    }

    @Test
    fun anUnreachableBusLeavesNoTrayAndDoesNotThrow() {
        val notifier = notifier("unix:path=/nonexistent/skipi-test-bus", Calls())

        val started = System.nanoTime()
        notifier.start(state())
        val seconds = (System.nanoTime() - started) / 1e9

        assertFalse(notifier.registered.value)
        assertTrue(seconds < 2, "dbus-java alone waits over nine seconds for a missing bus; the window must not ($seconds s)")
        notifier.close()
    }
}

/**
 * A panel's watcher: the item registers with it and it says whether a panel is attached. It implements the
 * app's own interface, because dbus-java keeps one Java class per D-Bus interface name for the whole JVM.
 */
internal class FakeWatcher(private val address: String, hostRegistered: Boolean = true) : StatusNotifierWatcherInterface, Properties {
    val registrations = CopyOnWriteArrayList<String>()

    @Volatile private var host = hostRegistered
    private var connection: DBusConnection? = null

    fun start() {
        val connection = DBusConnectionBuilder.forAddress(address).build()
        connection.requestBusName("org.kde.StatusNotifierWatcher")
        connection.exportObject("/StatusNotifierWatcher", this)
        this.connection = connection
    }

    fun stop() {
        connection?.close()
        connection = null
    }

    fun setHostRegistered(value: Boolean) {
        host = value
        if (value) connection?.sendMessage(StatusNotifierWatcherInterface.StatusNotifierHostRegistered("/StatusNotifierWatcher"))
    }

    override fun RegisterStatusNotifierItem(service: String) {
        registrations += service
    }

    // Answered as a real watcher does: wrapped in a variant.
    @Suppress("UNCHECKED_CAST")
    override fun <A> Get(interfaceName: String, propertyName: String): A = Variant(host) as A

    override fun <A> Set(interfaceName: String, propertyName: String, value: A) = Unit

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = mapOf("IsStatusNotifierHostRegistered" to Variant(host))

    override fun isRemote(): Boolean = false

    override fun getObjectPath(): String = "/StatusNotifierWatcher"
}

/** A throwaway session bus, so a test never touches the user's panel. Skipped where dbus-daemon or busctl is missing. */
internal class PrivateSessionBus private constructor(private val process: Process, val address: String) : AutoCloseable {
    override fun close() {
        process.destroy()
        process.waitFor(5, TimeUnit.SECONDS)
    }

    companion object {
        fun startOrNull(): PrivateSessionBus? {
            if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return null
            val process = runCatching {
                ProcessBuilder("dbus-daemon", "--session", "--nofork", "--print-address").redirectErrorStream(false).start()
            }.getOrNull() ?: return null
            val address = process.inputStream.bufferedReader().readLine()
            if (address.isNullOrBlank() || runCatching { ProcessBuilder("busctl", "--version").start().waitFor() }.getOrNull() != 0) {
                process.destroy()
                return null
            }
            return PrivateSessionBus(process, address)
        }
    }
}

internal class SignalMonitor(address: String) : AutoCloseable {
    val lines = CopyOnWriteArrayList<String>()
    private val process = ProcessBuilder(
        "dbus-monitor", "--address", address, "type='signal',interface='org.kde.StatusNotifierItem'",
    ).redirectErrorStream(true).start()
    private val reader = Thread { process.inputStream.bufferedReader().forEachLine { lines += it } }.apply {
        isDaemon = true
        start()
    }

    init {
        Thread.sleep(700) // dbus-monitor prints nothing until a signal arrives, so give it time to subscribe
    }

    fun sawAll(vararg members: String): Boolean = eventually { members.all { member -> lines.any { "member=$member" in it } } }

    fun sawAny(vararg members: String): Boolean {
        Thread.sleep(500)
        return members.any { member -> lines.any { "member=$member" in it } }
    }

    override fun close() {
        process.destroy()
        reader.join(2_000)
    }
}

private fun busctl(address: String, vararg args: String): String {
    val process = ProcessBuilder(listOf("busctl", "--address=$address") + args).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor(10, TimeUnit.SECONDS)
    return output
}

private fun eventually(timeoutMillis: Long = 5_000, check: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    while (System.nanoTime() < deadline) {
        if (check()) return true
        Thread.sleep(25)
    }
    return check()
}
