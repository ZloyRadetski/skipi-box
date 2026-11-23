---
title: "Audit of 2026-11-22: what was fixed, and the findings left alone because a test pins the opposite"
author: Radetski
date: 2026-11-22
branch: main
cost: { minutes: 24, tokens: 287507 }
id: l-8a447345
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonTest/kotlin/engine/xray/XrayGeoRuleValidatorTest.kt
  - shared/app/src/jvmMain/kotlin/app/skipi/app/tools/DiagnosticsRoute.kt
  - shared/app/src/jvmMain/kotlin/features/logs/BoundedLogFileStore.kt
---

## Decided
- The shared SOCKS authenticator is one process-wide registry keyed by port, never removed, with no lock held across the transfer. A credential challenge counts as the proxy's only when the requestor type is PROXY or the protocol starts with SOCKS: the JDK reports a SOCKS handshake as a SERVER-type request with protocol SOCKS5, so the audit's "check RequestorType.PROXY" alone would have broken SOCKS auth.
- geosite:/geoip: always read the bundled .dat; only ext: names a file, and that name must be a plain file name. geosite:tag@attr is checked against the base tag.
- A bare http(s) line in a subscription body is no longer a routing config; only the routing headers and routing deep links are.

## Tried and ruled out
- ProxyHomeLatencyRunner rethrowing CancellationException (audit APP-01): ProxyHomeLatencyRunnerTest pins that a cancelled run marks the pending servers as failed. Reverted; needs a product decision.
- Splitting RULE-SET,ext: into domain or ip (audit FEAT-01): the frozen Android oracle test pins both fields on purpose. Reverted; needs a product decision.
- CORE-08 (AmneziaWG UAPI injection) was already fixed in skipi-core; the audit text was stale for it.

## Watch out
Not done: AND-12 cleartext traffic (needs the decision from the earlier audit), APP-03 log I/O inside the lock, and the required/optional performance findings. AndroidResourceFileUpdateBatchOracleTest is timing-flaky (failed once, passed twice on rerun).
In the code: engine/network/SubscriptionHttpSupport.kt (SocksAuthenticatorRegistry), engine/xray/XrayGeoRuleValidator.kt, features/subscription/SubscriptionRedirectPolicy.kt.
