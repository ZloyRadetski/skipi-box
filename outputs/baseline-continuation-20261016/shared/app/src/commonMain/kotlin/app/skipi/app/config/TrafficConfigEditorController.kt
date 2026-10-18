// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigState
import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.analyzeShadowrocketConfig

/** Host-owned latest-profile update port for the visual traffic-config editor. */
fun interface TrafficConfigEditorProfileStore {
    /** Applies [transform] to the latest row with [profileId], returning false when it is absent. */
    fun update(
        profileId: Int,
        transform: (TrafficConfigState) -> TrafficConfigState,
    ): Boolean
}

data class TrafficConfigProfileBasicsDraft(
    val name: String,
    val sourceUrl: String,
    val updateLocked: Boolean,
    val autoUpdate: Boolean,
    val updateInterval: String,
    val resourceAutoUpdate: Boolean,
    val resourceUpdateInterval: String,
)

sealed interface TrafficConfigEditorResult {
    data object Saved : TrafficConfigEditorResult
    data object Missing : TrafficConfigEditorResult
    data class Rejected(val message: String) : TrafficConfigEditorResult
    data class Failed(val cause: Exception) : TrafficConfigEditorResult
}

/** Shared, platform-neutral save and save-before-navigation behavior for profile editors. */
class TrafficConfigEditorController(
    private val store: TrafficConfigEditorProfileStore,
    private val onSaveFailure: ((Exception) -> Unit)? = null,
) {
    fun saveBasics(
        profileId: Int,
        draft: TrafficConfigProfileBasicsDraft,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        current.withProfileBasics(
            name = draft.name,
            sourceUrl = draft.sourceUrl,
            updateLocked = draft.updateLocked,
            autoUpdate = draft.autoUpdate,
            updateInterval = draft.updateInterval,
            resourceAutoUpdate = draft.resourceAutoUpdate,
            resourceUpdateInterval = draft.resourceUpdateInterval,
        )
    }

    fun saveGeneral(
        profileId: Int,
        ipv6: Boolean,
        preferIpv6: Boolean,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        current.withGeneralOptions(ipv6 = ipv6, preferIpv6 = preferIpv6)
    }

    fun saveDns(
        profileId: Int,
        settings: TrafficConfigAndroidSettings,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        current.withDnsOptions(settings)
    }

    fun saveRaw(
        profileId: Int,
        content: String,
    ): TrafficConfigEditorResult {
        if (content.isBlank()) return TrafficConfigEditorResult.Rejected("Config cannot be blank")
        val normalized = content.trimEnd() + "\n"
        val diagnostic = normalized.analyzeShadowrocketConfig().diagnostics.firstOrNull {
            it.severity == ShadowrocketConfigDiagnosticSeverity.Error
        }
        if (diagnostic != null) return TrafficConfigEditorResult.Rejected(diagnostic.message)
        return update(profileId) { current -> current.copy(rawConfig = normalized) }
    }

    fun saveBasicsThenNavigate(
        profileId: Int,
        draft: TrafficConfigProfileBasicsDraft,
        onNavigate: () -> Unit,
    ): TrafficConfigEditorResult = saveThenNavigate(onNavigate) {
        saveBasics(profileId, draft)
    }

    /** Skips the host write when the captured profile and its Basics draft are unchanged. */
    fun saveBasicsThenNavigate(
        profile: TrafficConfigState,
        draft: TrafficConfigProfileBasicsDraft,
        onNavigate: () -> Unit,
    ): TrafficConfigEditorResult {
        val edited = profile.withProfileBasics(
            name = draft.name,
            sourceUrl = draft.sourceUrl,
            updateLocked = draft.updateLocked,
            autoUpdate = draft.autoUpdate,
            updateInterval = draft.updateInterval,
            resourceAutoUpdate = draft.resourceAutoUpdate,
            resourceUpdateInterval = draft.resourceUpdateInterval,
        )
        if (edited == profile) {
            onNavigate()
            return TrafficConfigEditorResult.Saved
        }
        return saveBasicsThenNavigate(profile.id, draft, onNavigate)
    }

    fun saveGeneralThenNavigate(
        profileId: Int,
        ipv6: Boolean,
        preferIpv6: Boolean,
        onNavigate: () -> Unit,
    ): TrafficConfigEditorResult = saveThenNavigate(onNavigate) {
        saveGeneral(profileId, ipv6, preferIpv6)
    }

    fun saveDnsThenNavigate(
        profileId: Int,
        settings: TrafficConfigAndroidSettings,
        onNavigate: () -> Unit,
    ): TrafficConfigEditorResult = saveThenNavigate(onNavigate) {
        saveDns(profileId, settings)
    }

    /** Attempts the raw save and leaves after success, validation rejection, or a missing profile. */
    fun leaveRaw(
        profileId: Int,
        content: String,
        onLeave: () -> Unit,
    ): TrafficConfigEditorResult = saveThenNavigate(onLeave) {
        saveRaw(profileId, content)
    }

    private fun update(
        profileId: Int,
        transform: (TrafficConfigState) -> TrafficConfigState,
    ): TrafficConfigEditorResult = try {
        if (store.update(profileId, transform)) TrafficConfigEditorResult.Saved else TrafficConfigEditorResult.Missing
    } catch (failure: Exception) {
        val handler = onSaveFailure ?: throw failure
        handler(failure)
        TrafficConfigEditorResult.Failed(failure)
    }

    private inline fun saveThenNavigate(
        onNavigate: () -> Unit,
        save: () -> TrafficConfigEditorResult,
    ): TrafficConfigEditorResult {
        val result = save()
        if (result !is TrafficConfigEditorResult.Failed) onNavigate()
        return result
    }
}
