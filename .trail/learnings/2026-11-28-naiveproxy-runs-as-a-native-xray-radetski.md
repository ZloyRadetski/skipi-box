---
title: "NaiveProxy runs as a native Xray outbound inside SKIPI Core through cronet-go, not as a second sing-box core or a SOCKS bridge"
author: Radetski
date: 2026-11-28
branch: feat/naive
cost: { minutes: 1, tokens: 2712 }
id: l-30d218d7
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/NaiveProxy.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/ProxyServer.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/ProxyServerEndpoint.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/model/ProxyServerPersistenceJson.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/usecase/importer/SharedProxyServerUrlImport.kt
  - shared/core/src/commonMain/kotlin/features/subscription/SubscriptionServerImport.kt
  - shared/core/src/commonTest/kotlin/engine/xray/XrayConfigLogRedactionTest.kt
  - shared/core/src/commonTest/kotlin/features/proxy/server/model/NaiveProxyTest.kt
  - shared/core/src/commonTest/kotlin/features/proxy/server/usecase/importer/ProxyServerUrlImportTest.kt
  - shared/core/src/commonTest/kotlin/features/subscription/SubscriptionServerImportTest.kt
---

## Decided
NaiveProxy is a `naive` outbound handler that SKIPI Core registers into Xray, built on sagernet/cronet-go (Chromium's network stack, so the TLS/H2/QUIC fingerprint is real Chrome). Xray keeps routing, balancers, chains (dialerProxy), stats and latency probes for naive like any other protocol, and the app side is shaped like Masque: the model only renders the outbound. Desktop loads libcronet lazily via purego on the first naive outbound; Android links a static libcronet per ABI through cgo. No build tag or capability flag gates naive: it is always in the core.

## Tried and ruled out
- sing-box as a second core: its naive outbound is a thin wrapper over the same cronet-go, so it adds nothing for naive. It would bring a second router/DNS stack, a localhost hop, restarts when servers change, and a shared Go dependency graph that has to keep two cores happy on every bump.
- A loopback SOCKS bridge like AmneziaWG: only one runner per process, a special path for probes, no per-server tags.
- libcronet from the cronet-go GitHub release v150 with Go code from the `go` branch: access violation in the dialer callback. The binary and the Go code must come from the same commit.

## Watch out
- Android needs NDK r28 or newer. r27 and r26d fail on arm64 with `unknown relocation (315)`, a PAuth relocation from Chromium.
- Cronet hands the server address to our dialer as a domain name and its own fake DNS never calls our resolver for it. The dialer must resolve through Xray's DNS.
- Return *net.TCPConn or *net.UDPConn from the dialer so cronet dups the fd. A wrapped conn falls back to a socketpair relay that costs up to 2x CPU.
- Import only the lib module for the target arch, one build-tagged file per Android ABI. `cronet-go/all` pulls every platform's ~70 MB archive into go.mod.

In the code: cronet-go pinned at go-branch commit ccefd17767e7 (as in sing-box 1.14.6), Android lib modules github.com/sagernet/cronet-go/lib/android_{arm64,arm,386,amd64}@2767273ce7ab, desktop tag with_purego. Measured: AAR 57 -> 77 MB, about +11 MB per ABI; libcronet.dll 9.9 MB; DoH off in NetLog (DnsServerOverride, secure_dns_mode 0).
