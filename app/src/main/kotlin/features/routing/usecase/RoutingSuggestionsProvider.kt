// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import android.content.Context
import app.AppState
import features.config.TrafficConfigState
import features.resources.runtime.XrayResourceFileScope
import features.resources.runtime.xrayResourceFilesDir
import java.io.File

/** Android file and profile-scope adapter for the portable routing suggestion rules. */
object RoutingSuggestionsProvider {
    val NetworkOptions get() = RoutingSuggestionPresets.NetworkOptions
    val NetworkOptionsWithAny get() = RoutingSuggestionPresets.NetworkOptionsWithAny
    val ProtocolOptions get() = RoutingSuggestionPresets.ProtocolOptions
    val PortPresets get() = RoutingSuggestionPresets.PortPresets
    val DomainPrefixes get() = RoutingSuggestionPresets.DomainPrefixes
    val PrivateIpPresets get() = RoutingSuggestionPresets.PrivateIpPresets
    val ShadowrocketUserAgentPresets get() = RoutingSuggestionPresets.ShadowrocketUserAgentPresets
    val ShadowrocketNetworkOptions get() = RoutingSuggestionPresets.ShadowrocketNetworkOptions
    val RuleSetPresets get() = RoutingSuggestionPresets.RuleSetPresets
    val DomainSetPresets get() = RoutingSuggestionPresets.DomainSetPresets

    fun resolveRuleSetSuggestions(
        context: Context,
        appState: AppState,
        trafficConfigId: Int? = null,
    ): List<GeoSuggestionItem> {
        val profile = selectProfile(appState, trafficConfigId)
        return features.routing.usecase.resolveRuleSetSuggestions(
            profile = profile,
            globalCustomFiles = appState.customResourceFiles,
            fileTags = fileTagReader(context, profile),
        )
    }

    fun resolveGeoSiteSuggestions(
        context: Context,
        appState: AppState,
        trafficConfigId: Int? = null,
    ): List<GeoSuggestionItem> {
        val profile = selectProfile(appState, trafficConfigId)
        return features.routing.usecase.resolveGeoSiteSuggestions(
            profile = profile,
            globalCustomFiles = appState.customResourceFiles,
            fileTags = fileTagReader(context, profile),
        )
    }

    fun resolveGeoIpSuggestions(
        context: Context,
        appState: AppState,
        trafficConfigId: Int? = null,
        forShadowrocket: Boolean = false,
    ): List<GeoSuggestionItem> {
        val profile = selectProfile(appState, trafficConfigId)
        return features.routing.usecase.resolveGeoIpSuggestions(
            profile = profile,
            globalCustomFiles = appState.customResourceFiles,
            fileTags = fileTagReader(context, profile),
            forShadowrocket = forShadowrocket,
        )
    }

    private fun selectProfile(appState: AppState, trafficConfigId: Int?): TrafficConfigState? =
        selectTrafficConfigForRoutingSuggestions(
            configs = appState.trafficConfigs,
            activeTrafficConfigId = appState.activeTrafficConfigId,
            requestedTrafficConfigId = trafficConfigId,
        )

    private fun fileTagReader(context: Context, profile: TrafficConfigState?): (String) -> List<String> {
        val dataDir = context.xrayResourceFilesDir(profile.resourceFileScopeOrNull())
        return { fileName ->
            val file = File(dataDir, fileName)
            if (file.isFile && file.length() > 0) GeoDatParser.parseTags(file) else emptyList()
        }
    }

    private fun TrafficConfigState?.resourceFileScopeOrNull(): XrayResourceFileScope? =
        this?.let { profile ->
            XrayResourceFileScope(
                trafficConfigId = profile.id,
                resourceFileSource = profile.resourceSettings.source,
            )
        }
}
