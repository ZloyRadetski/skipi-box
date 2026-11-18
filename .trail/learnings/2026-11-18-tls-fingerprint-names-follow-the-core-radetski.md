---
title: "TLS fingerprint names follow the core's lists: TLS accepts unsafe and hellogolang, REALITY refuses both"
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 9, tokens: 58692 }
id: l-af0def0f
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/features/proxy/server/list/ProxyHomeAndroidInput.kt
  - app/src/main/kotlin/features/proxy/server/list/ProxyServerListPage.kt
  - app/src/main/kotlin/features/proxy/server/usecase/ProxyServerUseCases.kt
  - app/src/test/kotlin/features/proxy/server/list/ProxyHomeAndroidInputTest.kt
  - app/src/test/kotlin/features/proxy/server/usecase/ProxyServerListAddActionTest.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/home/ProxyHomeStore.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/server/ProxyServerDraftFactory.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/server/ProxyServerDraftFactoryTest.kt
  - shared/core/src/commonMain/kotlin/engine/xray/XrayConfigLogRedaction.kt
  - shared/core/src/commonMain/kotlin/engine/xray/XrayOutboundRenderer.kt
---

## Decided
The fingerprint check uses the full list of the core (presets, explicit versions like hellochrome_155, and the legacy hello names), case-insensitive because the core lowercases. The editor offers the presets and the explicit versions, and shows a stored value that is not in the list instead of falling back to the first entry.
"chrome" already is Chrome 155+ in this core, so the default needed no change.

## Tried and ruled out
Reading which of TLS and REALITY refuses unsafe and hellogolang from a grep of line numbers: I attributed the lines to the wrong builder and got it backwards. The core's own config builder says TLS accepts both and REALITY refuses both.

## Watch out
Check a rule of the core by running generated JSON through its parser (infra/conf from the version SKIPI Core uses), not by reading a grep hit without its function.

In the code: TlsFingerprint.kt, validateOptionalFingerprint in ProxyServerValidation.kt; test TlsFingerprintTest.kt
