---
title: "MASQUE servers use WARP keys as PEM or padded standard base64, and our own masque:// link format"
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 20, tokens: 153567 }
id: l-5e0cf390
scope: repo
status: active
replaces: l-934d39ca
src: agent
touches:
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/ProxyServerValidation.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/TlsFingerprint.kt
  - shared/core/src/commonTest/kotlin/features/proxy/server/model/TlsFingerprintTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/server/editor/ProxyServerV2RayTransportEditor.kt
  - shared/core/src/commonTest/kotlin/features/proxy/server/usecase/importer/MihomoMasqueImportTest.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/usecase/importer/MihomoMasqueImport.kt
  - shared/core/src/commonTest/kotlin/app/CustomColorsMasqueTest.kt
---

## Decided
MASQUE is a server type with either user/pass or a WARP identity; the core rejects both together. The masque:// link format is ours: masque://user:pass@host:port?sni&pinSHA256&host&path&dns&warp-private-key&warp-public-key&warp-address&headers&xmux#remarks. Headers and xmux are raw JSON objects.
WARP keys are ECDSA P-256 DER. The core takes a PEM block or base64.StdEncoding: padded, standard alphabet. Unpadded and URL-safe base64 are refused by the core, so validation refuses them too.
Mihomo type masque (a WARP-style profile) is imported: ip and ipv6 become the WARP addresses, sni defaults to Mihomo's consumer-masque.cloudflareclient.com, uri becomes host and path, dns is used only with remote-dns-resolve. skip-cert-verify and network h2 or h3-l4proxy are refused. Mihomo has no xdrive, so there is nothing to import for it.
MASQUE has its own colour in Appearance, wired through CustomColors like the others.

## Tried and ruled out
The WireGuard key check (44 characters) for WARP keys, and our lenient base64 decoder: both disagree with the core.

## Watch out
A link with an empty host such as masque://u:p@:443 is read by ktor as host localhost, as for every URL protocol. Not fixed.
Checked against the core's own config parser (infra/conf of the version SKIPI Core uses), not against a running core.
Header rules of the core: valid names and values, no host or capsule-protocol, no Authorization together with user or pass. Log redaction masks all headers.

In the code: Masque.kt, MihomoMasqueImport.kt, ProxyServerMasqueEditor.kt, CustomColors.kt; tests MasqueTest.kt, MihomoMasqueImportTest.kt, CustomColorsMasqueTest.kt
