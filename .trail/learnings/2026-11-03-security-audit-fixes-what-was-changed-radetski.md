---
title: "Security audit fixes: what was changed, what was left on purpose and why"
author: Radetski
date: 2026-11-03
branch: main
cost: { minutes: 22, tokens: 170443 }
id: l-6e1dd17e
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/ui/clipboard/ClipboardText.kt
  - app/src/main/res/xml/file_paths.xml
  - shared/core/src/desktopTest/kotlin/features/resources/ResourceSnapshotFilesTest.kt
  - shared/core/src/jvmMain/kotlin/features/resources/ResourceSnapshotFiles.kt
  - shared/core/src/commonMain/kotlin/engine/xray/CustomXrayDnsRewriter.kt
  - shared/core/src/commonTest/kotlin/engine/xray/CustomXrayConfigHardeningTest.kt
  - app/src/main/kotlin/features/subscription/runtime/AndroidSubscriptionFetcher.kt
  - shared/core/src/commonMain/kotlin/features/subscription/SubscriptionRedirectPolicy.kt
  - shared/core/src/commonTest/kotlin/features/subscription/SubscriptionRedirectPolicyTest.kt
  - app/src/main/kotlin/engine/vpn/SkipiVpnService.kt
---

## Decided
- IPv6 is always captured by the tunnel (a ::/0 route even when IPv6 is off) so it is dropped instead of leaking around the VPN.
- During a network handover the old TUN is closed only after the new one exists; with the kill switch on, a failed recovery keeps the TUN up with no core (traffic blackholed) instead of stopping the VPN. Android lockdown itself can only be set by the user in system settings.
- Links that add servers or configs ask the user first; connect/disconnect/toggle links need the external-control setting, which is now off by default (existing installs keep their stored value). Widget actions carry a per-install token.
- Redirects of subscription fetches are vetted: no https to http, http(s) only, no jump to an internal address unless the user chose that host, and other hosts never get the device headers.
- Imported custom Xray configs lose api, metrics and reverse (they open sockets outside our inbounds).

## Tried and ruled out
- Making the shared custom-resource filename sanitizer drop ':' : an existing Android-parity test pins that ':' is kept. The colon is neutralised only where the file is written to disk.
- Treating "no kill switch" as a missing feature: the setting deliberately opens Android's Always-on/lockdown screen; only the in-app hold-on-failure was missing.

## Watch out
Not fixed and needs a decision or an on-device check: DNS leaks beyond port 53 (DoT/DoH), pre-connect hostname resolution in latency tests and subscriptions, plaintext secrets in the database and backup export, cleartext traffic allowed, resource downloads without hashes, and the desktop items (macOS root script symlinks, Windows temp hev config, unauthenticated LAN proxy default).
In the code: planVpnRoutes and handoverRecoveryFailureAction in engine/vpn/VpnLeakProtection.kt; SkipiDeepLink.gate in features/config/SkipiDeepLinkPolicy.kt; decideSubscriptionRedirect in features/subscription/SubscriptionRedirectPolicy.kt; redactXrayConfigForLog in engine/xray/XrayConfigLogRedaction.kt.
