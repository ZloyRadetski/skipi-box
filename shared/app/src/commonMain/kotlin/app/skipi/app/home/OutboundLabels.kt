// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import engine.xray.XrayTags

/**
 * Names the outbound tags of one session for the connection log. The planner tags the selected server
 * `proxy`, any other routed server by its id, a balancer by the tag of its target and its members
 * `<balancer>-policy-<id>`; Xray reports the member it picked, so a balancer reads "Group - Server".
 *
 * [serverNames] maps a server id to its name and [selectedName] is the name of the selected server.
 * A tag that is not known stays as Core reported it, and plain `proxy` stays `proxy`.
 */
data class OutboundLabels(
    val serverNames: Map<Int, String>,
    val selectedName: String?,
) {
    fun label(tag: String): String {
        ProfileServerTag.matchEntire(tag)?.let { match ->
            return serverNames[match.groupValues[1].toIntOrNull()] ?: tag
        }
        ProfileGroupMemberTag.matchEntire(tag)?.let { match ->
            val server = serverNames[match.groupValues[2].toIntOrNull()] ?: return tag
            return "$ProfileGroupLabel - $server"
        }
        // A profile group: `shadowrocket-group:<Name>`, its members `shadowrocket-group:<Name>-policy-<id>`.
        ShadowrocketGroupMemberTag.matchEntire(tag)?.let { match ->
            val server = serverNames[match.groupValues[2].toIntOrNull()] ?: return tag
            return "${match.groupValues[1]} - $server"
        }
        if (tag.startsWith(ShadowrocketGroupPrefix)) {
            return tag.removePrefix(ShadowrocketGroupPrefix).ifBlank { tag }
        }
        val member = MemberTag.matchEntire(tag)
        if (member != null) {
            val group = groupName(member.groupValues[1]) ?: return tag
            val server = serverNames[member.groupValues[2].toIntOrNull()] ?: return tag
            return "$group - $server"
        }
        if (tag == XrayTags.PROXY) return tag
        return tag.toIntOrNull()?.let(serverNames::get)?.takeIf(String::isNotBlank) ?: tag
    }

    private fun groupName(tag: String): String? =
        if (tag == XrayTags.PROXY) selectedName else tag.toIntOrNull()?.let(serverNames::get)

    private companion object {
        const val ShadowrocketGroupPrefix = "shadowrocket-group:"
        val ShadowrocketGroupMemberTag = Regex("""shadowrocket-group:(.+)-policy-(\d+)""")
        val MemberTag = Regex("""(.+)-policy-(\d+)""")
        // A profile's servers and groups (TrafficProfileXrayPlanner); the group's own name is not in the tag.
        val ProfileServerTag = Regex("""skipi-server-(\d+)""")
        val ProfileGroupMemberTag = Regex("""skipi-group-member-(\d+)-(\d+)""")
        const val ProfileGroupLabel = "Balancer"
    }
}
