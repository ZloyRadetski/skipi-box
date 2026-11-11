// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.settings_category_dns_leak
import app.skipi.ui.resources.settings_category_ip_info
import app.skipi.ui.resources.settings_category_speed_test
import app.skipi.ui.resources.settings_core_logs
import org.jetbrains.compose.resources.stringResource

/**
 * A page the navigation chrome opens directly, without going through Settings: the diagnostics and the
 * logs. Both hosts open the same set; each decides how its own navigation reaches a page.
 */
enum class SkipiTool(
    /** Stable name for storage; never reuse one, and never derive it from the declaration order. */
    val key: String,
    val icon: ImageVector,
) {
    SpeedTest(key = "speed_test", icon = Icons.Filled.Bolt),
    DnsLeakTest(key = "dns_leak_test", icon = Icons.Filled.Security),
    IpInfo(key = "ip_info", icon = Icons.Filled.Public),
    CoreLogs(key = "core_logs", icon = Icons.Filled.Description),

    /** The rule editor of the profile that is selected, wherever that lives on the host. */
    RoutingRules(key = "routing_rules", icon = Icons.Filled.AltRoute),
    ;

    companion object {
        /** What a fresh install pins: every tool, in declaration order. */
        val defaultPinned: List<SkipiTool> = entries.toList()

        fun fromKey(key: String): SkipiTool? = entries.firstOrNull { tool -> tool.key == key }
    }
}

@Composable
fun SkipiTool.label(): String = stringResource(
    when (this) {
        SkipiTool.SpeedTest -> Res.string.settings_category_speed_test
        SkipiTool.DnsLeakTest -> Res.string.settings_category_dns_leak
        SkipiTool.IpInfo -> Res.string.settings_category_ip_info
        SkipiTool.CoreLogs -> Res.string.settings_core_logs
        SkipiTool.RoutingRules -> Res.string.configs_rules_title
    },
)

/** A navigation item that does something instead of switching the section on screen. */
class SkipiNavigationAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** The pinned [tools] as navigation actions, in the given order. */
@Composable
fun skipiToolActions(tools: List<SkipiTool>, onOpen: (SkipiTool) -> Unit): List<SkipiNavigationAction> =
    tools.map { tool -> SkipiNavigationAction(label = tool.label(), icon = tool.icon, onClick = { onOpen(tool) }) }

private const val ToolMenuIdPrefix = "pinned-tool:"

/** The id a menu gives the entry of [tool]; [skipiToolFromMenuId] reads it back. */
fun skipiToolMenuId(tool: SkipiTool): String = ToolMenuIdPrefix + tool.key

/** The tool a menu entry [id] stands for, or null when the id belongs to something else. */
fun skipiToolFromMenuId(id: String): SkipiTool? =
    id.takeIf { it.startsWith(ToolMenuIdPrefix) }?.let { SkipiTool.fromKey(it.removePrefix(ToolMenuIdPrefix)) }
