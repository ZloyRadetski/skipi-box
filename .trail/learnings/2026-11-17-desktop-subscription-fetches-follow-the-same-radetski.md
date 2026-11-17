---
title: "Desktop subscription fetches follow the same redirect policy as Android, so another site never gets the device identity"
author: Radetski
date: 2026-11-17
branch: main
cost: { minutes: 30, tokens: 163256 }
id: l-3d8dfae9
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/engine/vpn/SkipiCoreRuntime.kt
  - app/src/main/kotlin/engine/vpn/SkipiVpnService.kt
  - app/src/main/kotlin/engine/xray/XrayCoreLogRuntime.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopConnectionLog.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/home/ConnectionLogReader.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/home/ConnectionLogReaderTest.kt
  - shared/app/src/desktopTest/kotlin/features/logs/ConnectionAccessLogFeedTest.kt
  - shared/app/src/jvmMain/kotlin/features/logs/ConnectionAccessLogFeed.kt
  - shared/ui/src/commonMain/composeResources/values-fa/strings.xml
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
---

## Decided
Both desktop fetch paths (HttpClient and HttpURLConnection) call the shared decideSubscriptionRedirect on every hop. A hop to another site is followed without the hardware id, device headers, age public key and embedded basic auth; a downgrade to http, a non-http(s) target or an internal address literal is refused. This is the Android behaviour, now pinned by desktop tests.

## Tried and ruled out
Nothing dropped. The gap was found by an audit of Desktop/Android duplicates: Desktop re-implemented redirect following and never called the shared policy, so a panel redirect could collect the HWID and reach the LAN.

## Watch out
Android has no provider/embedded-resource fetch, so the stricter requireSafeAutomaticSubscriptionResourceUrl exists only on Desktop. A redirect test needs a urlConnectionFactory that maps fake public hosts to a local server and a RecordingAgeCrypto (a made-up age key is rejected).

In the code: desktopApp DesktopSubscriptionFetcher.applyRedirectPolicy, shared/core SubscriptionRedirectPolicy.kt, DesktopSubscriptionFetcherTest (a_redirect_*).
