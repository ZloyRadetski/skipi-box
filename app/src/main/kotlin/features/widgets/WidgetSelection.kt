// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import app.AppState
import features.proxy.server.list.isVisibleOnProxyServerList

/** Direction used by the previous/next controls on the expanded widget. */
internal enum class WidgetCycleDirection {
    Previous,
    Next,
}

/**
 * Returns the adjacent option while preserving the order configured in the app.
 * If the current entry no longer exists, the direction chooses the nearest edge.
 */
internal fun cycleWidgetOptionId(
    optionIds: List<Int>,
    currentId: Int,
    direction: WidgetCycleDirection,
): Int? {
    val orderedIds = optionIds.distinct()
    if (orderedIds.isEmpty()) return null
    val currentIndex = orderedIds.indexOf(currentId)
    if (currentIndex < 0) {
        return when (direction) {
            WidgetCycleDirection.Previous -> orderedIds.last()
            WidgetCycleDirection.Next -> orderedIds.first()
        }
    }
    val nextIndex = when (direction) {
        WidgetCycleDirection.Previous -> (currentIndex - 1 + orderedIds.size) % orderedIds.size
        WidgetCycleDirection.Next -> (currentIndex + 1) % orderedIds.size
    }
    return orderedIds[nextIndex]
}

internal fun AppState.widgetConfigIds(): List<Int> = trafficConfigs.map { config -> config.id }.distinct()

/** Mirrors the selectable set from the home server list, omitting hidden generated groups. */
internal fun AppState.widgetServerIds(): List<Int> {
    return proxyServers
        .asSequence()
        .filter { server -> server.isVisibleOnProxyServerList(activeTrafficConfigId) }
        .map { server -> server.id }
        .distinct()
        .toList()
}
