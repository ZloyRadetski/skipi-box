// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.ResourceFileUpdateSources
import features.resources.ResourceFileSourceCustom
import kotlinx.serialization.json.JsonObject

/** Android supplies its persisted geo-resource preset IDs to the shared converter. */
internal fun JsonObject.toRoscomRoutingShadowrocketConf(fallbackName: String): String {
    return toRoscomRoutingShadowrocketConf(
        fallbackName = fallbackName,
        resourceSources = ResourceFileUpdateSources.map { source ->
            RoscomRoutingResourceSource(
                id = source.id,
                geoIpUrl = source.geoIpUrl,
                geoSiteUrl = source.geoSiteUrl,
            )
        },
        customResourceSourceId = ResourceFileSourceCustom,
    )
}
