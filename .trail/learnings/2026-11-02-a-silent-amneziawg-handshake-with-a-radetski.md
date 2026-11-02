---
title: "A silent AmneziaWG handshake with a valid-looking conf usually means a dead peer, not an app bug"
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 11, tokens: 81279 }
id: l-b91343cc
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonTest/kotlin/features/proxy/server/usecase/importer/AmneziaWgUserConfTest.kt
  - shared/core/src/jvmMain/kotlin/engine/vpn/AmneziaWgBridgePlan.kt
---

## Decided
- To triage an AWG conf that imports fine but never connects, run the real native runner against it before touching app code: import, validate, build the runner config, then start it through the Desktop Core and probe a URL.
- The runner accepting the config (no parse error) plus no handshake in 12 s, identical across stripped variants, points at the server side (peer revoked or port filtered).

## Tried and ruled out
- Stripping AmneziaWG 3.1 fields, then 3.0 fields, then I1: all fail the same way, so no single field is the cause.
- Using IP-literal probe targets to exclude DNS inside the tunnel: same timeout.

## Watch out
- The native runner silences its own logs, so a failed handshake only shows as a context deadline in skipi.log.
- Never commit a user's real conf: keys and endpoint in the regression test are placeholders.

In the code:
- Native config: shared/core/src/jvmMain/kotlin/engine/vpn/AmneziaWgBridgePlan.kt
- Opt-in runner tests: desktopApp/src/test/.../core/DesktopCoreAmneziaWgIntegrationTest.kt (SKIPI_CORE_INTEGRATION_DIR=desktopApp/build/generated/skipi-core-resources/common)
