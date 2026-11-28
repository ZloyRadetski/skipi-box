---
title: "A download the user starts must not wait for Android to call the network connected, and the update route is the user's choice"
author: Radetski
date: 2026-11-28
branch: feat/net-merge
cost: { minutes: 19, tokens: 179784 }
id: l-c5ec9730
scope: repo
status: active
src: agent
---

## Decided
A manual update download has no WorkManager network constraint; it fails and retries when offline. Automatic downloads keep unmetered + charging. A switch (on by default) sends update checks and downloads through the running tunnel, or past it when off. Android only. The route is strict: no silent fallback to the other path, because that would override what the user chose.
In the code: appUpdateDownloadConstraints, AppState.updateAppViaProxy, AndroidUpdateTransport(viaProxy).

## Tried and ruled out
NetworkType.CONNECTED for manual downloads: on Android 8+ it requires a validated network, so where the system probe is blocked the work can stay queued at 0%. Found by reading the WorkManager 2.12 bytecode; not reproduced on an affected phone.

## Watch out
The periodic release check still uses CONNECTED, so it may not run on such networks; the manual check button is not affected.
