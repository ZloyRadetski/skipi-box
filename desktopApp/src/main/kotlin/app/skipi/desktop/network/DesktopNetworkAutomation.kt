// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.networkautomation.NetworkAutomationServerOption

/** Catalog servers a rule can switch to, named by their remarks like the Android picker. */
internal fun desktopNetworkAutomationServers(library: DesktopServerLibrary): List<NetworkAutomationServerOption> =
    library.servers.map { stored ->
        val remarks = stored.decode().getOrNull()?.getInfo()?.remarks?.trim().orEmpty()
        NetworkAutomationServerOption(id = stored.id, name = remarks.ifBlank { "#${stored.id}" })
    }
