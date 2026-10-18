# X01 portable route matching

X01 reuses Android's `ShadowrocketRule.toXrayRouteRule` for Desktop route match fields. Desktop policy targets, FINAL output, and resource availability remain host-owned. This verifies converter parity, not complete Xray runtime parity.

## Test-first evidence

- Before production changes, the root ran `outputs/shared-migration-20261016-editor-rules-tests-before-production.log`: the frozen Android fixtures passed, while Desktop parity and URL-rule omission with an unknown policy failed (6 selected core tests, 2 failures).
- After wiring the canonical converter, the root ran `outputs/shared-migration-20261016-controller-green-resource-red.log`: the six X01 tests passed, including valid CIDR `RULE-SET`, URL omission before policy resolution, raw `DOMAIN-SET`/IP-ASN values, ext rules, wildcard escaping, and invalid CIDR omission. The added `domainRuleWithExtLookingValueIsNotAnExternalResourceReference` test failed as intended: `DOMAIN,ext:custom.dat:blocked` becomes `full:ext:custom.dat:blocked`, but validation incorrectly inspected the raw source value.
- The validator now checks only `ext:` references emitted in the canonical `RouteRule.domain` and `RouteRule.ip` fields. It does not normalize or classify raw rule values. Desktop protocol serialization remains a one-element JSON array, matching the existing output shape.
- The root then ran `:shared:core:allTests` successfully (`outputs/shared-migration-20261016-x01-green.log`). XML reports show Desktop `desktopTest`: 244 tests / 79 suites, and Android `testAndroidHostTest`: 243 tests / 78 suites; both have 0 failures, errors, or skipped tests. The X01 oracle and planner classes account for 3 + 4 passing tests on each target, including the fixed DOMAIN `ext:` regression.
- Desktop integrated runtime verification is still pending; the shared/core unit suites do not prove an FFM/Core runtime launch. Do not mark the wider profile-routing work complete until that host gate passes.

## Remaining boundaries

- `supportsIpAsnRules = false` remains a Desktop capability policy. Current code establishes that Desktop packages `geoip.dat` and `geosite.dat`, but does not establish an IP-ASN-specific native Core limitation.
- Android runtime sanitization against actual GeoIP/GeoSite tags, Desktop resource failures, target-policy behavior, and FINAL output remain outside this match-converter slice (X02/X03).

## Final host checkpoint

The full Desktop suite subsequently passed 399 tests with no failures/errors/skips (`outputs/shared-migration-20261016-desktop-final.log`). A separate real native FFM run passed 3/3 tests (`outputs/shared-migration-20261016-real-core-ffm.log`), including Core lifecycle/geosite and SOCKS authentication. These host checks supplement the converter fixtures; they do not establish runtime parity for every profile rule or close the remaining policy/resource/outbound boundaries above. Full session status: [checkpoint.md](checkpoint.md).
