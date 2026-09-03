// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.analyzeShadowrocketConfig
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.formatCustomXrayConfigJson
import features.subscription.isValidManualSubscriptionUrl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Pure, desktop-side planning for the ``+`` import flow.
 *
 * The caller supplies text it has already read from the clipboard or a file,
 * receives typed mutations to apply to the local libraries, and owns all UI,
 * file picking and networking.  In particular, a Mihomo `proxy-providers`
 * URL is reported as pending instead of being fetched here.
 */
object DesktopProxyImportPlanner {
    fun plan(
        input: DesktopProxyImportInput,
        existing: DesktopProxyImportExisting = DesktopProxyImportExisting(),
    ): DesktopProxyImportPlan {
        val text = input.text.removePrefix(ImportByteOrderMark)
        if (text.isBlank()) {
            return DesktopProxyImportPlan(
                source = input.source,
                diagnostics = listOf(
                    DesktopProxyImportDiagnostic(
                        severity = DesktopProxyImportDiagnosticSeverity.Error,
                        code = DesktopProxyImportDiagnosticCode.EmptyInput,
                        message = "Нет данных для импорта.",
                    ),
                ),
            )
        }

        val shadowrocket = text.analyzeShadowrocketConfig()
        val looksLikeConfig = shadowrocket.sections.isNotEmpty()
        val requiresConfig = input is DesktopProxyImportInput.File && input.fileName.endsWith(".conf", ignoreCase = true)
        if (looksLikeConfig || requiresConfig) {
            return planShadowrocketConfig(
                input = input,
                text = text,
                existing = existing,
                looksLikeConfig = looksLikeConfig,
            )
        }

        planCustomXrayJson(
            source = input.source,
            text = text,
            existing = existing,
        )?.let { plan -> return plan }

        val imported = DesktopMihomoPayloadImporter.import(text)
        // `importSubscriptionServers()` intentionally chooses either an ordinary
        // line list or one Base64 payload. A mixed paste needs per-line parsing
        // so a subscription URL or a direct link cannot hide an adjacent Base64
        // payload. A wrapped Base64-only payload still goes through the whole
        // text parser above.
        val parsedServers = when {
            imported.recognizedYaml -> imported.servers
            text.needsPerLineProxyImport(imported.servers.isEmpty()) -> text.lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .flatMap { line -> DesktopMihomoPayloadImporter.import(line).servers }
                .toList()

            else -> imported.servers
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
        if (!imported.recognizedYaml) {
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
        val candidate = text.trimStart()
        if (!candidate.startsWith('{') && !candidate.startsWith('[')) return null

        val root = runCatching {
            ProxyServer.json.parseToJsonElement(candidate)
        }.getOrElse {
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
        val configs = when (root) {
            is JsonObject -> listOf(root)
            is JsonArray -> root.mapNotNull { element -> element as? JsonObject }
            else -> return null
        }
        if (configs.isEmpty()) {
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

        var rejectedCount = 0
        val parsedServers = configs.mapIndexedNotNull { index, config ->
            runCatching {
                Custom(
                    remarks = config.customXrayRemarks(index),
                    configJson = formatCustomXrayConfigJson(config),
                ).also { server ->
                    require(server.validateBasic().isEmpty()) { "custom Xray config is invalid" }
                }
            }.onFailure {
                rejectedCount += 1
            }.getOrNull()
        }
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

private fun String.needsPerLineProxyImport(wholeTextProducedNoServers: Boolean): Boolean =
    wholeTextProducedNoServers || lineSequence().any { rawLine ->
        val line = rawLine.trim()
        line.isValidManualSubscriptionUrl() ||
            line.substringBefore("://", missingDelimiterValue = "").lowercase() in ProxyLinkSchemes
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

private fun JsonObject.customXrayRemarks(index: Int): String =
    string("remarks")
        ?: string("remark")
        ?: string("name")
        ?: string("tag")
        ?: "${customXrayTypePrefix()} ${index + 1}"

private fun JsonObject.customXrayTypePrefix(): String {
    val outbounds = this["outbounds"] as? JsonArray
    val objects = outbounds?.mapNotNull { element -> element as? JsonObject }.orEmpty()
    val primary = objects.firstOrNull { outbound -> outbound.string("tag") == "proxy" }
        ?: objects.firstOrNull { outbound ->
            val tag = outbound.string("tag")
            tag != "direct" && tag != "block" && tag != "dns-out" && tag != "fragment"
        }
    val rawProtocol = primary?.string("protocol")?.trim()?.lowercase()
    val protocol = when (rawProtocol) {
        "vless" -> "VLESS"
        "vmess" -> "VMess"
        "trojan" -> "Trojan"
        "hysteria2", "hy2" -> "Hysteria2"
        "shadowsocks", "ss" -> "Shadowsocks"
        "wireguard" -> "WireGuard"
        "socks" -> "SOCKS"
        "http" -> "HTTP"
        null, "" -> null
        else -> rawProtocol.uppercase()
    }
    return if (protocol == null) "JSON" else "JSON ($protocol)"
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

private const val ImportByteOrderMark = "\uFEFF"
private const val DefaultImportedConfigName = "Импортированный конфиг"
private val ProxyLinkSchemes = setOf(
    "http",
    "socks",
    "socks4",
    "socks5",
    "ss",
    "vmess",
    "vless",
    "trojan",
    "hy2",
    "hysteria2",
    "wireguard",
)
