# R01 global routing: resource-aware Desktop validation

> Historical tests-first/source contract. R01 was subsequently completed and verified; see the [final acceptance](../../tasks/shared-migration-20261023/r01-acceptance-verification.md) and [resource verification](../../tasks/shared-migration-20261023/r01-routing-resources-verification.md). Statements about pending work below describe the pre-implementation checkpoint.

## Source behavior

Android validates enabled global `ext:` domain criteria before building a generated configuration. It first reports malformed rules with the exact message `Invalid external routing domain rule: ...`, then reports absent or empty referenced files with `Missing external routing resource file: ...` ([XrayExternalRoutingResources.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/engine/xray/XrayExternalRoutingResources.kt)). Disabled rules do not participate in either check.

Android's resource-aware sanitizer then checks GeoIP/GeoSite tags against the selected `.dat` files. It removes missing geo criteria while preserving valid literals; if a rule has no remaining conditions, the shared routing planner omits the rule. The filesystem adapter is [XrayGeoRuleSanitizer.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/engine/xray/XrayGeoRuleSanitizer.kt), the portable tag checks are [XrayGeoRuleValidator.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/engine/xray/XrayGeoRuleValidator.kt), and the JVM fixture parser is [GeoDatParser.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/jvmMain/kotlin/features/routing/usecase/GeoDatParser.kt).

## Expected Desktop behavior

For the generated no-profile branch, the start-plan factory accepts an optional `globalAssetsDirectory: Path? = null`. It uses that path both for generated-routing validation and as `DesktopTunnelStartPlan.assetsDirectory` when there is no active profile. The runtime caller supplies `DesktopCoreRuntimes.discover().getOrThrow().directory`; Core starts with the selected profile path when one exists, otherwise with that captured runtime directory. The plan's rebuild/retry closure retains the same captured path.

The selected-server config entry point/factory accepts an optional `routingDataDirectory: Path? = null` and applies filesystem checks only in its generated branch. A profile continues to own its config and profile asset path. A selected raw `Custom` entry continues to take precedence over both profile and global routing.

## Draft test inventory

`DesktopGlobalRoutingResourceValidationTest.kt.draft` covers actual `.dat` tag parsing, present and missing geo tags, literal-condition retention, dropping a geo-only rule, exact malformed/missing `ext:` errors, disabled ext-rule omission, no-profile global asset fallback, profile/custom precedence, and retry path capture. `DesktopGlobalRoutingCoreResourcesIntegrationTest.kt.draft` is opt-in through `SKIPI_CORE_INTEGRATION_DIR`; it uses the runtime's own assets, a missing `geosite:block` criterion targeting `block`, and loopback SOCKS/HTTP fixtures to verify that the default proxy still carries the request.

These are test drafts only. No production API or behavior is changed in this slice, no existing test is modified, and no Gradle/Core test has been run. The intended order is to materialize and run the drafts RED before implementation.
