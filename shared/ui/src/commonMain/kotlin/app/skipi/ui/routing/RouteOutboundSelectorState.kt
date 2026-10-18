// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skipi.app.routing.RouteOutboundItem
import features.proxy.server.model.extractLeadingCountryFlagOrNull
import features.proxy.server.model.getTransportDisplay
import features.proxy.server.model.stripLeadingCountryFlag
import features.routing.model.DefaultRouteOutboundTag

/** Pending selection and per-group expansion overrides for the route-outbound picker. */
internal class RouteOutboundSelectorState(initialSelectedTag: String) {
    private var externalSelectedTag = initialSelectedTag
    private val expandedOverrides = mutableStateMapOf<String, Boolean>()

    var targetTag by mutableStateOf(initialSelectedTag)
        private set

    fun select(tag: String) {
        targetTag = tag
    }

    /** Resets a pending choice only when the host supplies a different selected tag. */
    fun updateSelectedTag(selectedTag: String) {
        if (selectedTag == externalSelectedTag) return
        externalSelectedTag = selectedTag
        targetTag = selectedTag
    }

    fun isExpanded(key: String, initiallyExpandedGroupKey: String?): Boolean =
        expandedOverrides[key] ?: (key == initiallyExpandedGroupKey)

    fun setExpanded(key: String, expanded: Boolean) {
        expandedOverrides[key] = expanded
    }
}

/** Converts a projected picker target into the Android selector's row presentation. */
internal fun presentRouteOutboundOption(
    item: RouteOutboundItem,
    defaultProxyServerTemplate: String,
): SkipiRoutingOutboundOption = when (item) {
    is RouteOutboundItem.Static -> {
        val flag = when (item.tag) {
            "PROXY", DefaultRouteOutboundTag -> "⚡"
            "DIRECT", "direct" -> "🔄"
            "REJECT", "block" -> "🚫"
            else -> if (item.tag.startsWith("CONFIG:")) "⚙️" else "🌐"
        }
        SkipiRoutingOutboundOption(
            key = item.key,
            tag = item.tag,
            title = item.title,
            subtitle = item.summary.takeIf { it.isNotBlank() },
            flag = flag,
        )
    }
    is RouteOutboundItem.Server -> {
        val info = item.server.server.getInfo()
        val rawRemarks = info.remarks
        val flag = rawRemarks.extractLeadingCountryFlagOrNull()
        val title = if (flag != null) {
            rawRemarks.stripLeadingCountryFlag()
        } else {
            rawRemarks.ifBlank { defaultProxyServerTemplate.replace("{id}", item.server.id.toString()) }
        }
        val transport = item.server.server.getTransportDisplay()
        val subtitle = if (!transport.isNullOrBlank()) "${info.protocol} • $transport" else info.protocol
        SkipiRoutingOutboundOption(
            key = item.key,
            tag = item.tag,
            title = title,
            subtitle = subtitle,
            flag = flag,
        )
    }
}
