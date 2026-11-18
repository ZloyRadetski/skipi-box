---
title: "MASQUE servers use WARP keys as PEM or padded standard base64, and our own masque:// link format"
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 15, tokens: 119465 }
id: l-934d39ca
scope: repo
status: active
replaces: l-4b885d48
src: agent
touches:
  - shared/core/src/desktopTest/kotlin/TmpDumpXrayConfigs.kt
  - shared/core/src/commonTest/kotlin/engine/xray/XdriveMuxOutboundTest.kt
---

## Decided
MASQUE is a server type with either user/pass or a WARP identity; the core rejects both together. The masque:// link format is ours: masque://user:pass@host:port?sni&pinSHA256&host&path&dns&warp-private-key&warp-public-key&warp-address#remarks.
WARP keys are ECDSA P-256 DER. The core takes a PEM block or base64.StdEncoding: padded, standard alphabet. Unpadded and URL-safe base64 are refused by the core, so validation refuses them too.

## Tried and ruled out
The WireGuard key check (44 characters) for WARP keys, and our lenient base64 decoder: both disagree with the core.

## Watch out
A link with an empty host such as masque://u:p@:443 is read by ktor as host localhost, as for every URL protocol. Not fixed.
Checked against the core's own config parser (infra/conf from the version SKIPI Core uses), not against a running core.
Not done: Mihomo import, headers and xmux, a custom colour in Appearance.

In the code: Masque.kt, ProxyServerMasqueEditor.kt; tests MasqueTest.kt
