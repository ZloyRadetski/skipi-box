---
title: "vpn:// links are plain Base64 of an AmneziaWG or WireGuard config and go through the same conf parser"
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 3, tokens: 20840 }
id: l-1a37f3d3
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonMain/kotlin/features/proxy/server/usecase/importer/SharedProxyServerUrlImport.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/usecase/importer/VpnLinkImport.kt
  - shared/core/src/commonTest/kotlin/features/proxy/server/usecase/importer/VpnLinkImportTest.kt
---

## Decided
- A vpn:// link is read as Base64 (standard or URL-safe, padding optional) of a whole .conf and handed to the existing conf parser, so remarks, ranges and AmneziaWG 3.1 fields behave exactly like an imported .conf. It is recognised both as a line and inside surrounding text or a JSON subscription.

## Tried and ruled out
- Treating vpn:// as a new server type with its own fields: the conf parser already covers everything.

## Watch out
- Only the plain-Base64 conf form is supported. The official Amnezia client's own vpn:// format (compressed JSON) was not seen and is not handled; ask for a sample before adding it.
- The reporter's link used the first comment line of the conf as the server name.

In the code: shared/core/.../usecase/importer/VpnLinkImport.kt, SharedProxyServerUrlImport.kt, test VpnLinkImportTest
