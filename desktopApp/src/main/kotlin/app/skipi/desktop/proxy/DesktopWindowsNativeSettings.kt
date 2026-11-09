// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * In-process Win32 replacements for what the system proxy used to run as processes: about ten reg.exe calls
 * and a PowerShell with an inline C# compile, together ~0.9 s of every connect.
 */
private object Win32 {
    private val linker = Linker.nativeLinker()
    private val advapi32 = SymbolLookup.libraryLookup("advapi32", Arena.global())
    private val wininet = SymbolLookup.libraryLookup("wininet", Arena.global())

    private fun handle(lookup: SymbolLookup, name: String, descriptor: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(lookup.findOrThrow(name), descriptor)

    private val A = ValueLayout.ADDRESS
    private val I = ValueLayout.JAVA_INT

    val internetSetOption: MethodHandle by lazy { handle(wininet, "InternetSetOptionW", FunctionDescriptor.of(I, A, I, A, I)) }
    val regOpenKeyEx: MethodHandle by lazy { handle(advapi32, "RegOpenKeyExW", FunctionDescriptor.of(I, A, A, I, I, A)) }
    val regCreateKeyEx: MethodHandle by lazy { handle(advapi32, "RegCreateKeyExW", FunctionDescriptor.of(I, A, A, I, A, I, I, A, A, A)) }
    val regQueryValueEx: MethodHandle by lazy { handle(advapi32, "RegQueryValueExW", FunctionDescriptor.of(I, A, A, A, A, A, A)) }
    val regSetValueEx: MethodHandle by lazy { handle(advapi32, "RegSetValueExW", FunctionDescriptor.of(I, A, A, I, I, A, I)) }
    val regDeleteValue: MethodHandle by lazy { handle(advapi32, "RegDeleteValueW", FunctionDescriptor.of(I, A, A)) }
    val regCloseKey: MethodHandle by lazy { handle(advapi32, "RegCloseKey", FunctionDescriptor.of(I, A)) }

    /** HKEY_CURRENT_USER is the sign-extended constant 0x80000001. */
    val currentUser: MemorySegment = MemorySegment.ofAddress(-0x7FFFFFFFL)

    const val ERROR_SUCCESS = 0
    const val ERROR_FILE_NOT_FOUND = 2
    const val KEY_READ_ACCESS = 0x20019
    const val KEY_WRITE_ACCESS = 0x20006
    const val REG_SZ = 1
    const val REG_DWORD = 4

    fun wide(arena: Arena, text: String): MemorySegment = bytes(arena, (text + NUL).toByteArray(Charsets.UTF_16LE))

    fun bytes(arena: Arena, data: ByteArray): MemorySegment =
        arena.allocate(maxOf(data.size, 1).toLong()).also { it.copyFrom(MemorySegment.ofArray(data)) }

    const val NUL = "\u0000"
}

/** Refreshes WinINet with InternetSetOptionW(NULL, SETTINGS_CHANGED=39 / REFRESH=37), as the PowerShell script did. */
class NativeWinInetSettingsRefresher : DesktopWindowsInternetSettingsRefresher {
    override fun refresh(): Result<Unit> = runCatching {
        val changed = Win32.internetSetOption.invokeWithArguments(MemorySegment.NULL, 39, MemorySegment.NULL, 0) as Int
        val refreshed = Win32.internetSetOption.invokeWithArguments(MemorySegment.NULL, 37, MemorySegment.NULL, 0) as Int
        check(changed != 0 && refreshed != 0) { "Windows did not refresh its Internet proxy settings" }
    }
}

/** The native refresh, falling back to the PowerShell one where the native call cannot be made. */
class FallbackWinInetSettingsRefresher(
    private val primary: DesktopWindowsInternetSettingsRefresher,
    private val fallback: DesktopWindowsInternetSettingsRefresher,
) : DesktopWindowsInternetSettingsRefresher {
    override fun refresh(): Result<Unit> = primary.refresh().recoverCatching { fallback.refresh().getOrThrow() }
}

/**
 * HKCU registry access through advapi32. Same surface and value format as [RegExeDesktopWindowsRegistry]
 * (a DWORD reads as `0x1`), and likewise limited to the current user's hive.
 */
class NativeDesktopWindowsRegistry : DesktopWindowsRegistry {
    override fun read(key: String, valueName: String): Result<DesktopWindowsRegistryValue?> = runCatching {
        Arena.ofConfined().use { arena ->
            val hKey = openKey(arena, key, Win32.KEY_READ_ACCESS, create = false) ?: return@runCatching null
            try {
                val name = Win32.wide(arena, valueName)
                val type = arena.allocate(4)
                val size = arena.allocate(4)
                val probe = Win32.regQueryValueEx.invokeWithArguments(hKey, name, MemorySegment.NULL, type, MemorySegment.NULL, size) as Int
                if (probe == Win32.ERROR_FILE_NOT_FOUND) return@runCatching null
                check(probe == Win32.ERROR_SUCCESS) { "Registry could not read $key/$valueName (error $probe)" }
                val data = arena.allocate(maxOf(size.get(ValueLayout.JAVA_INT, 0).toLong(), 4L))
                size.set(ValueLayout.JAVA_INT, 0, data.byteSize().toInt())
                val status = Win32.regQueryValueEx.invokeWithArguments(hKey, name, MemorySegment.NULL, type, data, size) as Int
                if (status == Win32.ERROR_FILE_NOT_FOUND) return@runCatching null
                check(status == Win32.ERROR_SUCCESS) { "Registry could not read $key/$valueName (error $status)" }
                val length = size.get(ValueLayout.JAVA_INT, 0).toLong()
                when (type.get(ValueLayout.JAVA_INT, 0)) {
                    Win32.REG_SZ -> DesktopWindowsRegistryValue.string(
                        String(data.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE), Charsets.UTF_16LE).trimEnd(Char(0)),
                    )
                    Win32.REG_DWORD -> DesktopWindowsRegistryValue(
                        DesktopWindowsRegistryValueType.Dword,
                        "0x" + Integer.toHexString(data.get(ValueLayout.JAVA_INT_UNALIGNED, 0)),
                    )
                    else -> error("Registry value $valueName has an unsupported type")
                }
            } finally {
                Win32.regCloseKey.invokeWithArguments(hKey)
            }
        }
    }

    override fun write(key: String, valueName: String, value: DesktopWindowsRegistryValue): Result<Unit> = runCatching {
        Arena.ofConfined().use { arena ->
            val hKey = openKey(arena, key, Win32.KEY_WRITE_ACCESS, create = true)
                ?: error("Could not open the registry key $key for writing")
            try {
                val (type, bytes) = when (value.type) {
                    DesktopWindowsRegistryValueType.String ->
                        Win32.REG_SZ to (value.data + Win32.NUL).toByteArray(Charsets.UTF_16LE)
                    DesktopWindowsRegistryValueType.Dword -> {
                        val text = value.data.trim()
                        val number = (if (text.startsWith("0x", ignoreCase = true)) text.substring(2).toLong(16) else text.toLong()).toInt()
                        Win32.REG_DWORD to ByteArray(4) { index -> (number ushr (8 * index)).toByte() }
                    }
                }
                val status = Win32.regSetValueEx.invokeWithArguments(
                    hKey, Win32.wide(arena, valueName), 0, type, Win32.bytes(arena, bytes), bytes.size,
                ) as Int
                check(status == Win32.ERROR_SUCCESS) { "Registry could not write $key/$valueName (error $status)" }
            } finally {
                Win32.regCloseKey.invokeWithArguments(hKey)
            }
        }
    }

    override fun delete(key: String, valueName: String): Result<Unit> = runCatching {
        Arena.ofConfined().use { arena ->
            val hKey = openKey(arena, key, Win32.KEY_WRITE_ACCESS, create = false) ?: return@runCatching
            try {
                val status = Win32.regDeleteValue.invokeWithArguments(hKey, Win32.wide(arena, valueName)) as Int
                check(status == Win32.ERROR_SUCCESS || status == Win32.ERROR_FILE_NOT_FOUND) {
                    "Registry could not delete $key/$valueName (error $status)"
                }
            } finally {
                Win32.regCloseKey.invokeWithArguments(hKey)
            }
        }
    }

    /** Null when the key does not exist and [create] is false. */
    private fun openKey(arena: Arena, key: String, access: Int, create: Boolean): MemorySegment? {
        require(key.startsWith(HkcuPrefix, ignoreCase = true)) { "Only HKCU registry keys are supported: $key" }
        val subKey = Win32.wide(arena, key.substring(HkcuPrefix.length))
        val result = arena.allocate(ValueLayout.ADDRESS)
        val status = if (create) {
            Win32.regCreateKeyEx.invokeWithArguments(
                Win32.currentUser, subKey, 0, MemorySegment.NULL, 0, access, MemorySegment.NULL, result, MemorySegment.NULL,
            ) as Int
        } else {
            Win32.regOpenKeyEx.invokeWithArguments(Win32.currentUser, subKey, 0, access, result) as Int
        }
        if (status == Win32.ERROR_FILE_NOT_FOUND && !create) return null
        check(status == Win32.ERROR_SUCCESS) { "Registry could not open $key (error $status)" }
        return result.get(ValueLayout.ADDRESS, 0)
    }

    private companion object {
        val HkcuPrefix = "HKCU" + Char(92)
    }
}
