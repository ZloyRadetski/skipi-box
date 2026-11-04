---
title: "Test the Windows TUN end to end against a copy of the app data, never the real settings file"
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 19, tokens: 74971 }
id: l-0c9e8b1f
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopWindowsTunRoutes.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/tunnel/DesktopWindowsTunRoutesTest.kt
---

## Decided
To check TUN for real, start the packaged app elevated with APPDATA pointed at a throwaway copy (configs, servers, subscriptions, settings with TUN mode and auto-connect on app open turned on), then read that copy's skipi.log, tun2socks.log and xray logs and run curl with --noproxy "*" through the tunnel. The user's own data stays untouched.

## Tried and ruled out
Editing the real settings.json and restoring it afterwards: Windows PowerShell 5.1 writes UTF-8 with a BOM, the app cannot parse that, falls back to defaults (TUN off, fixed port 10808) and saves them back, leaving the real file reduced to one line. Sending --connect right after launch: the link is handled before settings are loaded, so it connects with defaults and fails on port 10808 when another proxy holds it. Plain curl: the user-level HTTP_PROXY and HTTPS_PROXY variables point at 127.0.0.1:10808 and send curl through another proxy instead of the tunnel.

## Watch out
Write test files with .NET WriteAllText and a UTF8Encoding without BOM. A packaged build folder can be wiped by other Gradle runs, so check it exists before launching. The Windows DNS client sends queries out of the adapter that owns the DNS server, so they bypass the tunnel routes: UDP 53 never shows in Core's access.log. The user's own DNS server returns unblock-proxy addresses for some sites, which gives 403 through a VPN and is not a SKIPI fault.

In the code: the packaged app is built by :desktopApp:createDistributable; the data folder comes from the APPDATA variable in DesktopSettingsLibrary.kt.
