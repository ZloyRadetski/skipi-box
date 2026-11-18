---
title: A manual subscription fetch on desktop has the same 32 MB body limit as Android; the Android photo background is left alone
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 4, tokens: 19793 }
id: l-1a1e4bf2
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/subscription/DesktopSubscriptionFetcher.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/subscription/DesktopSubscriptionFetcherTest.kt
---

## Decided
Desktop manual subscription fetch is capped at 32 MB, the Android value. The 8 MB cap stays for automatic resource downloads only.

## Tried and ruled out
- Shrinking the Android background photo decode: the saved file is already at most 2560 px and covering a 1080x2400 screen needs about 2400 px of height, so a smaller decode saves almost nothing. RGB_565 would save about 10 MB but bands on gradients, a look change.
- Adding a trim hook for the image cache on UI hidden: Coil is believed to trim its own memory cache on system callbacks; not verified in code.

## Watch out
The existing test for a response above 8 MB must keep passing: manual fetches above the old cap are allowed, only unlimited reading is not.

In the code:
- DesktopSubscriptionFetcher.kt MaxDesktopManualSubscriptionResponseBytes
- DesktopSubscriptionFetcherTest a_manual_fetch_stops_at_the_android_body_limit_instead_of_reading_without_end
