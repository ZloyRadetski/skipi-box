// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

fun updateProxyAppListSelection(
    selectedApps: List<String>,
    selectionKeys: List<String>,
    isChecked: Boolean,
): List<String> {
    return if (isChecked) {
        (selectedApps + selectionKeys).distinct()
    } else {
        selectedApps - selectionKeys.toSet()
    }
}
