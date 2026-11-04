// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import app.skipi.app.server.ProxyServerEditResult
import features.clipboard.ClipboardImportMode
import features.config.ProxyGroupServerChoice
import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigState
import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.analyzeShadowrocketConfig
import features.config.RoutingTableRow
import features.config.RuleRemarksSeparator
import features.config.ShadowrocketRule
import features.config.UnwritableRowValue
import features.config.V2rayRoutingImport
import features.config.toShadowrocketLine
import features.config.toShadowrocketLineOrNull
import features.config.unwritableValues
import features.config.withDisabled
import features.config.withRoutingRowsImported
import features.config.withShadowrocketRulesReordered
import features.config.withShadowrocketProxyGroupAdded
import features.config.withShadowrocketProxyGroupLine
import features.config.withShadowrocketRuleAdded
import features.config.withShadowrocketRuleLine
import features.config.withoutShadowrocketProxyGroupLine
import features.config.withoutShadowrocketRuleLine
import features.proxy.app.updateProxyAppListSelection
import features.proxy.app.usecase.ProxyAppListClipboardData
import features.proxy.app.usecase.applyProxyAppListClipboardImport
import features.proxy.server.model.StrategyGroup

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

data class TrafficConfigRuleDraft(
    val lineNumber: Int?,
    val isFinal: Boolean,
    val type: String,
    val value: String,
    val policy: String,
)

sealed interface TrafficConfigEditorResult {
    data object Saved : TrafficConfigEditorResult
    data object Missing : TrafficConfigEditorResult
    data object Skipped : TrafficConfigEditorResult
    data class Rejected(val message: String) : TrafficConfigEditorResult
    data class Failed(val cause: Exception) : TrafficConfigEditorResult
}

/** [unwritable] lists the values left out of the rule because a profile line cannot hold them. */
data class TrafficConfigRowSaveResult(
    val result: TrafficConfigEditorResult,
    val unwritable: List<String> = emptyList(),
    /** The line the rule is on after the save, so the table can keep it selected; null when nothing was written. */
    val lineNumber: Int? = null,
)

data class TrafficConfigRoutingImportResult(
    val result: TrafficConfigEditorResult,
    val added: Int,
    val unwritable: List<UnwritableRowValue>,
)

data class TrafficConfigProxyAppListImportResult(
    val updateResult: TrafficConfigEditorResult,
    val importedCount: Int,
)

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

    fun saveTunnel(
        profileId: Int,
        settings: TrafficConfigAndroidSettings,
        muxConcurrency: String,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        current.withTunnelOptions(settings, muxConcurrency)
    }

    fun saveNetworkActivation(
        profileId: Int,
        enabled: Boolean,
        transport: Int,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        current.withNetworkActivation(enabled = enabled, transport = transport)
    }

    fun setProxyAppListMode(
        profileId: Int,
        mode: Int,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        current.copy(proxyAppListMode = mode)
    }

    fun setProxyAppListSelection(
        profileId: Int,
        selectionKeys: List<String>,
        isChecked: Boolean,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        current.copy(
            proxyAppListSelectedApps = updateProxyAppListSelection(
                selectedApps = current.proxyAppListSelectedApps,
                selectionKeys = selectionKeys,
                isChecked = isChecked,
            ),
        )
    }

    fun applyProxyAppListImport(
        profileId: Int,
        imported: ProxyAppListClipboardData,
        importMode: ClipboardImportMode,
    ): TrafficConfigProxyAppListImportResult {
        var importedCount = 0
        val updateResult = update(profileId) { current ->
            val result = applyProxyAppListClipboardImport(
                currentMode = current.proxyAppListMode,
                currentSelectedApps = current.proxyAppListSelectedApps,
                imported = imported,
                mode = importMode,
            )
            importedCount = when (importMode) {
                ClipboardImportMode.Replace -> result.selectedApps.size
                ClipboardImportMode.Merge ->
                    (result.selectedApps.size - current.proxyAppListSelectedApps.size).coerceAtLeast(0)
            }
            current.copy(
                proxyAppListMode = result.mode,
                proxyAppListSelectedApps = result.selectedApps,
            )
        }
        return TrafficConfigProxyAppListImportResult(
            updateResult = updateResult,
            importedCount = if (updateResult == TrafficConfigEditorResult.Saved) importedCount else 0,
        )
    }

    /** Persists the exact document captured by the rules-list action. */
    fun saveRuleDocument(
        profileId: Int,
        document: String,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        current.copy(rawConfig = document)
    }

    /** Removes one profile proxy-group line from the document captured by the list screen. */
    fun deleteProxyGroup(
        profile: TrafficConfigState,
        lineNumber: Int,
    ): TrafficConfigEditorResult {
        val document = profile.rawConfig.withoutShadowrocketProxyGroupLine(lineNumber)
        return update(profile.id) { current -> current.copy(rawConfig = document) }
    }

    /** Applies an Android profile-group editor result to the captured document and latest profile row. */
    fun saveProxyGroupResult(
        profile: TrafficConfigState,
        result: ProxyServerEditResult,
        serverChoices: List<ProxyGroupServerChoice>,
    ): TrafficConfigEditorResult {
        if (result.deleted) {
            val lineNumber = result.serverId?.takeIf { it > 0 }
                ?: return TrafficConfigEditorResult.Skipped
            return deleteProxyGroup(profile, lineNumber)
        }

        val strategy = result.server as? StrategyGroup ?: return TrafficConfigEditorResult.Skipped
        if (strategy.remarks.isBlank()) return TrafficConfigEditorResult.Skipped

        val line = strategy.toShadowrocketLine(serverChoices)
        val document = result.serverId
            ?.takeIf { it > 0 }
            ?.let { lineNumber -> profile.rawConfig.withShadowrocketProxyGroupLine(lineNumber, line) }
            ?: profile.rawConfig.withShadowrocketProxyGroupAdded(line)
        return update(profile.id) { current -> current.copy(rawConfig = document) }
    }

    /** Saves an add/edit draft against the latest same-ID profile's raw document. */
    fun saveRuleDraft(
        profileId: Int,
        draft: TrafficConfigRuleDraft,
    ): TrafficConfigEditorResult {
        if (draft.policy.isBlank() || (!draft.isFinal && draft.value.isBlank())) {
            return TrafficConfigEditorResult.Skipped
        }
        val line = if (draft.isFinal) {
            "FINAL,${draft.policy}"
        } else {
            "${draft.type},${draft.value.trim()},${draft.policy}"
        }
        return update(profileId) { current ->
            // The name of an edited rule is not part of the draft, so it is carried over from the latest line.
            val remarks = draft.lineNumber?.let { lineNumber ->
                current.rawConfig.analyzeShadowrocketConfig().rules.firstOrNull { it.lineNumber == lineNumber }?.remarks
            }.orEmpty()
            val line = if (remarks.isBlank()) line else "$line$RuleRemarksSeparator$remarks"
            val updatedRaw = draft.lineNumber?.let { lineNumber ->
                current.rawConfig.withShadowrocketRuleLine(lineNumber, line)
            } ?: current.rawConfig.withShadowrocketRuleAdded(line)
            current.copy(rawConfig = updatedRaw)
        }
    }

    /** Writes a table row as a rule: replaces the rule on [lineNumber], or adds it before FINAL when that is null. */
    fun saveRoutingRow(
        profileId: Int,
        lineNumber: Int?,
        row: RoutingTableRow,
    ): TrafficConfigRowSaveResult {
        val written = row.toShadowrocketLineOrNull()
            ?: return TrafficConfigRowSaveResult(TrafficConfigEditorResult.Skipped, row.unwritableValues())
        var savedLine: Int? = null
        val result = update(profileId) { current ->
            val document = lineNumber?.let { current.rawConfig.withShadowrocketRuleLine(it, written.line) }
                ?: current.rawConfig.withShadowrocketRuleAdded(written.line)
            savedLine = lineNumber ?: document.analyzeShadowrocketConfig().rules
                .lastOrNull { !it.isFinal && it.raw.trim() == written.line }?.lineNumber
            current.copy(rawConfig = document)
        }
        val saved = result == TrafficConfigEditorResult.Saved
        return TrafficConfigRowSaveResult(result, if (saved) written.unwritable else emptyList(), if (saved) savedLine else null)
    }

    /** Switches a rule on or off without moving it. */
    fun setRoutingRuleEnabled(
        profileId: Int,
        lineNumber: Int,
        enabled: Boolean,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        val rule = current.rawConfig.analyzeShadowrocketConfig().rules.firstOrNull { it.lineNumber == lineNumber }
        if (rule == null || rule.isFinal || rule.isDisabled == !enabled) {
            current
        } else {
            current.copy(rawConfig = current.rawConfig.withShadowrocketRuleLine(lineNumber, rule.withDisabled(disabled = !enabled)))
        }
    }

    fun deleteRoutingRule(
        profileId: Int,
        lineNumber: Int,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        val exists = current.rawConfig.analyzeShadowrocketConfig().rules.any { it.lineNumber == lineNumber && !it.isFinal }
        if (exists) current.copy(rawConfig = current.rawConfig.withoutShadowrocketRuleLine(lineNumber)) else current
    }

    /** Moves a rule among the rules other than FINAL; the indices count those rules only. */
    fun moveRoutingRule(
        profileId: Int,
        fromRuleIndex: Int,
        toRuleIndex: Int,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        current.copy(rawConfig = current.rawConfig.withShadowrocketRulesReordered(fromRuleIndex, toRuleIndex))
    }

    /** Sets where the traffic goes that no rule matched, creating FINAL when the profile has none. */
    fun setRoutingFinalPolicy(
        profileId: Int,
        policy: String,
    ): TrafficConfigEditorResult = update(profileId) { current ->
        val line = "FINAL,${policy.trim()}"
        val finalRule = current.rawConfig.analyzeShadowrocketConfig().rules.lastOrNull(ShadowrocketRule::isFinal)
        current.copy(
            rawConfig = finalRule?.let { current.rawConfig.withShadowrocketRuleLine(it.lineNumber, line) }
                ?: current.rawConfig.withShadowrocketRuleAdded(line),
        )
    }

    /** Writes the rules of a v2rayN export into the profile, merging with its rules or replacing them. */
    fun importRoutingRows(
        profileId: Int,
        import: V2rayRoutingImport,
        mode: ClipboardImportMode,
    ): TrafficConfigRoutingImportResult {
        var added = 0
        var unwritable = emptyList<UnwritableRowValue>()
        val result = update(profileId) { current ->
            val imported = current.rawConfig.withRoutingRowsImported(import.rows, import.finalPolicy, mode)
            added = imported.added
            unwritable = imported.unwritable
            current.copy(rawConfig = imported.document)
        }
        val saved = result == TrafficConfigEditorResult.Saved
        return TrafficConfigRoutingImportResult(result, if (saved) added else 0, if (saved) unwritable else emptyList())
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

    fun saveTunnelThenNavigate(
        profileId: Int,
        settings: TrafficConfigAndroidSettings,
        muxConcurrency: String,
        onNavigate: () -> Unit,
    ): TrafficConfigEditorResult = saveThenNavigate(onNavigate) {
        saveTunnel(profileId, settings, muxConcurrency)
    }

    fun saveNetworkActivationThenNavigate(
        profileId: Int,
        enabled: Boolean,
        transport: Int,
        onNavigate: () -> Unit,
    ): TrafficConfigEditorResult = saveThenNavigate(onNavigate) {
        saveNetworkActivation(profileId, enabled, transport)
    }

    fun saveRuleDraftThenNavigate(
        profileId: Int,
        draft: TrafficConfigRuleDraft,
        onNavigate: () -> Unit,
    ): TrafficConfigEditorResult = saveThenNavigate(onNavigate) {
        saveRuleDraft(profileId, draft)
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
