# R01 routing resource validation extraction

> Historical tests-first/source contract. R01 was subsequently completed and verified; see the [final acceptance](../../tasks/shared-migration-20261023/r01-acceptance-verification.md) and [resource verification](../../tasks/shared-migration-20261023/r01-routing-resources-verification.md). Statements about pending work below describe the pre-implementation checkpoint.

Status: tests/spec first. The Android baseline oracle below is active and must pass before the proposed shared API RED. The common test files remain `.kt.draft` until that baseline is recorded. No production code is part of this step.

## Goal and boundary

Make the global route-rule validator portable so generated Desktop routing can use the same GeoIP/GeoSite and `ext:` resource checks as Android once the Desktop runtime resource directory is available. Keep the existing no-resource compatibility behavior for preview/export paths that do not have a resource directory. Extract only validation and preflight behavior; do not add routing UI, alter route records, or change how profile routing works.

The Android source of truth is `app/src/main/kotlin/engine/xray/XrayGeoRuleSanitizer.kt` plus `app/src/main/kotlin/engine/xray/XrayExternalRoutingResources.kt`. Android passes `resourceFilePaths.dataDir` from `app/src/main/kotlin/engine/vpn/VpnXrayConfig.kt`. Its file-backed tag lookup uses the existing `XrayGeoRuleValidator` and `GeoDatParser`; the protobuf parser is already shared and must not be reimplemented.

## Proposed shared API

Add `engine.xray.XrayResourceRoutingRuleValidator` in shared core with this public shape:

```kotlin
class XrayResourceRoutingRuleValidator(
    hasResourceTag: ((fileName: String, tag: String, originalRule: String) -> Boolean)?,
    onInvalidIpRule: (String) -> Unit = {},
) : XrayRoutingRuleValidator
```

`hasResourceTag == null` means the host has no usable resource directory (`dataDir.isNullOrBlank()` on Android). In that mode domain validation remains permissive, IP validation retains the existing distinction (blank and malformed plain IP/CIDR are invalid; `geoip:` and `ext:` are permissive), and both `filterValid*Rules` methods return their input list unchanged. An invalid raw IP/CIDR calls `onInvalidIpRule` with its trimmed value only when `isIpRuleValid` is evaluated; filtering in no-resource mode must not validate or report list entries.

With a non-null lookup, use `XrayGeoRuleValidator.isDomainRuleValid` and `isIpRuleValid` directly. Pass the trimmed original rule to the lookup. This retains existing syntax handling, standard file names, `.dat` extension normalization, case-insensitive prefixes/tags, and the `geoip:private` exemption. Filter methods retain only values accepted by the corresponding validator. A blank IP returns `false` before resource lookup or diagnostics. Invalid `geoip:` / `ext:` references do not call `onInvalidIpRule`; that callback is reserved for invalid raw IP/CIDR values, matching Android’s log rule.

Add `engine.xray.validateXrayExternalDomainResources(routeRules, hasNonEmptyFile)` as a shared pure preflight. It must preserve Android’s exact order and messages:

1. Keep enabled rules only, flatten `domain` values only, trim, select `ext:` candidates case-insensitively, and exact-string `distinct` while retaining encounter order.
2. Parse every candidate with `isValidXrayExternalDomainRule`. If malformed candidates exist, throw `IllegalStateException("Invalid external routing domain rule: " + candidates.joinToString())` before invoking `hasNonEmptyFile`.
3. For valid candidates, extract the filename exactly as `toXrayExternalDomainRuleOrNull()` returns it, exact-string `distinct` in encounter order, and call `hasNonEmptyFile` for each. Do not append `.dat` here. If files are unavailable or empty, throw `IllegalStateException("Missing external routing resource file: " + names.joinToString())`.

Preserve the existing suffix distinction: `XrayGeoRuleValidator` normalizes `ext:custom:tag` to `custom.dat` for tag lookup, while the Android external preflight checks the literal file name `custom`. Characterize both paths; do not reconcile them as part of this extraction.

The Android adapter should pass `File(dataDir, fileName).let { it.isFile && it.length() > 0 }`. It should preserve the current Android logger text for invalid raw IP/CIDR values and the current GeoDatParser-backed tag lookup. The shared preflight callback represents Android’s current file existence/non-empty check; it must not parse tags or inspect `ip` fields.

## Tests-first order

1. Run the new active Android baseline oracle in isolation. It exercises Android’s existing sanitizer and `AppState.validateXrayExternalRoutingResources` with temporary `.dat` fixtures.
2. After the baseline is green, install the two common `.kt.draft` files as active common tests and run the shared API RED before production changes.
3. Implement the common validator and preflight, then make the Android files thin adapters. Compare the Android oracle after delegation to preserve behavior.
4. Use the resource-aware validator in the Desktop generated no-profile runtime path only when that start path has a non-null runtime resource directory. Preview/export and all other no-directory callers keep null compatibility mode until their resources are actually available.

## Coverage matrix

The Android oracle pins real file behavior: valid/missing/empty files; case-insensitive tag lookup; standard, custom `ext:`, and plain rules; invalid raw IP filtering; no-directory list retention and IP validation; valid external preflight; malformed-before-missing error precedence; missing/empty file messages; deduplication; disabled/IP-only external references being ignored; and the distinct `.dat` normalization behavior between tag lookup and external preflight.

The common drafts pin the portable contract: null-lookup identity-preserving filters and callback behavior; shared resource tag parsing/filtering using callback arguments; and the preflight’s exact validation order, literal filename behavior, distinct-file order, disabled-rule behavior, and error messages.

## Non-goals

Do not redesign `XrayGeoRuleValidator`, `GeoDatTagParser`, or Android’s logger. Do not make Desktop silently fall back to no-resource mode after a resource-aware lookup failure. Do not add UI or change `RoutingConfigRecord`, default routing, profile/custom precedence, or runtime asset ownership.
