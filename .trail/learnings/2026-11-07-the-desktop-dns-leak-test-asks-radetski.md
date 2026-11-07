---
title: "The Desktop DNS leak test asks the system DNS servers directly, not through the tunnel proxy"
author: Radetski
date: 2026-11-07
branch: main
cost: { minutes: 16, tokens: 205477 }
id: l-efc0933b
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonTest/kotlin/features/tools/dnsleak/DnsLeakAnalysisTest.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/tools/DnsLeakTestRunnerTest.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/tools/DiagnosticsControllersTest.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/tools/SystemPathDnsTxtResolverTest.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/diagnostics/SkipiDnsLeakModelTest.kt
  - shared/core/src/commonMain/kotlin/features/tools/dnsleak/DnsLeakResultPolicy.kt
  - shared/core/src/commonMain/kotlin/features/tools/dnsleak/DnsLeakModels.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/diagnostics/SkipiDnsLeakModel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/diagnostics/SkipiDnsLeakComponents.kt
  - shared/app/src/jvmMain/kotlin/app/skipi/app/tools/JvmDnsTxtResolver.kt
---

## Decided
The system DNS servers (LAN or ISP addresses) are asked the way the OS resolver would reach them, directly. Only the public resolvers travel through the tunnel's SOCKS proxy. Servers that never answer are listed on the screen under "No answer" instead of being dropped.

## Tried and ruled out
Asking every server through the SOCKS route: a private address like 192.168.x.x means nothing at the far end of the proxy, so the system servers went silent, were dropped, and the test only showed the public resolvers and almost always said "no leak".

## Watch out
If the packaged app's own traffic is excluded from the TUN, the direct probes to system servers would bypass the tunnel and could report a leak that the OS resolver does not have. The new screen was compiled and unit tested but not looked at in a running app.
In the code: SystemPathDnsTxtResolver, wired in DesktopDiagnostics.dnsLeak. Android keeps its established fallbackToDirect behaviour.
