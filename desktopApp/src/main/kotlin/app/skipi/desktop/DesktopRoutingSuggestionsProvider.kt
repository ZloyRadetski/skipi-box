// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigState
import features.routing.usecase.GeoDatParser
import features.routing.usecase.GeoSuggestionItem
import features.routing.usecase.resolveGeoIpSuggestions as resolveGeoIpSuggestionsCommon
import features.routing.usecase.resolveGeoSiteSuggestions as resolveGeoSiteSuggestionsCommon
import features.routing.usecase.resolveRuleSetSuggestions as resolveRuleSetSuggestionsCommon
import java.io.File

internal class DesktopRoutingSuggestionsProvider(
    private val resourceFiles: DesktopProfileResourceFiles,
) {
    fun resolveGeoSiteSuggestions(profile: TrafficConfigState): List<GeoSuggestionItem> {
        val snapshot = resourceFiles.snapshot(profile)
        return resolveGeoSiteSuggestionsCommon(
            profile = profile,
            globalCustomFiles = emptyList(),
            fileTags = { fileName -> readTags(snapshot.dataDir, fileName) },
        )
    }

    fun resolveGeoIpSuggestions(
        profile: TrafficConfigState,
        forShadowrocket: Boolean = false,
    ): List<GeoSuggestionItem> {
        val snapshot = resourceFiles.snapshot(profile)
        return resolveGeoIpSuggestionsCommon(
            profile = profile,
            globalCustomFiles = emptyList(),
            fileTags = { fileName -> readTags(snapshot.dataDir, fileName) },
            forShadowrocket = forShadowrocket,
        )
    }

    fun resolveRuleSetSuggestions(profile: TrafficConfigState): List<GeoSuggestionItem> {
        val snapshot = resourceFiles.snapshot(profile)
        return resolveRuleSetSuggestionsCommon(
            profile = profile,
            globalCustomFiles = emptyList(),
            fileTags = { fileName -> readTags(snapshot.dataDir, fileName) },
        )
    }

    private fun readTags(dataDir: File, fileName: String): List<String> {
        return GeoDatParser.parseTags(File(dataDir, fileName))
    }
}
