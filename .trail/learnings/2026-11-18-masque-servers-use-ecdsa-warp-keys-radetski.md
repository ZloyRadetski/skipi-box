---
title: "MASQUE servers use ECDSA WARP keys and our own masque:// link format, neither comes from the Xray core"
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 13, tokens: 140671 }
id: l-cbea6fe0
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonTest/kotlin/features/proxy/server/model/MasqueTest.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/Masque.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/ProxyServer.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/ProxyServerPersistenceJson.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/ProxyServerEndpoint.kt
---

## Decided
MASQUE is a normal server type (Masque model) with either user/pass or a WARP identity; the core rejects both together. The masque:// link format is ours, the core defines none: masque://user:pass@host:port?sni&pinSHA256&host&path&dns&warp-private-key&warp-public-key&warp-address#remarks.
Path may only use the variables {target} and {ipproto}, as the core checks.

## Tried and ruled out
Reusing the WireGuard key check for WARP keys: they are ECDSA P-256 DER blobs (PKCS8/SEC1 private, PKIX public), so a 44-character WireGuard key check would reject real keys. Only "is base64" is checked, the core does the real parsing.

## Watch out
The core allows at most one IPv4 and one IPv6 in the WARP address list, plain IP or CIDR.
The Xray core already ships masque and xdrive through main/distro/all, so SKIPI Core needs no change, only this app does.
Not done yet in this slice: editor screen, Mihomo import, protocol colour, texts, per-protocol latency handling.

In the code: shared/core/.../features/proxy/server/model/Masque.kt, test MasqueTest.kt
