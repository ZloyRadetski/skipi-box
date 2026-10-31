// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.analyzeShadowrocketConfig
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerProviderUrlFetcher
import features.proxy.server.usecase.importer.CustomXrayConfigImportResult
import features.proxy.server.usecase.importer.WireguardConfParseResult
import features.proxy.server.usecase.importer.parseCustomXrayConfigPayload
import features.proxy.server.usecase.importer.parseWireguardConf
import features.subscription.isValidManualSubscriptionUrl
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Pure, desktop-side planning for the ``+`` import flow.
 *
 * The caller supplies text it has already read from the clipboard or a file,
 * receives typed mutations to apply to the local libraries, and owns the UI,
 * file picking and network callback. `plan` reports unavailable Mihomo
 * providers as pending; `planWithProviderFetcher` resolves them through the
 * caller-supplied callback.
 */
object DesktopProxyImportPlanner {
    fun plan(
        input: DesktopProxyImportInput,
        existing: DesktopProxyImportExisting = DesktopProxyImportExisting(),
    ): DesktopProxyImportPlan {
        val text = input.text.removePrefix(ImportByteOrderMark)
        emptyInputPlan(input, text)?.let { plan -> return plan }

        val imported = DesktopMihomoPayloadImporter.import(
            text = text,
            source = input.source.proxyServerImportSource(),
        )
        return planImported(input, existing, text, imported)
    }

    /** Plans a manual import while resolving Mihomo providers through the caller's network boundary. */
    suspend fun planWithProviderFetcher(
        input: DesktopProxyImportInput,
        existing: DesktopProxyImportExisting = DesktopProxyImportExisting(),
        providerUrlFetcher: ProxyServerProviderUrlFetcher,
    ): DesktopProxyImportPlan {
        val text = input.text.removePrefix(ImportByteOrderMark)
        emptyInputPlan(input, text)?.let { plan -> return plan }

        val imported = DesktopMihomoPayloadImporter.importWithProviderFetcher(
            text = text,
            source = input.source.proxyServerImportSource(),
            providerUrlFetcher = providerUrlFetcher,
        )
        currentCoroutineContext().ensureActive()
        return planImported(input, existing, text, imported)
    }

    private fun emptyInputPlan(
        input: DesktopProxyImportInput,
        text: String,
    ): DesktopProxyImportPlan? = if (text.isBlank()) {
        DesktopProxyImportPlan(
            source = input.source,
            diagnostics = listOf(
                DesktopProxyImportDiagnostic(
                    severity = DesktopProxyImportDiagnosticSeverity.Error,
                    code = DesktopProxyImportDiagnosticCode.EmptyInput,
                    message = "Нет данных для импорта.",
                ),
            ),
        )
    } else {
        null
    }

    private fun planImported(
        input: DesktopProxyImportInput,
        existing: DesktopProxyImportExisting,
        text: String,
        imported: DesktopMihomoPayloadImportResult,
    ): DesktopProxyImportPlan {
        val parsedServers = imported.servers
        val wireguard = parseWireguardConf(text)
        val shadowrocket = text.analyzeShadowrocketConfig()
        val hasConfigSections = shadowrocket.sections.isNotEmpty()
        val invalidWireguardOnlyDocument =
            wireguard is WireguardConfParseResult.Invalid &&
                shadowrocket.sections.keys.all { section -> section == "interface" || section == "peer" }
        val looksLikeShadowrocketProfile = hasConfigSections && !invalidWireguardOnlyDocument
        val recognizedCustomJson = looksLikeShadowrocketProfile &&
            when (parseCustomXrayConfigPayload(text)) {
                is CustomXrayConfigImportResult.Imported,
                CustomXrayConfigImportResult.NoConfigObjects -> true

                CustomXrayConfigImportResult.InvalidJson,
                CustomXrayConfigImportResult.NotJson -> false
            }
        val sharedStructuredPayloadOwnsRoute =
            imported.recognizedYaml ||
                recognizedCustomJson ||
                (wireguard is WireguardConfParseResult.Imported &&
                    !imported.recognizedYaml && imported.servers.size == 1)

        // Recognized Shadowrocket documents are profile imports even when the
        // shared URL parser finds a proxy link in a comment or section. The
        // common structured routes remain authoritative for JSON, YAML, and
        // standalone WireGuard payloads.
        if (looksLikeShadowrocketProfile && !sharedStructuredPayloadOwnsRoute) {
            return planShadowrocketConfig(
                input = input,
                text = text,
                existing = existing,
                looksLikeConfig = true,
            )
        }

        if (imported.servers.isEmpty()) {
            if (
                imported.proxyEntryCount == 0 &&
                !imported.recognizedYaml &&
                imported.pendingProviders.isEmpty() &&
                wireguard is WireguardConfParseResult.Invalid &&
                !looksLikeShadowrocketProfile
            ) {
                return DesktopProxyImportPlan(
                    source = input.source,
                    diagnostics = listOf(
                        DesktopProxyImportDiagnostic(
                            severity = DesktopProxyImportDiagnosticSeverity.Error,
                            code = DesktopProxyImportDiagnosticCode.RejectedProxy,
                            message = "Invalid WireGuard / AmneziaWG configuration.",
                        ),
                    ),
                )
            }

            val requiresConfig = input is DesktopProxyImportInput.File &&
                input.fileName.endsWith(".conf", ignoreCase = true)
            if (requiresConfig) {
                return planShadowrocketConfig(
                    input = input,
                    text = text,
                    existing = existing,
                    looksLikeConfig = looksLikeShadowrocketProfile,
                )
            }

            planCustomXrayJson(
                source = input.source,
                text = text,
                existing = existing,
            )?.let { plan -> return plan }
        }
        val diagnostics = imported.diagnostics.map { message ->
            DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Warning,
                code = DesktopProxyImportDiagnosticCode.RejectedProxy,
                message = message,
            )
        }.toMutableList()
        if (imported.rejectedProxyCount > 0) {
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Warning,
                code = DesktopProxyImportDiagnosticCode.RejectedProxy,
                message = "Не удалось разобрать серверов: ${imported.rejectedProxyCount}.",
            )
        }
        if (imported.pendingProviders.isNotEmpty()) {
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Info,
                code = DesktopProxyImportDiagnosticCode.PendingMihomoProvider,
                message = "Найдены внешние Mihomo-провайдеры (${imported.pendingProviders.size}); они не загружались автоматически.",
            )
        }

        val actions = mutableListOf<DesktopProxyImportAction>()
        val deduplicatedServers = parsedServers.withoutKnownServers(
            existing.serverFingerprints,
            existing.serverDuplicatePolicy,
        )
        if (deduplicatedServers.values.isNotEmpty()) {
            actions += DesktopProxyImportAction.AddServers(deduplicatedServers.values)
        }
        deduplicatedServers.duplicateCount.takeIf { it > 0 }?.let { duplicates ->
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Info,
                code = DesktopProxyImportDiagnosticCode.DuplicateServers,
                message = "Повторяющиеся серверы пропущены: $duplicates.",
            )
        }

        // A recognized YAML document may legitimately contain HTTP(S) provider URLs.
        // They are not subscription URLs selected by the user, so only scan ordinary
        // text/base64 payloads for manual subscriptions.
        if (
            !imported.recognizedYaml &&
            imported.servers.none { server -> server is Custom } &&
            !text.isJsonImportPayload()
        ) {
            val subscriptions = text.manualSubscriptionUrls()
                .withoutKnownSubscriptions(
                    existing.subscriptionUrls,
                    existing.subscriptionDuplicatePolicy,
                )
            subscriptions.values.forEach { url ->
                actions += DesktopProxyImportAction.AddSubscription(url)
            }
            subscriptions.duplicateCount.takeIf { it > 0 }?.let { duplicates ->
                diagnostics += DesktopProxyImportDiagnostic(
                    severity = DesktopProxyImportDiagnosticSeverity.Info,
                    code = DesktopProxyImportDiagnosticCode.DuplicateSubscriptions,
                    message = "Повторяющиеся подписки пропущены: $duplicates.",
                )
            }
        }

        if (
            actions.isEmpty() &&
            diagnostics.none { diagnostic ->
                diagnostic.severity == DesktopProxyImportDiagnosticSeverity.Error ||
                    diagnostic.code == DesktopProxyImportDiagnosticCode.PendingMihomoProvider
            }
        ) {
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Error,
                code = DesktopProxyImportDiagnosticCode.UnsupportedFormat,
                message = "Не найдены ссылки серверов, подписки или совместимый конфиг.",
            )
        }
        return DesktopProxyImportPlan(
            source = input.source,
            actions = actions,
            diagnostics = diagnostics.distinct(),
        )
    }

    private fun DesktopProxyImportSource.proxyServerImportSource(): ProxyServerImportSource = when (this) {
        DesktopProxyImportSource.Text,
        DesktopProxyImportSource.Clipboard -> ProxyServerImportSource.Clipboard

        DesktopProxyImportSource.File -> ProxyServerImportSource.File
    }

    /** Convenience entry point for a pasted text field. */
    fun planText(
        text: String,
        existing: DesktopProxyImportExisting = DesktopProxyImportExisting(),
    ): DesktopProxyImportPlan = plan(DesktopProxyImportInput.Text(text), existing)

    /** Convenience entry point for the system clipboard. */
    fun planClipboard(
        text: String,
        existing: DesktopProxyImportExisting = DesktopProxyImportExisting(),
    ): DesktopProxyImportPlan = plan(DesktopProxyImportInput.Clipboard(text), existing)

    /**
     * Plans an already-read file.  Reading the file stays with the desktop UI,
     * so this method remains deterministic and safe to invoke in tests.
     */
    fun planFile(
        fileName: String,
        text: String,
        existing: DesktopProxyImportExisting = DesktopProxyImportExisting(),
    ): DesktopProxyImportPlan = plan(DesktopProxyImportInput.File(fileName, text), existing)

    private fun planShadowrocketConfig(
        input: DesktopProxyImportInput,
        text: String,
        existing: DesktopProxyImportExisting,
        looksLikeConfig: Boolean,
    ): DesktopProxyImportPlan {
        val analysis = text.analyzeShadowrocketConfig()
        val diagnostics = mutableListOf<DesktopProxyImportDiagnostic>()
        if (!looksLikeConfig) {
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Error,
                code = DesktopProxyImportDiagnosticCode.InvalidConfig,
                message = "Файл .conf не содержит секций Shadowrocket.",
            )
        }
        analysis.diagnostics.forEach { diagnostic ->
            diagnostics += DesktopProxyImportDiagnostic(
                severity = if (diagnostic.severity == ShadowrocketConfigDiagnosticSeverity.Error) {
                    DesktopProxyImportDiagnosticSeverity.Error
                } else {
                    DesktopProxyImportDiagnosticSeverity.Warning
                },
                code = DesktopProxyImportDiagnosticCode.InvalidConfig,
                message = diagnostic.message,
                lineNumber = diagnostic.lineNumber,
            )
        }
        if (analysis.unsupportedSections.isNotEmpty()) {
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Warning,
                code = DesktopProxyImportDiagnosticCode.UnsupportedConfigSection,
                message = "Некоторые разделы будут сохранены как текст: ${analysis.unsupportedSections.joinToString(", ")}.",
            )
        }
        if (diagnostics.any { it.severity == DesktopProxyImportDiagnosticSeverity.Error }) {
            return DesktopProxyImportPlan(source = input.source, diagnostics = diagnostics.distinct())
        }

        val key = text.configDeduplicationKey()
        if (existing.configContents.any { content -> content.configDeduplicationKey() == key }) {
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Info,
                code = DesktopProxyImportDiagnosticCode.DuplicateConfigs,
                message = "Такой конфиг уже есть в библиотеке.",
            )
            return DesktopProxyImportPlan(source = input.source, diagnostics = diagnostics.distinct())
        }

        val metadata = text.readDesktopSkipiProfileMetadata()
        val fileName = (input as? DesktopProxyImportInput.File)
            ?.fileName
            ?.removeSuffixIgnoreCase(".conf")
            ?.trim()
            .orEmpty()
        val suggestedName = metadata.name
            ?: analysis.general["name"]?.trim()?.takeIf(String::isNotBlank)
            ?: fileName.takeIf(String::isNotBlank)
            ?: DefaultImportedConfigName
        return DesktopProxyImportPlan(
            source = input.source,
            actions = listOf(
                DesktopProxyImportAction.AddConfig(
                    name = suggestedName,
                    content = text.ensureTrailingLineFeed(),
                    sourceUrl = metadata.sourceUrl.orEmpty(),
                    updateLocked = metadata.updateLocked ?: false,
                ),
            ),
            diagnostics = diagnostics.distinct(),
        )
    }

    /**
     * Mirrors Android's JSON import path using only the shared proxy model.
     *
     * A JSON object is one complete Custom Xray server.  An array accepts the
     * same collection form as Android and ignores non-object array entries,
     * which lets a provider append harmless metadata without turning a valid
     * server collection into a failed import.
     */
    private fun planCustomXrayJson(
        source: DesktopProxyImportSource,
        text: String,
        existing: DesktopProxyImportExisting,
    ): DesktopProxyImportPlan? {
        val imported = when (val parsed = parseCustomXrayConfigPayload(text)) {
            CustomXrayConfigImportResult.NotJson -> return null
            CustomXrayConfigImportResult.InvalidJson -> {
                return DesktopProxyImportPlan(
                    source = source,
                    diagnostics = listOf(
                        DesktopProxyImportDiagnostic(
                            severity = DesktopProxyImportDiagnosticSeverity.Error,
                            code = DesktopProxyImportDiagnosticCode.InvalidJson,
                            message = "Некорректный JSON-конфиг Xray.",
                        ),
                    ),
                )
            }

            CustomXrayConfigImportResult.NoConfigObjects -> {
                return DesktopProxyImportPlan(
                    source = source,
                    diagnostics = listOf(
                        DesktopProxyImportDiagnostic(
                            severity = DesktopProxyImportDiagnosticSeverity.Error,
                            code = DesktopProxyImportDiagnosticCode.InvalidConfig,
                            message = "JSON-массив не содержит объектов конфигурации Xray.",
                        ),
                    ),
                )
            }

            is CustomXrayConfigImportResult.Imported -> parsed
        }
        val rejectedCount = imported.rejectedConfigCount
        val parsedServers = imported.servers
        val diagnostics = mutableListOf<DesktopProxyImportDiagnostic>()
        if (rejectedCount > 0) {
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Warning,
                code = DesktopProxyImportDiagnosticCode.RejectedProxy,
                message = "Не удалось разобрать JSON-конфигов Xray: $rejectedCount.",
            )
        }
        if (parsedServers.isEmpty()) {
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Error,
                code = DesktopProxyImportDiagnosticCode.InvalidConfig,
                message = "JSON не содержит корректных конфигураций Xray.",
            )
            return DesktopProxyImportPlan(source = source, diagnostics = diagnostics)
        }

        val deduplicatedServers = parsedServers.withoutKnownServers(
            existing.serverFingerprints,
            existing.serverDuplicatePolicy,
        )
        val actions = deduplicatedServers.values.takeIf { servers -> servers.isNotEmpty() }
            ?.let { servers -> listOf(DesktopProxyImportAction.AddServers(servers)) }
            .orEmpty()
        deduplicatedServers.duplicateCount.takeIf { it > 0 }?.let { duplicates ->
            diagnostics += DesktopProxyImportDiagnostic(
                severity = DesktopProxyImportDiagnosticSeverity.Info,
                code = DesktopProxyImportDiagnosticCode.DuplicateServers,
                message = "Повторяющиеся серверы пропущены: $duplicates.",
            )
        }
        return DesktopProxyImportPlan(
            source = source,
            actions = actions,
            diagnostics = diagnostics,
        )
    }
}

/** Input supplied by the desktop text field, clipboard adapter or file picker. */
sealed interface DesktopProxyImportInput {
    val text: String
    val source: DesktopProxyImportSource

    data class Text(override val text: String) : DesktopProxyImportInput {
        override val source: DesktopProxyImportSource = DesktopProxyImportSource.Text
    }

    data class Clipboard(override val text: String) : DesktopProxyImportInput {
        override val source: DesktopProxyImportSource = DesktopProxyImportSource.Clipboard
    }

    data class File(
        val fileName: String,
        override val text: String,
    ) : DesktopProxyImportInput {
        override val source: DesktopProxyImportSource = DesktopProxyImportSource.File
    }
}

enum class DesktopProxyImportSource {
    Text,
    Clipboard,
    File,
}

/** Existing library fingerprints supplied by the integration layer for non-destructive import. */
data class DesktopProxyImportExisting(
    val serverFingerprints: Set<String> = emptySet(),
    val subscriptionUrls: Set<String> = emptySet(),
    val configContents: Set<String> = emptySet(),
    val serverDuplicatePolicy: DesktopProxyImportDuplicatePolicy =
        DesktopProxyImportDuplicatePolicy.SkipExistingAndRepeated,
    val subscriptionDuplicatePolicy: DesktopProxyImportDuplicatePolicy =
        DesktopProxyImportDuplicatePolicy.SkipExistingAndRepeated,
)

/**
 * Android keeps manually pasted proxy links, even if they repeat an existing
 * link. Subscription URLs are different: a repeated URL updates the same
 * provider record, so callers can retain existing URLs while folding repeats
 * from one clipboard payload.
 */
enum class DesktopProxyImportDuplicatePolicy {
    SkipExistingAndRepeated,
    KeepExistingAndRepeated,
    KeepExistingDeduplicateRepeated,
}

data class DesktopProxyImportPlan(
    val source: DesktopProxyImportSource,
    val actions: List<DesktopProxyImportAction> = emptyList(),
    val diagnostics: List<DesktopProxyImportDiagnostic> = emptyList(),
) {
    val servers: List<ProxyServer<*>>
        get() = actions.filterIsInstance<DesktopProxyImportAction.AddServers>()
            .flatMap(DesktopProxyImportAction.AddServers::servers)

    val subscriptions: List<String>
        get() = actions.filterIsInstance<DesktopProxyImportAction.AddSubscription>()
            .map(DesktopProxyImportAction.AddSubscription::url)

    val configs: List<DesktopProxyImportAction.AddConfig>
        get() = actions.filterIsInstance<DesktopProxyImportAction.AddConfig>()
}

/** Explicit local mutations which the `+` UI can show, confirm and then apply. */
sealed interface DesktopProxyImportAction {
    data class AddServers(val servers: List<ProxyServer<*>>) : DesktopProxyImportAction
    data class AddSubscription(val url: String) : DesktopProxyImportAction
    data class AddConfig(
        val name: String,
        val content: String,
        val sourceUrl: String,
        val updateLocked: Boolean,
    ) : DesktopProxyImportAction
}

data class DesktopProxyImportDiagnostic(
    val severity: DesktopProxyImportDiagnosticSeverity,
    val code: DesktopProxyImportDiagnosticCode,
    val message: String,
    val lineNumber: Int? = null,
)

enum class DesktopProxyImportDiagnosticSeverity {
    Info,
    Warning,
    Error,
}

enum class DesktopProxyImportDiagnosticCode {
    EmptyInput,
    UnsupportedFormat,
    InvalidJson,
    InvalidConfig,
    UnsupportedConfigSection,
    RejectedProxy,
    PendingMihomoProvider,
    DuplicateServers,
    DuplicateSubscriptions,
    DuplicateConfigs,
}

private data class DesktopImportDeduplication<T>(
    val values: List<T>,
    val duplicateCount: Int,
)

private fun List<ProxyServer<*>>.withoutKnownServers(
    existingFingerprints: Set<String>,
    policy: DesktopProxyImportDuplicatePolicy = DesktopProxyImportDuplicatePolicy.SkipExistingAndRepeated,
): DesktopImportDeduplication<ProxyServer<*>> {
    if (policy == DesktopProxyImportDuplicatePolicy.KeepExistingAndRepeated) {
        return DesktopImportDeduplication(values = this, duplicateCount = 0)
    }
    val known = when (policy) {
        DesktopProxyImportDuplicatePolicy.SkipExistingAndRepeated -> existingFingerprints.toMutableSet()
        DesktopProxyImportDuplicatePolicy.KeepExistingDeduplicateRepeated -> mutableSetOf()
        DesktopProxyImportDuplicatePolicy.KeepExistingAndRepeated -> error("Handled above")
    }
    val values = mutableListOf<ProxyServer<*>>()
    var duplicateCount = 0
    forEach { server ->
        if (known.add(server.connectionFingerprint())) values += server else duplicateCount += 1
    }
    return DesktopImportDeduplication(values, duplicateCount)
}

private fun String.manualSubscriptionUrls(): List<String> = lineSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .filter(String::isValidManualSubscriptionUrl)
    // `http://host:port` can be a manual HTTP proxy, which the shared parser
    // recognizes as a valid server.  Prefer the explicit proxy interpretation.
    .filterNot(::isValidProxyServerLink)
    .toList()

private fun String.isJsonImportPayload(): Boolean {
    val candidate = trimStart('\uFEFF', ' ', '\t', '\r', '\n')
    return candidate.startsWith('{') || candidate.startsWith('[')
}

private fun List<String>.withoutKnownSubscriptions(
    existingUrls: Set<String>,
    policy: DesktopProxyImportDuplicatePolicy = DesktopProxyImportDuplicatePolicy.SkipExistingAndRepeated,
): DesktopImportDeduplication<String> {
    if (policy == DesktopProxyImportDuplicatePolicy.KeepExistingAndRepeated) {
        return DesktopImportDeduplication(values = this, duplicateCount = 0)
    }
    val known = when (policy) {
        DesktopProxyImportDuplicatePolicy.SkipExistingAndRepeated ->
            existingUrls.mapTo(mutableSetOf(), String::subscriptionDeduplicationKey)
        DesktopProxyImportDuplicatePolicy.KeepExistingDeduplicateRepeated -> mutableSetOf()
        DesktopProxyImportDuplicatePolicy.KeepExistingAndRepeated -> error("Handled above")
    }
    val values = mutableListOf<String>()
    var duplicateCount = 0
    forEach { url ->
        if (known.add(url.subscriptionDeduplicationKey())) values += url else duplicateCount += 1
    }
    return DesktopImportDeduplication(values, duplicateCount)
}

private fun isValidProxyServerLink(value: String): Boolean = runCatching {
    ProxyServer.parse(value).validateFull().isEmpty()
}.getOrDefault(false)

private fun String.subscriptionDeduplicationKey(): String {
    val trimmed = trim()
    val schemeEnd = trimmed.indexOf("://")
    return if (schemeEnd > 0) trimmed.substring(0, schemeEnd).lowercase() + trimmed.substring(schemeEnd) else trimmed
}

private fun String.configDeduplicationKey(): String = removePrefix(ImportByteOrderMark)
    .replace("\r\n", "\n")
    .trimEnd()

private fun String.ensureTrailingLineFeed(): String = configDeduplicationKey().trimEnd() + "\n"

private fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

private const val ImportByteOrderMark = "\uFEFF"
private const val DefaultImportedConfigName = "Импортированный конфиг"
