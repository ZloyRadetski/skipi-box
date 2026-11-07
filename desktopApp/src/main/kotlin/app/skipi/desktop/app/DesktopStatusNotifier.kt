// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/** What the tray shows: the tooltip, the menu entries and the icon at a few sizes. */
internal data class DesktopStatusNotifierState(
    val tooltip: String,
    val menu: List<DesktopTrayMenuEntry>,
    val icons: List<DesktopTrayPixmap>,
)

/**
 * The tray icon as a StatusNotifierItem on the session bus, the way KDE, waybar, quickshell and GNOME's
 * AppIndicator show one. AWT's tray only speaks the old XEmbed protocol, which most Wayland panels lack.
 *
 * [registered] is true while a watcher holds the item and a panel is attached to it, i.e. while the icon is
 * really on screen. It follows the watcher: a panel that starts after SKIPI, or restarts, gets the item.
 *
 * The menu is exported as a dbusmenu and drawn by the panel in its own style: panels such as quickshell and waybar
 * only show a menu the item exports, they never ask the app to draw one. The Compose menu of Windows and macOS is
 * for the AWT tray only.
 */
internal class DesktopStatusNotifier(
    private val busAddress: String?,
    private val onActivate: () -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private val shown = MutableStateFlow(false)
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "skipi-tray").apply { isDaemon = true }
    }
    private val subscriptions = mutableListOf<AutoCloseable>()

    @Volatile private var state: DesktopStatusNotifierState? = null
    private var connection: DBusConnection? = null
    private var menu: DesktopStatusNotifierMenu? = null
    private var serviceName: String = ""
    private var registeredWith: String? = null

    val registered: StateFlow<Boolean> get() = shown

    /** Connects and registers; a missing bus or watcher is not an error, it just means no icon. */
    fun start(initial: DesktopStatusNotifierState): Unit = synchronized(lock) {
        if (connection != null) return
        state = initial
        try {
            // dbus-java retries a missing bus for over nine seconds; a socket that is not there is not worth it.
            val address = (if (busAddress != null) desktopBusAddressIfReachable(busAddress) else desktopSessionBusAddress())
                ?: run {
                    DesktopLogger.info("Tray", "No D-Bus session bus to show a StatusNotifier tray on")
                    return
                }
            val bus = DBusConnectionBuilder.forAddress(address).withShared(false).build()
            val trayMenu = DesktopStatusNotifierMenu(initial.menu)
            menu = trayMenu
            serviceName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
            bus.exportObject(ItemPath, StatusNotifierItemObject({ state ?: initial }, onActivate))
            bus.exportObject(MenuPath, DbusMenuObject(trayMenu))
            runCatching { bus.requestBusName(serviceName) }.onFailure { serviceName = bus.uniqueName }
            subscriptions += bus.addSigHandler(DBus.NameOwnerChanged::class.java, DBusSigHandler { change ->
                if (change.name == WatcherName) evaluateLater()
            })
            subscriptions += bus.addSigHandler(StatusNotifierWatcherInterface.StatusNotifierHostRegistered::class.java, DBusSigHandler {
                evaluateLater()
            })
            connection = bus
            evaluate()
        } catch (failure: Throwable) {
            // A tray is never worth the application: a runtime missing a module throws an Error, not an Exception.
            if (failure is VirtualMachineError) throw failure
            DesktopLogger.warn("Tray", "No StatusNotifier tray", failure)
            disconnect()
        }
    }

    fun update(next: DesktopStatusNotifierState): Unit = synchronized(lock) {
        val previous = state
        state = next
        val trayMenu = menu
        val menuChanged = trayMenu?.set(next.menu) ?: false
        val bus = connection ?: return
        if (previous == null) return
        runCatching {
            if (menuChanged && trayMenu != null) {
                bus.sendMessage(DbusMenuInterface.LayoutUpdated(MenuPath, UInt32(trayMenu.revision.toLong()), 0))
            }
            if (previous.icons != next.icons) bus.sendMessage(StatusNotifierItemInterface.NewIcon(ItemPath))
            if (previous.tooltip != next.tooltip) bus.sendMessage(StatusNotifierItemInterface.NewToolTip(ItemPath))
        }.onFailure { DesktopLogger.warn("Tray", "Could not tell the panel about the new tray state", it) }
    }

    override fun close() {
        synchronized(lock) { disconnect() }
        worker.shutdownNow()
    }

    private fun disconnect() {
        subscriptions.forEach { runCatching { it.close() } }
        subscriptions.clear()
        runCatching { connection?.close() }
        connection = null
        menu = null
        registeredWith = null
        shown.value = false
    }

    private fun evaluateLater() {
        try {
            worker.execute { runCatching { synchronized(lock) { evaluate() } } }
        } catch (_: RejectedExecutionException) {
            // closed meanwhile
        }
    }

    /** Hands the item to the watcher that owns the name now, and works out whether a panel is attached to it. */
    private fun evaluate() {
        val bus = connection ?: return
        val owner = runCatching { bus.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus::class.java).GetNameOwner(WatcherName) }
            .getOrNull()
        if (owner == null) {
            registeredWith = null
            shown.value = false
            return
        }
        if (registeredWith != owner) {
            try {
                bus.getRemoteObject(WatcherName, WatcherPath, StatusNotifierWatcherInterface::class.java)
                    .RegisterStatusNotifierItem(serviceName)
                registeredWith = owner
            } catch (failure: Exception) {
                DesktopLogger.warn("Tray", "The StatusNotifier watcher refused the tray item", failure)
                shown.value = false
                return
            }
        }
        // A watcher that does not say whether a panel is attached is taken to have one.
        val hostAttached = runCatching {
            bus.getRemoteObject(WatcherName, WatcherPath, Properties::class.java).Get<Boolean>(WatcherName, "IsStatusNotifierHostRegistered")
        }.getOrDefault(true)
        shown.value = hostAttached
    }

    private companion object {
        const val WatcherName = "org.kde.StatusNotifierWatcher"
        const val WatcherPath = "/StatusNotifierWatcher"
        const val ItemPath = "/StatusNotifierItem"
        const val MenuPath = "/MenuBar"
    }
}

/** The session bus address when its socket can be reached, otherwise null. */
internal fun desktopSessionBusAddress(
    env: Map<String, String> = System.getenv(),
    exists: (String) -> Boolean = { java.io.File(it).exists() },
): String? {
    val configured = env["DBUS_SESSION_BUS_ADDRESS"]?.takeIf(String::isNotBlank)
    val address = configured ?: env["XDG_RUNTIME_DIR"]?.takeIf(String::isNotBlank)?.let { "unix:path=$it/bus" }
    return address?.let { desktopBusAddressIfReachable(it, exists) }
}

/** A `unix:path=` address is only worth connecting to while the socket file exists; other kinds cannot be checked. */
internal fun desktopBusAddressIfReachable(
    address: String,
    exists: (String) -> Boolean = { java.io.File(it).exists() },
): String? {
    val path = address.removePrefix("unix:").split(',')
        .firstOrNull { it.startsWith("path=") }?.removePrefix("path=")
        ?.takeIf { address.startsWith("unix:") }
    return if (path == null || exists(path)) address else null
}

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
internal interface StatusNotifierWatcherInterface : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)

    class StatusNotifierHostRegistered(path: String) : DBusSignal(path)
}

internal class SniPixmap(
    @field:Position(0) @JvmField val width: Int,
    @field:Position(1) @JvmField val height: Int,
    @field:Position(2) @JvmField val data: ByteArray,
) : Struct()

internal class SniToolTip(
    @field:Position(0) @JvmField val iconName: String,
    @field:Position(1) @JvmField val iconData: List<SniPixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val description: String,
) : Struct()

@DBusInterfaceName("org.kde.StatusNotifierItem")
internal interface StatusNotifierItemInterface : DBusInterface {
    fun ContextMenu(x: Int, y: Int)
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)

    class NewIcon(path: String) : DBusSignal(path)
    class NewToolTip(path: String) : DBusSignal(path)
}

/**
 * Read-only properties by hand: dbus-java's annotation-bound properties answer Get without the variant
 * wrapper and fail GetAll, and a variant needs its signature spelled out for the lists and structs here.
 */
private abstract class ReadOnlyProperties(private val interfaceName: String) : Properties {
    abstract fun values(): Map<String, Variant<*>>

    @Suppress("UNCHECKED_CAST")
    override fun <A> Get(interfaceName: String, propertyName: String): A {
        val value = if (interfaceName == this.interfaceName) values()[propertyName] else null
        return (value ?: throw DBusExecutionException("No property $propertyName on $interfaceName")) as A
    }

    override fun <A> Set(interfaceName: String, propertyName: String, value: A) {
        throw DBusExecutionException("Property $propertyName is read-only")
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> =
        if (interfaceName == this.interfaceName) values() else emptyMap()
}

private class StatusNotifierItemObject(
    private val state: () -> DesktopStatusNotifierState,
    private val onActivate: () -> Unit,
) : ReadOnlyProperties("org.kde.StatusNotifierItem"), StatusNotifierItemInterface {
    private fun pixmaps() = state().icons.map { SniPixmap(it.width, it.height, trayPixmapBytes(it.argb)) }

    override fun values(): Map<String, Variant<*>> {
        val noPixmaps = Variant(emptyList<SniPixmap>(), "a(iiay)")
        return mapOf(
            "Category" to Variant("ApplicationStatus"),
            "Id" to Variant("skipi"),
            "Title" to Variant("SKIPI"),
            "Status" to Variant("Active"),
            "WindowId" to Variant(0),
            "IconThemePath" to Variant(""),
            "Menu" to Variant(DBusPath("/MenuBar")),
            "ItemIsMenu" to Variant(false),
            "IconName" to Variant(""),
            "IconPixmap" to Variant(pixmaps(), "a(iiay)"),
            "OverlayIconName" to Variant(""),
            "OverlayIconPixmap" to noPixmaps,
            "AttentionIconName" to Variant(""),
            "AttentionIconPixmap" to noPixmaps,
            "AttentionMovieName" to Variant(""),
            "ToolTip" to Variant(SniToolTip("", emptyList(), state().tooltip, ""), "(sa(iiay)ss)"),
        )
    }

    // A click arrives on a D-Bus thread; the window belongs to the UI thread.
    override fun Activate(x: Int, y: Int) = java.awt.EventQueue.invokeLater(onActivate)

    // Panels ask the item to draw its own menu on ContextMenu only when it has none; ours is dbusmenu.
    override fun ContextMenu(x: Int, y: Int) = Unit
    override fun SecondaryActivate(x: Int, y: Int) = Unit
    override fun Scroll(delta: Int, orientation: String) = Unit

    override fun isRemote() = false
    override fun getObjectPath() = "/StatusNotifierItem"
}

internal class DbusMenuNode(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

internal class DbusMenuItemProperties(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

// dbus-java reads the types of a multi-value reply from the type arguments of its Tuple.
internal class DbusMenuLayout<Revision, Root>(
    @field:Position(0) @JvmField val revision: Revision,
    @field:Position(1) @JvmField val root: Root,
) : Tuple()

@DBusInterfaceName("com.canonical.dbusmenu")
internal interface DbusMenuInterface : DBusInterface {
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: Array<String>): DbusMenuLayout<UInt32, DbusMenuNode>
    fun GetGroupProperties(ids: IntArray, propertyNames: Array<String>): List<DbusMenuItemProperties>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun AboutToShow(id: Int): Boolean

    class LayoutUpdated(path: String, revision: UInt32, parent: Int) : DBusSignal(path, revision, parent)
}

private class DbusMenuObject(private val menu: DesktopStatusNotifierMenu) : ReadOnlyProperties("com.canonical.dbusmenu"), DbusMenuInterface {
    override fun values(): Map<String, Variant<*>> = mapOf(
        "Version" to Variant(UInt32(3)),
        "TextDirection" to Variant("ltr"),
        "Status" to Variant("normal"),
        "IconThemePath" to Variant(emptyArray<String>()),
    )

    private fun propertiesOf(entry: DesktopTrayMenuEntry): Map<String, Variant<*>> = when (entry) {
        DesktopTrayMenuEntry.Separator -> mapOf("type" to Variant("separator"))
        is DesktopTrayMenuEntry.Action -> mapOf("label" to Variant(entry.label), "enabled" to Variant(true), "visible" to Variant(true))
        is DesktopTrayMenuEntry.Info -> mapOf("label" to Variant(entry.label), "enabled" to Variant(false), "visible" to Variant(true))
    }

    override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: Array<String>): DbusMenuLayout<UInt32, DbusMenuNode> {
        val revision = UInt32(menu.revision.toLong())
        val entries = menu.entries
        val root = if (parentId == 0 && recursionDepth != 0) {
            val children = entries.mapIndexed { index, entry ->
                Variant(DbusMenuNode(index + 1, propertiesOf(entry), emptyList()), "(ia{sv}av)")
            }
            DbusMenuNode(0, mapOf("children-display" to Variant("submenu")), children)
        } else if (parentId == 0) {
            DbusMenuNode(0, mapOf("children-display" to Variant("submenu")), emptyList())
        } else {
            DbusMenuNode(parentId, entries.getOrNull(parentId - 1)?.let(::propertiesOf).orEmpty(), emptyList())
        }
        return DbusMenuLayout(revision, root)
    }

    override fun GetGroupProperties(ids: IntArray, propertyNames: Array<String>): List<DbusMenuItemProperties> =
        menu.entries.mapIndexedNotNull { index, entry ->
            val id = index + 1
            if (ids.isEmpty() || id in ids) DbusMenuItemProperties(id, propertiesOf(entry)) else null
        }

    override fun GetProperty(id: Int, name: String): Variant<*> =
        menu.entries.getOrNull(id - 1)?.let(::propertiesOf)?.get(name) ?: Variant("")

    // Hovers, opens and closes are not clicks; only a click runs an entry, and on the UI thread.
    override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
        if (eventId == "clicked") java.awt.EventQueue.invokeLater { menu.click(id) }
    }

    override fun AboutToShow(id: Int) = false

    override fun isRemote() = false
    override fun getObjectPath() = "/MenuBar"
}
