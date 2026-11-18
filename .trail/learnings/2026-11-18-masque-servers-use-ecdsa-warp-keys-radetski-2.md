---
title: "MASQUE servers use ECDSA WARP keys and our own masque:// link format, neither comes from the Xray core"
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 11, tokens: 78132 }
id: l-4b885d48
scope: repo
status: active
replaces: l-cbea6fe0
src: agent
touches:
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/server/display/ProtocolColorUtilsMasqueTest.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/server/ProxyServerDraftFactoryTest.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/server/editor/ProxyServerEditorRegistryTest.kt
  - app/src/test/kotlin/features/proxy/server/usecase/ProxyServerListAddActionTest.kt
  - app/src/test/kotlin/features/proxy/server/list/ProxyHomeAndroidInputTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/server/editor/ProxyServerMasqueEditor.kt
  - shared/core/src/commonTest/kotlin/features/proxy/server/usecase/importer/ProxyServerUrlImportTest.kt
---

## Decided
MASQUE is a normal server type (Masque model, editor, add-menu entry, import) with either user/pass or a WARP identity; the core rejects both together. The masque:// link format is ours, the core defines none: masque://user:pass@host:port?sni&pinSHA256&host&path&dns&warp-private-key&warp-public-key&warp-address#remarks.
Path may only use the variables {target} and {ipproto}, as the core checks.
MASQUE has its own default colour but no entry in Appearance custom colours: that would touch the app state, the backup and the settings screen.

## Tried and ruled out
Reusing the WireGuard key check for WARP keys: they are ECDSA P-256 DER blobs (PKCS8/SEC1 private, PKIX public), so a 44-character WireGuard key check would reject real keys. Only "is base64" is checked, the core does the real parsing.

## Watch out
The core allows at most one IPv4 and one IPv6 in the WARP address list, plain IP or CIDR.
The Xray core already ships masque and xdrive through main/distro/all, so SKIPI Core needs no change, only this app does.
A link with an empty host such as masque://u:p@:443 is read by ktor as host "localhost", so it imports as a server called localhost instead of failing. Not fixed, it is how every URL protocol is parsed.
Not done: Mihomo YAML import (the field names are unknown), headers and xmux in the model, a run against the real core.

In the code: shared/core/.../features/proxy/server/model/Masque.kt, shared/ui/.../server/editor/ProxyServerMasqueEditor.kt, tests MasqueTest.kt and ProxyServerUrlImportTest.kt
