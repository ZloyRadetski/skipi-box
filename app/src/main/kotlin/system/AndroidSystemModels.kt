// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system

data class ShellExecOptions(
    val cwd: String? = null,
    val env: Map<String, String> = emptyMap(),
    val logFailure: Boolean = true,
)

data class ShellExecResult(
    val errno: Int,
    val stdout: String,
    val stderr: String,
)

data class InstalledPackageInfo(
    val packageName: String,
    val appLabel: String,
    val isSystem: Boolean,
    val uid: Int,
)

data class AndroidUser(
    val id: Int,
    val name: String,
)
